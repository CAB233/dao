package win.zuoye.dao.ui.about

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import win.zuoye.dao.R
import win.zuoye.dao.ui.common.PageCardStack

/**
 * 关于页：大号应用标识与版本信息置于页面头部，下方是关于入口卡片。
 * 视觉层级参考 InstallerX-Revived 的 MiuixAboutPage，并使用本项目的主题色实现。
 * “获取更新”入口目前是占位，查看源代码与开源许可均可直接使用。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.loadAppInfo() }
    var showLicenses by remember { mutableStateOf(false) }

    BackHandler(enabled = !showLicenses) { onBack() }
    PageCardStack(
        visible = showLicenses,
        base = {
            AboutHomeContent(
                app = app,
                context = context,
                onBack = onBack,
                onOpenLicenses = { showLicenses = true },
            )
        },
        card = {
            OpenSourceLicensesScreen(onBack = { showLicenses = false })
        },
    )
}

@Composable
private fun AboutHomeContent(
    app: AppInfo,
    context: Context,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
        ) {
            item {
                AboutHero(
                    app = app,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }

            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    AboutEntry(
                        title = "查看源代码",
                        summary = "在 GitHub 上查看项目源码",
                        onClick = { context.openRepository() },
                    )
                    AboutEntry(
                        title = "开放源代码许可",
                        summary = "查看项目使用的开源组件及许可证",
                        onClick = onOpenLicenses,
                    )
                    AboutEntry(
                        title = "获取更新",
                        summary = "检查是否有新版本",
                        onClick = { context.openReleases() },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
private fun AboutHero(
    app: AppInfo,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme

    Column(
        modifier = modifier
            .height(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(156.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "v${app.versionName}",
            fontSize = 14.sp,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.weight(1f))
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

private const val repositoryUrl = "https://github.com/CAB233/dao"
private const val releasesUrl = "https://github.com/CAB233/dao/releases"

private fun Context.openUrl(url: String, failureName: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        if (this@openUrl !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching { startActivity(intent) }
        .onFailure { notImplemented(failureName) }
}

private fun Context.openRepository() = openUrl(repositoryUrl, "打开项目仓库")

private fun Context.openReleases() = openUrl(releasesUrl, "打开更新页面")

/** 链接无法打开时的临时反馈 */
private fun Context.notImplemented(name: String) {
    Toast.makeText(this, "「$name」暂未实现", Toast.LENGTH_SHORT).show()
}

private data class OpenSourceProject(
    val name: String,
    val license: String,
)

private val openSourceProjects = listOf(
    OpenSourceProject("miuix", "Apache License 2.0"),
    OpenSourceProject("AndroidX 与 Jetpack Compose", "Apache License 2.0"),
    OpenSourceProject("Kotlin 与 kotlinx.serialization", "Apache License 2.0"),
    OpenSourceProject("kotlinx.collections.immutable", "Apache License 2.0"),
    OpenSourceProject("ZXing 与 zxing-android-embedded", "Apache License 2.0"),
)

@Composable
private fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "开放源代码许可",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            items(openSourceProjects, key = { it.name }) { project ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    BasicComponent(
                        title = project.name,
                        summary = "许可证：${project.license}",
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }
}

private class AppInfo(
    val versionName: String,
    val versionCode: Long,
)

private fun Context.loadAppInfo(): AppInfo {
    val pm = packageManager
    val packageInfo = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        info
    }.getOrNull()
    val versionName = packageInfo?.versionName.orEmpty()
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            it.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            it.versionCode.toLong()
        }
    } ?: 0L
    return AppInfo(versionName, versionCode)
}

/** 应用版本名（设置页也用它显示版本） */
fun Context.appVersionName(): String = loadAppInfo().versionName
