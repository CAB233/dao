package win.zuoye.dao.domain

import win.zuoye.dao.data.Ymd

/**
 * 今天所在月 + [delta] 个月，跨年自动进位；返回的是该月 1 日（只关心年月的地方都用它）。
 *
 * 小组件翻页时 delta 就是 `WidgetPageStore` 里那个偏移（相对今月）。
 */
fun monthWithDelta(today: Ymd, delta: Int): Ymd {
    val index = today.year * MONTHS_PER_YEAR + (today.month - 1) + delta
    return Ymd(
        year = Math.floorDiv(index, MONTHS_PER_YEAR),
        month = Math.floorMod(index, MONTHS_PER_YEAR) + 1,
        day = 1,
    )
}

/**
 * [epochDay] 所在周的起始日（[weekStartDay] 0 = 周一，与设置里一致）。
 *
 * 用 `floorMod` 而不是 `%`：负数取模在 Kotlin 里仍是负的，跨到 1970 之前会算错。
 */
fun weekStartOf(epochDay: Long, weekStartDay: Int): Long =
    epochDay - Math.floorMod(Ymd.fromEpochDay(epochDay).weekdayIndex - weekStartDay, 7)

private const val MONTHS_PER_YEAR = 12
