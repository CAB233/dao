package win.zuoye.dao.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * 滚动时收起 FAB：往下滚藏起来，往回滚（或回到顶部）再露出来。
 *
 * 传一个单调递增的"滚动位置"就行——LazyColumn 用 `index * 大数 + offset` 合成，
 * 普通 Column 直接给 `ScrollState.value`。判定在协程里做，不参与组合期读取。
 */
@Composable
fun rememberFabVisible(scrollPosition: () -> Int): Boolean {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        var previous = scrollPosition()
        snapshotFlow { scrollPosition() }.collect { current ->
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
