package win.zuoye.dao

import android.content.Intent
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Scaffold
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.ThemeMode
import win.zuoye.dao.ui.about.AboutScreen
import win.zuoye.dao.ui.common.MainTab
import win.zuoye.dao.ui.common.MainBottomBar
import win.zuoye.dao.ui.common.MainNavigationRail
import win.zuoye.dao.ui.common.PageCardStack
import win.zuoye.dao.ui.common.localizedMessage
import win.zuoye.dao.ui.home.HomeScreen
import win.zuoye.dao.ui.onboarding.OnboardingScreen
import win.zuoye.dao.ui.scheme.PlanEditScreen
import win.zuoye.dao.ui.settings.SettingsScreen
import win.zuoye.dao.ui.share.SharePlanScreen
import win.zuoye.dao.ui.theme.AppTheme
import win.zuoye.dao.ui.update.UpdateDialog
import win.zuoye.dao.update.AppUpdater
import win.zuoye.dao.update.UpdateInfo
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Serializable
private sealed interface AppRoute : NavKey {
    /** 二级页面（卡片推入；Main = 停在底栏页面） */
    @Serializable data object Main : AppRoute
    @Serializable data object About : AppRoute
    @Serializable data object SharePlan : AppRoute
    @Serializable data object Plan : AppRoute
}

class MainActivity : ComponentActivity() {

    /** 首帧数据是否读完；启动图据此决定什么时候撤下 */
    @Volatile
    private var planReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ 标准启动图；低版本由 core-splashscreen 兼容
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !planReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
                val navBackStack = rememberNavBackStack(AppRoute.Main)
                val pushedPage = navBackStack.lastOrNull()?.takeUnless { it == AppRoute.Main } as? AppRoute
                var pendingInstall by remember { mutableStateOf<File?>(null) }
                val doc = uiState.document

                fun navigateTo(route: AppRoute) {
                    if (route == AppRoute.Main) return
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
                    val apk = pendingInstall
                    pendingInstall = null
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
                        pendingInstall = apk
                        runCatching {
                            unknownSourcesLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    "package:${activityContext.packageName}".toUri(),
                                ),
                            )
                        }.onFailure {
                            pendingInstall = null
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

                LaunchedEffect(mainViewModel) {
                    mainViewModel.events.collect { event ->
                        when (event) {
                            MainEvent.UpdateCheckFailed -> Toast.makeText(
                                activityContext,
                                R.string.update_check_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                            MainEvent.UpdateDownloadFailed -> Toast.makeText(
                                activityContext,
                                R.string.update_download_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                            is MainEvent.InstallUpdate -> requestInstall(event.apk)
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
                        // 退出动画期间还要继续渲染这张卡片，所以记住最后一个二级页面
                        var cardRoute by remember { mutableStateOf<AppRoute?>(null) }
                        LaunchedEffect(pushedPage) {
                            if (pushedPage != null) cardRoute = pushedPage
                        }
                        PageCardStack(
                            visible = pushedPage != null,
                            base = {
                                MainTabs(
                                    doc = doc,
                                    current = baseTab,
                                    onSelectTab = { baseTab = it },
                                    onExportPlan = { navigateTo(AppRoute.SharePlan) },
                                    onOpenAbout = { navigateTo(AppRoute.About) },
                                    onOpenPlan = { navigateTo(AppRoute.Plan) },
                                    onMutate = mainViewModel::mutate,
                                    updateInfo = uiState.updateInfo,
                                    showUpdateDialog = uiState.showUpdateDialog,
                                    downloadingUpdate = uiState.downloadingUpdate,
                                    downloadProgress = uiState.downloadProgress,
                                    onDismissUpdate = mainViewModel::dismissUpdate,
                                    onStartUpdate = mainViewModel::startUpdateDownload,
                                )
                            },
                            card = {
                                when (val route = cardRoute) {
                                    AppRoute.About -> AboutScreen(onBack = ::popToMain)
                                    AppRoute.SharePlan -> SharePlanScreen(
                                        doc = doc,
                                        onBack = ::popToMain,
                                    )
                                    AppRoute.Plan -> PlanEditScreen(
                                        doc = doc,
                                        onBack = ::popToMain,
                                        onImportPlan = { importPlan(it) },
                                        onMutate = mainViewModel::mutate,
                                    )
                                    AppRoute.Main -> Unit
                                    null -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 主页 / 设置两页共用同一个 Scaffold 与导航区：导航区固定不动，
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
    onOpenPlan: () -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
    updateInfo: UpdateInfo?,
    showUpdateDialog: Boolean,
    downloadingUpdate: Boolean,
    downloadProgress: Int?,
    onDismissUpdate: () -> Unit,
    onStartUpdate: () -> Unit,
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(initialPage = current.ordinal) { tabs.size }

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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        Scaffold(
            bottomBar = {
                if (!useNavigationRail) {
                    MainBottomBar(selected = current, onSelect = onSelectTab)
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
                    MainNavigationRail(selected = current, onSelect = onSelectTab)
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    // 相邻页保持组合：来回切的时候日历不会重置回本月
                    beyondViewportPageCount = 1,
                    overscrollEffect = null,
                ) { page ->
                    when (tabs[page]) {
                        MainTab.Home -> HomeScreen(
                            doc = doc,
                            onExportPlan = onExportPlan,
                            onOpenPlan = onOpenPlan,
                        )
                        MainTab.Settings -> SettingsScreen(
                            doc = doc,
                            onBack = { onSelectTab(MainTab.Home) },
                            onOpenPlan = onOpenPlan,
                            onOpenAbout = onOpenAbout,
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
                downloading = downloadingUpdate,
                downloadProgress = downloadProgress,
                onDismiss = onDismissUpdate,
                onUpdate = onStartUpdate,
            )
        }
    }
}
