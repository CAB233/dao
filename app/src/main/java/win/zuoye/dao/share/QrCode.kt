package win.zuoye.dao.share

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    val pixels = IntArray(sizePx * sizePx) { Color.WHITE }
    // 整数倍放大并居中，保留至少四个模块宽的白色静区。
    for (y in 0 until matrix.size) {
        val top = padding + y * scale
        for (x in 0 until matrix.size) {
            if (matrix.getModule(x, y)) {
                val left = padding + x * scale
                for (dy in 0 until scale) {
                    val start = (top + dy) * sizePx + left
                    pixels.fill(Color.BLACK, start, start + scale)
                }
            }
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}
