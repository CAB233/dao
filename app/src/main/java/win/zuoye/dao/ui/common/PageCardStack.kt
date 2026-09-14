package win.zuoye.dao.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 覆盖式卡片过渡，对齐 miuix 导航的默认过渡（`NavTransitions.MiuixDefault`）：
 *
 * - 底层页面（含底栏）**留在原地**，只做轻微压暗——不会整页滑走，也不会淡出；
 * - 卡片从右侧整页滑入，滑到位时圆角收成 0、贴合屏幕；
 * - 卡片完全移出后才停止组合它，避免它的 BackHandler 等在收起后仍然生效。
 *
 * 二级页面与方案编辑页都用它，所以放在 common 里；区别只在调用方自己记住
 * "退出动画期间还要继续渲染的那份内容"。
 */
@Composable
fun PageCardStack(
    visible: Boolean,
    modifier: Modifier = Modifier,
    base: @Composable () -> Unit,
    card: @Composable () -> Unit,
) {
    // 1f = 完全露出底层，0f = 卡片完全贴合屏幕
    val progress = remember { Animatable(if (visible) 0f else 1f) }
    var cardComposed by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            cardComposed = true
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = EaseInOut),
            )
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = EaseInOut),
            )
            cardComposed = false
        }
    }

    val surface = MiuixTheme.colorScheme.surface
    val maxCorner = with(LocalDensity.current) { 32.dp.toPx() }

    Box(modifier.fillMaxSize().background(surface)) {
        // 底层页面（含底栏）完全不动，只被上面的遮罩压暗
        Box(Modifier.fillMaxSize()) { base() }

        if (cardComposed) {
            // 底层压暗；过渡中顺便吃掉落在底层的点击
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.5f * (1f - progress.value) }
                    .background(MiuixTheme.colorScheme.windowDimming)
                    .pointerInput(Unit) { detectTapGestures { } },
            )

            // 卡片本体：从右侧滑入，前段保持圆角，贴合时圆角归零
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        translationX = (p * size.width).roundToInt().toFloat()
                        // 圆角逐帧跟着进度收放（贴合时归零），只能在 draw 阶段读进度，
                        // 所以这里用 graphicsLayer 的 shape 而不是 Dp 驱动的 squircle 修饰符
                        val corner = maxCorner * (p / 0.15f).coerceIn(0f, 1f)
                        shape = RoundedCornerShape(corner, 0f, 0f, corner)
                        clip = true
                    }
                    .background(surface),
            ) { card() }
        }
    }
}
