package win.zuoye.dao.ui.common

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings

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
            label = "主页",
        )
        NavigationBarItem(
            selected = selected == MainTab.Settings,
            onClick = { onSelect(MainTab.Settings) },
            icon = MiuixIcons.Regular.Settings,
            label = "设置",
        )
    }
}
