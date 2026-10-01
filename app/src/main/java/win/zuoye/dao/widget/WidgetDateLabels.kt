package win.zuoye.dao.widget

import android.content.Context
import com.tyme.solar.SolarDay
import win.zuoye.dao.R
import win.zuoye.dao.data.LegalHolidays
import win.zuoye.dao.data.Ymd

/** 与主页一致的农历和节日名称；资源每次绘制只读一份。 */
internal class WidgetDateLabels(
    private val context: Context,
    private val showHolidays: Boolean,
    private val showLunar: Boolean,
    private val currentYear: Int,
) {
    private val strings = calendarStrings(context)

    fun label(date: Ymd): String? {
        if (!showHolidays && !showLunar) return null
        val lunar = lunarDate(date)
        if (showHolidays) {
            holidayName(date, lunar, currentYear)?.let {
                return context.getString(it)
            }
        }
        return if (showLunar) lunarLabel(lunar, strings) else null
    }
}

private data class LunarDate(
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
    val daysInMonth: Int,
)

private data class CalendarStrings(
    val lunarMonths: List<String>,
    val lunarDays: List<String>,
    val lunarLeap: String,
    val lunarFormat: String,
)

private fun calendarStrings(context: Context) =
    CalendarStrings(
        lunarMonths = context.resources.getStringArray(R.array.lunar_month_names).toList(),
        lunarDays = context.resources.getStringArray(R.array.lunar_day_names).toList(),
        lunarLeap = context.getString(R.string.lunar_leap),
        lunarFormat = context.getString(R.string.lunar_date, "%1\$s", "%2\$s", "%3\$s"),
    )

private fun lunarDate(date: Ymd): LunarDate {
    val lunar = SolarDay.fromYmd(date.year, date.month, date.day).getLunarDay()
    val lunarMonth = lunar.getLunarMonth()
    return LunarDate(
        month = kotlin.math.abs(lunar.month),
        day = lunar.day,
        isLeapMonth = lunar.month < 0,
        daysInMonth = lunarMonth.getDayCount(),
    )
}

private fun lunarLabel(lunar: LunarDate, strings: CalendarStrings): String {
    val dayName = strings.lunarDays.getOrElse(lunar.day - 1) { lunar.day.toString() }
    if (lunar.day != 1) return dayName
    val leapPrefix = if (lunar.isLeapMonth) strings.lunarLeap else ""
    val monthName = strings.lunarMonths.getOrElse(lunar.month - 1) { lunar.month.toString() }
    return strings.lunarFormat
        .replace("%1\$s", leapPrefix)
        .replace("%2\$s", monthName)
        .replace("%3\$s", dayName)
}

/** 节日信息只在当前年和下一年显示；法定节假日优先于通用节日名称。 */
private fun holidayName(date: Ymd, lunar: LunarDate, currentYear: Int): Int? {
    LegalHolidays.of(date.epochDay)
        ?.takeIf { it.isNameDay }
        ?.let {
            return it.name.stringRes()
        }
    if (date.year !in currentYear..(currentYear + 1) || lunar.isLeapMonth) return null

    return when {
        date.month == 1 && date.day == 1 -> R.string.holiday_new_year
        lunar.month == 1 && lunar.day == 1 -> R.string.holiday_spring_festival
        lunar.month == 1 && lunar.day == 15 -> R.string.holiday_lantern_festival
        lunar.month == 2 && lunar.day == 2 -> R.string.holiday_dragon_heads_raising
        lunar.month == 5 && lunar.day == 5 -> R.string.holiday_dragon_boat
        lunar.month == 7 && lunar.day == 7 -> R.string.holiday_qixi
        lunar.month == 7 && lunar.day == 15 -> R.string.holiday_ghost_festival
        lunar.month == 8 && lunar.day == 15 -> R.string.holiday_mid_autumn
        lunar.month == 9 && lunar.day == 9 -> R.string.holiday_double_ninth
        lunar.month == 12 && lunar.day == 8 -> R.string.holiday_laba
        lunar.month == 12 && lunar.day >= lunar.daysInMonth -> R.string.holiday_new_year_eve
        date.month == 2 && date.day == 14 -> R.string.holiday_valentines
        date.month == 3 && date.day == 8 -> R.string.holiday_womens_day
        date.month == 4 && date.day == 1 -> R.string.holiday_april_fools
        date.month == 6 && date.day == 1 -> R.string.holiday_childrens_day
        date.month == 10 && date.day == 31 -> R.string.holiday_halloween
        date.month == 12 && date.day == 25 -> R.string.holiday_christmas
        date.month == 5 && date.weekdayIndex == 6 && date.day in 8..14 ->
            R.string.holiday_mothers_day
        date.month == 6 && date.weekdayIndex == 6 && date.day in 15..21 ->
            R.string.holiday_fathers_day
        date.month == 11 && date.weekdayIndex == 3 && date.day in 22..28 ->
            R.string.holiday_thanksgiving
        else -> null
    }
}

private fun LegalHolidays.HolidayName.stringRes(): Int =
    when (this) {
        LegalHolidays.HolidayName.NEW_YEAR -> R.string.holiday_new_year
        LegalHolidays.HolidayName.SPRING_FESTIVAL -> R.string.holiday_spring_festival
        LegalHolidays.HolidayName.QINGMING -> R.string.holiday_qingming
        LegalHolidays.HolidayName.LABOR_DAY -> R.string.holiday_labor_day
        LegalHolidays.HolidayName.DRAGON_BOAT -> R.string.holiday_dragon_boat
        LegalHolidays.HolidayName.MID_AUTUMN -> R.string.holiday_mid_autumn
        LegalHolidays.HolidayName.NATIONAL_DAY -> R.string.holiday_national_day
    }
