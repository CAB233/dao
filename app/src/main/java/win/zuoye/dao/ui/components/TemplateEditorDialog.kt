package win.zuoye.dao.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.R
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.ui.ShiftPalette

internal enum class TemplateTimePickerStyle {
    Inline,
    SeparateDialog,
}

/** 共用模板编辑状态与颜色弹窗，时间选择按所在页面使用内嵌滚轮或独立弹窗。 */
@Composable
internal fun TemplateEditorDialog(
    show: Boolean,
    existing: ShiftTemplate?,
    usedColors: List<Int>,
    onSave:
        (name: String, startMinute: Int, endMinute: Int, colorArgb: Int, isRest: Boolean) -> Unit,
    onDismiss: () -> Unit,
    timePickerStyle: TemplateTimePickerStyle = TemplateTimePickerStyle.SeparateDialog,
) {
    val inlineTimePicker = timePickerStyle == TemplateTimePickerStyle.Inline
    val defaultRestName = stringResource(R.string.shift_rest)
    val fieldColors =
        if (inlineTimePicker) TextFieldDefaults.textFieldColors()
        else
            TextFieldDefaults.textFieldColors(
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer
            )
    val cardBackground =
        if (inlineTimePicker) MiuixTheme.colorScheme.surface
        else MiuixTheme.colorScheme.surfaceContainer
    val initialColor =
        existing?.colorArgb
            ?: ShiftPalette.presets.firstOrNull { it !in usedColors }
            ?: ShiftPalette.presets.first()
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var startMinute by remember(existing) { mutableIntStateOf(existing?.startMinute ?: 8 * 60) }
    var endMinute by remember(existing) { mutableIntStateOf(existing?.endMinute ?: 15 * 60) }
    var color by remember(existing) { mutableIntStateOf(initialColor) }
    var isRest by remember(existing) { mutableStateOf(existing?.isRest == true) }
    var editingEnd by remember(existing) { mutableStateOf(false) }
    var showTimeDialog by remember(existing) { mutableStateOf(false) }
    var showColorDialog by remember(existing) { mutableStateOf(false) }

    // 常驻组合保留退出动画；每次打开恢复已保存的值，重新新建也从默认值开始。
    LaunchedEffect(show, existing) {
        if (show) {
            name = existing?.name ?: ""
            startMinute = existing?.startMinute ?: 8 * 60
            endMinute = existing?.endMinute ?: 15 * 60
            color = initialColor
            isRest = existing?.isRest == true
            editingEnd = false
            showTimeDialog = false
            showColorDialog = false
        }
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
        Column(Modifier.heightIn(max = 500.dp).imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.shift_name),
                    colors = fieldColors,
                    trailingIcon = {
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
                            color = cardBackground,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                ) {
                    if (inlineTimePicker) {
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
                    } else {
                        BasicComponent(
                            title = stringResource(R.string.shift_rest),
                            onClick = { isRest = !isRest },
                            endActions = {
                                Switch(checked = isRest, onCheckedChange = { isRest = it })
                            },
                        )
                        AnimatedVisibility(
                            visible = !isRest,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            Column {
                                TemplateTimeRow(
                                    title = stringResource(R.string.shift_start_time),
                                    time = ShiftTemplate.format(startMinute),
                                    enabled = !isRest,
                                    holdDown = showTimeDialog && !editingEnd,
                                    onClick = {
                                        editingEnd = false
                                        showTimeDialog = true
                                    },
                                )
                                val end = ShiftTemplate.format(endMinute)
                                TemplateTimeRow(
                                    title = stringResource(R.string.shift_end_time),
                                    time =
                                        if (endMinute < startMinute)
                                            stringResource(R.string.shift_next_day, end)
                                        else end,
                                    enabled = !isRest,
                                    holdDown = showTimeDialog && editingEnd,
                                    onClick = {
                                        editingEnd = true
                                        showTimeDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
                if (inlineTimePicker && !isRest) {
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
                    TimePicker(
                        currentMinute = if (editingEnd) endMinute else startMinute,
                        onHourChange = {
                            if (editingEnd) endMinute = it * 60 + endMinute % 60
                            else startMinute = it * 60 + startMinute % 60
                        },
                        onMinuteChange = {
                            if (editingEnd) endMinute = endMinute / 60 * 60 + it
                            else startMinute = startMinute / 60 * 60 + it
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DialogActions(
                confirmText = stringResource(R.string.action_save),
                enabled = isRest || name.isNotBlank(),
                onDismiss = onDismiss,
                onConfirm = {
                    onSave(
                        name.trim().ifBlank { defaultRestName },
                        if (isRest) 0 else startMinute,
                        if (isRest) 0 else endMinute,
                        color,
                        isRest,
                    )
                },
            )
        }
    }

    if (!inlineTimePicker) {
        ShiftTimeDialog(
            show = show && !isRest && showTimeDialog,
            title =
                stringResource(
                    if (editingEnd) R.string.shift_end_time else R.string.shift_start_time
                ),
            currentMinute = if (editingEnd) endMinute else startMinute,
            onDismiss = { showTimeDialog = false },
            onConfirm = {
                if (editingEnd) endMinute = it else startMinute = it
                showTimeDialog = false
            },
        )
    }
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

@Composable
private fun TemplateTimeRow(
    title: String,
    time: String,
    enabled: Boolean,
    holdDown: Boolean,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        enabled = enabled,
        holdDownState = holdDown,
        onClick = onClick,
        endActions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = time, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

@Composable
private fun TimePicker(
    currentMinute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        NumberPicker(
            value = currentMinute / 60,
            onValueChange = onHourChange,
            range = 0..23,
            wrapAround = true,
            label = { "%02d".format(it) },
            itemHeight = 48.dp,
            modifier = Modifier.weight(1f),
        )
        Text(":", color = MiuixTheme.colorScheme.onSurface)
        NumberPicker(
            value = currentMinute % 60,
            onValueChange = onMinuteChange,
            range = 0..59,
            wrapAround = true,
            label = { "%02d".format(it) },
            itemHeight = 48.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShiftTimeDialog(
    show: Boolean,
    title: String,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draft by remember { mutableIntStateOf(currentMinute) }
    LaunchedEffect(show) { if (show) draft = currentMinute }
    OverlayDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 500.dp)) {
            TimePicker(
                currentMinute = draft,
                onHourChange = { draft = it * 60 + draft % 60 },
                onMinuteChange = { draft = draft / 60 * 60 + it },
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(16.dp))
            DialogActions(
                confirmText = stringResource(R.string.action_confirm),
                onDismiss = onDismiss,
                onConfirm = { onConfirm(draft) },
            )
        }
    }
}

@Composable
private fun ColorDialog(
    show: Boolean,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draft by remember(current) { mutableStateOf(Color(current)) }
    LaunchedEffect(show, current) { if (show) draft = Color(current) }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.color_select),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 500.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ColorPalette(
                    color = draft,
                    onColorChanged = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            DialogActions(
                confirmText = stringResource(R.string.action_confirm),
                onDismiss = onDismiss,
                onConfirm = { onConfirm(draft.copy(alpha = 1f).toArgb()) },
            )
        }
    }
}

@Composable
private fun DialogActions(
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = confirmText,
            enabled = enabled,
            onClick = onConfirm,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.weight(1f),
        )
    }
}
