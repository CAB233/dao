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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.ui.ShiftPalette

/** 新建/编辑班次模板：名称 + 起止时间（滚轮）+ 色板。existing = null 表示新建。 */
@Composable
fun TemplateEditorDialog(
    show: Boolean,
    existing: ShiftTemplate?,
    usedColors: List<Int>,
    onSave: (name: String, startMinute: Int, endMinute: Int, colorArgb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
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
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                TimeRow("开始", startH, { startH = it }, startM, { startM = it })
                TimeRow("结束", endH, { endH = it }, endM, { endM = it })
                Spacer(Modifier.height(12.dp))
                ColorGrid(selected = color, onSelect = { color = it })
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
}

@Composable
private fun TimeRow(
    label: String,
    hour: Int,
    onHour: (Int) -> Unit,
    minute: Int,
    onMinute: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(44.dp), color = MiuixTheme.colorScheme.onSurface)
        NumberPicker(
            value = hour,
            onValueChange = onHour,
            range = 0..23,
            wrapAround = true,
            label = { "%02d".format(it) },
            modifier = Modifier.weight(1f).height(110.dp),
        )
        Text(":", color = MiuixTheme.colorScheme.onSurface)
        NumberPicker(
            value = minute,
            onValueChange = onMinute,
            range = 0..59,
            wrapAround = true,
            label = { "%02d".format(it) },
            modifier = Modifier.weight(1f).height(110.dp),
        )
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
