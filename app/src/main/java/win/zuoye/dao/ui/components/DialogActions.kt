package win.zuoye.dao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.R

/** 弹窗底部「取消 + 确定」按钮行；删除类操作把 [destructive] 置 true 用错误色。 */
@Composable
internal fun DialogActions(
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
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
            colors =
                if (destructive) {
                    ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColorsPrimary()
                },
            modifier = Modifier.weight(1f),
        )
    }
}
