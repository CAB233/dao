package win.zuoye.dao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        content()
    }
}

/** 便捷取色 */
object AppColors {
    val surface @Composable get() = MiuixTheme.colorScheme.surface
    val onSurface @Composable get() = MiuixTheme.colorScheme.onSurface
    val onSurfaceVariant @Composable get() = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val primary @Composable get() = MiuixTheme.colorScheme.primary
}
