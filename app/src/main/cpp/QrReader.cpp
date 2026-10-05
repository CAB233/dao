#include <jni.h>

#include "ReadBarcode.h"

#include <cstdint>
#include <exception>
#include <limits>

namespace {
void throwJava(JNIEnv* env, const char* type, const char* message)
{
    if (jclass exception = env->FindClass(type)) {
        env->ThrowNew(exception, message);
        env->DeleteLocalRef(exception);
    }
}
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_win_zuoye_dao_ui_scan_NativeQrReader_readQr(
    JNIEnv* env, jobject, jobject buffer, jint rowStride, jint left, jint top,
    jint width, jint height, jint rotation)
{
    const auto* data = static_cast<const uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    const int64_t endOfRow = static_cast<int64_t>(left) + width;
    const int64_t lastRow = static_cast<int64_t>(top) + height - 1;
    if (!data || rowStride <= 0 || left < 0 || top < 0 || width <= 0 || height <= 0 ||
        endOfRow > rowStride || lastRow * rowStride + endOfRow > capacity ||
        (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)) {
        throwJava(env, "java/lang/IllegalArgumentException", "Invalid luminance buffer or crop");
        return nullptr;
    }

    try {
        const ZXing::ImageView image(
            data + static_cast<int64_t>(top) * rowStride + left,
            width, height, ZXing::ImageFormat::Lum, rowStride);
        const auto options = ZXing::ReaderOptions()
            .setFormats(ZXing::BarcodeFormat::QRCode)
            .setTryHarder(true)
            .setTryRotate(true)
            .setTryInvert(true)
            .setTryDownscale(true)
            .setMaxNumberOfSymbols(1)
            .setTextMode(ZXing::TextMode::Plain);
        const auto results = ZXing::ReadBarcodes(image.rotated(rotation), options);
        for (const auto& result : results) {
            if (!result.isValid()) continue;
            const auto text = result.text();
            if (text.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
                throwJava(env, "java/lang/IllegalStateException", "QR text exceeds JNI limits");
                return nullptr;
            }
            auto bytes = env->NewByteArray(static_cast<jsize>(text.size()));
            if (bytes) {
                env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()),
                    reinterpret_cast<const jbyte*>(text.data()));
            }
            return bytes;
        }
    } catch (const std::exception& error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "QR decoding failed");
    }
    return nullptr;
}
