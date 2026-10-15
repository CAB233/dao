package win.zuoye.dao.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingActionButton

/** 右下角圆形按钮：关阴影（默认阴影会建离屏图层，动画时明显卡顿）+ 54dp， 显隐统一用淡入缩放；外边距放在 AnimatedVisibility 之外，保证缩放动画以圆心为中心。 */
@Composable
internal fun CornerFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.padding(end = 8.dp, bottom = 12.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            FloatingActionButton(
                onClick = onClick,
                shadowElevation = 0.dp,
                minWidth = 54.dp,
                minHeight = 54.dp,
            ) {
                content()
            }
        }
    }
}
