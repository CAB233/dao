package win.zuoye.dao

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanRepository
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.domain.ImportResult
import win.zuoye.dao.domain.importPlan
import win.zuoye.dao.ui.home.HomeScreen
import win.zuoye.dao.ui.common.MainTab
import win.zuoye.dao.ui.common.MainBottomBar
import win.zuoye.dao.ui.about.AboutScreen
import win.zuoye.dao.ui.onboarding.OnboardingScreen
import win.zuoye.dao.ui.scheme.PlanEditScreen
import win.zuoye.dao.ui.share.SharePlanScreen
import top.yukonga.miuix.kmp.basic.Scaffold
import win.zuoye.dao.ui.settings.SettingsScreen
import win.zuoye.dao.ui.theme.AppTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

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
                    delay(2_000)
                    planReady = true
                }
                val repo = remember { PlanRepository.get(applicationContext) }
                // 分享要从 Activity 发起（Application context 启动分享面板会闪退）
                val activityContext = LocalContext.current
                val docState by repo.document.collectAsStateWithLifecycle(initialValue = null)
                val docSnapshot = docState
                if (docSnapshot != null) planReady = true
                // 底栏标签页：单一来源（可跨进程恢复），二级页面单独记
                var baseTab by rememberSaveable { mutableStateOf(MainTab.Home) }
                var pushedPage by remember { mutableStateOf<Screen?>(null) }
                val doc = docState

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
                                        name = "方案 ${current.schemes.size + 1}",
                                        cycleDays = cycleDays,
                                        anchorEpochDay = anchorEpochDay,
                                        dayTemplateIds = dayTemplateIds,
                                        createdAt = id,
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

                fun importPlan(payload: PlanShare) {
                    lifecycleScope.launch {
                        var result: ImportResult? = null
                        repo.update { current ->
                            val (merged, outcome) = current.importPlan(payload)
                            result = outcome
                            merged
                        }
                        Toast.makeText(activityContext, result?.message() ?: "导入失败", Toast.LENGTH_LONG).show()
                    }
                }

                when {
                    // 启动图会盖住这段等待，正常看不到
                    doc == null -> Box(Modifier.fillMaxSize())
                    doc.activeScheme() == null && !doc.onboardingDone -> OnboardingScreen(
                        doc = doc,
                        editing = null,
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
                            pushed = pushedPage != null,
                            base = {
                                MainTabs(
                                    doc = doc,
                                    current = baseTab,
                                    onSelectTab = { baseTab = it },
                                    onExportPlan = { pushedPage = Screen.SharePlan },
                                    onOpenAbout = { pushedPage = Screen.About },
                                    onOpenPlan = { pushedPage = Screen.Plan },
                                    onImportPlan = { importPlan(it) },
                                    onMutate = { transform -> lifecycleScope.launch { repo.update(transform) } },
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
 * 二级页面的进入 / 退出：卡片式推入，对齐 miuix 导航的默认过渡（`NavTransitions.MiuixDefault`）。
 *
 * - 底层页面（含底栏）**留在原地**，只做 1/4 宽度的视差 + 轻微压暗——不会整页滑走，也不会淡出；
 * - 二级页面作为一张圆角卡片从右侧整页滑入，滑到位时圆角收成 0、贴合屏幕；
 * - 卡片完全移出后才停止组合它（`cardComposed`），避免它的 BackHandler 等在收起后仍然生效。
 */
@Composable
private fun PageCardStack(
    pushed: Boolean,
    modifier: Modifier = Modifier,
    base: @Composable () -> Unit,
    card: @Composable () -> Unit,
) {
    // 1f = 完全露出底层，0f = 卡片完全贴合屏幕
    val progress = remember { Animatable(if (pushed) 0f else 1f) }
    var cardComposed by remember { mutableStateOf(pushed) }

    LaunchedEffect(pushed) {
        if (pushed) {
            cardComposed = true
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = EaseInOut),
            )
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = EaseInOut),
            )
            cardComposed = false
        }
    }

    val surface = MiuixTheme.colorScheme.surface
    val maxCorner = with(LocalDensity.current) { 32.dp.toPx() }

    Box(modifier.fillMaxSize().background(surface)) {
        // 底层页面（含底栏）完全不动，只被上面的遮罩压暗
        // （miuix 默认还会给底层加 1/4 宽度的视差，这里按需求去掉了）
        Box(Modifier.fillMaxSize()) { base() }

        if (cardComposed) {
            // 底层压暗；过渡中顺便吃掉落在底层的点击
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.5f * (1f - progress.value) }
                    .background(MiuixTheme.colorScheme.windowDimming)
                    .pointerInput(Unit) { detectTapGestures { } },
            )

            // 卡片本体：从右侧滑入，前段保持圆角，贴合时圆角归零
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        translationX = (p * size.width).roundToInt().toFloat()
                        // 卡片圆角逐帧跟着进度收放（贴合时归零），只能在 draw 阶段读进度，
                        // 所以这里用 graphicsLayer 的 shape 而不是 Dp 驱动的 squircle 修饰符
                        val corner = maxCorner * (p / 0.15f).coerceIn(0f, 1f)
                        shape = RoundedCornerShape(corner, 0f, 0f, corner)
                        clip = true
                    }
                    .background(surface),
            ) { card() }
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
    onImportPlan: (PlanShare) -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
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
                MainTab.Home -> HomeScreen(doc = doc, onExportPlan = onExportPlan)
                MainTab.Settings -> SettingsScreen(
                    doc = doc,
                    onBack = { onSelectTab(MainTab.Home) },
                    onOpenPlan = onOpenPlan,
                    onOpenAbout = onOpenAbout,
                    onImportPlan = onImportPlan,
                )
            }
        }
    }
}
