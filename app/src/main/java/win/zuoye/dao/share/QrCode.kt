package win.zuoye.dao.share

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 文本 → 二维码矩阵（ZXing，纯本地；不依赖 Android，单测可直接编/解）。
 * 内容太长装不下时返回 null。
 */
fun encodeQrMatrix(content: String, sizePx: Int = 640): BitMatrix? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        // 静区（quiet zone）至少 4 个模块宽——二维码外面那圈白边就是靠它撑出来的。
        // 之前给 1，浅色模式下底板≈白色还能凑合，深色模式底板是纯黑，扫码器直接找不到定位图案。
        EncodeHintType.MARGIN to 4,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
}.getOrNull()

/** 文本 → 二维码位图（给 Compose 用）；装不下时返回 null，由调用方提示 */
fun encodeQrCode(content: String, sizePx: Int = 640): ImageBitmap? {
    val matrix = encodeQrMatrix(content, sizePx) ?: return null
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val row = y * sizePx
        for (x in 0 until sizePx) {
            pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}
