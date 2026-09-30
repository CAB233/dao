package win.zuoye.dao.domain

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import win.zuoye.dao.data.Ymd

/**
 * 月历里的一格。
 *
 * [inCurrentMonth] 为 false 表示这是补位的上月/下月日期 —— 要一起画（日历才连续、列才对齐表头）， 但整体弱化，让视线自然落在本月上。
 */
@Immutable data class MonthCell(val epochDay: Long, val inCurrentMonth: Boolean)

/**
 * 一个月的日历网格，固定 6 行 × 7 列。
 *
 * 固定 6 行是为了**列对齐**：每行都从同一个星期开始，格子正对表头，不会因为某行只剩两三天就把格子摊宽。 上下月的补位日期一并给出，由渲染方决定怎么弱化。
 */
@Immutable
data class MonthGrid(
    val year: Int,
    val month: Int,
    /** 第一格（左上角）对应的 epochDay */
    val firstCellEpochDay: Long,
    val weeks: ImmutableList<ImmutableList<MonthCell>>,
) {
    val cellCount: Int
        get() = weeks.sumOf { it.size }

    companion object {
        /** 固定行数：6 行足够覆盖任意月份（本月 31 天 + 首格偏移最多 6 天 = 37 ≤ 42） */
        const val ROWS = 6

        const val COLUMNS = 7

        /** 生成 [year]-[month] 的网格；[weekStartDay] 0 = 周一（与设置里的一致）。 */
        fun of(year: Int, month: Int, weekStartDay: Int): MonthGrid {
            val firstEpochDay = Ymd.ymdToEpochDay(year, month, 1)
            val leadingOffset =
                Math.floorMod(Ymd.fromEpochDay(firstEpochDay).weekdayIndex - weekStartDay, 7)
            val gridStart = firstEpochDay - leadingOffset
            val lastEpochDay = Ymd.ymdToEpochDay(year, month, Ymd.daysInMonth(year, month))

            val weeks =
                (0 until ROWS).fold(persistentListOf<ImmutableList<MonthCell>>()) { acc, row ->
                    val cells =
                        (0 until COLUMNS).fold(persistentListOf<MonthCell>()) { rowAcc, column ->
                            val epochDay = gridStart + row * COLUMNS + column
                            rowAcc.add(
                                MonthCell(
                                    epochDay = epochDay,
                                    inCurrentMonth = epochDay in firstEpochDay..lastEpochDay,
                                )
                            )
                        }
                    acc.add(cells)
                }

            return MonthGrid(
                year = year,
                month = month,
                firstCellEpochDay = gridStart,
                weeks = weeks,
            )
        }

        fun of(today: Ymd, weekStartDay: Int): MonthGrid = of(today.year, today.month, weekStartDay)
    }
}
