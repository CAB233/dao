package win.zuoye.dao.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import win.zuoye.dao.R

/** 底栏的两个主入口。 */
enum class MainTab {
    Home,
    Settings,
}

/**
 * 主界面底栏：主页 / 设置。
 * 必须放在各页面 [top.yukonga.miuix.kmp.basic.Scaffold] 的 bottomBar 插槽里。
 */
@Composable
fun MainBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == MainTab.Home,
            onClick = { onSelect(MainTab.Home) },
            icon = MiuixIcons.Regular.Home,
            label = stringResource(R.string.nav_home),
        )
        NavigationBarItem(
            selected = selected == MainTab.Settings,
            onClick = { onSelect(MainTab.Settings) },
            icon = MiuixIcons.Regular.Settings,
            label = stringResource(R.string.nav_settings),
        )
    }
}

/**
 * The wide-window equivalent of [MainBottomBar].  This deliberately uses miuix's own rail so
 * adaptive placement does not bring Material 3 navigation colors into the app.
 */
@Composable
fun MainNavigationRail(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    NavigationRail {
        NavigationRailItem(
            selected = selected == MainTab.Home,
            onClick = { onSelect(MainTab.Home) },
            icon = MiuixIcons.Regular.Home,
            label = stringResource(R.string.nav_home),
        )
        NavigationRailItem(
            selected = selected == MainTab.Settings,
            onClick = { onSelect(MainTab.Settings) },
            icon = MiuixIcons.Regular.Settings,
            label = stringResource(R.string.nav_settings),
        )
    }
}
