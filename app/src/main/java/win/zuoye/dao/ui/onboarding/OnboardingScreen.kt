package win.zuoye.dao.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.WindowDropdownDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.PlanShareCodec
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.primaryAnchorEpochDay
import win.zuoye.dao.ui.ShiftPalette
import win.zuoye.dao.ui.scan.ScanCaptureActivity
import win.zuoye.dao.ui.scheme.AnchorDialog
import win.zuoye.dao.ui.scheme.SchemeEditScreen
import win.zuoye.dao.ui.scheme.UNASSIGNED
import win.zuoye.dao.ui.scheme.formatYmd

private enum class Step(val labelRes: Int) {
    METHOD(R.string.onboarding_method_title),
    TEMPLATES(R.string.onboarding_templates_title),
    CYCLE_ASSIGN(R.string.onboarding_cycle_title),
    ANCHOR(R.string.onboarding_anchor_title),
}

private enum class CreateMethod(val titleRes: Int, val summaryRes: Int) {
    MANUAL(R.string.create_manual, R.string.create_manual_summary),
    CLIPBOARD(R.string.create_clipboard, R.string.create_clipboard_summary),
    QR_CODE(R.string.create_qr, R.string.create_qr_summary),
}

/** 首次启动先选择创建方式；手动添加时继续三步向导： ①班次模板 → ②周期天数 + 逐日指派 → ③开始日期。 editing 非空 = 从现有方案预填（走修改流程）。 */
@Composable
fun OnboardingScreen(
    doc: PlanDocument,
    editing: Scheme?,
    onImportPlan: (PlanShare) -> Unit,
    onSaveDocument: (PlanDocument) -> Unit,
    onSave:
        (
            cycleDays: Int,
            templates: ImmutableList<ShiftTemplate>,
            dayTemplateIds: ImmutableList<Long>,
            anchorEpochDay: Long,
        ) -> Unit,
    onSkip: (() -> Unit)?,
    onCancel: (() -> Unit)?,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = editing != null,
        onBackCompleted = { onCancel?.invoke() },
    )

    val context = LocalContext.current
    val importUnrecognized = stringResource(R.string.import_unrecognized)
    val defaultPlanName = stringResource(R.string.default_plan_name, doc.schemes.size + 1)
    val defaultGroupName = stringResource(R.string.default_group_name, 1)
    var step by remember { mutableStateOf(if (editing == null) Step.METHOD else Step.TEMPLATES) }
    var createMethod by remember { mutableStateOf<CreateMethod?>(null) }
    var manualMode by remember { mutableStateOf(false) }
    val manualScheme =
        remember(doc, defaultPlanName, defaultGroupName) {
            val id = System.currentTimeMillis()
            val today = Ymd.today().epochDay
            Scheme(
                id = id,
                name = defaultPlanName,
                cycleDays = 1,
                dayTemplateIds = persistentListOf(doc.templates.firstOrNull()?.id ?: UNASSIGNED),
                createdAt = id,
                groups =
                    persistentListOf(
                        SchemeGroup(
                            id = id,
                            name = defaultGroupName,
                            anchorEpochDay = today,
                        )
                    ),
                defaultGroupId = id,
            )
        }
    if (manualMode) {
        SchemeEditScreen(
            doc = doc,
            scheme = manualScheme,
            autoFocusName = true,
            onBack = { manualMode = false },
            onSave = { savedDocument ->
                onSaveDocument(
                    savedDocument.copy(
                        activeSchemeId = manualScheme.id,
                        onboardingDone = true,
                    )
                )
            },
            onDelete = {},
            onboardingMode = true,
        )
        return
    }
    var userTemplates by remember { mutableStateOf(doc.templates.toPersistentList()) }
    var cycleText by remember { mutableStateOf(editing?.cycleDays?.toString() ?: "") }
    var assignments by remember {
        mutableStateOf(
            editing
                ?.dayTemplateIds
                ?.map { if (doc.templateById(it) != null) it else null }
                ?.toPersistentList() ?: persistentListOf<Long?>()
        )
    }
    var anchor by remember {
        mutableStateOf(editing?.primaryAnchorEpochDay()?.let(Ymd::fromEpochDay) ?: Ymd.today())
    }
    var showEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ShiftTemplate?>(null) }
    var pickingDay by remember { mutableIntStateOf(-1) }
    var showAnchorDialog by remember { mutableStateOf(false) }
    // 弹层关闭后还要播退出动画，所以记住最后一次打开的是哪一天，动画期间继续渲染
    var shownPickDay by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pickingDay) { if (pickingDay >= 0) shownPickDay = pickingDay }

    val importFrom: (String?) -> Unit = { text ->
        val payload = text?.let(PlanShareCodec::decode)
        if (payload == null) {
            Toast.makeText(context, importUnrecognized, Toast.LENGTH_SHORT).show()
        } else {
            onImportPlan(payload)
        }
    }
    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let(importFrom)
        }

    val cycleDays: Int? = cycleText.toIntOrNull()?.takeIf { it in 1..99 }

    fun syncAssignments(n: Int) {
        var next = assignments
        while (next.size < n) next = next.adding(null)
        while (next.size > n) next = next.removingAt(next.size - 1)
        assignments = next
    }

    fun next() {
        when (step) {
            Step.METHOD ->
                when (createMethod) {
                    CreateMethod.MANUAL -> manualMode = true
                    CreateMethod.CLIPBOARD -> importFrom(context.clipboardText())
                    CreateMethod.QR_CODE ->
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setBeepEnabled(false)
                                setOrientationLocked(true)
                                setCaptureActivity(ScanCaptureActivity::class.java)
                            }
                        )
                    null -> Unit
                }
            Step.TEMPLATES -> {
                cycleDays?.let { syncAssignments(it) }
                step = Step.CYCLE_ASSIGN
            }
            Step.CYCLE_ASSIGN -> step = Step.ANCHOR
            Step.ANCHOR ->
                onSave(
                    cycleDays!!,
                    userTemplates,
                    assignments.map { requireNotNull(it) }.toImmutableList(),
                    anchor.epochDay,
                )
        }
    }

    fun back() {
        step =
            when (step) {
                Step.METHOD -> Step.METHOD
                Step.TEMPLATES -> if (editing == null) Step.METHOD else Step.TEMPLATES
                Step.CYCLE_ASSIGN -> Step.TEMPLATES
                Step.ANCHOR -> Step.CYCLE_ASSIGN
            }
    }

    val canNext =
        when (step) {
            Step.METHOD -> createMethod != null
            Step.TEMPLATES -> userTemplates.isNotEmpty()
            Step.CYCLE_ASSIGN ->
                cycleDays != null && assignments.size == cycleDays && assignments.all { it != null }
            Step.ANCHOR -> true
        }
    // 唯一的滚动源（第 2 步的列表），顶栏折叠与列表滚动共用同一个 behavior
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title =
                    stringResource(
                        if (editing == null) R.string.onboarding_title
                        else R.string.onboarding_edit_title
                    ),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (editing != null && step == Step.TEMPLATES) {
                        IconButton(onClick = { onCancel?.invoke() }) {
                            Icon(
                                MiuixIcons.Regular.Back,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // 和方案页一样：加号在右下角（只在「班次模板」这一步出现）
            if (step == Step.TEMPLATES) {
                Box(Modifier.padding(end = 8.dp, bottom = 12.dp)) {
                    FloatingActionButton(
                        onClick = {
                            editingTemplate = null
                            showEditor = true
                        },
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
        },
        // 底栏自己吃导航栏内边距，别再让内容重复算
        bottomBar = {
            Row(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    step == Step.METHOD && onSkip != null ->
                        TextButton(
                            text = stringResource(R.string.action_skip_configuration),
                            onClick = onSkip,
                            colors =
                                ButtonDefaults.textButtonColors(
                                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                ),
                            modifier = Modifier.weight(1f),
                        )
                    step != Step.METHOD ->
                        TextButton(
                            text = stringResource(R.string.action_previous),
                            onClick = { back() },
                            modifier = Modifier.weight(1f),
                        )
                    else -> Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = { next() },
                    enabled = canNext,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (step == Step.ANCHOR) R.string.action_done else R.string.action_next
                        )
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (step != Step.METHOD) StepIndicator(step)
            Box(Modifier.weight(1f)) {
                when (step) {
                    Step.METHOD ->
                        CreateMethodStep(
                            selected = createMethod,
                            onSelect = { createMethod = it },
                        )
                    Step.TEMPLATES ->
                        TemplatesStep(
                            templates = userTemplates,
                            // 小标题和行内加号都不要，加号在右下角 FAB
                            onAdd = null,
                            title = null,
                            onEdit = {
                                editingTemplate = it
                                showEditor = true
                            },
                            onDelete = { deleted ->
                                userTemplates =
                                    userTemplates
                                        .filterNot { it.id == deleted.id }
                                        .toPersistentList()
                                // 指向已删班次的指派一并清空，避免存下悬空 id
                                assignments =
                                    assignments
                                        .map { if (it == deleted.id) null else it }
                                        .toPersistentList()
                            },
                            editHoldDown = { showEditor && editingTemplate?.id == it.id },
                        )
                    Step.CYCLE_ASSIGN ->
                        CycleAssignStep(
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
                    Step.ANCHOR ->
                        AnchorPickerStep(
                            anchor = anchor,
                            holdDown = showAnchorDialog,
                            onOpen = { showAnchorDialog = true },
                        )
                }
            }
        }

        // 开始日期的日期设置弹窗（和方案页共用同一个）
        AnchorDialog(
            anchor = anchor,
            show = showAnchorDialog,
            onDismiss = { showAnchorDialog = false },
            onConfirm = { date ->
                anchor = date
                showAnchorDialog = false
            },
        )

        // 对话框必须挂在 Scaffold 内部（依赖 Scaffold 提供的弹层宿主）
        TemplateEditorDialog(
            show = showEditor,
            existing = editingTemplate,
            usedColors = userTemplates.map { it.colorArgb },
            onDismiss = { showEditor = false },
            onSave = { name, start, end, color, isRest ->
                val current = editingTemplate
                if (current == null) {
                    userTemplates =
                        userTemplates.adding(
                            ShiftTemplate(
                                id = System.currentTimeMillis(),
                                name = name,
                                startMinute = start,
                                endMinute = end,
                                colorArgb = color,
                                isRest = isRest,
                            )
                        )
                } else {
                    val idx = userTemplates.indexOfFirst { it.id == current.id }
                    if (idx >= 0) {
                        userTemplates =
                            userTemplates.replacingAt(
                                idx,
                                current.copy(
                                    name = name,
                                    startMinute = start,
                                    endMinute = end,
                                    colorArgb = color,
                                    isRest = isRest,
                                ),
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
                        assignments = assignments.replacingAt(shownPickDay, id)
                    }
                    pickingDay = -1
                },
                onDismiss = { pickingDay = -1 },
            )
        }
    }
}

@Composable
private fun CreateMethodStep(
    selected: CreateMethod?,
    onSelect: (CreateMethod) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.create_method_prompt),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            fontSize = 23.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CreateMethod.entries.forEach { method ->
            val isSelected = selected == method
            Card(
                onClick = { onSelect(method) },
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                colors =
                    CardDefaults.defaultColors(
                        color =
                            if (isSelected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.surfaceContainer
                            },
                        contentColor =
                            if (isSelected) {
                                MiuixTheme.colorScheme.onPrimary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(method.titleRes),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color =
                                if (isSelected) {
                                    MiuixTheme.colorScheme.onPrimary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(method.summaryRes),
                            fontSize = 13.sp,
                            color =
                                if (isSelected) {
                                    MiuixTheme.colorScheme.onPrimary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Step) {
    val steps = listOf(Step.TEMPLATES, Step.CYCLE_ASSIGN, Step.ANCHOR)
    val index = steps.indexOf(step)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { i, _ ->
            Box(
                Modifier.size(width = if (i == index) 28.dp else 12.dp, height = 6.dp)
                    .squircleBackground(
                        color =
                            if (i <= index) {
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
            stringResource(
                R.string.step_progress,
                index + 1,
                steps.size,
                stringResource(step.labelRes),
            ),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 读取剪贴板第一段文本，供首次引导导入分享载荷。 */
private fun Context.clipboardText(): String =
    (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        .orEmpty()

/**
 * 第 1 步：定义班次模板（名称 + 时间 + 颜色），后续逐日指派时点选复用。方案页复用同一组件。
 *
 * [title] 传 null 就不渲染表头；方案页的加号挪到了右下角 FAB，所以那边 [onAdd] 也传 null。
 */
@Composable
internal fun TemplatesStep(
    templates: ImmutableList<ShiftTemplate>,
    onAdd: (() -> Unit)?,
    onEdit: (ShiftTemplate) -> Unit,
    onDelete: (ShiftTemplate) -> Unit,
    title: String?,
    showEditAction: Boolean = true,
    addHoldDown: Boolean = false,
    editHoldDown: (ShiftTemplate) -> Boolean = { false },
    deleteHoldDown: (ShiftTemplate) -> Boolean = { false },
) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallTitle(text = title, modifier = Modifier.weight(1f))
                if (onAdd != null) {
                    IconButton(onClick = onAdd, holdDownState = addHoldDown) {
                        Icon(
                            MiuixIcons.Regular.Add,
                            contentDescription = stringResource(R.string.action_add_shift),
                        )
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            if (templates.isEmpty()) {
                Text(
                    stringResource(R.string.empty_shifts),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
            templates.forEach { template ->
                val editInteractionSource = rememberHoldDownSource(editHoldDown(template))
                Row(
                    Modifier.fillMaxWidth()
                        .let { modifier ->
                            if (showEditAction) {
                                modifier
                            } else {
                                modifier.clickable(
                                    interactionSource = editInteractionSource,
                                    indication = LocalIndication.current,
                                    onClick = { onEdit(template) },
                                )
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(14.dp)
                            .background(ShiftPalette.color(template.colorArgb), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, fontSize = 16.sp)
                        Text(
                            template.localizedTimeRangeText(),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (showEditAction) {
                        IconButton(
                            onClick = { onEdit(template) },
                            holdDownState = editHoldDown(template),
                        ) {
                            Icon(
                                MiuixIcons.Regular.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                    }
                    IconButton(
                        onClick = { onDelete(template) },
                        holdDownState = deleteHoldDown(template),
                    ) {
                        Icon(
                            MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                }
            }
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
    grouped: Boolean = false,
) {
    val interactionSource = rememberHoldDownSource(holdDownState)
    Row(
        modifier
            .fillMaxWidth()
            // 独立行用 squircle 底色；方案页的多行指派放在同一张 Card 内，沿用卡片底色
            .let {
                if (grouped) it
                else
                    it.squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceVariant,
                        cornerRadius = 12.dp,
                    )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.cycle_day, day + 1),
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (template == null) {
            Text(
                stringResource(R.string.tap_to_select),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            Box(
                Modifier.size(12.dp).background(ShiftPalette.color(template.colorArgb), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(template.name, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                template.localizedTimeRangeText(),
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
        Modifier.fillMaxWidth()
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // 底部由外层按钮行占据，contentPadding 只留 top
        contentPadding = PaddingValues(top = 4.dp),
    ) {
        item {
            TextField(
                value = cycleText,
                onValueChange = onCycleChange,
                label = stringResource(R.string.cycle_days_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier,
            )
        }
        if (cycleDays != null) {
            item { SmallTitle(text = stringResource(R.string.assign_each_day)) }
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

/** 第 3 步：开始日期。和方案页的「排班设置」一样是**点击行 + 月/日 弹窗**， 不再内联年/月/日 三列滚轮。 */
@Composable
private fun AnchorPickerStep(
    anchor: Ymd,
    holdDown: Boolean,
    onOpen: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            BasicComponent(
                title = stringResource(R.string.start_date),
                summary = stringResource(R.string.cycle_first_day, formatYmd(anchor)),
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp, 18.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                holdDownState = holdDown,
                onClick = onOpen,
            )
        }
        Text(
            stringResource(R.string.anchor_explanation),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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
        OverlayDialog(
            show = show,
            title = stringResource(R.string.select_shift),
            onDismissRequest = onDismiss,
        ) {
            Text(
                stringResource(R.string.select_shift_empty),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    val localizedRanges = templates.map { it.localizedTimeRangeText() }
    val entry =
        remember(templates, localizedRanges, currentId, onPick) {
            DropdownEntry(
                items =
                    templates.mapIndexed { index, template ->
                        DropdownItem(
                            text = template.name,
                            summary = localizedRanges[index],
                            selected = template.id == currentId,
                            onClick = { onPick(template.id) },
                            icon = { iconModifier ->
                                // 组件给的 icon 槽是 sizeIn(min 26dp) + 右边距，直接 size 会被它撑成矩形、
                                // CircleShape 就画成椭圆了——所以外面套一层把圆点居中画
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Box(
                                        Modifier.size(14.dp)
                                            .background(
                                                ShiftPalette.color(template.colorArgb),
                                                CircleShape,
                                            )
                                    )
                                }
                            },
                        )
                    }
            )
        }
    WindowDropdownDialog(
        entry = entry,
        title = stringResource(R.string.select_shift),
        dialogButtonString = stringResource(R.string.action_cancel),
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
        title = stringResource(R.string.delete_shift_title, template.name),
        summary =
            stringResource(
                if (inUse) R.string.delete_shift_in_use else R.string.delete_shift_confirm
            ),
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.action_delete),
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * miuix 组件（BasicComponent / IconButton …）自带 `holdDownState` 参数，直接用那个即可； 自绘的 `clickable` 行走这里——把状态作为
 * [HoldDownInteraction] 注入 interactionSource， 再交给 `Modifier.clickable(interactionSource = …,
 * indication = LocalIndication.current, …)`， 主题里的 MiuixIndication 会据此画出按住高亮（并在弹层关闭后释放）。
 */
@Composable
private fun rememberHoldDownSource(holdDownState: Boolean): MutableInteractionSource {
    val interactionSource = remember { MutableInteractionSource() }
    val active = remember { mutableStateOf<HoldDownInteraction.HoldDown?>(null) }

    LaunchedEffect(holdDownState, interactionSource) {
        suspend fun release() {
            active.value?.let { current ->
                interactionSource.emit(HoldDownInteraction.Release(current))
                active.value = null
            }
        }
        if (holdDownState) {
            release()
            val interaction = HoldDownInteraction.HoldDown()
            active.value = interaction
            interactionSource.emit(interaction)
        } else {
            release()
        }
    }

    // 行被移出组合（例如弹层关掉后整行消失）时也要释放，别把高亮留在 source 里
    DisposableEffect(interactionSource) {
        onDispose {
            active.value?.let { current ->
                interactionSource.tryEmit(HoldDownInteraction.Release(current))
            }
            active.value = null
        }
    }
    return interactionSource
}

@Composable
private fun ShiftTemplate.localizedTimeRangeText(): String {
    if (isRest) return stringResource(R.string.shift_rest_time)
    val end = ShiftTemplate.format(endMinute)
    val localizedEnd =
        if (crossesMidnight()) {
            stringResource(R.string.shift_next_day, end)
        } else {
            end
        }
    return "${ShiftTemplate.format(startMinute)}–$localizedEnd"
}

/** 时间滚轮的行高：miuix 默认 45dp，这里跟日期弹窗保持一致，数字别挨得太近 */
private val TIME_ITEM_HEIGHT = 48.dp

/**
 * 新建/编辑班次模板：名称（右边的圆点是当前颜色，点开是颜色页）+ 休班开关
 * + 「开始 / 结束」切换框与共用时间滚轮。existing = null 表示新建。
 */
@Composable
private fun TemplateEditorDialog(
    show: Boolean,
    existing: ShiftTemplate?,
    usedColors: List<Int>,
    onSave:
        (name: String, startMinute: Int, endMinute: Int, colorArgb: Int, isRest: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultRestName = stringResource(R.string.shift_rest)
    // 不在这里 return：常驻组合、交给 OverlayDialog 按 show 播进出动画
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var startH by
        remember(existing) { mutableIntStateOf(existing?.let { it.startMinute / 60 } ?: 8) }
    var startM by
        remember(existing) { mutableIntStateOf(existing?.let { it.startMinute % 60 } ?: 0) }
    var endH by remember(existing) { mutableIntStateOf(existing?.let { it.endMinute / 60 } ?: 15) }
    var endM by remember(existing) { mutableIntStateOf(existing?.let { it.endMinute % 60 } ?: 0) }
    var color by
        remember(existing) {
            mutableIntStateOf(
                existing?.colorArgb
                    ?: ShiftPalette.presets.firstOrNull { it !in usedColors }
                    ?: ShiftPalette.presets.first()
            )
        }
    // 开始 / 结束共用一个切换框，下面那组滚轮编辑当前选中的那一头
    var editingEnd by remember(existing) { mutableStateOf(false) }
    var isRest by remember(existing) { mutableStateOf(existing?.isRest == true) }
    var showColorDialog by remember(existing) { mutableStateOf(false) }

    // 每次打开都按 existing 重新初始化一次。弹窗为了退出动画是常驻组合的，
    // 只靠 remember(existing) 会在"新建 → 关闭 → 再新建"时留下上一次填的内容
    // （existing 一直是 null，key 没变），也会留下上次取消掉的编辑。
    LaunchedEffect(show, existing) {
        if (!show) return@LaunchedEffect
        name = existing?.name ?: ""
        startH = existing?.let { it.startMinute / 60 } ?: 8
        startM = existing?.let { it.startMinute % 60 } ?: 0
        endH = existing?.let { it.endMinute / 60 } ?: 15
        endM = existing?.let { it.endMinute % 60 } ?: 0
        color =
            existing?.colorArgb
                ?: ShiftPalette.presets.firstOrNull { it !in usedColors }
                ?: ShiftPalette.presets.first()
        editingEnd = false
        isRest = existing?.isRest == true
        showColorDialog = false
    }

    OverlayDialog(
        show = show,
        title =
            stringResource(
                if (existing == null) R.string.shift_add_title else R.string.shift_edit_title
            ),
        summary = stringResource(R.string.shift_editor_summary),
        onDismissRequest = onDismiss,
    ) {
        // 长内容 Dialog：miuix 的 WindowDialog 不限 content 高度，
        // 所以给内容一个上限、让滚动区自己滚，按钮作为非加权子项固定在底部。
        Column(Modifier.heightIn(max = 500.dp).imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.shift_name),
                    trailingIcon = {
                        // 颜色收进名称框右边这个圆点里，点它进颜色页
                        IconButton(onClick = { showColorDialog = true }) {
                            Box(
                                Modifier.size(24.dp)
                                    .background(ShiftPalette.color(color), CircleShape)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surface,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.shift_rest),
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = isRest, onCheckedChange = { isRest = it })
                    }
                }
                if (!isRest) {
                    Spacer(Modifier.height(12.dp))
                    TabRowWithContour(
                        tabs =
                            listOf(
                                stringResource(R.string.shift_start),
                                stringResource(R.string.shift_end),
                            ),
                        selectedTabIndex = if (editingEnd) 1 else 0,
                        onTabSelected = { editingEnd = it == 1 },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            TabRowDefaults.tabRowColors(
                                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                selectedBackgroundColor = MiuixTheme.colorScheme.primary,
                                selectedContentColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        height = 50.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberPicker(
                            value = if (editingEnd) endH else startH,
                            onValueChange = { if (editingEnd) endH = it else startH = it },
                            range = 0..23,
                            wrapAround = true,
                            label = { "%02d".format(it) },
                            itemHeight = TIME_ITEM_HEIGHT,
                            modifier = Modifier.weight(1f),
                        )
                        Text(":", color = MiuixTheme.colorScheme.onSurface)
                        NumberPicker(
                            value = if (editingEnd) endM else startM,
                            onValueChange = { if (editingEnd) endM = it else startM = it },
                            range = 0..59,
                            wrapAround = true,
                            label = { "%02d".format(it) },
                            itemHeight = TIME_ITEM_HEIGHT,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_save),
                    enabled = isRest || name.isNotBlank(),
                    onClick = {
                        onSave(
                            name.trim().ifBlank { defaultRestName },
                            if (isRest) 0 else startH * 60 + startM,
                            if (isRest) 0 else endH * 60 + endM,
                            color,
                            isRest,
                        )
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 颜色弹窗与主弹窗同时存在时，主弹窗收起就一起收
    ColorDialog(
        show = show && showColorDialog,
        current = color,
        onDismiss = { showColorDialog = false },
        onConfirm = {
            color = it
            showColorDialog = false
        },
    )
}

/** 颜色页使用 miuix [ColorPalette] 色板。确定时把透明度收成 1—— 班次色要画在日历格上，半透明会跟底色混在一起。 */
@Composable
private fun ColorDialog(
    show: Boolean,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // 同主弹窗：每次打开都从当前颜色重新开始，别让上次取消的改动留在里面
    var draft by remember(current) { mutableStateOf(Color(current)) }
    LaunchedEffect(show, current) { if (show) draft = Color(current) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.color_select),
        onDismissRequest = onDismiss,
    ) {
        // 调色盘本身挺高，长内容按 Dialog 规范交给滚动区
        Column(Modifier.heightIn(max = 500.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ColorPalette(
                    color = draft,
                    onColorChanged = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = { onConfirm(draft.copy(alpha = 1f).toArgb()) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
