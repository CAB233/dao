package win.zuoye.dao.ui.scheme

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import win.zuoye.dao.R
import win.zuoye.dao.data.IdSource
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.defaultGroup
import win.zuoye.dao.data.usedIds
import win.zuoye.dao.data.withoutSchemes
import win.zuoye.dao.share.clipboardText
import win.zuoye.dao.ui.components.CornerFab
import win.zuoye.dao.ui.components.DialogActions
import win.zuoye.dao.ui.components.rememberFabVisible
import win.zuoye.dao.ui.components.rememberPlanImportActions

/** 未指派时用的占位模板 id（模型里没有 null，指向不存在的模板即可显示「点击选择」） */
internal const val UNASSIGNED = 0L

internal fun formatYmd(ymd: Ymd): String =
    "${ymd.year}-${"%02d".format(ymd.month)}-${"%02d".format(ymd.day)}"

/** 倒班方案：整页是方案列表，点某张卡片时编辑页从底部以卡片形式滑入。 编辑卡片由外层主界面覆盖显示，使底栏与列表始终保留在卡片下方。 */
@Composable
fun PlanEditScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
    onImportPlan: (PlanShare) -> Unit,
    showBackButton: Boolean = true,
    backEnabled: Boolean = true,
    onSelectionChange: (Boolean) -> Unit = {},
    onEditScheme: (scheme: Scheme, autoFocusName: Boolean) -> Unit,
) {
    SchemeListScreen(
        doc = doc,
        onBack = onBack,
        showBackButton = showBackButton,
        backEnabled = backEnabled,
        onSelectionChange = onSelectionChange,
        onMutate = onMutate,
        onImportPlan = onImportPlan,
        onEnter = { onEditScheme(it, false) },
        onCreate = { onEditScheme(it, true) },
    )
}

