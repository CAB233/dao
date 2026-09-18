package win.zuoye.dao

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.ThemeMode
import win.zuoye.dao.ui.about.AboutScreen
import win.zuoye.dao.ui.about.OpenSourceLicensesScreen
import win.zuoye.dao.ui.common.MainTab
import win.zuoye.dao.ui.common.MainBottomBar
import win.zuoye.dao.ui.common.MainNavigationRail
import win.zuoye.dao.ui.common.PageCardStack
import win.zuoye.dao.ui.common.localizedMessage
import win.zuoye.dao.ui.home.HomeScreen
import win.zuoye.dao.ui.onboarding.OnboardingScreen
import win.zuoye.dao.ui.scheme.PlanEditScreen
import win.zuoye.dao.ui.scheme.SchemeEditScreen
import win.zuoye.dao.ui.settings.SettingsScreen
import win.zuoye.dao.ui.share.SharePlanScreen
import win.zuoye.dao.ui.theme.AppTheme
import win.zuoye.dao.ui.update.UpdateDialog
import win.zuoye.dao.ui.update.UpdateInstallDialog
import win.zuoye.dao.update.AppUpdater
import win.zuoye.dao.update.UpdateDownloadWorker
import win.zuoye.dao.update.UpdateInfo
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Serializable
private sealed interface AppRoute : NavKey {
    /** 二级页面（卡片推入；Main = 停在底栏页面） */
    @Serializable data object Main : AppRoute
    @Serializable data object About : AppRoute
    @Serializable data object Licenses : AppRoute
    @Serializable data object SharePlan : AppRoute
}

@Serializable
private sealed interface EditorRoute : NavKey {
    @Serializable data object Tabs : EditorRoute
    @Serializable data class Edit(val scheme: Scheme, val autoFocusName: Boolean) : EditorRoute
}

class MainActivity : ComponentActivity() {

