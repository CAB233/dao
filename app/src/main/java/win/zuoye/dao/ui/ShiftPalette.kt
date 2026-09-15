package win.zuoye.dao.ui

import androidx.compose.ui.graphics.Color

/** 节假日角标数据色（休=放假日绿、班=调休上班橙），与主题色无关 */
object HolidayPalette {
    val restBadge = Color(0xFF34A853)
    val restOnBadge = Color.White
    val makeupBadge = Color(0xFFF9A825)
    val makeupOnBadge = Color(0xFF1C1B1F)
}

/** 班次颜色预设色板（数据色，独立于主题色）。 */
object ShiftPalette {
    val statusCardLightBackground: Color = Color(0xFFDFFAE4)
    val statusCardDarkBackground: Color = Color(0xFF1A3825)

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
}
