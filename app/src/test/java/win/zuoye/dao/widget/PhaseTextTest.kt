package win.zuoye.dao.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.domain.hasCarriedOverNightShift

/**
 * 底部状态行的「到几点」。
 *
 * 用户报的规则：**同一个夜班，说法随时刻变**
 * - 当天 23:00（班还没上完、结束在明天）→「到次日07:30」
 * - 次日 06:00（已经跨过零点、结束就在今天）→「到07:30」
 *
 * 一律说「次日」会在次日凌晨显示成错的时刻。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh")
class PhaseTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** 用户方案里的夜班：22:30–07:30，跨零点 */
    private val night =
        ShiftTemplate(
            id = 1L,
            name = "夜班",
            startMinute = 22 * 60 + 30,
            endMinute = 7 * 60 + 30,
            colorArgb = 0xFF1E88E5.toInt(),
        )

    /** 早班：07:30–15:00 */
    private val morning =
        ShiftTemplate(
            id = 2L,
            name = "早班",
            startMinute = 7 * 60 + 30,
            endMinute = 15 * 60,
            colorArgb = 0xFF43A047.toInt(),
        )

    private val rest =
        ShiftTemplate(
            id = 3L,
            name = "休息",
            startMinute = 0,
            endMinute = 0,
            colorArgb = 0xFF9E9E9E.toInt(),
            isRest = true,
        )

    /**
     * 锚点 = 今天，[dayIds] 是周期里每天挂的模板 id。
     *
     * 周期第 1 天 = 今天，所以 `listOf(night.id, morning.id)` 表示「今天夜班、明天早班」， 而 `listOf(night.id, night.id)`
     * 表示今天也是夜班。
     */
    private fun document(vararg dayIds: Long): PlanDocument =
        PlanDocument(
            templates = persistentListOf(night, morning, rest),
            schemes =
                persistentListOf(
                    Scheme(
                        id = 1L,
                        name = "两班倒",
                        cycleDays = dayIds.size,
                        dayTemplateIds = persistentListOf<Long>().addAll(dayIds.toList()),
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

    private fun parts(doc: PlanDocument, minute: Int) =
        DaoWidgetProvider.phaseTextParts(context, doc, Ymd.today(), minute)

    /**
     * 两个班组、**默认班组不是第一个**：甲班今天休息（周期第 1 天），乙班今天夜班（早一天起算，落在周期第 2 天）。
     *
     * 底部状态行的时刻跟着默认班组乙班走，所以这里是「上班中」。
     */
    private fun twoGroupDocument(): PlanDocument {
        val today = Ymd.today().epochDay
        return PlanDocument(
            templates = persistentListOf(night, morning, rest),
            schemes =
                persistentListOf(
                    Scheme(
                        id = 1L,
                        name = "两班倒",
                        cycleDays = 2,
                        dayTemplateIds = persistentListOf(rest.id, night.id),
                        createdAt = 0L,
                        groups =
                            persistentListOf(
                                SchemeGroup(id = 10L, name = "甲班", anchorEpochDay = today),
                                SchemeGroup(id = 11L, name = "乙班", anchorEpochDay = today - 1),
                            ),
                        defaultGroupId = 11L,
                    )
                ),
            activeSchemeId = 1L,
        )
    }

    @Test
    fun `before midnight the night shift ends tomorrow`() {
        // 今天夜班、明天早班；23:00 已经在班上，结束在次日 07:30
        val doc = document(night.id, morning.id)

        val text = parts(doc, 23 * 60)

        assertEquals(R.string.widget_phase_on_shift, text.resId)
        assertEquals(listOf("次日07:30"), text.args)
    }

    @Test
    fun `right before the night shift starts it is still tomorrow`() {
        val doc = document(night.id, morning.id)

        val text = parts(doc, 21 * 60)

        // 还没上班：提示开始时间
        assertEquals(R.string.widget_phase_before_shift, text.resId)
        assertEquals(listOf("22:30"), text.args)
    }

    @Test
    fun `after midnight the carried over night shift ends today`() {
        // 今天早班、明天夜班 → 昨天是周期最后一天 = 夜班，凌晨还挂在昨天那班上
        val doc = document(morning.id, night.id)

        val text = parts(doc, 6 * 60)

        assertEquals(R.string.widget_phase_on_shift, text.resId)
        // 关键：结束就在今天，不能说「次日」
        assertEquals(listOf("07:30"), text.args)
    }

    @Test
    fun `after the carried over shift ends it is off work today`() {
        val doc = document(morning.id, night.id)

        // 07:30 之后夜班结束、今天早班 07:30 已开始 → 实际在早班
        val at8 = parts(doc, 8 * 60)
        assertEquals(R.string.widget_phase_on_shift, at8.resId)
        assertEquals(listOf("15:00"), at8.args)

        // 16:00 早班也结束了 → 下班
        val at16 = parts(doc, 16 * 60)
        assertEquals(R.string.widget_phase_after_shift, at16.resId)
        assertEquals(listOf("15:00"), at16.args)
    }

    @Test
    fun `today being a night shift keeps tomorrow for its end`() {
        // 今天也是夜班：23:00 与次日 03:00 都算"今天这班"，结束都在次日
        val doc = document(night.id, night.id)

        assertEquals(listOf("次日07:30"), parts(doc, 23 * 60).args)
        // 凌晨 03:00 挂在昨天那班（同样是夜班）上，结束就落在今天，不说「次日」
        assertEquals(listOf("07:30"), parts(doc, 3 * 60).args)
    }

    @Test
    fun `rest day says resting instead of a time`() {
        val doc = document(rest.id)

        val text = parts(doc, 10 * 60)

        assertEquals(R.string.widget_phase_resting, text.resId)
        assertEquals(emptyList(), text.args)
    }

    @Test
    fun `bottom line follows the default group even when it is not the first one`() {
        val doc = twoGroupDocument()

        val text = parts(doc, 23 * 60)

        // 默认班组乙班此刻在夜班上：结束在次日 07:30。
        // 取错班组（甲班今天休息、endMinute = 0）会显示成「上班中 · 到 00:00」。
        assertEquals(R.string.widget_phase_on_shift, text.resId)
        assertEquals(listOf("次日07:30"), text.args)
    }

    @Test
    fun `no plan says so`() {
        val text = parts(PlanDocument(), 10 * 60)

        assertEquals(R.string.widget_phase_no_plan, text.resId)
    }

    @Test
    fun `carry over detection matches the reported rule`() {
        val doc = document(morning.id, night.id)

        // 06:00 还挂在昨天夜班上
        assertEquals(true, hasCarriedOverNightShift(doc, Ymd.today(), 6 * 60))
        // 08:00 已是今天早班，不是"跨过来的"
        assertEquals(false, hasCarriedOverNightShift(doc, Ymd.today(), 8 * 60))
        // 16:00 下班了
        assertEquals(false, hasCarriedOverNightShift(doc, Ymd.today(), 16 * 60))
    }
}
