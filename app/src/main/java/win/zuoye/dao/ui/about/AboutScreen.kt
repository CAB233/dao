package win.zuoye.dao.ui.about

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.R

/**
 * 关于页：头部是应用图标 + 名称 + 版本号，下面是"查看源代码 / 获取更新"两个入口
 * （入口样式对齐 InstallerX 的设置项：标题 + 说明 + 右侧箭头）。
 * 两个入口目前是占位，没有实际功能。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val app = remember(context) { context.loadAppInfo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "关于",
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
            // ---- 头部 ----
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                app.icon?.let { icon ->
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        // 图片必须裁剪 → squircleClip
                        modifier = Modifier.size(84.dp).squircleClip(20.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.app_name), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "版本 ${app.versionName}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // ---- 入口 ----
            SmallTitle(text = "关于")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                AboutEntry(
                    title = "查看源代码",
                    summary = "在 GitHub 上查看项目源码",
                    onClick = { context.notImplemented("查看源代码") },
                )
                AboutEntry(
                    title = "获取更新",
                    summary = "检查是否有新版本",
                    onClick = { context.notImplemented("获取更新") },
                )
            }

            // 二级页面：末尾 Spacer 自己吃掉导航栏内边距（签名里不放 bottomPadding）
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

/** 一行设置项：标题 + 说明 + 右侧箭头 */
@Composable
private fun AboutEntry(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(12.dp, 18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        onClick = onClick,
    )
}

/** 占位入口的临时反馈，等功能接上后删掉 */
private fun Context.notImplemented(name: String) {
    Toast.makeText(this, "「$name」暂未实现", Toast.LENGTH_SHORT).show()
}

private class AppInfo(val versionName: String, val icon: ImageBitmap?)

private fun Context.loadAppInfo(): AppInfo {
    val pm = packageManager
    val versionName = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        info.versionName
    }.getOrNull().orEmpty()
    val icon = runCatching {
        pm.getApplicationIcon(packageName).toBitmap(width = 168, height = 168).asImageBitmap()
    }.getOrNull()
    return AppInfo(versionName, icon)
}

/** 应用版本名（设置页也用它显示版本） */
fun Context.appVersionName(): String = loadAppInfo().versionName
