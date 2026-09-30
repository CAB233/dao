package win.zuoye.dao.domain

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import win.zuoye.dao.data.ShiftTemplate

/**
 * 跨零点班次的结束时刻必须标「次日」。
 *
 * 踩过的线上问题：22:30–07:30 的夜班，底部状态显示成「上班中 · 到 00:00」—— 既看不出是次日，又容易被读成午夜收工。
 */
class ShiftEndFormatTest {

    private val marker = "次日"

    @Test
    fun `end just after midnight is marked as next day`() {
        val night =
            ShiftTemplate(
                id = 1L,
                name = "夜班",
                startMinute = 21 * 60,
                endMinute = 0,
                colorArgb = 0,
            )

        assertEquals("次日00:00", formatShiftEnd(night, marker))
    }

    @Test
    fun `end in the next morning is marked as next day`() {
        val night =
            ShiftTemplate(
                id = 1L,
                name = "夜班",
                startMinute = 22 * 60 + 30,
                endMinute = 7 * 60 + 30,
                colorArgb = 0,
            )

        assertEquals("次日07:30", formatShiftEnd(night, marker))
    }

    @Test
    fun `same day end is not marked`() {
        val day =
            ShiftTemplate(
                id = 1L,
                name = "早班",
                startMinute = 7 * 60 + 30,
                endMinute = 15 * 60,
                colorArgb = 0,
            )

        assertEquals("15:00", formatShiftEnd(day, marker))
    }

    @Test
    fun `rest template is not marked`() {
        val rest =
            ShiftTemplate(
                id = 1L,
                name = "休息",
                startMinute = 0,
                endMinute = 0,
                colorArgb = 0,
                isRest = true,
            )

        // isRest 时 crossesMidnight 恒为 false，不会误标次日
        assertEquals("00:00", formatShiftEnd(rest, marker))
    }
}

/** 一周的开始日：0 = 周一 … 6 = 周日；默认值是周日打头。 */
class WeekStartTest {

    @Test
    fun `monday first rotates weekday labels to start at monday`() {
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        val weekStartDay = 0

        val rotated = (0 until 7).map { labels[(weekStartDay + it) % 7] }

        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), rotated)
    }

    @Test
    fun `sunday first rotates weekday labels to start at sunday`() {
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        val weekStartDay = 6

        val rotated = (0 until 7).map { labels[(weekStartDay + it) % 7] }

        assertEquals(listOf("日", "一", "二", "三", "四", "五", "六"), rotated)
    }

    @Test
    fun `month grid columns shift with the week start day`() {
        // 2026-09-01 是周二：周一起排时首格落在 8/31，周日起排时落在 8/30
        val mondayFirst = MonthGrid.of(2026, 9, weekStartDay = 0)
        val sundayFirst = MonthGrid.of(2026, 9, weekStartDay = 6)

        val septemberFirst = win.zuoye.dao.data.Ymd.ymdToEpochDay(2026, 9, 1)
        assertEquals(1L, septemberFirst - mondayFirst.firstCellEpochDay)
        assertEquals(2L, septemberFirst - sundayFirst.firstCellEpochDay)

        // 不管怎么排，本月 30 天都在网格里，总格数固定 6×7
        assertEquals(30, mondayFirst.weeks.flatten().count { it.inCurrentMonth })
        assertEquals(30, sundayFirst.weeks.flatten().count { it.inCurrentMonth })
        assertEquals(42, mondayFirst.cellCount)
        assertEquals(42, sundayFirst.cellCount)
        assertTrue(sundayFirst.weeks.size == MonthGrid.ROWS)
    }
}
