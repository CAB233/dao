package win.zuoye.dao.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    downloading: Boolean,
    downloadProgress: Int?,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(
            if (downloading) R.string.update_downloading_title else R.string.update_available_title,
        ),
        onDismissRequest = { if (!downloading) onDismiss() },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            if (downloading) {
                Text(
                    text = downloadProgress?.let {
                        stringResource(R.string.update_downloading_progress, it)
                    } ?: stringResource(R.string.update_downloading),
                )
            } else if (update != null) {
                Text(stringResource(R.string.update_available_message, update.versionName))
                if (update.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = update.releaseNotes,
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    )
                }
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
