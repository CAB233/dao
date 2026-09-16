package win.zuoye.dao.data

import java.time.LocalDate
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YmdTest {
    @Test
    fun epochDayMatchesLocalDateAcrossEraBoundaries() {
        val dates = listOf(
            Ymd(-400, 2, 29),
            Ymd(1, 1, 1),
            Ymd(1582, 10, 15),
            Ymd(1969, 12, 31),
            Ymd(1970, 1, 1),
            Ymd(2000, 2, 29),
            Ymd(2100, 3, 1),
        )

        dates.forEach { date ->
            assertEquals(
                LocalDate.of(date.year, date.month, date.day).toEpochDay(),
                date.epochDay,
            )
            assertEquals(date, Ymd.fromEpochDay(date.epochDay))
        }
    }

    @Test
    fun calendarComparisonUsesUtc() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(1970, Calendar.JANUARY, 2)
        }

        assertEquals(1L, calendar.timeInMillis / 86_400_000L)
        assertEquals(1L, Ymd(1970, 1, 2).epochDay)
    }

    @Test
    fun leapYearAndMonthLengthsFollowGregorianRules() {
        assertTrue(Ymd.isLeapYear(2000))
        assertFalse(Ymd.isLeapYear(1900))
        assertTrue(Ymd.isLeapYear(2024))
        assertEquals(29, Ymd.daysInMonth(2024, 2))
        assertEquals(28, Ymd.daysInMonth(2023, 2))
        assertEquals(30, Ymd.daysInMonth(2024, 4))
        assertEquals(31, Ymd.daysInMonth(2024, 12))
    }

    @Test
    fun weekdayIndexStartsOnMondayAndHandlesNegativeEpochDays() {
        assertEquals(3, Ymd(1970, 1, 1).weekdayIndex)
        assertEquals(2, Ymd(1969, 12, 31).weekdayIndex)
        assertEquals(0, Ymd(2026, 9, 14).weekdayIndex)
    }
}
