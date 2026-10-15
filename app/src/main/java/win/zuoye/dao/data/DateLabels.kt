package win.zuoye.dao.data

import com.tyme.solar.SolarDay
import win.zuoye.dao.R

/** 农历日期：月、日、是否闰月，以及当月天数（判断除夕用）。 */
internal data class LunarDate(
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
    val daysInMonth: Int,
)

/** 农历文案：主页用 stringResource 读、小组件用 Context 读，取名逻辑只写一份。 */
internal data class LunarStrings(
    val months: List<String>,
    val days: List<String>,
    val leap: String,
    val format: String,
)

internal fun lunarDate(date: Ymd): LunarDate {
    val lunar = SolarDay.fromYmd(date.year, date.month, date.day).getLunarDay()
    val lunarMonth = lunar.getLunarMonth()
    return LunarDate(
        month = kotlin.math.abs(lunar.month),
        day = lunar.day,
        isLeapMonth = lunar.month < 0,
        daysInMonth = lunarMonth.getDayCount(),
    )
}

/** 农历标签：初一显示月份（跨月才有意义），其余日子显示日期名。 */
internal fun lunarLabel(lunar: LunarDate, strings: LunarStrings): String {
    val dayName = strings.days.getOrElse(lunar.day - 1) { lunar.day.toString() }
    if (lunar.day != 1) return dayName
    val leapPrefix = if (lunar.isLeapMonth) strings.leap else ""
    val monthName = strings.months.getOrElse(lunar.month - 1) { lunar.month.toString() }
    return strings.format
        .replace("%1\$s", leapPrefix)
        .replace("%2\$s", monthName)
        .replace("%3\$s", dayName)
}

/** 节日信息只在当前年和下一年显示；法定节假日优先于通用节日名称。 */
internal fun holidayNameRes(
    date: Ymd,
    lunar: LunarDate,
    currentYear: Int,
    holiday: LegalHolidays.HolidayDay? = LegalHolidays.of(date.epochDay),
): Int? {
    holiday
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

internal fun LegalHolidays.HolidayName.stringRes(): Int =
    when (this) {
        LegalHolidays.HolidayName.NEW_YEAR -> R.string.holiday_new_year
        LegalHolidays.HolidayName.SPRING_FESTIVAL -> R.string.holiday_spring_festival
        LegalHolidays.HolidayName.QINGMING -> R.string.holiday_qingming
        LegalHolidays.HolidayName.LABOR_DAY -> R.string.holiday_labor_day
        LegalHolidays.HolidayName.DRAGON_BOAT -> R.string.holiday_dragon_boat
        LegalHolidays.HolidayName.MID_AUTUMN -> R.string.holiday_mid_autumn
        LegalHolidays.HolidayName.NATIONAL_DAY -> R.string.holiday_national_day
    }
