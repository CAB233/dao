package win.zuoye.dao.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd

/**
 * 换班覆盖：个别日期手动指定班次，优先于周期推导。
 *
 * `overrides` 在数据模型里一直存在、[resolveShift] 也一直在读，这个测试把"写入口"的行为钉住。
 */
class ShiftOverrideTest {

    private val morning =
        ShiftTemplate(id = 1L, name = "早班", startMinute = 480, endMinute = 900, colorArgb = 0)
    private val night =
        ShiftTemplate(id = 2L, name = "夜班", startMinute = 1320, endMinute = 360, colorArgb = 0)
    private val rest =
        ShiftTemplate(
            id = 3L,
            name = "休息",
            startMinute = 0,
            endMinute = 0,
            colorArgb = 0,
            isRest = true,
        )

    /** 两天一周期：第 1 天早班、第 2 天夜班；锚点 = [anchor]（默认今天） */
    private fun document(anchor: Long = TODAY_EPOCH): PlanDocument =
        PlanDocument(
            templates = persistentListOf(morning, night, rest),
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
                                SchemeGroup(id = 10L, name = "甲班", anchorEpochDay = anchor)
                            ),
                        defaultGroupId = 10L,
                    )
                ),
            activeSchemeId = 1L,
        )

    @Test
    fun `setting an override changes what that day resolves to`() {
        val doc = document()
        // 周期第 1 天本来是早班
        assertEquals(morning.id, resolveShift(doc, TODAY_EPOCH)?.template?.id)

        val changed = ShiftOverride.set(doc, TODAY_EPOCH, night.id)

        assertEquals(night.id, resolveShift(changed, TODAY_EPOCH)?.template?.id)
        assertTrue(resolveShift(changed, TODAY_EPOCH)!!.isOverride)
    }

    @Test
    fun `override only affects that one day`() {
        val doc = document()
        val changed = ShiftOverride.set(doc, TODAY_EPOCH, night.id)

        // 明天仍是周期里的第 2 天（夜班），且不是覆盖
        val tomorrow = resolveShift(changed, TODAY_EPOCH + 1)!!
        assertEquals(night.id, tomorrow.template.id)
        assertFalse(tomorrow.isOverride)
        // 今天之前的日期也不受影响
        assertFalse(resolveShift(changed, TODAY_EPOCH - 1)!!.isOverride)
    }

    @Test
    fun `picking the value the cycle already gives clears the override`() {
        val doc = document()
        val withOverride = ShiftOverride.set(doc, TODAY_EPOCH, night.id)
        assertTrue(ShiftOverride.isOverridden(withOverride, TODAY_EPOCH))

        // 又选回周期值 → 不该留下"被改过"的痕迹
        val back = ShiftOverride.set(withOverride, TODAY_EPOCH, morning.id)

        assertFalse(ShiftOverride.isOverridden(back, TODAY_EPOCH))
        assertTrue(back.overrides.isEmpty())
        assertEquals(morning.id, resolveShift(back, TODAY_EPOCH)?.template?.id)
    }

    @Test
    fun `clear restores the cycle value`() {
        val doc = ShiftOverride.set(document(), TODAY_EPOCH, night.id)

        val cleared = ShiftOverride.clear(doc, TODAY_EPOCH)

        assertFalse(ShiftOverride.isOverridden(cleared, TODAY_EPOCH))
        assertEquals(morning.id, resolveShift(cleared, TODAY_EPOCH)?.template?.id)
    }

    @Test
    fun `clear on a day without override is a no-op`() {
        val doc = document()

        assertEquals(doc, ShiftOverride.clear(doc, TODAY_EPOCH))
    }

    @Test
    fun `a rest override hides the shift for that day`() {
        val doc = document()

        val changed = ShiftOverride.set(doc, TODAY_EPOCH, rest.id)

        assertEquals(rest.id, resolveShift(changed, TODAY_EPOCH)?.template?.id)
        assertTrue(resolveShift(changed, TODAY_EPOCH)!!.template.isRest)
    }

    @Test
    fun `without an active scheme nothing is written and nothing resolves`() {
        // 没有启用方案时 set 会记下覆盖（周期值解析不出来），但 resolveShift 在缺锚点时直接返回 null，
        // 所以覆盖也不会生效。这是既有语义：没有方案就没有"这一天是什么班"可言。
        val empty = PlanDocument()

        val changed = ShiftOverride.set(empty, TODAY_EPOCH, morning.id)

        assertTrue(ShiftOverride.isOverridden(changed, TODAY_EPOCH))
        assertEquals(null, resolveShift(changed, TODAY_EPOCH))
    }

    private companion object {
        val TODAY_EPOCH = Ymd.ymdToEpochDay(2026, 9, 30)
    }
}
