package win.zuoye.dao.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction

/**
 * MIUI 惯例：弹出 Dialog 的入口行在 Dialog 打开期间保持"按住"高亮。
 *
 * miuix 组件（BasicComponent / IconButton …）自带 `holdDownState` 参数，直接用那个即可；
 * 自绘的 `clickable` 行走这里——把状态作为 [HoldDownInteraction] 注入 interactionSource，
 * 再交给 `Modifier.clickable(interactionSource = …, indication = LocalIndication.current, …)`，
 * 主题里的 MiuixIndication 会据此画出按住高亮（并在弹层关闭后释放）。
 */
@Composable
fun rememberHoldDownSource(holdDownState: Boolean): MutableInteractionSource {
    val interactionSource = remember { MutableInteractionSource() }
    val active = remember { mutableStateOf<HoldDownInteraction.HoldDown?>(null) }

    LaunchedEffect(holdDownState, interactionSource) {
        suspend fun release() {
            active.value?.let { current ->
                interactionSource.emit(HoldDownInteraction.Release(current))
                active.value = null
            }
        }
        if (holdDownState) {
            release()
            val interaction = HoldDownInteraction.HoldDown()
            active.value = interaction
            interactionSource.emit(interaction)
        } else {
            release()
        }
    }

    // 行被移出组合（例如弹层关掉后整行消失）时也要释放，别把高亮留在 source 里
    DisposableEffect(interactionSource) {
        onDispose {
            active.value?.let { current ->
                interactionSource.tryEmit(HoldDownInteraction.Release(current))
            }
            active.value = null
        }
    }
    return interactionSource
}
