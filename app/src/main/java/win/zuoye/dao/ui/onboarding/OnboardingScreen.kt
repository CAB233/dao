package win.zuoye.dao.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.WindowDropdownDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.ui.ShiftPalette
import win.zuoye.dao.ui.common.TemplateEditorDialog
import win.zuoye.dao.ui.common.rememberHoldDownSource

private enum class Step(val label: String) {
    TEMPLATES("班次模板"), CYCLE_ASSIGN("周期与指派"), ANCHOR("开始日期")
}

/**
 * 首次启动的三步向导：
 * ①班次模板 → ②周期天数 + 逐日指派 → ③开始日期。
 * editing 非空 = 从现有方案预填（走修改流程）。
 */
@Composable
fun OnboardingScreen(
    doc: PlanDocument,
    editing: Scheme?,
    onSave: (
        cycleDays: Int,
        templates: ImmutableList<ShiftTemplate>,
        dayTemplateIds: ImmutableList<Long>,
        anchorEpochDay: Long,
    ) -> Unit,
    onSkip: (() -> Unit)?,
    onCancel: (() -> Unit)?,
) {
    BackHandler(enabled = editing != null) { onCancel?.invoke() }

    var step by remember { mutableStateOf(Step.TEMPLATES) }
    var userTemplates by remember {
        mutableStateOf(doc.templates.filter { !it.isRest }.toPersistentList())
    }
    var cycleText by remember { mutableStateOf(editing?.cycleDays?.toString() ?: "") }
    var assignments by remember {
        mutableStateOf(
            editing?.dayTemplateIds
                ?.map { if (doc.templateById(it) != null) it else null }
                ?.toPersistentList()
                ?: persistentListOf<Long?>(),
        )
    }
    var anchor by remember {
        mutableStateOf(editing?.let { Ymd.fromEpochDay(it.anchorEpochDay) } ?: Ymd.today())
    }
    var showEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ShiftTemplate?>(null) }
    var pickingDay by remember { mutableIntStateOf(-1) }
    // 弹层关闭后还要播退出动画，所以记住最后一次打开的是哪一天，动画期间继续渲染
    var shownPickDay by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pickingDay) { if (pickingDay >= 0) shownPickDay = pickingDay }

    val cycleDays: Int? = cycleText.toIntOrNull()?.takeIf { it in 1..99 }

    fun syncAssignments(n: Int) {
        var next = assignments
        while (next.size < n) next = next.add(null)
        while (next.size > n) next = next.removeAt(next.size - 1)
        assignments = next
    }

    fun next() {
        when (step) {
            Step.TEMPLATES -> {
                cycleDays?.let { syncAssignments(it) }
                step = Step.CYCLE_ASSIGN
            }
            Step.CYCLE_ASSIGN -> step = Step.ANCHOR
            Step.ANCHOR -> onSave(
                cycleDays!!,
                userTemplates,
                assignments.map { requireNotNull(it) }.toImmutableList(),
                anchor.epochDay,
            )
        }
    }

    fun back() {
        step = when (step) {
            Step.CYCLE_ASSIGN -> Step.TEMPLATES
            Step.ANCHOR -> Step.CYCLE_ASSIGN
            else -> Step.TEMPLATES
        }
    }

    val canNext = when (step) {
        Step.TEMPLATES -> userTemplates.isNotEmpty()
        Step.CYCLE_ASSIGN -> cycleDays != null && assignments.size == cycleDays && assignments.all { it != null }
        Step.ANCHOR -> true
    }
    // 唯一的滚动源（第 2 步的列表），顶栏折叠与列表滚动共用同一个 behavior
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (editing == null) "创建倒班安排" else "修改方案",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (editing != null && step == Step.TEMPLATES) {
                        IconButton(onClick = { onCancel?.invoke() }) {
                            Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            StepIndicator(step)
            Box(Modifier.weight(1f)) {
                when (step) {
                    Step.TEMPLATES -> TemplatesStep(
                        templates = userTemplates,
                        onAdd = { editingTemplate = null; showEditor = true },
                        onEdit = { editingTemplate = it; showEditor = true },
                        onDelete = { deleted ->
                            userTemplates = userTemplates.filterNot { it.id == deleted.id }.toPersistentList()
                            // 指向已删班次的指派一并清空，避免存下悬空 id
                            assignments = assignments
                                .map { if (it == deleted.id) null else it }
                                .toPersistentList()
                        },
                        addHoldDown = showEditor && editingTemplate == null,
                        editHoldDown = { showEditor && editingTemplate?.id == it.id },
                    )
                    Step.CYCLE_ASSIGN -> CycleAssignStep(
                        cycleText = cycleText,
                        onCycleChange = { input ->
                            val filtered = input.filter(Char::isDigit).take(2)
                            cycleText = filtered
                            filtered.toIntOrNull()?.let { syncAssignments(it) }
                        },
                        cycleDays = cycleDays,
                        assignments = assignments,
                        templates = userTemplates,
                        pickingDay = pickingDay,
                        onPick = { pickingDay = it },
                        scrollBehavior = scrollBehavior,
                    )
                    Step.ANCHOR -> AnchorStep(anchor) { anchor = it }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    step == Step.TEMPLATES && onSkip != null -> TextButton(
                        text = "跳过设置",
                        onClick = onSkip,
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    step != Step.TEMPLATES -> TextButton(
                        text = "上一步",
                        onClick = { back() },
                        modifier = Modifier.weight(1f),
                    )
                    else -> Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = { next() },
                    enabled = canNext,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (step == Step.ANCHOR) "完成" else "下一步")
                }
            }
        }

        // 对话框必须挂在 Scaffold 内部（依赖 Scaffold 提供的弹层宿主）
        TemplateEditorDialog(
            show = showEditor,
            existing = editingTemplate,
            usedColors = userTemplates.map { it.colorArgb },
            onDismiss = { showEditor = false },
            onSave = { name, start, end, color ->
                val current = editingTemplate
                if (current == null) {
                    userTemplates = userTemplates.add(
                        ShiftTemplate(
                            id = System.currentTimeMillis(),
                            name = name,
                            startMinute = start,
                            endMinute = end,
                            colorArgb = color,
                        )
                    )
                } else {
                    val idx = userTemplates.indexOfFirst { it.id == current.id }
                    if (idx >= 0) {
                        userTemplates = userTemplates.set(
                            idx,
                            current.copy(name = name, startMinute = start, endMinute = end, colorArgb = color),
                        )
                    }
                }
                showEditor = false
            },
        )

        if (shownPickDay >= 0) {
            TemplatePickDialog(
                templates = userTemplates,
                currentId = assignments.getOrNull(shownPickDay),
                show = pickingDay >= 0,
                onPick = { id ->
                    if (shownPickDay < assignments.size) {
                        assignments = assignments.set(shownPickDay, id)
                    }
                    pickingDay = -1
                },
                onDismiss = { pickingDay = -1 },
            )
        }
    }
}

