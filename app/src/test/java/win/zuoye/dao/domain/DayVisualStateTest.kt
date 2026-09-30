package win.zuoye.dao.domain

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.ui.ShiftPalette

/** 一天的视觉状态：App 内月历和桌面小组件共用这一份判定，所以规则的优先级必须钉死。 */
class DayVisualStateTest {

    private val morning =
        ShiftTemplate(id = 1L, name = "早班", startMinute = 480, endMinute = 900, colorArgb = 0)

    @Test
    fun `outside month wins over everything else`() {
        // 补位日就算"是今天"也不描边：它不属于当前这一页
        val state =
            DayVisualState.of(
                inCurrentMonth = false,
                isToday = true,
                isPast = false,
                template = morning,
            )

        assertIs<DayVisualState.OutsideMonth>(state)
        assertEquals(DayVisualState.OUTSIDE_MONTH_FADE, DayVisualState.fadeFor(state))
    }

    @Test
    fun `today wins over past and future`() {
        val state =
            DayVisualState.of(
                inCurrentMonth = true,
                isToday = true,
                isPast = false,
                template = morning,
            )

        assertIs<DayVisualState.Today>(state)
        // 今天不额外淡化，靠描边着重
        assertEquals(1f, DayVisualState.fadeFor(state))
    }

    @Test
    fun `no template means empty even if the day already passed`() {
        val state =
            DayVisualState.of(
                inCurrentMonth = true,
                isToday = false,
                isPast = true,
                template = null,
            )

        assertIs<DayVisualState.Empty>(state)
    }

    @Test
    fun `past day with a shift carries the template`() {
        val state =
            DayVisualState.of(
                inCurrentMonth = true,
                isToday = false,
                isPast = true,
                template = morning,
            )

        assertIs<DayVisualState.Past>(state)
        assertEquals(morning, state.template)
    }

    @Test
    fun `future day with a shift carries the template`() {
        val state =
            DayVisualState.of(
                inCurrentMonth = true,
                isToday = false,
                isPast = false,
                template = morning,
            )

        assertIs<DayVisualState.Upcoming>(state)
        assertEquals(morning, state.template)
    }

    @Test
    fun `cell overload derives today and past from the epoch day`() {
        val today = Ymd.fromEpochDay(20726) // 2026-09-30
        val yesterday = MonthCell(epochDay = 20725, inCurrentMonth = true)
        val tomorrow = MonthCell(epochDay = 20727, inCurrentMonth = true)
        val todayCell = MonthCell(epochDay = 20726, inCurrentMonth = true)

        assertIs<DayVisualState.Past>(DayVisualState.of(yesterday, today, morning))
        assertIs<DayVisualState.Upcoming>(DayVisualState.of(tomorrow, today, morning))
        assertIs<DayVisualState.Today>(DayVisualState.of(todayCell, today, morning))
    }

    @Test
    fun `only outside month days are faded as a whole`() {
        // 过去的日子靠"消色底"表达，不再额外压整体透明度，否则和补位日分不清
        val past = DayVisualState.of(true, isToday = false, isPast = true, template = morning)
        val upcoming = DayVisualState.of(true, isToday = false, isPast = false, template = morning)

        assertEquals(1f, DayVisualState.fadeFor(past))
        assertEquals(1f, DayVisualState.fadeFor(upcoming))
        assertTrue(
            DayVisualState.fadeFor(DayVisualState.OutsideMonth) < 1f,
            "outside-month cells must be visually recessive",
        )
    }
}

/** 实心底色上的文字对比色：色板里亮色不少，不能一律白字。 */
class ShiftPaletteContrastTest {

    private val white = android.graphics.Color.WHITE
    private val dark = ShiftPalette.onLightBackgroundArgb

    /** WCAG 相对亮度，用来独立验证选出来的颜色确实更好读 */
    private fun luminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `light presets get dark text`() {
        // 黄 #FDD835 白字仅 1.4:1；草绿 #7CB342 仅 2.5:1；蓝 #1E88E5 是 3.68 vs 深字 4.63。
        // 单阈值判断（比如"亮度 > 0.45 用深字"）会漏掉后两个。
        listOf(
                ShiftPalette.presets[2], // 黄
                ShiftPalette.presets[5], // 蓝
                ShiftPalette.presets[11], // 草绿
            )
            .forEach { argb ->
                assertEquals(
                    dark,
                    ShiftPalette.onColorArgb(argb),
                    "expected dark text on #${Integer.toHexString(argb)}",
                )
            }
    }

    @Test
    fun `dark presets get white text`() {
        listOf(
                ShiftPalette.presets[0], // 红（白 4.23 / 深 4.03，白略胜）
                ShiftPalette.presets[6], // 靛 7.7:1
                ShiftPalette.presets[7], // 紫 7.0:1
                ShiftPalette.presets[9], // 棕 7.6:1
            )
            .forEach { argb ->
                assertEquals(
                    white,
                    ShiftPalette.onColorArgb(argb),
                    "expected white text on #${Integer.toHexString(argb)}",
                )
            }
    }

    @Test
    fun `chosen colour is never worse than the alternative`() {
        // 核心保证：选出来的一定是两者中对比度更高的那个（不依赖任何魔法阈值）
        ShiftPalette.presets.forEach { argb ->
            val chosen = ShiftPalette.onColorArgb(argb)
            val other = if (chosen == white) dark else white
            assertTrue(
                contrast(argb, chosen) >= contrast(argb, other),
                "picked the lower-contrast text colour for #${Integer.toHexString(argb)}",
            )
        }
    }

    @Test
    fun `no preset ends up with unreadable text`() {
        // 4.5:1 是 WCAG AA 正文门槛。红 #E53935 恰好卡在门槛下方（白 4.23 / 深 4.03），
        // 属于色板本身的取舍，所以这里只要求达到大字号门槛 3:1。
        ShiftPalette.presets.forEach { argb ->
            val ratio = contrast(argb, ShiftPalette.onColorArgb(argb))
            assertTrue(
                ratio >= 3.0,
                "#${Integer.toHexString(argb)} only reaches ${"%.2f".format(ratio)}:1",
            )
        }
    }
}
