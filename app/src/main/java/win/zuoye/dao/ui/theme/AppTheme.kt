package win.zuoye.dao.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import win.zuoye.dao.data.ThemeMode

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val controller = remember(themeMode) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                ThemeMode.LIGHT -> ColorSchemeMode.Light
                ThemeMode.DARK -> ColorSchemeMode.Dark
                ThemeMode.SYSTEM -> ColorSchemeMode.System
            },
        )
    }
    MiuixTheme(controller = controller, content = content)
}
