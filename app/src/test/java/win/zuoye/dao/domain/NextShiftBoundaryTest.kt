package win.zuoye.dao.domain

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.minuteOfDay

/** [nextShiftBoundaryMinute] 的边界计算：小组件靠它对齐「上班中 / 下班了」的翻面时刻。 */
class NextShiftBoundaryTest {

    private val morning =
        ShiftTemplate(
            id = 1L,
            name = "早班",
            startMinute = 8 * 60,
            endMinute = 15 * 60,
            colorArgb = 0,
        )
    private val night =
        ShiftTemplate(
            id = 2L,
            name = "夜班",
            startMinute = 22 * 60,
            endMinute = 6 * 60,
            colorArgb = 0,
        )

    /** 周期两天：早班 → 夜班，锚点就是今天，所以「今天」永远落在这个周期的第 1 天 */
    private fun document(anchorEpochDay: Long): PlanDocument =
        PlanDocument(
            templates = persistentListOf(morning, night),
            schemes =
                persistentListOf(
                    Scheme(
                        id = 1L,
                        name = "两班倒",
                        cycleDays = 2,
                        dayTemplateIds = persistentListOf(1L, 2L),
                        createdAt = 0L,
                        groups =
                            persistentListOf(
                                SchemeGroup(id = 10L, name = "甲班", anchorEpochDay = anchorEpochDay)
                            ),
                        defaultGroupId = 10L,
                    )
                ),
            activeSchemeId = 1L,
        )

    /** 锚点 = 今天 → 今天是周期第 1 天（早班），昨天是周期第 2 天（夜班、跨零点） */
    private fun documentAnchoredToday(): PlanDocument = document(Ymd.today().epochDay)

    @Test
    fun `before the morning shift the boundary is its start`() {
        assertEquals(8 * 60, nextShiftBoundaryMinute(documentAnchoredToday(), 7 * 60))
    }

    @Test
    fun `during the morning shift the boundary is its end`() {
        assertEquals(15 * 60, nextShiftBoundaryMinute(documentAnchoredToday(), 10 * 60))
    }

    @Test
    fun `during the carried over night shift the boundary is its end`() {
        // 凌晨 3 点：昨天 22:00 的夜班还没结束，下一个翻面时刻是 06:00
        assertEquals(6 * 60, nextShiftBoundaryMinute(documentAnchoredToday(), 3 * 60))
    }

    @Test
    fun `evening before a night shift points at its start`() {
        // 锚点 = 昨天 → 今天是周期第 2 天（夜班），晚上 21:00 的下一个边界是 22:00 上班
        val doc = document(Ymd.today().epochDay - 1)

        assertEquals(22 * 60, nextShiftBoundaryMinute(doc, 21 * 60))
    }

    @Test
    fun `carried over night shift counts today's end and ignores its own start`() {
        // 锚点 = 前天 → 今天早班、昨天夜班（22:00-次日 06:00）
        val doc = document(Ymd.today().epochDay - 2)

        // 凌晨 5 点：下一个边界是 06:00 下班，而不是昨天那个早已过去的 22:00
        assertEquals(6 * 60, nextShiftBoundaryMinute(doc, 5 * 60))
        // 07:00：夜班已结束、早班 08:00 还没开始
        assertEquals(8 * 60, nextShiftBoundaryMinute(doc, 7 * 60))
    }

    @Test
    fun `after all of today's boundaries there is nothing left`() {
        assertNull(nextShiftBoundaryMinute(documentAnchoredToday(), 16 * 60))
    }

    @Test
    fun `document without an active scheme has no boundaries`() {
        assertNull(nextShiftBoundaryMinute(PlanDocument(), 10 * 60))
    }

    @Test
    fun `rest days contribute no boundaries`() {
        val rest =
            ShiftTemplate(
                id = 3L,
                name = "休息",
                startMinute = 0,
                endMinute = 0,
                colorArgb = 0,
                isRest = true,
            )
        val doc =
            PlanDocument(
                templates = persistentListOf(rest),
                schemes =
                    persistentListOf(
                        Scheme(
                            id = 1L,
                            name = "常休",
                            cycleDays = 1,
                            dayTemplateIds = persistentListOf(3L),
                            createdAt = 0L,
                            groups =
                                persistentListOf(
                                    SchemeGroup(
                                        id = 10L,
                                        name = "甲班",
                                        anchorEpochDay = Ymd.today().epochDay,
                                    )
                                ),
                            defaultGroupId = 10L,
                        )
                    ),
                activeSchemeId = 1L,
            )

        assertNull(nextShiftBoundaryMinute(doc, 10 * 60))
    }
}

/** [Ymd.of] 与 [minuteOfDay]：小组件和首页都靠这两个从毫秒推「今天」和「现在几点」。 */
class YmdInstantTest {

    @Test
    fun `ymd of matches calendar fields`() {
        val millis = fixedMillis(2026, 3, 9, 14, 35)
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }

        val ymd = Ymd.of(millis)

        assertEquals(calendar.get(Calendar.YEAR), ymd.year)
        assertEquals(calendar.get(Calendar.MONTH) + 1, ymd.month)
        assertEquals(calendar.get(Calendar.DAY_OF_MONTH), ymd.day)
        // 2026-03-09 是周一
        assertEquals(0, ymd.weekdayIndex)
    }

    @Test
    fun `minute of day counts from local midnight`() {
        assertEquals(14 * 60 + 35, minuteOfDay(fixedMillis(2026, 3, 9, 14, 35)))
        assertEquals(0, minuteOfDay(fixedMillis(2026, 3, 9, 0, 0)))
        assertEquals(23 * 60 + 59, minuteOfDay(fixedMillis(2026, 3, 9, 23, 59)))
    }

    private fun fixedMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        Calendar.getInstance(TimeZone.getDefault())
            .apply {
                clear()
                set(year, month - 1, day, hour, minute, 0)
            }
            .timeInMillis
}
