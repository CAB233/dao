package win.zuoye.dao.data

import kotlinx.serialization.Serializable

/**
 * 纯数学实现的公历日期（minSdk 24 下避开 java.time desugaring）。
 * epochDay 与 java.time.LocalDate.toEpochDay() 完全一致（1970-01-01 = 0）。
 */
@Serializable
data class Ymd(val year: Int, val month: Int, val day: Int) {

    val epochDay: Long get() = ymdToEpochDay(year, month, day)

    /** 0 = 周一 ... 6 = 周日（1970-01-01 为周四 → floorMod(epochDay + 3, 7)） */
    val weekdayIndex: Int get() = Math.floorMod(epochDay + 3, 7)

    companion object {
        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (isLeapYear(year)) 29 else 28
        }

        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

        fun fromEpochDay(epochDay: Long): Ymd {
            val z = epochDay + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = if (mp < 10) mp + 3 else mp - 9
            val year = (y + if (m <= 2) 1 else 0).toInt()
            return Ymd(year, m.toInt(), d.toInt())
        }

        fun ymdToEpochDay(year: Int, month: Int, day: Int): Long {
            val y = year - if (month <= 2) 1 else 0
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097L + doe - 719468
        }

        /** 今日（构造时取系统时间） */
        fun today(): Ymd {
            val cal = java.util.Calendar.getInstance()
            return Ymd(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }
}
