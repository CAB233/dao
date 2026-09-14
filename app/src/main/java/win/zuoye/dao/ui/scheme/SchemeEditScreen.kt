package win.zuoye.dao.ui.scheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.ui.common.TemplateEditorDialog
import win.zuoye.dao.ui.common.rememberFabVisible
import win.zuoye.dao.ui.common.SegmentedSwitch
import win.zuoye.dao.ui.onboarding.AssignmentRow
import win.zuoye.dao.ui.onboarding.DeleteTemplateDialog
import win.zuoye.dao.ui.onboarding.TemplatePickDialog
import win.zuoye.dao.ui.onboarding.TemplatesStep

/** 日期滚轮的行高（miuix 默认 45dp，这里放开一点，数字别挨得太近） */
private val PICKER_ITEM_HEIGHT = 48.dp

/**
 * 单个方案的编辑页：最上面是方案名输入框（同时也是重命名入口），
 * 下面用 [TabRow] 在「班次模板」和「排班设置」之间切换。
 */
@Composable
fun SchemeEditScreen(
    doc: PlanDocument,
    scheme: Scheme,
    autoFocusName: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
) {
    BackHandler { onBack() }

    var tabIndex by rememberSaveable(scheme.id) { mutableIntStateOf(0) }
    var nameDraft by rememberSaveable(scheme.id) { mutableStateOf(scheme.name) }
    var cycleText by rememberSaveable(scheme.id) { mutableStateOf(scheme.cycleDays.toString()) }
    var showAnchorDialog by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ShiftTemplate?>(null) }
    var deleteTemplate by remember { mutableStateOf<ShiftTemplate?>(null) }
    var showDeleteScheme by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableIntStateOf(-1) }
    // 弹层关闭后还要播退出动画，所以记住最后一次打开的内容，动画期间继续渲染
    var shownPickDay by remember { mutableIntStateOf(-1) }
    var shownDeleteTemplate by remember { mutableStateOf<ShiftTemplate?>(null) }
    LaunchedEffect(pickingDay) { if (pickingDay >= 0) shownPickDay = pickingDay }
    LaunchedEffect(deleteTemplate) { deleteTemplate?.let { shownDeleteTemplate = it } }
    // 新建方案时把光标直接放进名字框；打开已有方案保持不高亮
    val nameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(scheme.id, autoFocusName) {
        if (autoFocusName) nameFocusRequester.requestFocus()
    }
    // 页签内容滚动时，右下角的加号收起来
    val contentScrollState = rememberScrollState()
    val fabVisible = rememberFabVisible { contentScrollState.value }

    fun updateScheme(transform: (Scheme) -> Scheme) {
        onMutate { plan ->
            plan.copy(schemes = plan.schemes.map { if (it.id == scheme.id) transform(it) else it }.toImmutableList())
        }
    }

    Scaffold(
        floatingActionButton = {
            // 「班次模板」页签下，加号在右下角；滚动时收起
            if (tabIndex == 0) {
                Box(Modifier.padding(end = 8.dp, bottom = 12.dp)) {
                    AnimatedVisibility(
                        visible = fabVisible,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    ) {
                        FloatingActionButton(
                            onClick = { editingTemplate = null; showEditor = true },
                            shadowElevation = 0.dp,
                            minWidth = 54.dp,
                            minHeight = 54.dp,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = "新增班次",
                                tint = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // ---- 最上面：返回 + 方案名输入框（默认不高亮，只显示当前值）----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Regular.Back, contentDescription = "返回方案列表")
                }
                TextField(
                    value = nameDraft,
                    onValueChange = { input ->
                        nameDraft = input
                        // 清空时先不落库，等用户打出内容再写，避免出现空名方案
                        if (input.isNotBlank()) updateScheme { it.copy(name = input.trim()) }
                    },
                    label = "方案名",
                    useLabelAsPlaceholder = true,
                    // 它就是这一页的标题：不要灰底，点进去才出现主题色描边；字号跟页面标题一致
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                    ),
                    textStyle = MiuixTheme.textStyles.title3,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                        .focusRequester(nameFocusRequester),
                )
            }
            Spacer(Modifier.height(12.dp))

            // ---- 班次模板 / 排班设置（和下面的卡片一样留 12dp 边距）----
            // 与「新增班次」里的开始/结束同一个样式（共用 SegmentedSwitch）。
            // 切换框的轨道是 surface、胶囊是 surfaceContainer，得落在 surfaceContainer 这一层
            // （卡片/弹窗）上才看得见——放在页面底色（也是 surface）上会整个隐形。
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SegmentedSwitch(
                    tabs = listOf("班次模板", "排班设置"),
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                    // 连同灰色轨道一起填满整张卡片
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            // 名字行与页签固定，只滚页签内容
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(contentScrollState),
            ) {
                when (tabIndex) {
                    0 -> TemplatesStep(
                        templates = doc.templates,
                        // 表头小标题去掉，加号挪到右下角 FAB
                        onAdd = null,
                        title = null,
                        onEdit = { editingTemplate = it; showEditor = true },
                        onDelete = { deleteTemplate = it },
                        editHoldDown = { showEditor && editingTemplate?.id == it.id },
                        deleteHoldDown = { deleteTemplate?.id == it.id },
                    )
                    else -> ShiftSettingsTab(
                        doc = doc,
                        scheme = scheme,
                        cycleText = cycleText,
                        onCycleChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(2)
                            cycleText = digits
                            val days = digits.toIntOrNull()
                            if (days != null && days in 1..99) {
                                updateScheme { current ->
                                    current.copy(
                                        cycleDays = days,
                                        dayTemplateIds = List(days) { index ->
                                            current.dayTemplateIds.getOrNull(index) ?: UNASSIGNED
                                        }.toImmutableList(),
                                    )
                                }
                            }
                        },
                        anchorHoldDown = showAnchorDialog,
                        onOpenAnchor = { showAnchorDialog = true },
                        onPickDay = { pickingDay = it },
                        pickingDay = pickingDay,
                    )
                }

                // ---- 删除方案（方案级操作，不放在页签里）----
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 4.dp)
                        .padding(bottom = 12.dp),
                ) {
                    BasicComponent(
                        title = "删除方案",
                        summary = "删除后无法恢复",
                        titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                        holdDownState = showDeleteScheme,
                        onClick = { showDeleteScheme = true },
                    )
                }
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }

        // ---- 弹层：都在 Scaffold 内部 ----
        AnchorDialog(
            anchor = Ymd.fromEpochDay(scheme.anchorEpochDay),
            show = showAnchorDialog,
            onDismiss = { showAnchorDialog = false },
            onConfirm = { date ->
                updateScheme { it.copy(anchorEpochDay = date.epochDay) }
                showAnchorDialog = false
            },
        )

        TemplateEditorDialog(
            show = showEditor,
            existing = editingTemplate,
            usedColors = doc.templates.map { it.colorArgb },
            onDismiss = { showEditor = false },
            onSave = { name, start, end, color ->
                // 先抓一份：onMutate 交给协程稍后执行，别在 lambda 里读可变的组合状态
                val editing = editingTemplate
                onMutate { plan ->
                    if (editing == null) {
                        plan.copy(
                            templates = plan.templates.toPersistentList().add(
                                ShiftTemplate(
                                    id = System.currentTimeMillis(),
                                    name = name,
                                    startMinute = start,
                                    endMinute = end,
                                    colorArgb = color,
                                ),
                            ),
                        )
                    } else {
                        plan.copy(
                            templates = plan.templates.map {
                                if (it.id == editing.id) {
                                    it.copy(name = name, startMinute = start, endMinute = end, colorArgb = color)
                                } else {
                                    it
                                }
                            }.toImmutableList(),
                        )
                    }
                }
                showEditor = false
            },
        )

        if (shownPickDay >= 0) {
            val day = shownPickDay
            TemplatePickDialog(
                templates = doc.templates,
                currentId = scheme.dayTemplateIds.getOrNull(day),
                show = pickingDay >= 0,
                onPick = { templateId ->
                    updateScheme { current ->
                        current.copy(
                            dayTemplateIds = current.dayTemplateIds.mapIndexed { index, id ->
                                if (index == day) templateId else id
                            }.toImmutableList(),
                        )
                    }
                    pickingDay = -1
                },
                onDismiss = { pickingDay = -1 },
            )
        }

        shownDeleteTemplate?.let { template ->
            DeleteTemplateDialog(
                template = template,
                inUse = doc.schemes.any { template.id in it.dayTemplateIds },
                show = deleteTemplate != null,
                onDismiss = { deleteTemplate = null },
                onConfirm = {
                    onMutate { plan ->
                        plan.copy(templates = plan.templates.filterNot { it.id == template.id }.toImmutableList())
                    }
                    deleteTemplate = null
                },
            )
        }

        OverlayDialog(
            show = showDeleteScheme,
            title = "删除「${scheme.name}」？",
            summary = "删除后无法恢复。",
            onDismissRequest = { showDeleteScheme = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteScheme = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        showDeleteScheme = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 排班设置：开始日期（点击弹日期设置）→ 周期天数 → 逐日指派 */
@Composable
private fun ShiftSettingsTab(
    doc: PlanDocument,
    scheme: Scheme,
    cycleText: String,
    onCycleChange: (String) -> Unit,
    anchorHoldDown: Boolean,
    onOpenAnchor: () -> Unit,
    onPickDay: (Int) -> Unit,
    pickingDay: Int,
) {
    val anchor = Ymd.fromEpochDay(scheme.anchorEpochDay)
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            BasicComponent(
                title = "开始日期",
                summary = "周期第 1 天：${formatYmd(anchor)}",
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp, 18.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                holdDownState = anchorHoldDown,
                onClick = onOpenAnchor,
            )
        }

        TextField(
            value = cycleText,
            onValueChange = onCycleChange,
            label = "周期天数（1–99）",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            // 表单不包卡片，但高度要和上下那几张卡片（约 74dp）对齐，所以纵向内边距给 26dp
            insideMargin = DpSize(TextFieldDefaults.InsideMargin.width, 26.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        )
        scheme.dayTemplateIds.forEachIndexed { day, templateId ->
            AssignmentRow(
                day = day,
                template = doc.templates.firstOrNull { it.id == templateId },
                onClick = { onPickDay(day) },
                holdDownState = pickingDay == day,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            )
        }
    }
}

/**
 * 开始日期弹窗：只滚「月 / 日」，年沿用方案当前锚点的年份。
 * 「月」「日」作为固定表头写在滚轮上方，不跟着数字滚动。
 */
@Composable
internal fun AnchorDialog(
    anchor: Ymd,
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Ymd) -> Unit,
) {
    // 每次打开都从当前锚点重新开始（弹窗常驻组合，不能只靠 remember 的 key）
    var month by remember(anchor) { mutableIntStateOf(anchor.month) }
    var day by remember(anchor) { mutableIntStateOf(anchor.day) }
    LaunchedEffect(show, anchor) {
        if (show) {
            month = anchor.month
            day = anchor.day
        }
    }
    val maxDay = Ymd.daysInMonth(anchor.year, month)

    OverlayDialog(
        show = show,
        title = "开始日期",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = "月",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = "日",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 不要给 NumberPicker 定高：它按 itemHeight × 可见行数自己算高度，
                // 外部硬压高度会把每行挤扁（数字挨得太近）。这里干脆把行距调大一点。
                NumberPicker(
                    value = month,
                    onValueChange = { month = it },
                    range = 1..12,
                    wrapAround = true,
                    label = { it.toString() },
                    itemHeight = PICKER_ITEM_HEIGHT,
                    modifier = Modifier.weight(1f),
                )
                NumberPicker(
                    value = day.coerceIn(1, maxDay),
                    onValueChange = { day = it },
                    range = 1..maxDay,
                    wrapAround = true,
                    label = { it.toString() },
                    itemHeight = PICKER_ITEM_HEIGHT,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = { onConfirm(Ymd(anchor.year, month, day.coerceIn(1, maxDay))) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
