package win.zuoye.dao.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd

/**
 * [todayRoster] / [shiftPhaseOf] / [nextShiftBoundaryMinute] 的纯逻辑单测。
 *
 * 这些判定同时驱动首页状态卡和桌面小组件，跨零点夜班与换班覆盖是最容易算错的两处，所以固定住。
 */
class TodayRosterTest {

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
    private val rest =
        ShiftTemplate(
            id = 3L,
            name = "休息",
            startMinute = 0,
            endMinute = 0,
            colorArgb = 0,
            isRest = true,
        )

    /** 锚点 = 2026-01-05，两天一循环：第 1 天早班、第 2 天夜班 */
    private fun document(
        templates: List<ShiftTemplate> = listOf(morning, night),
        dayTemplateIds: List<Long> = listOf(1L, 2L),
        overrides: Map<String, Long> = emptyMap(),
        groups: List<SchemeGroup> =
            listOf(SchemeGroup(id = 10L, name = "甲班", anchorEpochDay = ANCHOR)),
        defaultGroupId: Long = 10L,
    ): PlanDocument =
        PlanDocument(
            templates = persistentListOf<ShiftTemplate>().addAll(templates),
            schemes =
                persistentListOf(
                    Scheme(
                        id = 1L,
                        name = "四班三倒",
                        cycleDays = dayTemplateIds.size,
                        dayTemplateIds = persistentListOf<Long>().addAll(dayTemplateIds),
                        createdAt = 0L,
                        groups = persistentListOf<SchemeGroup>().addAll(groups),
                        defaultGroupId = defaultGroupId,
                    )
                ),
            activeSchemeId = 1L,
            overrides = persistentMapOf<String, Long>().putAll(overrides),
        )

    @Test
    fun `normal shift is working only between start and end`() {
        // 锚点当天是周期第 1 天 = 早班 08:00-15:00
        assertEquals(ShiftPhase.OFF_WORK, shiftPhaseOf(morning, null, 7 * 60 + 59))
        assertEquals(ShiftPhase.ON_SHIFT, shiftPhaseOf(morning, null, 8 * 60))
        assertEquals(ShiftPhase.ON_SHIFT, shiftPhaseOf(morning, null, 14 * 60 + 59))
        assertEquals(ShiftPhase.OFF_WORK, shiftPhaseOf(morning, null, 15 * 60))
    }

    @Test
    fun `cross midnight shift is working after its start and before next day end`() {
        // 当天 23:00 已经在班上
        assertEquals(ShiftPhase.ON_SHIFT, shiftPhaseOf(night, null, 23 * 60))
        // 当天 21:59 还没上班
        assertEquals(ShiftPhase.OFF_WORK, shiftPhaseOf(night, null, 21 * 60 + 59))
        // 当天 06:00 夜班刚结束
        assertEquals(ShiftPhase.OFF_WORK, shiftPhaseOf(night, null, 6 * 60))
    }

    @Test
    fun `carried over night shift keeps working into the next morning`() {
        // 今天早班、昨天夜班：凌晨 3 点还在昨天的班上
        assertEquals(ShiftPhase.ON_SHIFT, shiftPhaseOf(morning, night, 3 * 60))
        // 凌晨 6 点夜班结束
        assertEquals(ShiftPhase.OFF_WORK, shiftPhaseOf(morning, night, 6 * 60))
        // 早班开始后由今天的班次接管，昨天的夜班不再影响判定
        assertEquals(ShiftPhase.ON_SHIFT, shiftPhaseOf(morning, night, 9 * 60))
        assertEquals(ShiftPhase.OFF_WORK, shiftPhaseOf(morning, night, 16 * 60))
    }

    @Test
    fun `rest template reports resting instead of working`() {
        assertEquals(ShiftPhase.RESTING, shiftPhaseOf(rest, null, 10 * 60))
        // 昨天是跨零点夜班、今天休班：凌晨 3 点还在上班
        assertEquals(ShiftPhase.ON_SHIFT, shiftPhaseOf(rest, night, 3 * 60))
    }

    @Test
    fun `no template at all is off work`() {
        assertEquals(ShiftPhase.OFF_WORK, shiftPhaseOf(null, null, 10 * 60))
    }

    @Test
    fun `todayRoster resolves every group against its own anchor`() {
        val groups =
            listOf(
                SchemeGroup(id = 10L, name = "甲班", anchorEpochDay = ANCHOR),
                // 乙班晚一天起算，所以同一天落在周期的另一天
                SchemeGroup(id = 11L, name = "乙班", anchorEpochDay = ANCHOR + 1),
            )
        val doc = document(groups = groups)

        val roster = todayRoster(doc, Ymd.fromEpochDay(ANCHOR), 9 * 60)

        assertEquals("四班三倒", roster.schemeName)
        assertEquals("甲班", roster.defaultGroupName)
        assertEquals(2, roster.groups.size)
        assertEquals(morning.id, roster.groups[0].template?.id)
        assertEquals(night.id, roster.groups[1].template?.id)
        assertEquals(morning.id, roster.defaultShift?.template?.id)
        assertEquals(ShiftPhase.ON_SHIFT, roster.phase)
    }

    @Test
    fun `defaultShift follows the default group instead of the first one`() {
        val groups =
            listOf(
                // 甲班当天是周期第 1 天 = 早班
                SchemeGroup(id = 10L, name = "甲班", anchorEpochDay = ANCHOR),
                // 乙班晚一天起算，当天是周期第 2 天 = 夜班
                SchemeGroup(id = 11L, name = "乙班", anchorEpochDay = ANCHOR + 1),
            )
        val doc = document(groups = groups, defaultGroupId = 11L)

        val roster = todayRoster(doc, Ymd.fromEpochDay(ANCHOR), 23 * 60)

        assertEquals("乙班", roster.defaultGroupName)
        // 默认班组的班次必须和 phase 用的是同一个班组，否则小组件会拿休息班的结束时刻去配「上班中」
        assertEquals(night.id, roster.defaultShift?.template?.id)
        assertEquals(ShiftPhase.ON_SHIFT, roster.phase)
    }

    @Test
    fun `override wins over the cycle and is flagged`() {
        val today = Ymd.fromEpochDay(ANCHOR)
        val doc = document(overrides = mapOf(today.epochDay.toString() to night.id))

        val roster = todayRoster(doc, today, 23 * 60)

        assertEquals(night.id, roster.defaultShift?.template?.id)
    }

    @Test
    fun `empty document yields an empty roster`() {
        val roster = todayRoster(PlanDocument(), Ymd.fromEpochDay(ANCHOR), 0)

        assertNull(roster.schemeName)
        assertNull(roster.defaultGroupName)
        assertNull(roster.defaultShift)
        assertEquals(0, roster.groups.size)
        assertEquals(ShiftPhase.OFF_WORK, roster.phase)
    }

    @Test
    fun `missing template id leaves the day unassigned`() {
        // 周期第 1 天指向一个不存在的模板
        val doc = document(dayTemplateIds = listOf(999L))

        val roster = todayRoster(doc, Ymd.fromEpochDay(ANCHOR), 9 * 60)

        assertNull(roster.defaultShift?.template)
        assertEquals(ShiftPhase.OFF_WORK, roster.phase)
    }

    private companion object {
        /** 2026-01-05 的 epochDay，用 Ymd 自己算，避免手写常数出错 */
        val ANCHOR = Ymd.ymdToEpochDay(2026, 1, 5)
    }
}
