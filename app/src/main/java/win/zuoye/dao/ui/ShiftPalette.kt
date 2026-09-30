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

    /**
     * 「已过去的日子」的消色比例：原色向白色混这么多。
     *
     * App 内月历和桌面小组件共用这一个值（`DayVisualState.Past` 的消费方）， 改它时小组件的 `widget_day_faded_*.xml` 也要跟着重算。
     */
    const val pastFadeRatio = 0.78f

    val presets: List<Int> =
        listOf(
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

    /**
     * 铺在 [argb] 实心底色之上的文字色：**取白字与深字里对比度更高的那个**。
     *
     * 不能用"亮度超过某阈值就用深字"这种单阈值判断：色板里的草绿 (#7CB342) 亮度 0.369、白字对比度只有 2.5:1，而深字有 6.8:1 ——
     * 单阈值（0.45）会错误地给它白字。直接比对比度不会有这种漏网。 App 内月历与桌面小组件都走这里。
     */
    fun onColor(argb: Int): Color =
        if (contrastWithWhite(argb) >= contrastWithDark(argb)) Color.White else onLightBackground

    /** 同一规则的 Int 版本，RemoteViews 的 setTextColor 需要裸 ARGB */
    fun onColorArgb(argb: Int): Int =
        if (contrastWithWhite(argb) >= contrastWithDark(argb)) android.graphics.Color.WHITE
        else onLightBackgroundArgb

    val onLightBackground: Color = Color(0xFF1C1B1F)
    val onLightBackgroundArgb: Int = 0xFF1C1B1F.toInt()

    private fun contrastWithWhite(argb: Int): Double = contrast(relativeLuminance(argb), 1.0)

    private fun contrastWithDark(argb: Int): Double =
        contrast(relativeLuminance(argb), relativeLuminance(onLightBackgroundArgb))

    /** WCAG 对比度：(亮的 + 0.05) / (暗的 + 0.05) */
    private fun contrast(a: Double, b: Double): Double {
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** WCAG 相对亮度：通道先做 sRGB 反伽马，再按感知权重加权 */
    private fun relativeLuminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }
}