@Composable
private fun StepIndicator(step: Step) {
    val index = Step.entries.indexOf(step)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Step.entries.forEachIndexed { i, _ ->
            Box(
                Modifier
                    .size(width = if (i == index) 28.dp else 12.dp, height = 6.dp)
                    .squircleBackground(
                        color = if (i <= index) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.dividerLine
                        },
                        cornerRadius = 3.dp,
                    )
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${index + 1}/3 ${step.label}",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 第 1 步：定义班次模板（名称 + 时间 + 颜色），后续逐日指派时点选复用。方案页复用同一组件。 */
@Composable
internal fun TemplatesStep(
    templates: ImmutableList<ShiftTemplate>,
    onAdd: () -> Unit,
    onEdit: (ShiftTemplate) -> Unit,
    onDelete: (ShiftTemplate) -> Unit,
    title: String = "我的班次",
    hint: String? = "在这里定义好每个班次，下一步安排周期时直接点选即可，无需重复输入时间。",
    addHoldDown: Boolean = false,
    editHoldDown: (ShiftTemplate) -> Boolean = { false },
    deleteHoldDown: (ShiftTemplate) -> Boolean = { false },
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(text = title, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd, holdDownState = addHoldDown) {
                Icon(MiuixIcons.Regular.Add, contentDescription = "新增班次")
            }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            if (templates.isEmpty()) {
                Text(
                    "还没有班次。点右上角 + 添加一栏，例如：早班 08:00–15:00",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
            templates.forEach { template ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).background(ShiftPalette.color(template.colorArgb), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, fontSize = 16.sp)
                        Text(
                            ShiftTemplate.format(template.startMinute) + "–" +
                                (if (template.crossesMidnight()) "次日" else "") +
                                ShiftTemplate.format(template.endMinute),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    IconButton(onClick = { onEdit(template) }, holdDownState = editHoldDown(template)) {
                        Icon(MiuixIcons.Regular.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { onDelete(template) }, holdDownState = deleteHoldDown(template)) {
                        Icon(MiuixIcons.Regular.Delete, contentDescription = "删除")
                    }
                }
            }
        }
        if (hint != null) {
            Text(
                hint,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** 周期里某一天的指派行（向导与方案页共用） */
@Composable
internal fun AssignmentRow(
    day: Int,
    template: ShiftTemplate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    holdDownState: Boolean = false,
) {
    val interactionSource = rememberHoldDownSource(holdDownState)
    Row(
        modifier
            .fillMaxWidth()
            // 可点击：squircleSurface 负责底色 + 把涟漪裁进 squircle
            .squircleSurface(color = MiuixTheme.colorScheme.surfaceVariant, cornerRadius = 12.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("第 ${day + 1} 天", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        if (template == null) {
            Text("点击选择", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            Box(Modifier.size(12.dp).background(ShiftPalette.color(template.colorArgb), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(template.name, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                template.timeRangeText(),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 第 2 步：输入周期天数，并为周期内每一天点选班次 */
@Composable
private fun CycleAssignStep(
    cycleText: String,
    onCycleChange: (String) -> Unit,
    cycleDays: Int?,
    assignments: ImmutableList<Long?>,
    templates: ImmutableList<ShiftTemplate>,
    pickingDay: Int,
    onPick: (Int) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // 底部由外层按钮行占据，contentPadding 只留 top
        contentPadding = PaddingValues(top = 4.dp),
    ) {
        item {
            // TextField 表单不包 Card，直接同样的 12dp 间距
            TextField(
                value = cycleText,
                onValueChange = onCycleChange,
                label = "1–99 天，如 4",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            )
        }
        if (cycleDays != null) {
            item {
                SmallTitle(text = "为周期内每天选择班次")
            }
            items(cycleDays) { day ->
                AssignmentRow(
                    day = day,
                    template = templates.firstOrNull { it.id == assignments.getOrNull(day) },
                    onClick = { onPick(day) },
                    holdDownState = day == pickingDay,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                )
            }
        }
        item {
            // 这是向导步骤，下方还有按钮行；Scaffold 已按系统栏留白，不再重复算导航栏
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 第 3 步：锚点日期（周期第 1 天是哪天）。方案页复用同一组件。 */
@Composable
internal fun AnchorStep(
    anchor: Ymd,
    title: String = "周期第 1 天从哪天开始算？",
    hint: String? = "设置后，日历会按周期自动推导任意日期的班次。",
    onChange: (Ymd) -> Unit,
) {
    var year by remember(anchor) { mutableIntStateOf(anchor.year) }
    var month by remember(anchor) { mutableIntStateOf(anchor.month) }
    var day by remember(anchor) { mutableIntStateOf(anchor.day) }
    val maxDay = Ymd.daysInMonth(year, month)
    Column(Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = year,
                    onValueChange = {
                        year = it
                        onChange(Ymd(it, month, day.coerceAtMost(Ymd.daysInMonth(it, month))))
                    },
                    range = 2000..2100,
                    label = { "${it}年" },
                    modifier = Modifier.weight(1.2f).height(150.dp),
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = month,
                    onValueChange = {
                        month = it
                        onChange(Ymd(year, it, day.coerceAtMost(Ymd.daysInMonth(year, it))))
                    },
                    range = 1..12,
                    wrapAround = true,
                    label = { "${it}月" },
                    modifier = Modifier.weight(1f).height(150.dp),
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
                    value = day.coerceIn(1..maxDay),
                    onValueChange = { day = it; onChange(Ymd(year, month, it)) },
                    range = 1..maxDay,
                    wrapAround = true,
                    label = { "${it}日" },
                    modifier = Modifier.weight(1f).height(150.dp),
                )
            }
        }
        TextButton(
            text = "重置为今天",
            onClick = { onChange(Ymd.today()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        )
        if (hint != null) {
            Text(
                hint,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** 选择某天用哪个班次：单选互斥，用 miuix 的 WindowDropdownDialog（选项列表全出血 + 行内 24dp 内缩） */
@Composable
internal fun TemplatePickDialog(
    templates: ImmutableList<ShiftTemplate>,
    currentId: Long?,
    show: Boolean,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    if (templates.isEmpty()) {
        OverlayDialog(show = show, title = "选择班次", onDismissRequest = onDismiss) {
            Text(
                "请先在第一步添加班次。",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    val entry = remember(templates, currentId, onPick) {
        DropdownEntry(
            items = templates.map { template ->
                DropdownItem(
                    text = template.name,
                    summary = template.timeRangeText(),
                    selected = template.id == currentId,
                    onClick = { onPick(template.id) },
                    icon = { iconModifier ->
                        Box(
                            iconModifier
                                .size(14.dp)
                                .background(ShiftPalette.color(template.colorArgb), CircleShape)
                        )
                    },
                )
            },
        )
    }
    WindowDropdownDialog(
        entry = entry,
        title = "选择班次",
        dialogButtonString = "取消",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = {},
        dropdownColors = DropdownDefaults.dropdownColors(),
    )
}

/** 删除班次确认（取消 | 删除，确认按钮用主色文字） */
@Composable
internal fun DeleteTemplateDialog(
    template: ShiftTemplate,
    inUse: Boolean = false,
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "删除班次「${template.name}」？",
        summary = if (inUse) "该班次正被方案使用，删除后相关日期会显示「未排班」。" else "确定删除该班次吗？",
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton(
                text = "删除",
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
