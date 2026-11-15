package win.zuoye.dao.ui.about

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import win.zuoye.dao.R
import win.zuoye.dao.ui.components.Chevron
import win.zuoye.dao.update.UpdateInfo

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    updateInfo: UpdateInfo?,
    updateCheckInProgress: Boolean,
    updateChecked: Boolean,
    onCheckForUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.loadAppInfo() }
    AboutHomeContent(
        app = app,
        context = context,
        onBack = onBack,
        onOpenLicenses = onOpenLicenses,
        updateInfo = updateInfo,
        updateCheckInProgress = updateCheckInProgress,
        updateChecked = updateChecked,
        onCheckForUpdate = onCheckForUpdate,
    )
}

@Composable
private fun AboutHomeContent(
    app: AppInfo,
    context: Context,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    updateInfo: UpdateInfo?,
    updateCheckInProgress: Boolean,
    updateChecked: Boolean,
    onCheckForUpdate: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var heroHeightPx by remember { mutableIntStateOf(0) }
    val scrollProgress by remember {
        derivedStateOf {
            if (heroHeightPx <= 0) {
                0f
            } else {
                val index = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / heroHeightPx).coerceIn(0f, 1f)
            }
        }
    }
    val collapseProgress by remember {
        derivedStateOf { ((scrollProgress - 0.80f) / 0.20f).coerceIn(0f, 1f) }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about_title),
                // 默认状态不显示标题，收紧到顶才淡入
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = collapseProgress),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets =
            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            AboutHero(
                app = app,
                scrollProgress = { scrollProgress },
                modifier =
                    Modifier.widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding()),
            )
            LazyColumn(
                modifier =
                    Modifier.widthIn(max = 720.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .consumeWindowInsets(padding)
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding = padding,
                overscrollEffect = null,
            ) {
                item(key = "heroSpacer") {
                    Spacer(
                        Modifier.fillMaxWidth().height(AboutHeroHeight).onSizeChanged {
                            heroHeightPx = it.height
                        }
                    )
                }
                item(key = "about_content") {
                    Column(
                        Modifier.fillParentMaxHeight()
                            .padding(bottom = padding.calculateBottomPadding())
                    ) {
                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(top = 12.dp, bottom = 12.dp)
                        ) {
                            AboutEntry(
                                title = stringResource(R.string.about_source),
                                summary = stringResource(R.string.about_source_summary),
                                onClick = { context.openRepository() },
                            )
                            AboutEntry(
                                title = stringResource(R.string.about_licenses),
                                summary = stringResource(R.string.about_licenses_summary),
                                onClick = onOpenLicenses,
                            )
                            AboutEntry(
                                title = stringResource(R.string.about_check_update),
                                summary =
                                    when {
                                        updateCheckInProgress ->
                                            stringResource(R.string.about_check_update_checking)
                                        updateInfo != null ->
                                            stringResource(
                                                R.string.about_check_update_available,
                                                updateInfo.versionName,
                                            )
                                        updateChecked ->
                                            stringResource(R.string.about_check_update_latest)
                                        else -> stringResource(R.string.about_check_update_summary)
                                    },
                                onClick = onCheckForUpdate,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val AboutHeroHeight = 300.dp

@Composable
private fun AboutHero(
    app: AppInfo,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme

    Column(
        modifier = modifier.height(AboutHeroHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier =
                Modifier.size(156.dp).graphicsLayer {
                    val progress = ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.05f
                    scaleY = 1f - progress * 0.05f
                },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier.graphicsLayer {
                    val progress = ((scrollProgress() - 0.20f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.05f
                    scaleY = 1f - progress * 0.05f
                },
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.version_text, app.versionName),
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.fillMaxWidth().graphicsLayer {
                    val progress = ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.05f
                    scaleY = 1f - progress * 0.05f
                },
        )
    }
}

@Composable
private fun AboutEntry(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = { Chevron() },
        onClick = onClick,
    )
}

private const val repositoryUrl = "https://github.com/CAB233/dao"

private fun Context.openUrl(url: String, failureName: String) {
    val intent =
        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            if (this@openUrl !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    runCatching { startActivity(intent) }.onFailure { notImplemented(failureName) }
}

private fun Context.openRepository() = openUrl(repositoryUrl, getString(R.string.open_repository))

private fun Context.notImplemented(name: String) {
    Toast.makeText(this, getString(R.string.not_implemented, name), Toast.LENGTH_SHORT).show()
}

@Composable
internal fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val libraries =
        remember(context) {
            runCatching { Libs.Builder().withJson(context, R.raw.aboutlibraries).build().libraries }
                .getOrDefault(emptyList())
        }
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.about_licenses),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets =
            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .consumeWindowInsets(padding)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = padding,
            overscrollEffect = null,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            items(libraries, key = { it.uniqueId }) { library ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    LicenseCardContent(library)
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
private fun LicenseCardContent(library: Library) {
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = library.name,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            library.artifactVersion
                ?.takeIf { it.isNotBlank() }
                ?.let { version ->
                    Text(
                        text = version,
                        modifier = Modifier.padding(start = 12.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = summaryColor,
                        maxLines = 1,
                    )
                }
        }
        Text(
            text = library.authorSummary(),
            style = MiuixTheme.textStyles.body2,
            color = summaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = library.licenseNames(),
            style = MiuixTheme.textStyles.body2,
            color = summaryColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Library.authorSummary(): String =
    developers
        .mapNotNull { developer -> developer.name }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
        .ifBlank { organization?.name?.takeIf { it.isNotBlank() } ?: uniqueId.substringBefore(':') }

private fun Library.licenseNames(): String =
    licenses
        .map { license -> license.name.ifBlank { license.spdxId.orEmpty() } }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
        .ifBlank { "Unknown license" }

private class AppInfo(
    val versionName: String,
    val versionCode: Long,
)

private fun Context.loadAppInfo(): AppInfo {
    val pm = packageManager
    val packageInfo = runCatching {
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)
            }
        info
    }
        .getOrNull()
    val versionName = packageInfo?.versionName.orEmpty()
    val versionCode =
        packageInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.longVersionCode
            } else {
                @Suppress("DEPRECATION") it.versionCode.toLong()
            }
        } ?: 0L
    return AppInfo(versionName, versionCode)
}

fun Context.appVersionName(): String = loadAppInfo().versionName
