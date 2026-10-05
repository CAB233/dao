package win.zuoye.dao.ui.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/** 扫码成功返回二维码文本，关闭或权限拒绝返回 null。 */
internal class ScanContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, ScanCaptureActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_CONTENTS) else null

    companion object {
        private const val EXTRA_CONTENTS = "win.zuoye.dao.SCAN_CONTENTS"

        fun resultIntent(text: String): Intent = Intent().putExtra(EXTRA_CONTENTS, text)
    }
}
