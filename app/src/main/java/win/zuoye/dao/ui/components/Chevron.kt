package win.zuoye.dao.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 表单行右侧的「>」箭头，尺寸与配色统一在这里。 */
@Composable
internal fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        modifier = modifier.size(12.dp, 18.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
