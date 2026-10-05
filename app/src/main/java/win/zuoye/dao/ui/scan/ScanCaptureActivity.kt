package win.zuoye.dao.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import win.zuoye.dao.R
import win.zuoye.dao.ui.theme.AppTheme

/** CameraX 管理相机生命周期，ZXing-C++ 在单独线程分析取景框内的亮度数据。 */
class ScanCaptureActivity : ComponentActivity() {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val resultDelivered = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var previewSize: Size? = null
    private var surfaceRequest by mutableStateOf<SurfaceRequest?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) prepareCamera() else closeWithMessage(R.string.scan_permission_denied)
        }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        setContent {
            AppTheme {
                ScanScreen(
                    surfaceRequest = surfaceRequest,
                    onPreviewSizeChanged = { size ->
                        if (previewSize != size) {
                            previewSize = size
                            bindCamera()
                        }
                    },
                    onFocus = ::focusAt,
                    onBack = { finish() },
                )
            }
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            prepareCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun prepareCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                if (!isFinishing && !isDestroyed) {
                    try {
                        cameraProvider = future.get()
                        bindCamera()
                    } catch (error: Exception) {
                        Log.e(TAG, "Camera initialization failed", error)
                        closeWithMessage(R.string.scan_camera_unavailable)
                    }
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val size = previewSize ?: return
        if (size.width <= 0 || size.height <= 0 || isFinishing || isDestroyed) return
        try {
            val selector =
                if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            val rotation = window.decorView.display?.rotation ?: Surface.ROTATION_0
            val newPreview = Preview.Builder().setTargetRotation(rotation).build()
            val newAnalysis =
                ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 960),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
            val group =
                UseCaseGroup.Builder()
                    .setViewPort(
                        ViewPort.Builder(Rational(size.width, size.height), rotation)
                            .setScaleType(ViewPort.FILL_CENTER)
                            .build()
                    )
                    .addUseCase(newPreview)
                    .addUseCase(newAnalysis)
                    .build()

            releaseUseCases()
            preview = newPreview
            analysis = newAnalysis
            newPreview.setSurfaceProvider(ContextCompat.getMainExecutor(this)) { request ->
                if (preview === newPreview && !isFinishing && !isDestroyed) {
                    surfaceRequest = request
                } else {
                    request.willNotProvideSurface()
                }
            }
            newAnalysis.setAnalyzer(analysisExecutor) { image -> analyze(image, size) }
            camera = provider.bindToLifecycle(this, selector, group)
            camera?.let { boundCamera ->
                if (boundCamera.cameraInfo.isLowLightBoostSupported) {
                    boundCamera.cameraControl.enableLowLightBoostAsync(true)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Camera binding failed", error)
            closeWithMessage(R.string.scan_camera_unavailable)
        }
    }

    private fun analyze(image: ImageProxy, size: Size) {
        try {
            if (resultDelivered.get() || image.cropRect.isEmpty) return
            image.setCropRect(frameCrop(image.cropRect, image.imageInfo.rotationDegrees, size))
            val text = NativeQrReader.read(image)
            if (!text.isNullOrBlank() && resultDelivered.compareAndSet(false, true)) {
                ContextCompat.getMainExecutor(this).execute {
                    if (!isFinishing && !isDestroyed) {
                        analysis?.clearAnalyzer()
                        setResult(RESULT_OK, ScanContract.resultIntent(text))
                        finish()
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "QR frame analysis failed", error)
        } finally {
            image.close()
        }
    }

    private fun frameCrop(crop: Rect, rotation: Int, size: Size): Rect {
        val side =
            scanFrameSize(
                size.width.toFloat(),
                size.height.toFloat(),
                resources.displayMetrics.density,
            )
        val rotated = rotation % 180 != 0
        val widthFraction = side / if (rotated) size.height else size.width
        val heightFraction = side / if (rotated) size.width else size.height
        val width = (crop.width() * widthFraction).toInt().coerceIn(1, crop.width())
        val height = (crop.height() * heightFraction).toInt().coerceIn(1, crop.height())
        val left = crop.left + (crop.width() - width) / 2
        val top = crop.top + (crop.height() - height) / 2
        return Rect(left, top, left + width, top + height)
    }

    private fun focusAt(x: Float, y: Float, size: Size) {
        val point =
            SurfaceOrientedMeteringPointFactory(size.width.toFloat(), size.height.toFloat())
                .createPoint(x, y)
        camera
            ?.cameraControl
            ?.startFocusAndMetering(
                FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                    )
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
            )
    }

    private fun closeWithMessage(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun releaseUseCases() {
        analysis?.clearAnalyzer()
        cameraProvider?.unbind(*listOfNotNull(preview, analysis).toTypedArray())
        preview = null
        analysis = null
        camera = null
        surfaceRequest = null
    }

    override fun finish() {
        resultDelivered.set(true)
        super.finish()
    }

    override fun onDestroy() {
        resultDelivered.set(true)
        releaseUseCases()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ScanCaptureActivity"
    }
}
