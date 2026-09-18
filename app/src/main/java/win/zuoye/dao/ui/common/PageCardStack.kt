package win.zuoye.dao.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 被覆盖页面留在原地；按钮、页面滑动和系统预测性返回共用同一转场。
private val CardTransition = navGraphicsTransition(
    dismissDirection = NavSwipeDirection.LeftToRight,
) { scope ->
    translationX = -scope.relativeDepth.coerceAtMost(0f) * scope.layoutSize.width
}

@Composable
fun PageCardStack(
    backStack: NavBackStack,
    modifier: Modifier = Modifier,
    entries: NavEntryBuilder.() -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        transition = CardTransition,
        effects = NavDisplayEffects(
            cornerClipRadius = 32.dp,
            backdropColor = MiuixTheme.colorScheme.surface,
        ),
        content = entries,
    )
}
