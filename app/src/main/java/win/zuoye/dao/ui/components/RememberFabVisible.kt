package win.zuoye.dao.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/** 向下滚动时收起 FAB，向上滚动或回到顶部时显示；各调用处独立维护显隐状态。 */
@Composable
internal fun rememberFabVisible(scrollPosition: () -> Int): Boolean {
    var visible by remember { mutableStateOf(true) }
    val currentScrollPosition by rememberUpdatedState(scrollPosition)

    LaunchedEffect(Unit) {
        var previous = currentScrollPosition()
        snapshotFlow { currentScrollPosition() }
            .collect { current ->
                when {
                    current == 0 -> visible = true
                    current > previous -> visible = false
                    current < previous -> visible = true
                }
                previous = current
            }
    }
    return visible
}