/** 方案列表（像闹钟列表）：一张卡片一个方案，右侧是「使用中」开关，点卡片进入编辑页， 右下角加号可手动新建或导入方案；长按可多选，顶栏提供复制与删除操作。 */
@Composable
private fun SchemeListScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    showBackButton: Boolean,
    backEnabled: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
    onImportPlan: (PlanShare) -> Unit,
    onEnter: (Scheme) -> Unit,
    onCreate: (Scheme) -> Unit,
) {
    val context = LocalContext.current
    val defaultPlanName = stringResource(R.string.default_plan_name, doc.schemes.size + 1)
    val defaultGroupName = stringResource(R.string.default_group_name, 1)
    val activePlanRequired = stringResource(R.string.active_plan_required)
    val copySuffix = stringResource(R.string.plan_copy_suffix)
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showDeleteSelected by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    // 滚动时右下角的加号收起来（往下滚藏起来，往回滚或到顶再露出来）
    val fabVisible = rememberFabVisible {
        listState.firstVisibleItemIndex * 1_000_000 + listState.firstVisibleItemScrollOffset
    }

    fun exitSelection() {
        selecting = false
        selectedIds = emptySet()
        onSelectionChange(false)
    }

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun createScheme() {
        val now = System.currentTimeMillis()
        val newIds = IdSource(doc.usedIds(), now)
        val schemeId = newIds.nextId()
        val groupId = newIds.nextId()
        val today = Ymd.today()
        val scheme =
            Scheme(
                id = schemeId,
                name = defaultPlanName,
                cycleDays = 1,
                dayTemplateIds = persistentListOf(doc.templates.firstOrNull()?.id ?: UNASSIGNED),
                createdAt = now,
                groups =
                    persistentListOf(
                        SchemeGroup(
                            id = groupId,
                            name = defaultGroupName,
                            anchorEpochDay = today.epochDay,
                        )
                    ),
                defaultGroupId = groupId,
            )
        onCreate(scheme)
    }

    val planImport = rememberPlanImportActions { payload ->
        showAddMenu = false
        onImportPlan(payload)
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = backEnabled,
        onBackCompleted = { if (selecting) exitSelection() else onBack() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title =
                    if (selecting) {
                        pluralStringResource(
                            R.plurals.plans_selected,
                            selectedIds.size,
                            selectedIds.size,
                        )
                    } else {
                        stringResource(R.string.settings_plans)
                    },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(
                                MiuixIcons.Basic.Close,
                                contentDescription =
                                    stringResource(R.string.action_close_selection),
                            )
                        }
                    } else if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                MiuixIcons.Regular.Back,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(
                            onClick = {
                                val ids = selectedIds
                                onMutate { plan ->
                                    val newIds = IdSource(plan.usedIds())
                                    val copies =
                                        plan.schemes
                                            .filter { it.id in ids }
                                            .map { source ->
                                                val groupIds =
                                                    source.groups.associate {
                                                        it.id to newIds.nextId()
                                                    }
                                                source.copy(
                                                    id = newIds.nextId(),
                                                    name = source.name + copySuffix,
                                                    createdAt = newIds.nextId(),
                                                    groups =
                                                        source.groups
                                                            .map { group ->
                                                                group.copy(
                                                                    id = groupIds.getValue(group.id)
                                                                )
                                                            }
                                                            .toImmutableList(),
                                                    defaultGroupId =
                                                        groupIds.getValue(source.defaultGroupId),
                                                )
                                            }
                                    plan.copy(schemes = (plan.schemes + copies).toImmutableList())
                                }
                                exitSelection()
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                MiuixIcons.Regular.Copy,
                                contentDescription = stringResource(R.string.action_copy),
                            )
                        }
                        IconButton(
                            onClick = { showDeleteSelected = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                MiuixIcons.Regular.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // 和主页「今」按钮同一套做法
            if (!selecting) {
                CornerFab(visible = fabVisible, onClick = { showAddMenu = true }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.action_add_plan),
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        contentWindowInsets =
            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize()
                .consumeWindowInsets(padding)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = padding,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            if (doc.schemes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxHeight(0.8f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.empty_plans),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            items(doc.schemes.sortedByDescending { it.createdAt }, key = { it.id }) { scheme ->
                SchemeCard(
                    scheme = scheme,
                    active = scheme.id == doc.activeSchemeId,
                    selecting = selecting,
                    selected = scheme.id in selectedIds,
                    onEnter = { if (selecting) toggleSelection(scheme.id) else onEnter(scheme) },
                    onToggleSelection = { toggleSelection(scheme.id) },
                    onLongPress = {
                        selecting = true
                        selectedIds = selectedIds + scheme.id
                        onSelectionChange(true)
                    },
                    onToggleActive = { checked ->
                        if (checked) {
                            // 开另一个 = 前一个自动关闭（使用中同一时刻只有一个）
                            onMutate { plan -> plan.copy(activeSchemeId = scheme.id) }
                        } else {
                            // 不能全关，至少留一个
                            Toast.makeText(context, activePlanRequired, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }

        OverlayDialog(
            show = showAddMenu,
            title = stringResource(R.string.add_plan_title),
            onDismissRequest = { showAddMenu = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.create_manual),
                    onClick = {
                        showAddMenu = false
                        createScheme()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = stringResource(R.string.create_clipboard),
                    onClick = { planImport.importText(context.clipboardText()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = stringResource(R.string.create_qr),
                    onClick = { planImport.scan() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { showAddMenu = false },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }

        // 弹层必须在 Scaffold 的 content 里（宿主由 Scaffold 提供，放外面点不动）
        OverlayDialog(
            show = showDeleteSelected,
            title =
                pluralStringResource(
                    R.plurals.delete_selected_plans_title,
                    selectedIds.size,
                    selectedIds.size,
                ),
            summary = stringResource(R.string.delete_irreversible_period),
            onDismissRequest = { showDeleteSelected = false },
        ) {
            DialogActions(
                confirmText = stringResource(R.string.action_delete),
                destructive = true,
                onDismiss = { showDeleteSelected = false },
                onConfirm = {
                    // 先抓一份选中集合：onMutate 交给协程稍后执行，exitSelection() 会先把它清空
                    val ids = selectedIds
                    showDeleteSelected = false
                    onMutate { plan -> plan.withoutSchemes(ids) }
                    exitSelection()
                },
            )
        }
    }
}

/** 一个方案卡片（尺寸对齐系统闹钟列表）：标题（+「使用中」小字）， 右侧是使用中开关；关闭的方案整体变灰；多选模式下右侧用复选框替换开关。 */
@Composable
private fun SchemeCard(
    scheme: Scheme,
    active: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onEnter: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    // 关闭的方案整体灰掉
    val nameColor =
        if (active) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface
    val summaryColor =
        if (active) {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        } else {
            MiuixTheme.colorScheme.disabledOnSurface
        }
    val defaultGroupName = scheme.defaultGroup()?.name ?: stringResource(R.string.status_not_set)

    Card(
        onClick = { if (selecting) onToggleSelection() else onEnter() },
        onLongPress = onLongPress,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scheme.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = nameColor,
                    )
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.plan_active),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.current_default_group, defaultGroupName),
                    fontSize = 14.sp,
                    color = summaryColor,
                )
            }
            if (!selecting) {
                Switch(checked = active, onCheckedChange = onToggleActive)
            } else {
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = onToggleSelection,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
