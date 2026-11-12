package win.zuoye.dao.ui.about

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
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
    val blurSupported = isRuntimeShaderSupported()
    val backdrop = if (blurSupported) rememberLayerBackdrop() else null
    Box(Modifier.fillMaxSize()) {
        AboutBackdrop(
            modifier =
                Modifier.fillMaxSize()
                    .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
        )
        AboutHomeContent(
            app = app,
            context = context,
            onBack = onBack,
            onOpenLicenses = onOpenLicenses,
            updateInfo = updateInfo,
            updateCheckInProgress = updateCheckInProgress,
            updateChecked = updateChecked,
            onCheckForUpdate = onCheckForUpdate,
            backdrop = backdrop,
        )
    }
}

@Composable
private fun Modifier.glass(backdrop: LayerBackdrop?, cornerRadius: Dp = 16.dp): Modifier {
    if (backdrop == null) return this
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val tint = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.32f else 0.42f)
    val edge = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(cornerRadius)
    return textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = 70f,
            noiseCoefficient = 0.09f,
            colors =
                BlurColors(
                    blendColors = listOf(BlendColorEntry(tint, BlurBlendMode.SrcOver)),
                    saturation = 1.15f,
                ),
        )
        .border(width = 1.dp, color = edge, shape = shape)
}

@Composable
private fun AboutBackdrop(modifier: Modifier = Modifier) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val base = if (isDark) Color(0xFF0E0F12) else Color(0xFFF7F7F7)
    val paletteA =
        if (isDark) {
            listOf(Color(0xFF1E4A32), Color(0xFF1E3B5C), Color(0xFF392B54), Color(0xFF4A3220))
        } else {
            listOf(Color(0xFFB8F0C9), Color(0xFFC7DEFF), Color(0xFFF3D7FF), Color(0xFFFFE3C7))
        }
    val paletteB =
        if (isDark) {
            listOf(Color(0xFF123B2A), Color(0xFF27355E), Color(0xFF4A2A3A), Color(0xFF2F4420))
        } else {
            listOf(Color(0xFFCFF5D8), Color(0xFFD8E6FF), Color(0xFFFADFF6), Color(0xFFFFEFD8))
        }
    val transition = rememberInfiniteTransition(label = "about-backdrop")
    val spin by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(tween(durationMillis = 12_000, easing = LinearEasing)),
            label = "about-backdrop-spin",
        )
    // 颜色：两套色板 9s 来回
    val blend by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(durationMillis = 9_000, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
            label = "about-backdrop-blend",
        )
    val blobs = paletteA.indices.map { lerp(paletteA[it], paletteB[it], blend) }
    val tau = (2 * PI).toFloat()
    Box(
        modifier =
            modifier.drawBehind {
                drawRect(base)
                val w = size.width
                val h = size.height
                val anchors =
                    listOf(
                        0.18f to 0.15f,
                        0.82f to 0.22f,
                        0.30f to 0.70f,
                        0.78f to 0.78f,
                    )
                anchors.forEachIndexed { index, (ax, ay) ->
                    val phase = index * 1.7f
                    val cx = w * (ax + 0.18f * sin(tau * spin + phase))
                    val cy = h * (ay + 0.12f * cos(tau * spin + phase))
                    val radius = w * (0.58f + 0.06f * sin(tau * spin * 2f + phase))
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(blobs[index].copy(alpha = 0.92f), Color.Transparent),
                                center = Offset(cx, cy),
                                radius = radius,
                            ),
                        radius = radius,
                        center = Offset(cx, cy),
                    )
                }
            }
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
    backdrop: LayerBackdrop?,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier =
                    backdrop?.let {
                        Modifier.textureBlur(
                            backdrop = it,
                            shape = RectangleShape,
                            blurRadius = 40f,
                            noiseCoefficient = 0.05f,
                        )
                    } ?: Modifier,
                title = "",
                color = Color.Transparent,
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
            ) {
                item {
                    AboutHero(
                        app = app,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }

                item { SmallTitle(text = stringResource(R.string.about_title)) }

                item {
                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .glass(backdrop),
                        colors =
                            CardDefaults.defaultColors(
                                color =
                                    if (backdrop != null) Color.Transparent
                                    else MiuixTheme.colorScheme.surfaceContainer
                            ),
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
                item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun AboutHero(
    app: AppInfo,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val titleBlend =
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }

    Column(
        modifier = modifier.height(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
            modifier =
                Modifier.then(
                    backdrop?.let {
                        Modifier.textureBlur(
                            backdrop = it,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            colors = BlurColors(blendColors = titleBlend),
                            contentBlendMode = BlendMode.DstIn,
                        )
                    } ?: Modifier
                ),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.version_text, app.versionName),
            fontSize = 14.sp,
            color = colors.onSurfaceVariantSummary,
        )
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

/** 链接无法打开时的临时反馈 */
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
                fontSize = 17.sp,
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
                        fontSize = 14.sp,
                        color = summaryColor,
                        maxLines = 1,
                    )
                }
        }
        Text(
            text = library.authorSummary(),
            fontSize = 14.sp,
            color = summaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = library.licenseNames(),
            fontSize = 14.sp,
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

/** 应用版本名（设置页也用它显示版本） */
fun Context.appVersionName(): String = loadAppInfo().versionName
