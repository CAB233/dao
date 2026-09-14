package win.zuoye.dao.ui.scan

import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import win.zuoye.dao.R
import win.zuoye.dao.ui.theme.AppTheme

/**
 * 扫码导入用的相机页。
 *
 * 相机权限、解码、返回结果仍然全部交给 zxing 的 [CaptureManager]
 * （和库自带的 CaptureActivity 做的是同一件事，只是那个页面的布局与顶栏没法定制）。
 * 视图和 Manager 依旧在 [onCreate] 里就建好，生命周期调用顺序与库自带页一致，
 * 页面本身则交给 Compose 画 —— 这样顶栏能直接用 miuix 的 `TopAppBar` 与 `MiuixIcons.Back`，
 * 和 App 里其它页面保持一致。
 *
 * 屏幕方向由启动方通过 `ScanOptions.setOrientationLocked(true)` 钉住，
 * 所以这里不声明 `android:screenOrientation`：锁的是"进入时的那一刻"，
 * 不会像库自带的 `sensorLandscape` 那样把页面强行掰成横屏。
 */
class ScanCaptureActivity : ComponentActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var capture: CaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        barcodeView = layoutInflater.inflate(
            R.layout.scan_camera_view,
            FrameLayout(this),
            false,
        ) as DecoratedBarcodeView
        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()
        setContent {
            AppTheme {
                ScanScreen(barcodeView = barcodeView, onBack = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        capture.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        capture.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    // CaptureManager 的相机权限走的是这套旧回调（库自带页也是这么转发的），没有用 Activity Result API
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
}
