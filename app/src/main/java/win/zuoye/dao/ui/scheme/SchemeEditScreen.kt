package win.zuoye.dao.ui.scheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.WindowDropdownDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.R
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.defaultGroup
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
    onSave: (PlanDocument) -> Unit,
    onDelete: () -> Unit,
    onboardingMode: Boolean = false,
) {
    val defaultGroupNames = (1..99).map { stringResource(R.string.default_group_name, it) }
    val isNewScheme = remember(scheme.id) { doc.schemes.none { it.id == scheme.id } }
    val initialDraft = remember(scheme.id) {
        if (isNewScheme) {
            doc.copy(schemes = doc.schemes.toPersistentList().add(scheme))
        } else {
            doc
        }
    }
    var draftDocument by remember(scheme.id) { mutableStateOf(initialDraft) }
    val draftScheme = draftDocument.schemes.first { it.id == scheme.id }

    fun requestExit() = onBack()

    fun saveAndExit() {
        if (draftScheme.name.isBlank()) return
        val normalizedName = draftScheme.name.trim()
        val savedDocument = if (normalizedName == draftScheme.name) {
            draftDocument
        } else {
            draftDocument.copy(
                schemes = draftDocument.schemes.map {
                    if (it.id == draftScheme.id) it.copy(name = normalizedName) else it
                }.toImmutableList(),
            )
        }
        onSave(savedDocument)
    }

    var tabIndex by rememberSaveable(scheme.id) { mutableIntStateOf(0) }
    BackHandler {
        if (onboardingMode) {
            if (tabIndex > 0) tabIndex-- else onBack()
        } else {
            requestExit()
        }
    }
    var cycleText by rememberSaveable(scheme.id) { mutableStateOf(scheme.cycleDays.toString()) }
    var showCycleDialog by remember { mutableStateOf(false) }
    var cycleDraft by rememberSaveable(scheme.id) { mutableStateOf(scheme.cycleDays.toString()) }
    var groupCountText by rememberSaveable(scheme.id) {
        mutableStateOf(scheme.groups.size.toString())
    }
    var showGroupCountDialog by remember { mutableStateOf(false) }
    var groupCountDraft by rememberSaveable(scheme.id) {
        mutableStateOf(scheme.groups.size.toString())
    }
    var showDefaultGroupDialog by remember { mutableStateOf(false) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var editingGroupIndex by rememberSaveable(scheme.id) { mutableIntStateOf(-1) }
    var groupNameDraft by rememberSaveable(scheme.id) { mutableStateOf("") }
    var groupAnchorEpochDay by rememberSaveable(scheme.id) {
        mutableLongStateOf(scheme.primaryAnchorEpochDay() ?: Ymd.today().epochDay)
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
    val focusManager = LocalFocusManager.current
    var nameFieldFocused by remember { mutableStateOf(false) }
    var nameFieldBounds by remember { mutableStateOf(Rect.Zero) }
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(scheme.id, autoFocusName) {
        if (autoFocusName) nameFocusRequester.requestFocus()
    }
    // 页签内容滚动时，右下角的加号收起来
    val contentScrollState = rememberScrollState()
    val fabVisible = rememberFabVisible { contentScrollState.value }
    val groups = draftScheme.groups
    val defaultGroup = draftScheme.defaultGroup()

    var dismissOffset by remember(scheme.id) { mutableFloatStateOf(0f) }
    var editorHeight by remember(scheme.id) { mutableIntStateOf(1) }
    val dismissThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val currentOnBack by rememberUpdatedState(onBack)

    suspend fun settleDismiss(velocityY: Float): Boolean {
        val dismiss = dismissOffset >= dismissThreshold || velocityY >= 1_200f
        val target = if (dismiss) editorHeight.toFloat() else 0f
        animate(
            initialValue = dismissOffset,
            targetValue = target,
            animationSpec = tween(durationMillis = if (dismiss) 180 else 220),
        ) { value, _ -> dismissOffset = value }
        if (dismiss) currentOnBack()
        return dismiss
    }

    val dismissDragState = rememberDraggableState { delta ->
        dismissOffset = (dismissOffset + delta).coerceIn(0f, editorHeight.toFloat())
    }
    val dismissNestedScroll = remember(dismissThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || dismissOffset <= 0f) {
                    return Offset.Zero
                }
                val consumed = available.y.coerceAtLeast(-dismissOffset)
                dismissOffset += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val previous = dismissOffset
                dismissOffset = (dismissOffset + available.y).coerceAtMost(editorHeight.toFloat())
                return Offset(0f, dismissOffset - previous)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissOffset <= 0f) return Velocity.Zero
                val dismissed = settleDismiss(available.y)
                return if (dismissed) available else Velocity(0f, available.y)
            }
        }
    }

    fun updateScheme(transform: (Scheme) -> Scheme) {
        draftDocument = draftDocument.copy(
            schemes = draftDocument.schemes.map {
                if (it.id == draftScheme.id) transform(it) else it
            }.toImmutableList(),
        )
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
            val existing = current.groups
            val next = List(count) { index ->
                existing.getOrNull(index) ?: SchemeGroup(
                    id = System.currentTimeMillis() + index,
                    name = defaultGroupNames[index],
                    anchorEpochDay = existing.firstOrNull()?.anchorEpochDay
                        ?: Ymd.today().epochDay,
                )
            }.toImmutableList()
            val defaultGroupId = next.firstOrNull { it.id == current.defaultGroupId }?.id
                ?: next.first().id
            current.copy(
                groups = next,
                defaultGroupId = defaultGroupId,
            )
        }
    }

    fun selectDefaultGroup(groupId: Long) {
        updateScheme { current ->
            if (current.groups.none { it.id == groupId }) return@updateScheme current
            current.copy(defaultGroupId = groupId)
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
            val existing = current.groups
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
                groups = next,
                defaultGroupId = defaultGroupId,
            )
        }
        showGroupEditor = false
    }

    val canContinueOnboarding = when (tabIndex) {
        0 -> draftDocument.templates.isNotEmpty()
        1 -> draftScheme.cycleDays in 1..99 &&
            draftScheme.dayTemplateIds.size == draftScheme.cycleDays &&
            draftScheme.dayTemplateIds.all { id -> draftDocument.templates.any { it.id == id } }
        else -> draftScheme.name.isNotBlank() &&
            draftScheme.groups.isNotEmpty() &&
            draftScheme.defaultGroup() != null
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { editorHeight = it.height.coerceAtLeast(1) }
            .graphicsLayer { translationY = dismissOffset }
            .nestedScroll(dismissNestedScroll),
        topBar = {
            if (onboardingMode) {
                TopAppBar(title = stringResource(R.string.onboarding_title))
            } else {
                SmallTopAppBar(
                    title = stringResource(R.string.plan_edit_title),
                    modifier = Modifier.draggable(
                        state = dismissDragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settleDismiss(velocity) },
                    ),
                    navigationIcon = {
                        TextButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = ::requestExit,
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                                disabledColor = MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                                textColor = MiuixTheme.colorScheme.primary,
                            ),
                        )
                    },
                    actions = {
                        TextButton(
                            text = stringResource(R.string.action_save),
                            enabled = draftScheme.name.isNotBlank(),
                            onClick = ::saveAndExit,
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                                disabledColor = MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                                textColor = MiuixTheme.colorScheme.primary,
                            ),
                        )
                    },
                )
            }
        },
        bottomBar = {
            if (onboardingMode) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.action_previous),
                        onClick = { if (tabIndex > 0) tabIndex-- else onBack() },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { if (tabIndex < 2) tabIndex++ else saveAndExit() },
                        enabled = canContinueOnboarding,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(if (tabIndex == 2) R.string.action_done else R.string.action_next))
                    }
                }
            }
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
                                contentDescription = stringResource(R.string.action_add_shift),
                                tint = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .onGloballyPositioned { contentCoordinates = it }
                .pointerInput(focusManager) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val windowPosition = contentCoordinates?.localToWindow(down.position)
                        if (
                            nameFieldFocused &&
                            windowPosition != null &&
                            !nameFieldBounds.contains(windowPosition)
                        ) {
                            focusManager.clearFocus()
                        }
                    }
                },
        ) {
            Column(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
            ) {
            // ---- 方案名（默认不高亮，只显示当前值）----
            TextField(
                value = draftScheme.name,
                onValueChange = { input ->
                    updateScheme { it.copy(name = input) }
                },
                label = stringResource(R.string.plan_name),
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
                    .focusRequester(nameFocusRequester)
                    .onFocusChanged { nameFieldFocused = it.isFocused }
                    .onGloballyPositioned { nameFieldBounds = it.boundsInWindow() },
            )

            // ---- 班次模板 / 排班设置 / 班组设置（位于方案名下面）----
            // 与「新增班次」里的开始/结束同一个样式（共用 SegmentedSwitch）。
            // 切换框的轨道是 surface、胶囊是 surfaceContainer，得落在 surfaceContainer 这一层
            // （卡片/弹窗）上才看得见——放在页面底色（也是 surface）上会整个隐形。
            if (!onboardingMode) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SegmentedSwitch(
                        tabs = listOf(
                            stringResource(R.string.plan_tab_templates),
                            stringResource(R.string.plan_tab_schedule),
                            stringResource(R.string.plan_tab_groups),
                        ),
                        selectedIndex = tabIndex,
                        onSelect = { tabIndex = it },
                        // 连同灰色轨道一起填满整张卡片
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                        templates = draftDocument.templates,
                        // 表头小标题去掉，加号挪到右下角 FAB
                        onAdd = null,
                        title = null,
                        onEdit = { editingTemplate = it; showEditor = true },
                        onDelete = { deleteTemplate = it },
                        showEditAction = false,
                        editHoldDown = { showEditor && editingTemplate?.id == it.id },
                        deleteHoldDown = { deleteTemplate?.id == it.id },
                    )
                    1 -> ShiftSettingsTab(
                        doc = draftDocument,
                        scheme = draftScheme,
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
                if (!isNewScheme) {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 4.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.plan_delete),
                            summary = stringResource(R.string.delete_irreversible),
                            titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                            holdDownState = showDeleteScheme,
                            onClick = { showDeleteScheme = true },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
            }
        }

        // ---- 弹层：都在 Scaffold 内部 ----
        OverlayDialog(
            show = showGroupCountDialog,
            title = stringResource(R.string.group_count),
            onDismissRequest = { showGroupCountDialog = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = groupCountDraft,
                    onValueChange = { input ->
                        groupCountDraft = input.filter { it.isDigit() }.take(2)
                    },
                    label = stringResource(R.string.group_count_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    insideMargin = DpSize(TextFieldDefaults.InsideMargin.width, 26.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showGroupCountDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_confirm),
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
            title = stringResource(R.string.group_edit),
            onDismissRequest = { showGroupEditor = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = groupNameDraft,
                    onValueChange = { groupNameDraft = it },
                    label = stringResource(R.string.group_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.group_anchor),
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
                        text = stringResource(R.string.action_cancel),
                        onClick = { showGroupEditor = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_confirm),
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
            title = stringResource(R.string.group_anchor),
            show = showGroupAnchorDialog,
            onDismiss = { showGroupAnchorDialog = false },
            onConfirm = { date ->
                groupAnchorEpochDay = date.epochDay
                showGroupAnchorDialog = false
            },
        )

        OverlayDialog(
            show = showCycleDialog,
            title = stringResource(R.string.cycle_days),
            onDismissRequest = { showCycleDialog = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = cycleDraft,
                    onValueChange = { input ->
                        cycleDraft = input.filter { it.isDigit() }.take(2)
                    },
                    label = stringResource(R.string.cycle_days_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    insideMargin = DpSize(TextFieldDefaults.InsideMargin.width, 26.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showCycleDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_confirm),
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
            usedColors = draftDocument.templates.map { it.colorArgb },
            onDismiss = { showEditor = false },
            onSave = { name, start, end, color, isRest ->
                val editing = editingTemplate
                draftDocument = if (editing == null) {
                    draftDocument.copy(
                        templates = draftDocument.templates.toPersistentList().add(
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
                    draftDocument.copy(
                        templates = draftDocument.templates.map {
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
                showEditor = false
            },
        )

        if (shownPickDay >= 0) {
            val day = shownPickDay
            TemplatePickDialog(
                templates = draftDocument.templates,
                currentId = draftScheme.dayTemplateIds.getOrNull(day),
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
                inUse = draftDocument.schemes.any { template.id in it.dayTemplateIds },
                show = deleteTemplate != null,
                onDismiss = { deleteTemplate = null },
                onConfirm = {
                    draftDocument = draftDocument.copy(
                        templates = draftDocument.templates
                            .filterNot { it.id == template.id }
                            .toImmutableList(),
                    )
                    deleteTemplate = null
                },
            )
        }

        OverlayDialog(
            show = showDeleteScheme,
            title = stringResource(R.string.delete_plan_title, draftScheme.name),
            summary = stringResource(R.string.delete_irreversible_period),
            onDismissRequest = { showDeleteScheme = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showDeleteScheme = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_delete),
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
                title = stringResource(R.string.cycle_days),
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cycleText.toIntOrNull()?.takeIf { it in 1..99 }
                                ?.let { pluralStringResource(R.plurals.days_count, it, it) }
                                ?: stringResource(R.string.status_not_set),
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
                title = stringResource(R.string.group_count),
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = groupCountText.toIntOrNull()?.takeIf { it in 1..99 }
                                ?.let { pluralStringResource(R.plurals.groups_count, it, it) }
                                ?: stringResource(R.string.status_not_set),
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
                title = stringResource(R.string.default_group),
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = defaultGroup?.name ?: stringResource(R.string.status_not_set),
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
        title = stringResource(R.string.select_default_group),
        dialogButtonString = stringResource(R.string.action_cancel),
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
    title: String? = null,
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
        title = title ?: stringResource(R.string.start_date),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.month_label),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(R.string.day_label),
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
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = { onConfirm(Ymd(anchor.year, month, day.coerceIn(1, maxDay))) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
