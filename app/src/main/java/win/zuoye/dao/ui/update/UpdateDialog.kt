package win.zuoye.dao.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import win.zuoye.dao.R
import win.zuoye.dao.update.UpdateInfo

@Composable
fun UpdateDialog(
    show: Boolean,
    update: UpdateInfo?,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.update_available_title),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (update != null) {
                Text(stringResource(R.string.update_available_message, update.versionName))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onUpdate,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_update))
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateInstallDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.update_install_confirm_title),
        summary = stringResource(R.string.update_install_confirm_message),
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.update_install_confirm_title))
            }
        }
    }
}
