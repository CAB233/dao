package win.zuoye.dao.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.R
import win.zuoye.dao.ui.ShiftPalette

/** 时间滚轮的行高：miuix 默认 45dp，这里跟日期弹窗保持一致，数字别挨得太近 */
private val TIME_ITEM_HEIGHT = 48.dp

/**
 * 新建/编辑班次模板：名称（右边的圆点是当前颜色，点开是颜色页）+ 休班开关
 * + 「开始 / 结束」切换框与共用时间滚轮。existing = null 表示新建。
 */
@Composable
fun TemplateEditorDialog(
    show: Boolean,
    existing: ShiftTemplate?,
    usedColors: List<Int>,
    onSave: (name: String, startMinute: Int, endMinute: Int, colorArgb: Int, isRest: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultRestName = stringResource(R.string.shift_rest)
    // 不在这里 return：常驻组合、交给 OverlayDialog 按 show 播进出动画
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var startH by remember(existing) { mutableIntStateOf(existing?.let { it.startMinute / 60 } ?: 8) }
    var startM by remember(existing) { mutableIntStateOf(existing?.let { it.startMinute % 60 } ?: 0) }
    var endH by remember(existing) { mutableIntStateOf(existing?.let { it.endMinute / 60 } ?: 15) }
    var endM by remember(existing) { mutableIntStateOf(existing?.let { it.endMinute % 60 } ?: 0) }
    var color by remember(existing) {
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
        color = existing?.colorArgb
            ?: ShiftPalette.presets.firstOrNull { it !in usedColors }
            ?: ShiftPalette.presets.first()
        editingEnd = false
        isRest = existing?.isRest == true
        showColorDialog = false
    }

    OverlayDialog(
        show = show,
        title = stringResource(if (existing == null) R.string.shift_add_title else R.string.shift_edit_title),
        summary = stringResource(R.string.shift_editor_summary),
        onDismissRequest = onDismiss,
    ) {
        // 长内容 Dialog：miuix 的 WindowDialog 不限 content 高度，
        // 所以给内容一个上限、让滚动区自己滚，按钮作为非加权子项固定在底部。
        Column(
            Modifier
                .heightIn(max = 500.dp)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.shift_name),
                    trailingIcon = {
                        // 颜色收进名称框右边这个圆点里，点它进颜色页
                        IconButton(onClick = { showColorDialog = true }) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .background(ShiftPalette.color(color), CircleShape),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surface,
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.shift_rest), fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(checked = isRest, onCheckedChange = { isRest = it })
                    }
                }
                if (!isRest) {
                    Spacer(Modifier.height(12.dp))
                    SegmentedSwitch(
                        tabs = listOf(stringResource(R.string.shift_start), stringResource(R.string.shift_end)),
                        selectedIndex = if (editingEnd) 1 else 0,
                        onSelect = { editingEnd = it == 1 },
                        modifier = Modifier.fillMaxWidth(),
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

/**
 * 颜色页使用 miuix [ColorPalette] 色板。确定时把透明度收成 1——
 * 班次色要画在日历格上，半透明会跟底色混在一起。
 */
@Composable
private fun ColorDialog(
    show: Boolean,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // 同主弹窗：每次打开都从当前颜色重新开始，别让上次取消的改动留在里面
    var draft by remember(current) { mutableStateOf(Color(current)) }
    LaunchedEffect(show, current) {
        if (show) draft = Color(current)
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.color_select),
        onDismissRequest = onDismiss,
    ) {
        // 调色盘本身挺高，长内容按 Dialog 规范交给滚动区
        Column(Modifier.heightIn(max = 500.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
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
