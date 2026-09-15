package win.zuoye.dao.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.pluralStringResource
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
import win.zuoye.dao.data.toShare
import win.zuoye.dao.share.ShareUtils
import win.zuoye.dao.share.encodeQrCode

/**
 * 分享配置（二级页面）：选一个方案 → 亮出二维码给对方扫，
 * 另外保留复制到剪贴板和系统文本分享。
 */
@Composable
fun SharePlanScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val shareHeader = stringResource(R.string.share_text_header, appName)
    val copiedMessage = stringResource(R.string.copied_plan)
    val shareSubject = stringResource(R.string.share_subject, appName)

    var selectedSchemeId by remember(doc) {
        mutableStateOf(doc.activeSchemeId ?: doc.schemes.firstOrNull()?.id)
    }
    val selectedScheme = doc.schemes.firstOrNull { it.id == selectedSchemeId }
        ?: doc.schemes.firstOrNull()

    val payload = remember(doc, selectedScheme?.id) { doc.toShare(selectedScheme?.id) }
    val payloadText = remember(payload) { PlanShareCodec.encodePayload(payload) }
    val shareText = remember(doc, selectedScheme?.id, shareHeader) {
        PlanShareCodec.shareText(doc, shareHeader, selectedScheme?.id)
    }
    val qrCode = remember(payloadText) { encodeQrCode(payloadText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.share_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, contentDescription = stringResource(R.string.action_back))
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
                    stringResource(R.string.share_empty),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
            SmallTitle(text = stringResource(R.string.share_select_plan))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                doc.schemes.sortedByDescending { it.createdAt }.forEach { scheme ->
                    val selected = scheme.id == selectedScheme?.id
                    BasicComponent(
                        title = scheme.name,
                        summary = pluralStringResource(R.plurals.cycle_summary, scheme.cycleDays, scheme.cycleDays),
                        endActions = {
                            if (selected) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = stringResource(R.string.selected_description),
                                    modifier = Modifier.size(20.dp),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = { selectedSchemeId = scheme.id },
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.share_scan))
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
                                contentDescription = stringResource(R.string.qr_description),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.qr_too_large),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            SmallTitle(text = stringResource(R.string.share_other_methods))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = stringResource(R.string.copy_clipboard),
                    summary = stringResource(R.string.copy_clipboard_summary),
                    onClick = {
                        context.copyToClipboard(shareText)
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.system_share),
                    summary = stringResource(R.string.system_share_summary),
                    onClick = {
                        ShareUtils.shareText(
                            context,
                            shareSubject,
                            shareText,
                        )
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
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.share_clip_label), text))
}
