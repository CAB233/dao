package win.zuoye.dao.ui.scan

import android.graphics.ImageFormat
import androidx.annotation.Keep
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/** JNI 直接读取相机的亮度平面，识别成功后仅复制 UTF-8 文本。类名用于原生入口。 */
@Keep
internal object NativeQrReader {
    init {
        System.loadLibrary("dao_qr")
    }

    fun read(image: ImageProxy): String? {
        require(image.format == ImageFormat.YUV_420_888) { "Expected a YUV camera frame" }
        val luminance = image.planes[0]
        require(luminance.pixelStride == 1) { "Expected contiguous luminance pixels" }
        val crop = image.cropRect
        return readQr(
                luminance.buffer,
                luminance.rowStride,
                crop.left,
                crop.top,
                crop.width(),
                crop.height(),
                image.imageInfo.rotationDegrees,
            )
            ?.toString(Charsets.UTF_8)
    }

    private external fun readQr(
        luminance: ByteBuffer,
        rowStride: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        rotation: Int,
    ): ByteArray?
}
