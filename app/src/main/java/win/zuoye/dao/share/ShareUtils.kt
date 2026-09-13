package win.zuoye.dao.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShareCodec
import win.zuoye.dao.R
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.domain.resolveShift
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把月历渲染成图片 / 文本并通过系统分享面板发出，全程离线。 */
object ShareUtils {

    private const val WIDTH = 1080
    private const val PAD = 48
    private const val CELL_W = (WIDTH - PAD * 2) / 7
    private const val CELL_H = 150

    /** IO 线程渲染并落盘，返回可分享的文件 */
    suspend fun prepareImageFile(context: Context, doc: PlanDocument, year: Int, month: Int): File =
        withContext(Dispatchers.IO) {
            val bitmap = renderMonth(doc, year, month, context.getString(R.string.app_name))
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "roster_${year}_${month}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file
        }

    /** 主线程调用：发起图片分享 */
    fun shareImage(context: Context, file: File, text: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(context, intent, "分享计划表")
    }

    /** 主线程调用：发起纯文本分享 */
    fun shareText(context: Context, doc: PlanDocument, year: Int, month: Int) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, monthText(doc, year, month, context.getString(R.string.app_name)))
        }
        startChooser(context, intent, "分享计划表")
    }

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

    fun monthText(doc: PlanDocument, year: Int, month: Int, appName: String): String {
        val sb = StringBuilder("${year}年${month}月 $appName\n")
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        for (day in 1..Ymd.daysInMonth(year, month)) {
            val date = Ymd(year, month, day)
            val shift = resolveShift(doc, date.epochDay)
            val name = shift?.template?.name ?: "未排班"
            val time = shift?.takeIf { !it.template.isRest }?.template?.timeRangeText() ?: ""
            sb.append("${month}月${day}日 周${weekdays[date.weekdayIndex]} $name $time\n")
        }
        return sb.toString()
    }

    private fun renderMonth(doc: PlanDocument, year: Int, month: Int, appName: String): Bitmap {
        val daysInMonth = Ymd.daysInMonth(year, month)
        val firstOffset = Ymd(year, month, 1).weekdayIndex
        val rows = (firstOffset + daysInMonth + 6) / 7
        val headerH = 220
        val weekdayH = 64
        val footerH = 90
        val height = headerH + weekdayH + rows * CELL_H + footerH

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // 标题
        textPaint.color = Color.parseColor("#1C1B1F")
        textPaint.textSize = 76f
        textPaint.isFakeBoldText = true
        canvas.drawText("${year}年${month}月", WIDTH / 2f, 128f, textPaint)
        textPaint.textSize = 34f
        textPaint.isFakeBoldText = false
        textPaint.color = Color.parseColor("#757575")
        canvas.drawText(appName, WIDTH / 2f, 182f, textPaint)

        // 星期表头
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        textPaint.textSize = 36f
        textPaint.color = Color.parseColor("#757575")
        weekdays.forEachIndexed { i, w ->
            canvas.drawText(w, PAD + i * CELL_W + CELL_W / 2f, headerH + 44f, textPaint)
        }

        // 日期格子
        textPaint.textSize = 42f
        for (day in 1..daysInMonth) {
            val cellIndex = firstOffset + day - 1
            val col = cellIndex % 7
            val row = cellIndex / 7
            val left = PAD + col * CELL_W + 4f
            val top = headerH + weekdayH + row * CELL_H + 4f
            val right = left + CELL_W - 8f
            val bottom = top + CELL_H - 8f

            val shift = resolveShift(doc, Ymd(year, month, day).epochDay)
            val template = shift?.template
            if (template != null) {
                boxPaint.color = template.colorArgb
                canvas.drawRoundRect(RectF(left, top, right, bottom), 24f, 24f, boxPaint)
            }

            val onColor = template?.let { textColorFor(it.colorArgb) }
                ?: Color.parseColor("#757575")
            textPaint.color = onColor
            textPaint.textSize = 42f
            textPaint.isFakeBoldText = true
            canvas.drawText("$day", left + CELL_W / 2f - 4f, top + 62f, textPaint)
            textPaint.textSize = 27f
            textPaint.isFakeBoldText = false
            canvas.drawText(
                template?.name ?: "",
                left + CELL_W / 2f - 4f,
                top + 112f,
                textPaint,
            )
        }

        // 页脚
        textPaint.color = Color.parseColor("#BDBDBD")
        textPaint.textSize = 30f
        canvas.drawText("由「$appName」生成", WIDTH / 2f, height - 34f, textPaint)
        return bitmap
    }

    private fun textColorFor(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.62) Color.parseColor("#1C1B1F") else Color.WHITE
    }
}
