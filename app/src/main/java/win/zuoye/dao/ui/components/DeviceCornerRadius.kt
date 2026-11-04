package win.zuoye.dao.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.utils.getRoundedCorner

/**
 * 弹窗圆角跟随设备屏幕圆角（返回 dp；miuix 在没有圆角信息的平台会给出预设值）。
 *
 * 取不到或算出来是 0 时返回 null，让 miuix 继续用它自己的默认圆角（居中 32dp / 贴底 32–48dp）， 避免在没有圆角信息的设备上把弹窗变成直角。
 */
@Composable internal fun deviceCornerRadius(): Dp? = getRoundedCorner().takeIf { it > 0.dp }
