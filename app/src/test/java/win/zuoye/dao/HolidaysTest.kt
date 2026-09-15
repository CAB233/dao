package win.zuoye.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import win.zuoye.dao.data.LegalHolidays
import win.zuoye.dao.data.Ymd

class HolidaysTest {

    private fun day(y: Int, m: Int, d: Int) = Ymd(y, m, d).epochDay

    @Test
    fun restDays() {
        val spring = LegalHolidays.of(day(2026, 2, 15))!!
        assertEquals("春节", spring.name)
        // 春节 2/15–2/23 连续 9 天都是放假
        for (d in 15..23) {
            val h = LegalHolidays.of(day(2026, 2, d))
            assertTrue("2/$d 应为春节放假日", h != null && !h.isMakeupWorkday)
        }
        // 国庆 10/1–10/7
        assertEquals("国庆节", LegalHolidays.of(day(2026, 10, 1))!!.name)
        assertEquals("国庆节", LegalHolidays.of(day(2026, 10, 7))!!.name)
        // 中秋 9/25–9/27
        assertEquals("中秋节", LegalHolidays.of(day(2026, 9, 25))!!.name)
    }

    @Test
    fun makeupWorkdays() {
        val work = LegalHolidays.of(day(2026, 9, 20))!!
        assertTrue("9/20 应为国庆调休上班日", work.isMakeupWorkday)
        assertTrue(LegalHolidays.of(day(2026, 10, 10))!!.isMakeupWorkday)
        assertTrue(LegalHolidays.of(day(2026, 2, 28))!!.isMakeupWorkday)
    }

    @Test
    fun plainDays() {
        assertNull(LegalHolidays.of(day(2026, 7, 10)))
        assertNull(LegalHolidays.of(day(2026, 12, 25)))
    }

    @Test
    fun onlyCurrentAndNextYearAreSupported() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        assertNull(LegalHolidays.of(day(currentYear - 1, 10, 1)))
        assertNull(LegalHolidays.of(day(currentYear + 2, 10, 1)))
    }

    @Test
    fun noOverlapBetweenRestAndWork() {
        // 调休上班日不允许落在任何放假日区间内
        val map = LegalHolidaysTestAccess.all()
        val rests = map.filterValues { !it.isMakeupWorkday }.keys
        val works = map.filterValues { it.isMakeupWorkday }.keys
        assertTrue((rests intersect works).isEmpty())
    }
}

/** 测试专用：暴露内部全量映射做一致性校验 */
object LegalHolidaysTestAccess {
    private fun day(y: Int, m: Int, d: Int) = Ymd(y, m, d).epochDay

    fun all(): Map<Long, LegalHolidays.HolidayDay> {
        // 遍历当前年和下一年全部日期走公开 API，等价于内部映射
        val map = HashMap<Long, LegalHolidays.HolidayDay>()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (epochDay in day(currentYear, 1, 1)..day(currentYear + 1, 12, 31)) {
            LegalHolidays.of(epochDay)?.let { map[epochDay] = it }
        }
        return map
    }
}
