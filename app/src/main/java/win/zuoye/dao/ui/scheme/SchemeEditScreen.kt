package win.zuoye.dao.ui.scheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.WindowDropdownDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.editableGroups
import win.zuoye.dao.data.primaryAnchorEpochDay
import win.zuoye.dao.ui.common.TemplateEditorDialog
import win.zuoye.dao.ui.common.rememberHoldDownSource
import win.zuoye.dao.ui.common.rememberFabVisible
import win.zuoye.dao.ui.common.SegmentedSwitch
import win.zuoye.dao.ui.onboarding.AssignmentRow
import win.zuoye.dao.ui.onboarding.DeleteTemplateDialog
import win.zuoye.dao.ui.onboarding.TemplatePickDialog
import win.zuoye.dao.ui.onboarding.TemplatesStep

/** 日期滚轮的行高（miuix 默认 45dp，这里放开一点，数字别挨得太近） */
private val PICKER_ITEM_HEIGHT = 48.dp

/**
 * 单个方案的编辑页：顶部是返回栏，正文依次放方案名输入框和
 * 「班次模板 / 排班设置 / 班组设置」切换框。
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
    var showCycleDialog by remember { mutableStateOf(false) }
    var cycleDraft by rememberSaveable(scheme.id) { mutableStateOf(scheme.cycleDays.toString()) }
    var groupCountText by rememberSaveable(scheme.id) {
        mutableStateOf(scheme.editableGroups().size.toString())
    }
    var showGroupCountDialog by remember { mutableStateOf(false) }
    var groupCountDraft by rememberSaveable(scheme.id) {
        mutableStateOf(scheme.editableGroups().size.toString())
    }
    var showDefaultGroupDialog by remember { mutableStateOf(false) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var editingGroupIndex by rememberSaveable(scheme.id) { mutableIntStateOf(-1) }
    var groupNameDraft by rememberSaveable(scheme.id) { mutableStateOf("") }
    var groupAnchorEpochDay by rememberSaveable(scheme.id) {
        mutableStateOf(scheme.primaryAnchorEpochDay())
    }
    var showGroupAnchorDialog by remember { mutableStateOf(false) }
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
    val groups = remember(scheme.id, scheme.groups, scheme.anchorEpochDay) { scheme.editableGroups() }
    val defaultGroup = groups.firstOrNull { it.id == scheme.defaultGroupId } ?: groups.firstOrNull()

    fun updateScheme(transform: (Scheme) -> Scheme) {
        onMutate { plan ->
            plan.copy(schemes = plan.schemes.map { if (it.id == scheme.id) transform(it) else it }.toImmutableList())
        }
    }

    fun updateCycle(input: String) {
        val days = input.toIntOrNull()?.takeIf { it in 1..99 } ?: return
        cycleText = days.toString()
        updateScheme { current ->
            current.copy(
                cycleDays = days,
                dayTemplateIds = List(days) { index ->
                    current.dayTemplateIds.getOrNull(index) ?: UNASSIGNED
                }.toImmutableList(),
            )
        }
    }

    fun updateGroupCount(input: String) {
        val count = input.toIntOrNull()?.takeIf { it in 1..99 } ?: return
        groupCountText = count.toString()
        updateScheme { current ->
            val existing = current.editableGroups()
            val next = List(count) { index ->
                existing.getOrNull(index) ?: SchemeGroup(
                    id = System.currentTimeMillis() + index,
                    name = "班组 ${index + 1}",
                    anchorEpochDay = existing.firstOrNull()?.anchorEpochDay
                        ?: current.primaryAnchorEpochDay(),
                )
            }.toImmutableList()
            val defaultGroupId = next.firstOrNull { it.id == current.defaultGroupId }?.id
                ?: next.first().id
            current.copy(
                anchorEpochDay = next.first { it.id == defaultGroupId }.anchorEpochDay,
                groups = next,
                defaultGroupId = defaultGroupId,
            )
        }
    }

    fun selectDefaultGroup(groupId: Long) {
        updateScheme { current ->
            val existing = current.editableGroups()
            val selected = existing.firstOrNull { it.id == groupId } ?: return@updateScheme current
            current.copy(
                anchorEpochDay = selected.anchorEpochDay,
                groups = existing,
                defaultGroupId = selected.id,
            )
        }
        showDefaultGroupDialog = false
    }

    fun openGroup(index: Int) {
        val group = groups.getOrNull(index) ?: return
        editingGroupIndex = index
        groupNameDraft = group.name
        groupAnchorEpochDay = group.anchorEpochDay
        showGroupEditor = true
    }

    fun saveGroup() {
        val index = editingGroupIndex
        val name = groupNameDraft.trim()
        if (index < 0 || name.isEmpty()) return
        updateScheme { current ->
            val existing = current.editableGroups()
            val next = existing.mapIndexed { groupIndex, group ->
                if (groupIndex == index) {
                    group.copy(name = name, anchorEpochDay = groupAnchorEpochDay)
                } else {
                    group
                }
            }.toImmutableList()
            val defaultGroupId = next.firstOrNull { it.id == current.defaultGroupId }?.id
                ?: next.first().id
            current.copy(
                anchorEpochDay = next.first { it.id == defaultGroupId }.anchorEpochDay,
                groups = next,
                defaultGroupId = defaultGroupId,
            )
        }
        showGroupEditor = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "编辑方案",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, contentDescription = "返回方案列表")
                    }
                },
            )
        },
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
            // ---- 方案名（默认不高亮，只显示当前值）----
            TextField(
                value = nameDraft,
                onValueChange = { input ->
                    nameDraft = input
                    // 清空时先不落库，等用户打出内容再写，避免出现空名方案
                    if (input.isNotBlank()) updateScheme { it.copy(name = input.trim()) }
                },
                label = "方案名",
                useLabelAsPlaceholder = true,
                // 点进去才出现主题色描边，保持它作为正文表单的正常样式
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                ),
                textStyle = MiuixTheme.textStyles.title3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp, bottom = 12.dp)
                    .focusRequester(nameFocusRequester),
            )

            // ---- 班次模板 / 排班设置 / 班组设置（位于方案名下面）----
            // 与「新增班次」里的开始/结束同一个样式（共用 SegmentedSwitch）。
            // 切换框的轨道是 surface、胶囊是 surfaceContainer，得落在 surfaceContainer 这一层
            // （卡片/弹窗）上才看得见——放在页面底色（也是 surface）上会整个隐形。
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SegmentedSwitch(
                    tabs = listOf("班次模板", "排班设置", "班组设置"),
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
                    1 -> ShiftSettingsTab(
                        doc = doc,
                        scheme = scheme,
                        cycleText = cycleText,
                        cycleHoldDown = showCycleDialog,
                        onOpenCycle = {
                            cycleDraft = cycleText
                            showCycleDialog = true
                        },
                        onPickDay = { pickingDay = it },
                        pickingDay = pickingDay,
                    )
                    else -> GroupSettingsTab(
                        groups = groups,
                        groupCountText = groupCountText,
                        groupCountHoldDown = showGroupCountDialog,
                        onOpenGroupCount = {
                            groupCountDraft = groupCountText
                            showGroupCountDialog = true
                        },
                        defaultGroup = defaultGroup,
                        defaultGroupHoldDown = showDefaultGroupDialog,
                        onOpenDefaultGroup = { showDefaultGroupDialog = true },
                        editingGroupIndex = editingGroupIndex,
                        groupEditorShown = showGroupEditor,
                        onOpenGroup = ::openGroup,
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
        OverlayDialog(
            show = showGroupCountDialog,
            title = "班组数量",
            onDismissRequest = { showGroupCountDialog = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = groupCountDraft,
                    onValueChange = { input ->
                        groupCountDraft = input.filter { it.isDigit() }.take(2)
                    },
                    label = "班组数量（1–99）",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    insideMargin = DpSize(TextFieldDefaults.InsideMargin.width, 26.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { showGroupCountDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "确定",
                        enabled = groupCountDraft.toIntOrNull()?.let { it in 1..99 } == true,
                        onClick = {
                            updateGroupCount(groupCountDraft)
                            showGroupCountDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        OverlayDialog(
            show = showGroupEditor,
            title = "编辑班组",
            onDismissRequest = { showGroupEditor = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = groupNameDraft,
                    onValueChange = { groupNameDraft = it },
                    label = "班组名称",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "基准日期",
                        summary = formatYmd(Ymd.fromEpochDay(groupAnchorEpochDay)),
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Basic.ArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp, 18.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                        holdDownState = showGroupAnchorDialog,
                        onClick = { showGroupAnchorDialog = true },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { showGroupEditor = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "确定",
                        enabled = groupNameDraft.isNotBlank(),
                        onClick = ::saveGroup,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        DefaultGroupDialog(
            groups = groups,
            currentId = defaultGroup?.id,
            show = showDefaultGroupDialog,
            onSelect = ::selectDefaultGroup,
            onDismiss = { showDefaultGroupDialog = false },
        )

        AnchorDialog(
            anchor = Ymd.fromEpochDay(groupAnchorEpochDay),
            title = "基准日期",
            show = showGroupAnchorDialog,
            onDismiss = { showGroupAnchorDialog = false },
            onConfirm = { date ->
                groupAnchorEpochDay = date.epochDay
                showGroupAnchorDialog = false
            },
        )

        OverlayDialog(
            show = showCycleDialog,
            title = "周期天数",
            onDismissRequest = { showCycleDialog = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = cycleDraft,
                    onValueChange = { input ->
                        cycleDraft = input.filter { it.isDigit() }.take(2)
                    },
                    label = "周期天数（1–99）",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    insideMargin = DpSize(TextFieldDefaults.InsideMargin.width, 26.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { showCycleDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "确定",
                        enabled = cycleDraft.toIntOrNull()?.let { it in 1..99 } == true,
                        onClick = {
                            updateCycle(cycleDraft)
                            showCycleDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        TemplateEditorDialog(
            show = showEditor,
            existing = editingTemplate,
            usedColors = doc.templates.map { it.colorArgb },
            onDismiss = { showEditor = false },
            onSave = { name, start, end, color, isRest ->
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
                                    isRest = isRest,
                                ),
                            ),
                        )
                    } else {
                        plan.copy(
                            templates = plan.templates.map {
                                if (it.id == editing.id) {
                                    it.copy(
                                        name = name,
                                        startMinute = start,
                                        endMinute = end,
                                        colorArgb = color,
                                        isRest = isRest,
                                    )
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

/** 排班设置：周期天数 → 逐日指派 */
@Composable
private fun ShiftSettingsTab(
    doc: PlanDocument,
    scheme: Scheme,
    cycleText: String,
    cycleHoldDown: Boolean,
    onOpenCycle: () -> Unit,
    onPickDay: (Int) -> Unit,
    pickingDay: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            BasicComponent(
                title = "周期天数",
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cycleText.toIntOrNull()?.takeIf { it in 1..99 }?.let { "$it 天" } ?: "未设置",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).size(12.dp, 18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                },
                holdDownState = cycleHoldDown,
                onClick = onOpenCycle,
            )
        }

        if (scheme.dayTemplateIds.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                scheme.dayTemplateIds.forEachIndexed { day, templateId ->
                    AssignmentRow(
                        day = day,
                        template = doc.templates.firstOrNull { it.id == templateId },
                        onClick = { onPickDay(day) },
                        holdDownState = pickingDay == day,
                        grouped = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 班组设置：班组数量 → 各班组名称与基准日期。 */
@Composable
private fun GroupSettingsTab(
    groups: ImmutableList<SchemeGroup>,
    groupCountText: String,
    groupCountHoldDown: Boolean,
    onOpenGroupCount: () -> Unit,
    defaultGroup: SchemeGroup?,
    defaultGroupHoldDown: Boolean,
    onOpenDefaultGroup: () -> Unit,
    editingGroupIndex: Int,
    groupEditorShown: Boolean,
    onOpenGroup: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            BasicComponent(
                title = "班组数量",
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = groupCountText.toIntOrNull()?.takeIf { it in 1..99 }?.let { "$it 个" }
                                ?: "未设置",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).size(12.dp, 18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                },
                holdDownState = groupCountHoldDown,
                onClick = onOpenGroupCount,
            )
            BasicComponent(
                title = "默认班组",
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = defaultGroup?.name ?: "未设置",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).size(12.dp, 18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                },
                holdDownState = defaultGroupHoldDown,
                onClick = onOpenDefaultGroup,
            )
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            groups.forEachIndexed { index, group ->
                val holdDownState = groupEditorShown && editingGroupIndex == index
                val interactionSource = rememberHoldDownSource(holdDownState)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = { onOpenGroup(index) },
                        )
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = group.name, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatYmd(Ymd.fromEpochDay(group.anchorEpochDay)),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).size(12.dp, 18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

/** 默认班组单选弹窗：按班组名称选择，默认班组的基准日期会用于日历推导。 */
@Composable
private fun DefaultGroupDialog(
    groups: ImmutableList<SchemeGroup>,
    currentId: Long?,
    show: Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(groups, currentId, onSelect) {
        DropdownEntry(
            items = groups.map { group ->
                DropdownItem(
                    text = group.name,
                    summary = formatYmd(Ymd.fromEpochDay(group.anchorEpochDay)),
                    selected = group.id == currentId,
                    onClick = { onSelect(group.id) },
                )
            },
        )
    }
    WindowDropdownDialog(
        entry = entry,
        title = "选择默认班组",
        dialogButtonString = "取消",
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = {},
        dropdownColors = DropdownDefaults.dropdownColors(),
    )
}

/**
 * 日期弹窗：只滚「月 / 日」，年沿用当前基准日期的年份。
 * 「月」「日」作为固定表头写在滚轮上方，不跟着数字滚动。
 */
@Composable
internal fun AnchorDialog(
    anchor: Ymd,
    title: String = "开始日期",
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
        title = title,
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
