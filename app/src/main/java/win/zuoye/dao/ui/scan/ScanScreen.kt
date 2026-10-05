package win.zuoye.dao.ui.scan

import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import win.zuoye.dao.R

/** 全屏相机预览、中心取景框和返回按钮；点击预览位置调整对焦与测光。 */
@Composable
fun ScanScreen(
    surfaceRequest: SurfaceRequest?,
    onPreviewSizeChanged: (Size) -> Unit,
    onFocus: (Float, Float, Size) -> Unit,
    onBack: () -> Unit,
) {
    val overlayContentColor = colorResource(R.color.scan_overlay_content)
    val coordinateTransformer = remember { MutableCoordinateTransformer() }

    Box(Modifier.fillMaxSize().onSizeChanged { onPreviewSizeChanged(Size(it.width, it.height)) }) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = coordinateTransformer,
                modifier =
                    Modifier.fillMaxSize().pointerInput(request) {
                        detectTapGestures { position ->
                            val point = with(coordinateTransformer) { position.transform() }
                            onFocus(point.x, point.y, request.resolution)
                        }
                    },
            )
        }
        ScanFrame(Modifier.fillMaxSize())
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = stringResource(R.string.scan_back),
                tint = overlayContentColor,
            )
        }
    }
}
