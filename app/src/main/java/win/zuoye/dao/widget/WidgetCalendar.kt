package win.zuoye.dao.widget

import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.domain.Roster

enum class RosterWidgetKind {
    WEEK,
    MONTH,
}

internal enum class WidgetLayout(
    val navigationKind: RosterWidgetKind,
    val hasHeader: Boolean = true,
    val hasGroups: Boolean = false,
    val weeks: Int = 1,
) {
    COMPACT_WEEK(RosterWidgetKind.WEEK, hasHeader = false),
    WEEK(RosterWidgetKind.WEEK, hasGroups = true),
    THREE_WEEKS(RosterWidgetKind.WEEK, weeks = 3),
    MONTH(RosterWidgetKind.MONTH),
    MONTH_WITH_GROUPS(RosterWidgetKind.MONTH, hasGroups = true),
}

/** 桌面提供实际 dp 尺寸；分档兼容原有 130dp 周头部和 250dp 月历最小高度。 */
internal fun widgetLayout(height: Float): WidgetLayout =
    when {
        height < 130f -> WidgetLayout.COMPACT_WEEK
        height < 200f -> WidgetLayout.WEEK
        height < 250f -> WidgetLayout.THREE_WEEKS
        height < 350f -> WidgetLayout.MONTH
        else -> WidgetLayout.MONTH_WITH_GROUPS
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

internal fun widgetWeekStart(date: Ymd, weekStartDay: Int): Long =
    date.epochDay - Math.floorMod(date.weekdayIndex - Math.floorMod(weekStartDay, 7), 7)

internal fun moveWidgetWeek(week: Long, delta: Int, weekStartDay: Int): Long =
    (week + delta.toLong() * 7).coerceIn(
        widgetWeekStart(Ymd(2000, 1, 1), weekStartDay),
        widgetWeekStart(Ymd(2100, 12, 31), weekStartDay),
    )

/** 周视图沿用用户的一周起始日；月视图补齐首尾周，保留主页的相邻月份日期。 */
internal fun widgetDates(
    kind: RosterWidgetKind,
    today: Ymd,
    month: Int,
    weekStartDay: Int,
    weeks: Int = 1,
): List<Ymd> {
    val startDay = Math.floorMod(weekStartDay, 7)
    val first = if (kind == RosterWidgetKind.WEEK) today else widgetMonthDate(month)
    val offset = Math.floorMod(first.weekdayIndex - startDay, 7)
    val count =
        if (kind == RosterWidgetKind.WEEK) 7 * weeks
        else ((offset + Ymd.daysInMonth(first.year, first.month) + 6) / 7) * 7
    val start = first.epochDay - offset - if (kind == RosterWidgetKind.WEEK) (weeks / 2) * 7 else 0
    return List(count) { Ymd.fromEpochDay(start + it) }
}

internal data class WidgetGroupShift(val group: SchemeGroup, val template: ShiftTemplate?)

/** 全部班组使用各自的锚点推导当天排班，顺序沿用方案设置。 */
internal fun widgetGroupShifts(
    document: PlanDocument,
    date: Ymd,
    roster: Roster,
): List<WidgetGroupShift> {
    val scheme = document.activeScheme() ?: return emptyList()
    return scheme.groups.map {
        WidgetGroupShift(it, roster.templateFor(date.epochDay, it.anchorEpochDay))
    }
}
