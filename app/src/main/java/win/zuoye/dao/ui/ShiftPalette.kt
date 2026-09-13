package win.zuoye.dao.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 班次颜色预设色板（数据色，独立于主题色）。 */
object ShiftPalette {
    val presets: List<Int> = listOf(
        0xFFE53935.toInt(), // 红
        0xFFFB8C00.toInt(), // 橙
        0xFFFDD835.toInt(), // 黄
        0xFF43A047.toInt(), // 绿
        0xFF00ACC1.toInt(), // 青
        0xFF1E88E5.toInt(), // 蓝
        0xFF3949AB.toInt(), // 靛
        0xFF8E24AA.toInt(), // 紫
        0xFFD81B60.toInt(), // 粉
        0xFF6D4C41.toInt(), // 棕
        0xFF546E7A.toInt(), // 蓝灰
        0xFF7CB342.toInt(), // 草绿
    )

    fun color(argb: Int): Color = Color(argb)

    /** 色块上的文字：按亮度取黑/白，保证对比度 */
    fun onColor(argb: Int): Color {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.62) Color(0xFF1C1B1F) else Color.White
    }
}

/** 在 Compose 里取色块文字色的便捷函数 */
@Composable
fun onShiftColor(argb: Int): Color = ShiftPalette.onColor(argb)
