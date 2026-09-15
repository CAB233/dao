package win.zuoye.dao

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanRepository
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.domain.ImportResult
import win.zuoye.dao.domain.importPlan
import win.zuoye.dao.ui.about.AboutScreen
import win.zuoye.dao.ui.about.appVersionName
import win.zuoye.dao.ui.common.MainBottomBar
import win.zuoye.dao.ui.common.MainTab
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

private sealed interface Screen {
    /** 二级页面（卡片推入；null = 停在底栏页面） */
    data object About : Screen
    data object SharePlan : Screen
    data object Plan : Screen
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
        setContent {
            AppTheme {
                // 兜底：数据读取真出问题时也别一直卡在启动图上
                LaunchedEffect(Unit) {
                    delay(2_000.milliseconds)
                    planReady = true
                }
                val repo = remember { PlanRepository.get(applicationContext) }
                // 分享要从 Activity 发起（Application context 启动分享面板会闪退）
                val activityContext = LocalContext.current
                val resources = LocalResources.current
                val currentVersionName = remember(activityContext) { activityContext.appVersionName() }
                val docState by repo.document.collectAsStateWithLifecycle(initialValue = null)
                val docSnapshot = docState
                if (docSnapshot != null) planReady = true
                // 底栏标签页：单一来源（可跨进程恢复），二级页面单独记
                var baseTab by rememberSaveable { mutableStateOf(MainTab.Home) }
                var pushedPage by remember { mutableStateOf<Screen?>(null) }
                var updateCheckStarted by rememberSaveable { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var downloadingUpdate by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableStateOf<Int?>(null) }
                var pendingInstall by remember { mutableStateOf<File?>(null) }
                val doc = docState

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
                                    Uri.parse("package:${activityContext.packageName}"),
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

                fun startUpdateDownload() {
                    val update = updateInfo ?: return
                    downloadingUpdate = true
                    downloadProgress = null
                    lifecycleScope.launch {
                        try {
                            val apk = AppUpdater.downloadApk(
                                context = applicationContext,
                                update = update,
                                onProgress = { progress -> downloadProgress = progress },
                            )
                            showUpdateDialog = false
                            requestInstall(apk)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            showUpdateDialog = false
                            Toast.makeText(activityContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            downloadingUpdate = false
                        }
                    }
                }

                LaunchedEffect(doc) {
                    if (doc != null && !updateCheckStarted) {
                        updateCheckStarted = true
                        if (doc.checkUpdatesOnLaunch) {
                            try {
                                updateInfo = AppUpdater.checkForUpdate(
                                    currentVersionName = currentVersionName,
                                    channel = doc.updateChannel,
                                )
                                showUpdateDialog = updateInfo != null
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                Toast.makeText(
                                    activityContext,
                                    R.string.update_check_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }

                fun saveNewScheme(
                    cycleDays: Int,
                    templates: ImmutableList<ShiftTemplate>,
                    dayTemplateIds: ImmutableList<Long>,
                    anchorEpochDay: Long,
                ) {
                    lifecycleScope.launch {
                        repo.update { current ->
                            val id = System.currentTimeMillis()
                            current.copy(
                                templates = templates,
                                schemes = current.schemes.toPersistentList().add(
                                    Scheme(
                                        id = id,
                                        name = resources.getString(R.string.default_plan_name, current.schemes.size + 1),
                                        cycleDays = cycleDays,
                                        dayTemplateIds = dayTemplateIds,
                                        createdAt = id,
                                        groups = persistentListOf(
                                            SchemeGroup(
                                                id = id,
                                                name = resources.getString(R.string.default_group_name, 1),
                                                anchorEpochDay = anchorEpochDay,
                                            ),
                                        ),
                                        defaultGroupId = id,
                                    ),
                                ),
                                activeSchemeId = id,
                                onboardingDone = true,
                            )
                        }
                        pushedPage = null
                        baseTab = MainTab.Home
                    }
                }

                fun importPlan(payload: PlanShare, completeOnboarding: Boolean = false) {
                    lifecycleScope.launch {
                        var result: ImportResult? = null
                        repo.update { current ->
                            val (merged, outcome) = current.importPlan(payload)
                            result = outcome
                            if (completeOnboarding && outcome.changed) {
                                merged.copy(onboardingDone = true)
                            } else {
                                merged
                            }
                        }
                        Toast.makeText(
                            activityContext,
                            result?.localizedMessage(resources) ?: resources.getString(R.string.import_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }

                when {
                    // 启动图会盖住这段等待，正常看不到
                    doc == null -> Box(Modifier.fillMaxSize())
                    doc.activeScheme() == null && !doc.onboardingDone -> OnboardingScreen(
                        doc = doc,
                        editing = null,
                        onImportPlan = { importPlan(it, completeOnboarding = true) },
                        onSaveDocument = { savedDocument ->
                            lifecycleScope.launch { repo.update { savedDocument } }
                        },
                        onSave = { cycle, templates, dayIds, anchor -> saveNewScheme(cycle, templates, dayIds, anchor) },
                        onSkip = {
                            lifecycleScope.launch {
                                repo.update { it.copy(onboardingDone = true) }
                            }
                        },
                        onCancel = null,
                    )
                    else -> {
                        // 退出动画期间还要继续渲染这张卡片，所以记住最后一个二级页面
                        var cardRoute by remember { mutableStateOf<Screen?>(null) }
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
                                    onExportPlan = { pushedPage = Screen.SharePlan },
                                    onOpenAbout = { pushedPage = Screen.About },
                                    onOpenPlan = { pushedPage = Screen.Plan },
                                    onMutate = { transform -> lifecycleScope.launch { repo.update(transform) } },
                                    updateInfo = updateInfo,
                                    showUpdateDialog = showUpdateDialog,
                                    downloadingUpdate = downloadingUpdate,
                                    downloadProgress = downloadProgress,
                                    onDismissUpdate = { showUpdateDialog = false },
                                    onStartUpdate = ::startUpdateDownload,
                                )
                            },
                            card = {
                                when (val route = cardRoute) {
                                    Screen.About -> AboutScreen(onBack = { pushedPage = null })
                                    Screen.SharePlan -> SharePlanScreen(
                                        doc = doc,
                                        onBack = { pushedPage = null },
                                    )
                                    Screen.Plan -> PlanEditScreen(
                                        doc = doc,
                                        onBack = { pushedPage = null },
                                        onImportPlan = { importPlan(it) },
                                        onMutate = { transform -> lifecycleScope.launch { repo.update(transform) } },
                                    )
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
 * 主页 / 设置两页共用同一个 Scaffold 与底栏：底栏固定不动，
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

    Scaffold(
        bottomBar = { MainBottomBar(selected = current, onSelect = onSelectTab) },
        // 底部空间由底栏占据；各页面自己的 Scaffold/TopAppBar 负责其余 insets
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
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
