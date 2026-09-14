package win.zuoye.dao.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRowWithContour

/**
 * 分段切换：miuix `TabRowWithContour`——一个外框当轨道，选中的胶囊会滑过去（普通 `TabRow` 的胶囊是直接跳的）。
 *
 * **用组件默认配色**（轨道 `surface`、胶囊 `surfaceContainer`），但要注意它所在的底色必须是
 * `MiuixTheme.colorScheme.background`：轨道是 `surface`，落在同样 `surface` 底色的页面上会整个隐形
 * （弹窗底色本来就是 `background`，所以弹窗里默认值正好）。要用在页面上，就把那层 Scaffold 的
 * `containerColor` 设成 `background`，别去改这里的颜色——只改一处才能保证全 App 长得一样。
 */
@Composable
fun SegmentedSwitch(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelect,
        // 默认 45dp 在这套界面里偏扁，抬一点（两个切换框共用这个高度，保持一样）
        height = 52.dp,
        // TabRowWithContour 的外框圆角 = 这个值 + contourPadding(5dp)，给 11dp 正好等于卡片默认的 16dp，
        // 这样把它塞进 Card 里填满时，外框和卡片的圆角完全重合、四角不会露出卡片的底色
        cornerRadius = 11.dp,
        modifier = modifier,
    )
}
