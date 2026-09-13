package win.zuoye.dao.ui.scheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.ui.common.TemplateEditorDialog
import win.zuoye.dao.ui.onboarding.AnchorStep
import win.zuoye.dao.ui.onboarding.AssignmentRow
import win.zuoye.dao.ui.onboarding.DeleteTemplateDialog
import win.zuoye.dao.ui.onboarding.TemplatePickDialog
import win.zuoye.dao.ui.onboarding.TemplatesStep

/** 未指派时用的占位模板 id（模型里没有 null，指向不存在的模板即可显示「点击选择」） */
private const val UNASSIGNED = 0L

/**
 * 倒班方案（二级页面）：一个页面管完三块——
 * 上：班次模板；中：开始日期；下：周期与指派。
 * 编辑的是当前启用的方案，改动即时生效；方案列表 / 新建 / 删除放在右上角。
 */
@Composable
fun PlanEditScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
) {
    BackHandler { onBack() }

    val scheme = doc.activeScheme() ?: doc.schemes.firstOrNull()
    var cycleText by remember(scheme?.id) { mutableStateOf(scheme?.cycleDays?.toString().orEmpty()) }
    var showEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ShiftTemplate?>(null) }
    var deleteTemplate by remember { mutableStateOf<ShiftTemplate?>(null) }
    var deleteScheme by remember { mutableStateOf<Scheme?>(null) }
    var pickingDay by remember { mutableIntStateOf(-1) }
    var showSchemes by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    fun updateScheme(transform: (Scheme) -> Scheme) {
        val current = scheme ?: return
        onMutate { plan ->
            plan.copy(schemes = plan.schemes.map { if (it.id == current.id) transform(it) else it }.toImmutableList())
        }
    }

    fun createScheme() {
        onMutate { plan ->
            val id = System.currentTimeMillis()
            val first = plan.templates.firstOrNull()?.id ?: UNASSIGNED
            plan.copy(
                schemes = plan.schemes.toPersistentList().add(
                    Scheme(
                        id = id,
                        name = "方案 ${plan.schemes.size + 1}",
                        cycleDays = 1,
                        anchorEpochDay = Ymd.today().epochDay,
                        dayTemplateIds = persistentListOf(first),
                        createdAt = id,
                    ),
                ),
                activeSchemeId = id,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "倒班方案",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSchemes = true }, holdDownState = showSchemes) {
                        Icon(MiuixIcons.Regular.ListView, contentDescription = "方案列表")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 上：班次模板 ----
            TemplatesStep(
                templates = doc.templates,
                onAdd = { editingTemplate = null; showEditor = true },
                onEdit = { editingTemplate = it; showEditor = true },
                onDelete = { deleteTemplate = it },
                title = "班次模板",
                hint = "在这里定义好每个班次，下面安排周期时点选复用，无需重复输入时间。",
                addHoldDown = showEditor && editingTemplate == null,
                editHoldDown = { showEditor && editingTemplate?.id == it.id },
                deleteHoldDown = { deleteTemplate?.id == it.id },
            )

            // ---- 中：开始日期 ----
            if (scheme != null) {
                AnchorStep(
                    anchor = Ymd.fromEpochDay(scheme.anchorEpochDay),
                    onChange = { date -> updateScheme { it.copy(anchorEpochDay = date.epochDay) } },
                    title = "开始日期",
                    hint = "周期第 1 天对应这一天，日历按周期自动推导其它日期。",
                )
            }

            // ---- 下：周期与指派（TextField 表单不包 Card，直接同样的 12dp 间距）----
            // SmallTitle 自带 28dp 起始内缩，正好与卡片内文对齐，不要再补水平 padding
            SmallTitle(text = "周期与指派")
            TextField(
                value = cycleText,
                onValueChange = { input ->
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
                label = "周期天数（1–99）",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            )
            if (scheme != null && scheme.dayTemplateIds.isNotEmpty()) {
                repeat(scheme.dayTemplateIds.size) { day ->
                    AssignmentRow(
                        day = day,
                        template = doc.templates.firstOrNull { it.id == scheme.dayTemplateIds.getOrNull(day) },
                        onClick = { pickingDay = day },
                        holdDownState = pickingDay == day,
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    )
                }
            }

            if (scheme == null) {
                TextButton(
                    text = "新建一个方案",
                    onClick = { createScheme() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                )
            }

            // 二级页面：末尾 Spacer 自己吃掉导航栏内边距（签名里不放 bottomPadding）
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }

        // ---- 对话框：都在 Scaffold 内部 ----
        TemplateEditorDialog(
            show = showEditor,
            existing = editingTemplate,
            usedColors = doc.templates.map { it.colorArgb },
            onDismiss = { showEditor = false },
            onSave = { name, start, end, color ->
                onMutate { plan ->
                    val editing = editingTemplate
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

        if (pickingDay >= 0) {
            val day = pickingDay
            TemplatePickDialog(
                templates = doc.templates,
                currentId = scheme?.dayTemplateIds?.getOrNull(day),
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

        deleteTemplate?.let { template ->
            DeleteTemplateDialog(
                template = template,
                inUse = doc.schemes.any { template.id in it.dayTemplateIds },
                onDismiss = { deleteTemplate = null },
                onConfirm = {
                    onMutate { plan ->
                        plan.copy(templates = plan.templates.filterNot { it.id == template.id }.toImmutableList())
                    }
                    deleteTemplate = null
                },
            )
        }

        if (showSchemes) {
            SchemeListDialog(
                doc = doc,
                onDismiss = { showSchemes = false },
                onSelect = { item ->
                    onMutate { plan -> plan.copy(activeSchemeId = item.id) }
                    showSchemes = false
                },
                onDelete = { item -> deleteScheme = item },
                onCreate = {
                    createScheme()
                    showSchemes = false
                },
            )
        }

        deleteScheme?.let { target ->
            OverlayDialog(
                show = true,
                title = "删除${target.name}？",
                summary = "删除后无法恢复。",
                onDismissRequest = { deleteScheme = null },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(text = "取消", onClick = { deleteScheme = null }, modifier = Modifier.weight(1f))
                    TextButton(
                        text = "删除",
                        onClick = {
                            onMutate { plan ->
                                val remaining = plan.schemes.filterNot { it.id == target.id }.toImmutableList()
                                plan.copy(
                                    schemes = remaining,
                                    activeSchemeId = if (plan.activeSchemeId == target.id) {
                                        remaining.firstOrNull()?.id
                                    } else {
                                        plan.activeSchemeId
                                    },
                                )
                            }
                            deleteScheme = null
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 方案列表（选项列表 Dialog）：水平 insideMargin 设 0 让行全出血、行内自带 24dp 内缩。
 */
@Composable
private fun SchemeListDialog(
    doc: PlanDocument,
    onDismiss: () -> Unit,
    onSelect: (Scheme) -> Unit,
    onDelete: (Scheme) -> Unit,
    onCreate: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = "倒班方案",
        summary = "点一下切换启用，右侧可以删掉。",
        onDismissRequest = onDismiss,
        insideMargin = DpSize(0.dp, 24.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            doc.schemes.sortedByDescending { it.createdAt }.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontSize = 15.sp)
                        Text(
                            "${item.cycleDays} 天周期",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (item.id == doc.activeSchemeId) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            MiuixIcons.Basic.Check,
                            contentDescription = "使用中",
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(MiuixIcons.Regular.Delete, contentDescription = "删除方案")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "新建方案",
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }
    }
}