    /** 首帧数据是否读完；启动图据此决定什么时候撤下 */
    @Volatile
    private var planReady = false
    private val installRequestState = mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ 标准启动图；低版本由 core-splashscreen 兼容
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !planReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        handleUpdateInstallIntent(intent)
        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(applicationContext),
            )
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val themeMode = uiState.document?.themeMode ?: ThemeMode.SYSTEM
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            AppTheme(themeMode = themeMode) {
                Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { _ ->
                    // 根 Scaffold 为更新安装确认提供常驻弹层宿主，覆盖首次引导和主界面两种状态。
                    // 兜底：数据读取真出问题时也别一直卡在启动图上
                    LaunchedEffect(Unit) {
                        delay(2_000.milliseconds)
                        planReady = true
                    }
                    // 分享要从 Activity 发起（Application context 启动分享面板会闪退）
                    val activityContext = LocalContext.current
                    val resources = LocalResources.current
                    val docSnapshot = uiState.document
                    if (docSnapshot != null) planReady = true
                    LaunchedEffect(uiState.corruptionBackup) {
                        uiState.corruptionBackup?.let { backupName ->
                            val message = if (backupName.isNotEmpty()) {
                                resources.getString(R.string.plan_data_recovered_with_backup, backupName)
                            } else {
                                resources.getString(R.string.plan_data_recovered)
                            }
                            Toast.makeText(activityContext, message, Toast.LENGTH_LONG).show()
                            mainViewModel.acknowledgeCorruptionRecovery()
                        }
                    }
                    // 底栏标签页：单一来源（可跨进程恢复），二级页面单独记
                    var baseTab by rememberSaveable { mutableStateOf(MainTab.Home) }
                    val navBackStack = rememberNavBackStack<AppRoute>(AppRoute.Main)
                    var pendingPermissionInstall by remember { mutableStateOf<File?>(null) }
                    val requestedInstallApk by installRequestState
                    var shownInstallApk by remember { mutableStateOf<File?>(null) }
                    val doc = uiState.document

                    LaunchedEffect(requestedInstallApk) {
                        if (requestedInstallApk != null) shownInstallApk = requestedInstallApk
                    }

                    fun navigateTo(route: AppRoute) {
                        if (route == AppRoute.Main || route in navBackStack) return
                        while (navBackStack.size > 1) navBackStack.removeLastOrNull()
                        navBackStack.add(route)
                    }

                    fun popToMain() {
                        while (navBackStack.size > 1) navBackStack.removeLastOrNull()
                    }

                    fun openInstaller(apk: File) {
                        runCatching { AppUpdater.installApk(activityContext, apk) }
                            .onFailure {
                                Toast.makeText(activityContext, R.string.update_install_failed, Toast.LENGTH_LONG).show()
                            }
                    }

                    val unknownSourcesLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                    ) {
                        val apk = pendingPermissionInstall
                        pendingPermissionInstall = null
                        if (apk != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                                activityContext.packageManager.canRequestPackageInstalls())) {
                            openInstaller(apk)
                        } else {
                            Toast.makeText(activityContext, R.string.update_install_permission_required, Toast.LENGTH_LONG).show()
                        }
                    }

                    fun requestInstall(apk: File) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !activityContext.packageManager.canRequestPackageInstalls()) {
                            pendingPermissionInstall = apk
                            runCatching {
                                unknownSourcesLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        "package:${activityContext.packageName}".toUri(),
                                    ),
                                )
                            }.onFailure {
                                pendingPermissionInstall = null
                                Toast.makeText(
                                    activityContext,
                                    R.string.update_install_permission_required,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        } else {
                            openInstaller(apk)
                        }
                    }

                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (granted) {
                            mainViewModel.startUpdateDownload()
                        } else {
                            Toast.makeText(
                                activityContext,
                                R.string.update_notification_permission_required,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }

                    fun startUpdateDownload() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                activityContext,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            mainViewModel.startUpdateDownload()
                        }
                    }

                    LaunchedEffect(mainViewModel) {
                        mainViewModel.events.collect { event ->
                            when (event) {
                                MainEvent.UpdateCheckFailed -> Toast.makeText(
                                    activityContext,
                                    R.string.update_check_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                is MainEvent.ImportFinished -> Toast.makeText(
                                    activityContext,
                                    event.result.localizedMessage(resources),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }

                    fun saveNewScheme(
                        cycleDays: Int,
                        templates: ImmutableList<ShiftTemplate>,
                        dayTemplateIds: ImmutableList<Long>,
                        anchorEpochDay: Long,
                    ) {
                        val nextIndex = (doc?.schemes?.size ?: 0) + 1
                        mainViewModel.saveNewScheme(
                            cycleDays = cycleDays,
                            templates = templates,
                            dayTemplateIds = dayTemplateIds,
                            anchorEpochDay = anchorEpochDay,
                            planName = resources.getString(R.string.default_plan_name, nextIndex),
                            groupName = resources.getString(R.string.default_group_name, 1),
                        )
                        popToMain()
                        baseTab = MainTab.Home
                    }

                    fun importPlan(payload: PlanShare, completeOnboarding: Boolean = false) {
                        mainViewModel.importPlan(payload, completeOnboarding)
                    }

                    when {
                        // 启动图会盖住这段等待，正常看不到
                        doc == null -> Box(Modifier.fillMaxSize())
                        doc.activeScheme() == null && !doc.onboardingDone -> OnboardingScreen(
                            doc = doc,
                            editing = null,
                            onImportPlan = { importPlan(it, completeOnboarding = true) },
                            onSaveDocument = mainViewModel::saveDocument,
                            onSave = { cycle, templates, dayIds, anchor -> saveNewScheme(cycle, templates, dayIds, anchor) },
                            onSkip = mainViewModel::skipOnboarding,
                            onCancel = null,
                        )
                        else -> {
                            PageCardStack(backStack = navBackStack) {
                                entry<AppRoute.Main> {
                                    MainTabs(
                                        doc = doc,
                                        current = baseTab,
                                        onSelectTab = { baseTab = it },
                                        onExportPlan = { navigateTo(AppRoute.SharePlan) },
                                        onOpenAbout = { navigateTo(AppRoute.About) },
                                        onImportPlan = { importPlan(it) },
                                        onMutate = mainViewModel::mutate,
                                        updateInfo = uiState.updateInfo,
                                        showUpdateDialog = uiState.showUpdateDialog,
                                        showUpdateInSettings = uiState.showUpdateInSettings,
                                        onDismissUpdate = mainViewModel::dismissUpdate,
                                        onStartUpdate = ::startUpdateDownload,
                                    )
                                }
                                entry<AppRoute.About> {
                                    AboutScreen(
                                        onBack = ::popToMain,
                                        onOpenLicenses = {
                                            if (AppRoute.Licenses !in navBackStack) navBackStack.add(AppRoute.Licenses)
                                        },
                                    )
                                }
                                entry<AppRoute.Licenses> {
                                    OpenSourceLicensesScreen(onBack = {
                                        if (navBackStack.lastOrNull() == AppRoute.Licenses) navBackStack.removeLastOrNull()
                                    })
                                }
                                entry<AppRoute.SharePlan> {
                                    SharePlanScreen(doc = doc, onBack = ::popToMain)
                                }
                            }
                        }
                    }
                    UpdateInstallDialog(
                        show = requestedInstallApk != null,
                        onDismiss = { installRequestState.value = null },
                        onInstall = {
                            val apk = shownInstallApk ?: return@UpdateInstallDialog
                            installRequestState.value = null
                            requestInstall(apk)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateInstallIntent(intent)
    }

    private fun handleUpdateInstallIntent(intent: Intent?) {
        if (intent?.action != UpdateDownloadWorker.ACTION_CONFIRM_UPDATE_INSTALL) return
        val path = intent.getStringExtra(UpdateDownloadWorker.EXTRA_APK_PATH) ?: return
        val updateDirectory = runCatching { File(cacheDir, "updates").canonicalFile }.getOrNull() ?: return
        val apk = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        if (apk.parentFile == updateDirectory && apk.isFile && apk.extension.equals("apk", ignoreCase = true)) {
            installRequestState.value = apk
        }
    }
}

/**
 * 主页 / 配置 / 设置三页共用同一个 Scaffold 与导航区：导航区固定不动，
 * 内容区是一个 `HorizontalPager`，切换时整页横向滑动（对齐 InstallerX 的卡片式切换），
 * 顺带也能横滑切页，并且相邻页保持组合、来回切不会丢日历的浏览位置。
 */
@Composable
private fun MainTabs(
    doc: PlanDocument,
    current: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onExportPlan: () -> Unit,
    onOpenAbout: () -> Unit,
    onImportPlan: (PlanShare) -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
    updateInfo: UpdateInfo?,
    showUpdateDialog: Boolean,
    showUpdateInSettings: Boolean,
    onDismissUpdate: () -> Unit,
    onStartUpdate: () -> Unit,
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(initialPage = current.ordinal) { tabs.size }
    val editorStack = rememberNavBackStack<EditorRoute>(EditorRoute.Tabs)
    var planSelectionActive by remember { mutableStateOf(false) }

    fun closeSchemeEditor() {
        if (editorStack.size > 1) editorStack.removeLastOrNull()
    }

    fun openSchemeEditor(scheme: Scheme, autoFocusName: Boolean) {
        if (editorStack.size == 1) editorStack.add(EditorRoute.Edit(scheme, autoFocusName))
    }

    // 点底栏：把 pager 平滑滑过去（InstallerX 同款：整页滑动，不淡入淡出）
    LaunchedEffect(current) {
        if (pagerState.currentPage != current.ordinal) {
            pagerState.animateScrollToPage(
                page = current.ordinal,
                animationSpec = tween(durationMillis = 280, easing = EaseInOut),
            )
        }
    }
    // 手指横滑切页：停下来后同步回外部状态（返回键等逻辑依赖它）
    LaunchedEffect(pagerState.settledPage) {
        tabs.getOrNull(pagerState.settledPage)
            ?.let { tab -> if (tab != current) onSelectTab(tab) }
    }

    // 只让窗口宽度决定导航的摆放位置；绘制始终交给 miuix 组件。
    PageCardStack(backStack = editorStack) {
        entry<EditorRoute.Tabs> {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp
                Scaffold(
                    bottomBar = {
                        if (!useNavigationRail) {
                            MainBottomBar(
                                selected = current,
                                onSelect = onSelectTab,
                                enabled = !planSelectionActive,
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { padding ->
                    Row(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                    ) {
                        if (useNavigationRail) {
                            MainNavigationRail(
                                selected = current,
                                onSelect = onSelectTab,
                                enabled = !planSelectionActive,
                            )
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            // 相邻页保持组合：来回切的时候日历不会重置回本月
                            beyondViewportPageCount = 1,
                            overscrollEffect = null,
                            userScrollEnabled = editorStack.size == 1 && !planSelectionActive,
                        ) { page ->
                            when (tabs[page]) {
                                MainTab.Home -> HomeScreen(
                                    doc = doc,
                                    onExportPlan = onExportPlan,
                                    onOpenPlan = {
                                        val activeScheme = doc.activeScheme()
                                        if (activeScheme == null) {
                                            onSelectTab(MainTab.Config)
                                        } else {
                                            openSchemeEditor(activeScheme, false)
                                        }
                                    },
                                )
                                MainTab.Config -> PlanEditScreen(
                                    doc = doc,
                                    onBack = { onSelectTab(MainTab.Home) },
                                    onImportPlan = onImportPlan,
                                    onMutate = onMutate,
                                    showBackButton = false,
                                    backEnabled = pagerState.settledPage == page,
                                    onSelectionChange = { planSelectionActive = it },
                                    onEditScheme = ::openSchemeEditor,
                                )
                                MainTab.Settings -> SettingsScreen(
                                    doc = doc,
                                    updateInfo = updateInfo.takeIf { showUpdateInSettings },
                                    backEnabled = pagerState.settledPage == page,
                                    onBack = { onSelectTab(MainTab.Home) },
                                    onOpenAbout = onOpenAbout,
                                    onDownloadUpdate = onStartUpdate,
                                    onThemeModeChange = { themeMode ->
                                        onMutate { plan -> plan.copy(themeMode = themeMode) }
                                    },
                                    onWeekStartDayChange = { weekStartDay ->
                                        onMutate { plan -> plan.copy(weekStartDay = weekStartDay) }
                                    },
                                    onCalendarViewModeChange = { mode ->
                                        onMutate { plan -> plan.copy(calendarViewMode = mode) }
                                    },
                                    onCheckUpdatesOnLaunchChange = { enabled ->
                                        onMutate { plan -> plan.copy(checkUpdatesOnLaunch = enabled) }
                                    },
                                    onUpdateChannelChange = { channel ->
                                        onMutate { plan -> plan.copy(updateChannel = channel) }
                                    },
                                )
                            }
                        }
                    }
                    UpdateDialog(
                        show = showUpdateDialog,
                        update = updateInfo,
                        onDismiss = onDismissUpdate,
                        onUpdate = onStartUpdate,
                    )
                }
            }
        }
        entry<EditorRoute.Edit> { route ->
            val editorScheme = route.scheme
            SchemeEditScreen(
                doc = doc,
                scheme = editorScheme,
                autoFocusName = route.autoFocusName,
                onBack = { closeSchemeEditor() },
                onSave = { editedDocument ->
                    val editedScheme = editedDocument.schemes.first { it.id == editorScheme.id }
                    onMutate { currentDocument ->
                        val alreadyExists = currentDocument.schemes.any { it.id == editedScheme.id }
                        currentDocument.copy(
                            templates = editedDocument.templates,
                            schemes = if (alreadyExists) {
                                currentDocument.schemes.map { existing ->
                                    if (existing.id == editedScheme.id) editedScheme else existing
                                }.toImmutableList()
                            } else {
                                currentDocument.schemes.toPersistentList().add(editedScheme)
                            },
                            activeSchemeId = if (alreadyExists) {
                                currentDocument.activeSchemeId
                            } else {
                                editedScheme.id
                            },
                        )
                    }
                    closeSchemeEditor()
                },
                onDelete = {
                    onMutate { currentDocument ->
                        val remaining = currentDocument.schemes
                            .filterNot { it.id == editorScheme.id }
                            .toImmutableList()
                        currentDocument.copy(
                            schemes = remaining,
                            activeSchemeId = if (currentDocument.activeSchemeId == editorScheme.id) {
                                remaining.firstOrNull()?.id
                            } else {
                                currentDocument.activeSchemeId
                            },
                        )
                    }
                    closeSchemeEditor()
                },
            )
        }
    }
}
