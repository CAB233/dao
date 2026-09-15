package win.zuoye.dao.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.PlanShareCodec
import win.zuoye.dao.ui.about.appVersionName
import win.zuoye.dao.ui.common.WEEKDAY_LABELS
import win.zuoye.dao.ui.scan.ScanCaptureActivity

/** 设置：倒班方案（二级页面入口）、导入方案、关于。 */
@Composable
fun SettingsScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenAbout: () -> Unit,
    onImportPlan: (PlanShare) -> Unit,
    onWeekStartDayChange: (Int) -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var showImport by remember { mutableStateOf(false) }
    val versionName = remember(context) { context.appVersionName() }
    val weekStartDay = doc.weekStartDay.coerceIn(0, WEEKDAY_LABELS.lastIndex)

    val activeScheme = doc.activeScheme() ?: doc.schemes.firstOrNull()

    /** 三个入口都直接尝试导入：解析不出来就提示，识别到就合并（重复内容会自动跳过） */
    fun importFrom(text: String?) {
        val payload = text?.let { PlanShareCodec.decode(it) }
        if (payload == null) {
            Toast.makeText(context, "没识别到方案数据", Toast.LENGTH_SHORT).show()
        } else {
            onImportPlan(payload)
            showImport = false
        }
    }

    // 扫码导入：zxing 自带的相机扫码页，扫到直接导入
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        // 返回键 / 取消时 contents 为 null，别提示"没识别到方案数据"
        result.contents?.let { importFrom(it) }
    }

    // 从文件导入：选一个分享出去的 .json
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFrom(context.readText(uri))
    }

    Scaffold(
        topBar = { TopAppBar(title = "设置") },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            // 设置页行数少，按规范仍可用 Column + verticalScroll（不拆行、不改 LazyColumn）
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = "一周开始日",
                    summary = "选择每周的起始星期",
                    items = WEEKDAY_LABELS.map { "周$it" },
                    selectedIndex = weekStartDay,
                    onSelectedIndexChange = onWeekStartDayChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = "倒班方案",
                    summary = activeScheme?.let {
                        "${it.cycleDays} 天周期 · ${doc.templates.size} 个班次"
                    } ?: "还没有方案，点进去建一个",
                    endActions = { Chevron() },
                    onClick = onOpenPlan,
                )
                BasicComponent(
                    title = "导入倒班方案",
                    summary = "从剪贴板、二维码或文件导入",
                    endActions = { Chevron() },
                    // 弹 Dialog 的入口行：打开期间保持按住高亮
                    holdDownState = showImport,
                    onClick = {
                        showImport = true
                    },
                )
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = "关于",
                    summary = "版本 $versionName",
                    endActions = { Chevron() },
                    onClick = onOpenAbout,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // 对话框必须挂在 Scaffold 内部（依赖 Scaffold 提供的弹层宿主）。
        // 常驻组合、用 show 驱动：条件组合的话关闭时弹层会被直接拿走，退出动画来不及播。
        OverlayDialog(
            show = showImport,
            title = "导入排班方案",
            onDismissRequest = { showImport = false },
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "从剪贴板导入",
                    onClick = { importFrom(context.clipboardText()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "从文件导入",
                    onClick = { fileLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "扫码导入",
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setBeepEnabled(false)
                                // 钉住进入扫码页那一刻的屏幕方向（库自带页会强行横屏）
                                setOrientationLocked(true)
                                // 用我们自己的相机页：正方形取景框，其余流程不变
                                setCaptureActivity(ScanCaptureActivity::class.java)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { showImport = false },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        modifier = Modifier.size(12.dp, 18.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 取剪贴板文本，用于「从剪贴板粘贴」（Android 10+ 只允许前台应用读取） */
private fun Context.clipboardText(): String =
    (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        .orEmpty()

/** 读取用户选中的文件内容（导出的 .json） */
private fun Context.readText(uri: Uri): String? = runCatching {
    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
}.getOrNull()
