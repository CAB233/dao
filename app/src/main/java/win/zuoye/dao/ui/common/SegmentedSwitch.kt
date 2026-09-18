package win.zuoye.dao.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            selectedBackgroundColor = MiuixTheme.colorScheme.primary,
            selectedContentColor = MiuixTheme.colorScheme.onPrimary,
        ),
        // 默认 45dp 在这套界面里偏扁，抬一点（两个切换框共用这个高度，保持一样）
        height = 52.dp,
        // TabRowWithContour 的外框圆角 = 这个值 + contourPadding(5dp)，给 11dp 正好等于卡片默认的 16dp，
        // 这样把它塞进 Card 里填满时，外框和卡片的圆角完全重合、四角不会露出卡片的底色
        cornerRadius = 11.dp,
        modifier = modifier,
    )
}
