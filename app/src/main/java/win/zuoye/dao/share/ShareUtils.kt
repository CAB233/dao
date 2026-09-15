package win.zuoye.dao.share

import android.app.Activity
import android.content.Context
import android.content.Intent

/** 通过系统分享面板发送排班方案文本。 */
object ShareUtils {

    /** 主线程调用：分享一段文本（排班方案文本也走这里） */
    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startChooser(context, intent, "分享排班方案")
    }

    /**
     * 统一的分享入口。
     * 从 Application/Service 等非 Activity 的 Context 调 startActivity 必须带 NEW_TASK，
     * 否则会抛 AndroidRuntimeException 直接闪退——这里兜住这个坑。
     */
    private fun startChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

}
