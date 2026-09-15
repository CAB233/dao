package win.zuoye.dao.ui.scheme

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.PlanShareCodec
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.ui.common.PageCardStack
import win.zuoye.dao.ui.common.rememberFabVisible
import win.zuoye.dao.ui.scan.ScanCaptureActivity

/** 未指派时用的占位模板 id（模型里没有 null，指向不存在的模板即可显示「点击选择」） */
internal const val UNASSIGNED = 0L

internal fun formatYmd(ymd: Ymd): String =
    "${ymd.year}-${"%02d".format(ymd.month)}-${"%02d".format(ymd.day)}"

/**
 * 倒班方案（二级页面）：整页是方案列表，点某张卡片时编辑页像二级页面一样从右侧滑入，
 * 返回键 / 顶部返回箭头再滑回去。列表与编辑页共用这一个路由。
 */
@Composable
fun PlanEditScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
    onImportPlan: (PlanShare) -> Unit,
) {
    var editingSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    // 退出动画期间还要继续渲染这张卡片，所以记住最后打开的那个方案
    var cardScheme by remember { mutableStateOf<Scheme?>(null) }
    // 刚新建的方案进来时把焦点给名字输入框；打开已有方案则保持不高亮
    var autoFocusName by rememberSaveable { mutableStateOf(false) }
    val liveScheme = doc.schemes.firstOrNull { it.id == editingSchemeId }
    val shownScheme = liveScheme ?: cardScheme

    fun openScheme(scheme: Scheme, autoFocus: Boolean) {
        cardScheme = scheme
        autoFocusName = autoFocus
        editingSchemeId = scheme.id
    }

    PageCardStack(
        visible = editingSchemeId != null,
        base = {
            SchemeListScreen(
                doc = doc,
                onBack = onBack,
                onMutate = onMutate,
                onImportPlan = onImportPlan,
                onEnter = { openScheme(it, autoFocus = false) },
                onCreate = { openScheme(it, autoFocus = true) },
            )
        },
        card = {
            shownScheme?.let { scheme ->
                SchemeEditScreen(
                    doc = doc,
                    scheme = scheme,
                    autoFocusName = autoFocusName,
                    onBack = { editingSchemeId = null },
                    onSave = { editedDocument ->
                        val editedScheme = editedDocument.schemes.first { it.id == scheme.id }
                        onMutate { current ->
                            val alreadyExists = current.schemes.any { it.id == editedScheme.id }
                            current.copy(
                                templates = editedDocument.templates,
                                schemes = if (alreadyExists) {
                                    current.schemes.map {
                                        if (it.id == editedScheme.id) editedScheme else it
                                    }.toImmutableList()
                                } else {
                                    current.schemes.toPersistentList().add(editedScheme)
                                },
                                activeSchemeId = if (alreadyExists) {
                                    current.activeSchemeId
                                } else {
                                    editedScheme.id
                                },
                            )
                        }
                        editingSchemeId = null
                    },
                    onDelete = {
                        onMutate { plan ->
                            val remaining = plan.schemes.filterNot { it.id == scheme.id }.toImmutableList()
                            plan.copy(
                                schemes = remaining,
                                activeSchemeId = if (plan.activeSchemeId == scheme.id) {
                                    remaining.firstOrNull()?.id
                                } else {
                                    plan.activeSchemeId
                                },
                            )
                        }
                        editingSchemeId = null
                    },
                )
            }
        },
    )
}

/**
 * 方案列表（像闹钟列表）：一张卡片一个方案，右侧是「使用中」开关，点卡片进入编辑页，
 * 右下角加号可手动新建或导入方案；长按可多选，删除按钮在屏幕底部。
 */
