package win.zuoye.dao.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShareCodec
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.toShare
import win.zuoye.dao.share.ShareUtils
import win.zuoye.dao.share.encodeQrCode

/**
 * 分享配置（二级页面）：选一个方案 → 亮出二维码给对方扫，
 * 另外保留复制到剪贴板 / 导出配置文件 / 系统分享。
 */
@Composable
fun SharePlanScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)

    var selectedSchemeId by remember(doc) {
        mutableStateOf(doc.activeSchemeId ?: doc.schemes.firstOrNull()?.id)
    }
    val selectedScheme = doc.schemes.firstOrNull { it.id == selectedSchemeId }
        ?: doc.schemes.firstOrNull()

    val payload = remember(doc, selectedScheme?.id) { doc.toShare(selectedScheme?.id) }
    val payloadText = remember(payload) { PlanShareCodec.encodePayload(payload) }
    val shareText = remember(doc, selectedScheme?.id) {
        PlanShareCodec.shareText(doc, appName, selectedScheme?.id)
    }
    val qrCode = remember(payloadText) { encodeQrCode(payloadText) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(PlanShareCodec.encode(payload).toByteArray())
            }
        }.isSuccess
        Toast.makeText(
            context,
            if (ok) "已导出配置文件" else "导出失败",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "分享配置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            if (doc.schemes.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "还没有排班方案，先去设置里新建一个吧。",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
            SmallTitle(text = "选择要分享的方案")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                doc.schemes.sortedByDescending { it.createdAt }.forEach { scheme ->
                    val selected = scheme.id == selectedScheme?.id
                    val anchor = Ymd.fromEpochDay(scheme.anchorEpochDay)
                    BasicComponent(
                        title = scheme.name,
                        summary = "${scheme.cycleDays} 天周期 · ${anchor.year}-${anchor.month}-${anchor.day} 起",
                        endActions = {
                            if (selected) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(20.dp),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = { selectedSchemeId = scheme.id },
                    )
                }
            }

            SmallTitle(text = "扫码分享")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (qrCode != null) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.72f)
                                .aspectRatio(1f)
                                // 二维码需要稳定的浅色底：填 + 裁剪都用 squircle
                                .squircleSurface(color = MiuixTheme.colorScheme.surface, cornerRadius = 12.dp)
                                .padding(8.dp),
                        ) {
                            Image(
                                bitmap = qrCode,
                                contentDescription = "排班方案二维码",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Text(
                            "方案太大，二维码装不下，改用下面的导出或分享吧。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "扫码即可导入。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            SmallTitle(text = "其它方式")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = "复制到剪贴板",
                    summary = "直接粘到聊天软件发给对方",
                    onClick = {
                        context.copyToClipboard(shareText)
                        Toast.makeText(context, "已复制分享文本", Toast.LENGTH_SHORT).show()
                    },
                )
                BasicComponent(
                    title = "导出配置文件",
                    summary = "存成 JSON 文件，对方可用「导入方案」粘贴或打开",
                    onClick = {
                        val name = selectedScheme?.name?.takeIf { it.isNotBlank() } ?: "排班方案"
                        exportLauncher.launch("$name.json")
                    },
                )
                BasicComponent(
                    title = "系统分享",
                    summary = "调起系统分享面板发送文本",
                    onClick = {
                        ShareUtils.shareText(context, "$appName · 排班方案", shareText)
                    },
                )
            }
            }
            // 二级页面：末尾 Spacer 自己吃掉导航栏内边距（签名里不放 bottomPadding）
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("排班方案", text))
}
