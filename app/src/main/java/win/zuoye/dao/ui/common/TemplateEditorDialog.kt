package win.zuoye.dao.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.ui.ShiftPalette

/** 时间滚轮的行高：miuix 默认 45dp，这里跟日期弹窗保持一致，数字别挨得太近 */
private val TIME_ITEM_HEIGHT = 48.dp

/**
 * 新建/编辑班次模板：名称（右边的圆点是当前颜色，点开是颜色页）+ 「开始 / 结束」切换框
 * + 一组共用时间滚轮。existing = null 表示新建。
 */
@Composable
fun TemplateEditorDialog(
    show: Boolean,
    existing: ShiftTemplate?,
    usedColors: List<Int>,
    onSave: (name: String, startMinute: Int, endMinute: Int, colorArgb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
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
    var showColorDialog by remember(existing) { mutableStateOf(false) }

    OverlayDialog(
        show = show,
        title = if (existing == null) "新增班次" else "编辑班次",
        summary = "例：早班 08:00–15:00；结束时间早于开始表示跨零点夜班",
        onDismissRequest = onDismiss,
    ) {
        // 长内容 Dialog：miuix 的 WindowDialog 不限 content 高度，
        // 所以给内容一个上限、让滚动区自己滚，按钮作为非加权子项固定在底部。
        Column(Modifier.heightIn(max = 500.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "班次名称",
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
                SegmentedSwitch(
                    tabs = listOf("开始", "结束"),
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
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "保存",
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(name.trim(), startH * 60 + startM, endH * 60 + endM, color)
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
 * 颜色页：上面是调色盘（miuix [ColorPicker]，色相/饱和度/明度/透明度四条滑杆 + 预览），
 * 下面是预设色板。确定时把透明度收成 1——班次色要画在日历格上，半透明会跟底色混在一起。
 */
@Composable
private fun ColorDialog(
    show: Boolean,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draft by remember(show, current) { mutableStateOf(Color(current)) }

    OverlayDialog(
        show = show,
        title = "选择颜色",
        onDismissRequest = onDismiss,
    ) {
        // 调色盘本身挺高，长内容按 Dialog 规范交给滚动区
        Column(Modifier.heightIn(max = 500.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                ColorPicker(
                    color = draft,
                    onColorChanged = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "预设颜色",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                ColorGrid(selected = draft.toArgb(), onSelect = { draft = Color(it) })
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = { onConfirm(draft.copy(alpha = 1f).toArgb()) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ColorGrid(selected: Int, onSelect: (Int) -> Unit) {
    val rows = ShiftPalette.presets.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { argb ->
                    val isSel = argb == selected
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(ShiftPalette.color(argb), CircleShape)
                            .then(
                                if (isSel) Modifier.border(
                                    3.dp,
                                    MiuixTheme.colorScheme.onSurface,
                                    CircleShape,
                                ) else Modifier
                            )
                            .clickable { onSelect(argb) },
                    )
                }
            }
        }
    }
}