@Composable
private fun SchemeListScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    onMutate: (transform: (PlanDocument) -> PlanDocument) -> Unit,
    onImportPlan: (PlanShare) -> Unit,
    onEnter: (Scheme) -> Unit,
    onCreate: (Scheme) -> Unit,
) {
    val context = LocalContext.current
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
    }

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun createScheme() {
        val id = System.currentTimeMillis()
        val today = Ymd.today()
        val scheme = Scheme(
            id = id,
            name = "方案 ${doc.schemes.size + 1}",
            cycleDays = 1,
            anchorEpochDay = today.epochDay,
            dayTemplateIds = persistentListOf(doc.templates.firstOrNull()?.id ?: UNASSIGNED),
            createdAt = id,
            groups = persistentListOf(
                SchemeGroup(
                    id = id,
                    name = "班组 1",
                    anchorEpochDay = today.epochDay,
                ),
            ),
            defaultGroupId = id,
        )
        onCreate(scheme)
    }

    fun importFrom(text: String?) {
        val payload = text?.let(PlanShareCodec::decode)
        if (payload == null) {
            Toast.makeText(context, "没识别到方案数据", Toast.LENGTH_SHORT).show()
        } else {
            showAddMenu = false
            onImportPlan(payload)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { importFrom(it) }
    }

    BackHandler {
        if (selecting) exitSelection() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (selecting) "已选 ${selectedIds.size} 个" else "倒班方案",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(MiuixIcons.Basic.Close, contentDescription = "取消选择")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // 和主页「今」按钮同一套做法：关阴影（默认阴影会建离屏图层）
            if (!selecting) {
                Box(Modifier.padding(end = 8.dp, bottom = 12.dp)) {
                    AnimatedVisibility(
                        visible = fabVisible,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    ) {
                        FloatingActionButton(
                            onClick = { showAddMenu = true },
                            shadowElevation = 0.dp,
                            minWidth = 54.dp,
                            minHeight = 54.dp,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = "添加方案",
                                tint = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (selecting) {
                Surface(color = MiuixTheme.colorScheme.surface) {
                    Button(
                        onClick = { showDeleteSelected = true },
                        enabled = selectedIds.isNotEmpty(),
                        // 删除用红色底
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            disabledColor = MiuixTheme.colorScheme.error.copy(alpha = 0.4f),
                            contentColor = MiuixTheme.colorScheme.onError,
                            disabledContentColor = MiuixTheme.colorScheme.onError.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text("删除")
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
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
                    },
                    onToggleActive = { checked ->
                        if (checked) {
                            // 开另一个 = 前一个自动关闭（使用中同一时刻只有一个）
                            onMutate { plan -> plan.copy(activeSchemeId = scheme.id) }
                        } else {
                            // 不能全关，至少留一个
                            Toast.makeText(context, "至少保留一个使用中的方案", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }

        OverlayDialog(
            show = showAddMenu,
            title = "添加倒班方案",
            onDismissRequest = { showAddMenu = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "手动添加",
                    onClick = {
                        showAddMenu = false
                        createScheme()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "从剪贴板导入",
                    onClick = { importFrom(context.clipboardText()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "扫码导入",
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setBeepEnabled(false)
                                setOrientationLocked(true)
                                setCaptureActivity(ScanCaptureActivity::class.java)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { showAddMenu = false },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("取消")
                }
            }
        }

        // 弹层必须在 Scaffold 的 content 里（宿主由 Scaffold 提供，放外面点不动）
        OverlayDialog(
            show = showDeleteSelected,
            title = "删除选中的 ${selectedIds.size} 个方案？",
            summary = "删除后无法恢复。",
            onDismissRequest = { showDeleteSelected = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteSelected = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        // 先抓一份选中集合：onMutate 交给协程稍后执行，exitSelection() 会先把它清空
                        val ids = selectedIds
                        showDeleteSelected = false
                        onMutate { plan ->
                            val remaining = plan.schemes
                                .filterNot { it.id in ids }
                                .toImmutableList()
                            plan.copy(
                                schemes = remaining,
                                // 使用中的那个被删掉时，顺位到剩下的第一个
                                activeSchemeId = plan.activeSchemeId
                                    ?.takeIf { id -> remaining.any { it.id == id } }
                                    ?: remaining.firstOrNull()?.id,
                            )
                        }
                        exitSelection()
                    },
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 读取剪贴板第一段文本，供分享载荷导入。 */
private fun Context.clipboardText(): String =
    (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        .orEmpty()

/**
 * 一个方案卡片（尺寸对齐系统闹钟列表）：标题（+「使用中」小字）/ 周期摘要，
 * 右侧是使用中开关；关闭的方案整体变灰；多选模式下左侧出现复选框、开关隐藏。
 */
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
    val nameColor = if (active) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface
    val summaryColor = if (active) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }

    Card(
        onClick = { if (selecting) onToggleSelection() else onEnter() },
        onLongPress = onLongPress,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = onToggleSelection,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
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
                            text = "使用中",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${scheme.cycleDays} 天周期",
                    fontSize = 13.sp,
                    color = summaryColor,
                )
            }
            if (!selecting) {
                Switch(checked = active, onCheckedChange = onToggleActive)
            }
        }
    }
}
