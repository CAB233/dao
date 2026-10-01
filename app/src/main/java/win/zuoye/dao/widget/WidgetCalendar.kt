package win.zuoye.dao.widget

import win.zuoye.dao.data.Ymd

enum class RosterWidgetKind {
    WEEK,
    MONTH,
}

internal const val FIRST_WIDGET_MONTH = 2000 * 12
internal const val LAST_WIDGET_MONTH = 2100 * 12 + 11

internal fun Ymd.widgetMonth(): Int = year * 12 + month - 1

internal fun widgetMonthDate(month: Int): Ymd =
    Ymd(Math.floorDiv(month, 12), Math.floorMod(month, 12) + 1, 1)

internal fun moveWidgetMonth(month: Int, delta: Int): Int =
    (month.toLong() + delta)
        .coerceIn(FIRST_WIDGET_MONTH.toLong(), LAST_WIDGET_MONTH.toLong())
        .toInt()

/** 周视图沿用用户的一周起始日；月视图补齐首尾周，保留主页的相邻月份日期。 */
internal fun widgetDates(
    kind: RosterWidgetKind,
    today: Ymd,
    month: Int,
    weekStartDay: Int,
): List<Ymd> {
    val startDay = Math.floorMod(weekStartDay, 7)
    val first = if (kind == RosterWidgetKind.WEEK) today else widgetMonthDate(month)
    val offset = Math.floorMod(first.weekdayIndex - startDay, 7)
    val count =
        if (kind == RosterWidgetKind.WEEK) 7
        else ((offset + Ymd.daysInMonth(first.year, first.month) + 6) / 7) * 7
    val start = first.epochDay - offset
    return List(count) { Ymd.fromEpochDay(start + it) }
}
