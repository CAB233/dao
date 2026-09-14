package win.zuoye.dao.ui.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import win.zuoye.dao.R

/**
 * 扫码页：相机预览铺满整屏（含状态栏区域），只把返回键浮在左上角，不占可见高度。
 *
 * 预览视图由 [ScanCaptureActivity] 建好传进来，这里只负责摆位。
 * 取景框外的区域本来就被压暗了一层，白箭头看得清，不用再加底。
 */
@Composable
fun ScanScreen(
    barcodeView: DecoratedBarcodeView,
    onBack: () -> Unit,
) {
    val overlayContentColor = colorResource(R.color.scan_overlay_content)

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { barcodeView },
            modifier = Modifier.fillMaxSize(),
        )

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
