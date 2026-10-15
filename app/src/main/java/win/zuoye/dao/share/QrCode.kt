package win.zuoye.dao.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import io.nayuki.qrcodegen.DataTooLongException
import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment

private const val QUIET_ZONE_MODULES = 4
private const val UTF8_ECI = 26

/** 文本 → 原始二维码模块（M 级纠错与 UTF-8 ECI），内容为空或过长时返回 null。 */
fun encodeQrMatrix(content: String): QrCode? {
    if (content.isEmpty()) return null
    val segments = QrSegment.makeSegments(content)
    if (segments.any { it.mode == QrSegment.Mode.BYTE }) {
        segments.add(0, QrSegment.makeEci(UTF8_ECI))
    }
    return try {
        QrCode.encodeSegments(
            segments,
            QrCode.Ecc.MEDIUM,
            QrCode.MIN_VERSION,
            QrCode.MAX_VERSION,
            -1,
            false,
        )
    } catch (_: DataTooLongException) {
        null
    }
}

/** 文本 → 二维码位图（给 Compose 用）；装不下时返回 null，由调用方提示 */
fun encodeQrCode(content: String, sizePx: Int = 640): ImageBitmap? {
    if (sizePx <= 0) return null
    val matrix = encodeQrMatrix(content) ?: return null
    val scale = sizePx / (matrix.size + QUIET_ZONE_MODULES * 2)
    if (scale == 0) return null
    val padding = (sizePx - matrix.size * scale) / 2
    // 直接画到位图上：省掉一个 sizePx² 的 IntArray 和一次整块像素拷贝
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply { color = Color.BLACK }
    // 整数倍放大并居中，保留至少四个模块宽的白色静区。
    for (y in 0 until matrix.size) {
        val top = (padding + y * scale).toFloat()
        for (x in 0 until matrix.size) {
            if (matrix.getModule(x, y)) {
                val left = (padding + x * scale).toFloat()
                canvas.drawRect(left, top, left + scale, top + scale, paint)
            }
        }
    }
    return bitmap.asImageBitmap()
}
