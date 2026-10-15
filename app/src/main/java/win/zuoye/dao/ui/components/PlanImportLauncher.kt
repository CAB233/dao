package win.zuoye.dao.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.PlanShareCodec
import win.zuoye.dao.ui.scan.ScanContract

/** 方案导入入口：剪贴板文本与扫码结果共用同一段解码与失败提示。 */
internal class PlanImportActions(
    val importText: (String?) -> Unit,
    val scan: () -> Unit,
)

/** 引导页与方案列表页共用：解码分享载荷，失败提示「没识别到方案数据」。 */
@Composable
internal fun rememberPlanImportActions(onImport: (PlanShare) -> Unit): PlanImportActions {
    val context = LocalContext.current
    val unrecognized = stringResource(R.string.import_unrecognized)
    val importText: (String?) -> Unit = { text ->
        val payload = text?.let(PlanShareCodec::decode)
        if (payload == null) {
            Toast.makeText(context, unrecognized, Toast.LENGTH_SHORT).show()
        } else {
            onImport(payload)
        }
    }
    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result -> result?.let(importText) }
    return PlanImportActions(importText = importText, scan = { scanLauncher.launch(Unit) })
}
