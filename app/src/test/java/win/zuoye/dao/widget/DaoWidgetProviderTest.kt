package win.zuoye.dao.widget

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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

/**
 * 真的把小组件的 RemoteViews inflate 并 apply 一遍。
 *
 * RemoteViews 的 action 是**运行时**才校验的：`setColorFilter` 打在 `TextView` 上、`setBackgroundResource` 在 API
 * 31 以下调用，都会在宿主 `apply()` 时抛 `ActionException`，而编译期一点提示都没有——真机上表现为
 * 小组件一直停在加载占位图。这个测试就是那道防线：**只编译通过不代表小组件能显示**。
 *
 * `sdk` 必须显式指定：Robolectric 自带的 android-all 最高到 36，而应用 targetSdk 是 37， 不指定会直接以
 * "targetSdkVersion=37 > maxSdkVersion=36" 报配置失败。 类上取 35，再用方法级 `@Config(sdk = [30])` 覆盖
 * `setBackgroundResource` 的降级分支。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoWidgetProviderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val morning =
        ShiftTemplate(
            id = 1L,
            name = "早班",
            startMinute = 8 * 60,
            endMinute = 15 * 60,
            colorArgb = 0xFF43A047.toInt(),
        )
    private val night =
        ShiftTemplate(
            id = 2L,
            name = "夜班",
            startMinute = 22 * 60,
            endMinute = 6 * 60,
            colorArgb = 0xFF8E24AA.toInt(),
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
    /** 色板外的自定义颜色，用来覆盖「没有对应 drawable」的降级分支 */
    private val custom =
        ShiftTemplate(
            id = 4L,
            name = "自定",
            startMinute = 9 * 60,
            endMinute = 18 * 60,
            colorArgb = 0xFF123456.toInt(),
        )

    private fun document(templates: List<ShiftTemplate>): PlanDocument =
        PlanDocument(
            templates = persistentListOf<ShiftTemplate>().addAll(templates),
            schemes =
                persistentListOf(
                    Scheme(
                        id = 1L,
                        name = "四班三倒",
                        cycleDays = templates.size,
                        dayTemplateIds = persistentListOf<Long>().addAll(templates.map { it.id }),
                        createdAt = 0L,
                        groups =
                            persistentListOf(
                                SchemeGroup(
                                    id = 10L,
                                    name = "甲班",
                                    anchorEpochDay = TODAY.epochDay,
                                )
                            ),
                        defaultGroupId = 10L,
                    )
                ),
            activeSchemeId = 1L,
        )

    /** inflate + 逐条 apply；任何非法 action 都会在这里抛 ActionException */
    private fun applyRemoteViews(views: android.widget.RemoteViews): android.view.View {
        val parent = FrameLayout(context) as ViewGroup
        val applied = views.apply(context, parent)
        assertNotNull(applied, "RemoteViews.apply returned null")
        return applied
    }

    @Test
    fun `month widget builds and applies without invalid remote views actions`() {
        val views =
            DaoWidgetProvider.buildViews(context, document(listOf(morning, night, rest)), null)

        applyRemoteViews(views)
    }

    @Test
    fun `month widget applies when the document is empty`() {
        val views = DaoWidgetProvider.buildViews(context, PlanDocument(), null)

        applyRemoteViews(views)
    }

    @Test
    fun `day cell applies for every state`() {
        val roster = win.zuoye.dao.domain.Roster.of(document(listOf(morning, night, rest, custom)))
        val anchor = TODAY.epochDay

        // 覆盖四种状态：今天（有班次描边）/ 今天之后（实心）/ 已过去（消色）/ 没排班（浅灰）
        // 加色板外的自定义色，以及上下月补位格
        val grid = win.zuoye.dao.domain.MonthGrid.of(TODAY, weekStartDay = 0)
        grid.weeks.flatten().forEach { cell ->
            applyRemoteViews(DaoWidgetProvider.dayCellViews(context, cell, TODAY, roster, anchor))
        }
        // 没有锚点（没有启用方案）时也不能炸
        grid.weeks.flatten().forEach { cell ->
            applyRemoteViews(DaoWidgetProvider.dayCellViews(context, cell, TODAY, roster, null))
        }
    }

    @Test
    @Config(sdk = [30])
    fun `day cell falls back to setBackgroundColor below api 31`() {
        val roster = win.zuoye.dao.domain.Roster.of(document(listOf(morning, custom)))
        val anchor = TODAY.epochDay

        // API 30 上 setBackgroundResource 不存在，必须走 setBackgroundColor 分支
        win.zuoye.dao.domain.MonthGrid.of(TODAY, weekStartDay = 0).weeks.flatten().forEach { cell ->
            applyRemoteViews(DaoWidgetProvider.dayCellViews(context, cell, TODAY, roster, anchor))
        }
    }

    @Test
    fun `grid is a fixed 6x7 block covering the month plus leading and trailing days`() {
        val grid = win.zuoye.dao.domain.MonthGrid.of(TODAY, weekStartDay = 0)

        // 固定 6×7：列才对齐表头，不会因为某行只剩两三天就把格子摊宽
        assertEquals(win.zuoye.dao.domain.MonthGrid.ROWS, grid.weeks.size)
        grid.weeks.forEach { week ->
            assertEquals(win.zuoye.dao.domain.MonthGrid.COLUMNS, week.size)
        }
        assertEquals(42, grid.cellCount)

        // 本月每一天都在网格里，且被标成 inCurrentMonth
        val days = win.zuoye.dao.data.Ymd.daysInMonth(TODAY.year, TODAY.month)
        val inMonth = grid.weeks.flatten().filter { it.inCurrentMonth }
        assertEquals(days, inMonth.size, "grid must cover every day of the month")

        // 上下月补位格必须连续地排在首尾
        val all = grid.weeks.flatten().map { it.epochDay }
        assertEquals(all, all.sorted(), "grid days must be consecutive")
        assertEquals(42 - days, grid.weeks.flatten().count { !it.inCurrentMonth })
    }

    private companion object {
        val TODAY =
            win.zuoye.dao.data.Ymd.ymdToEpochDay(2026, 9, 30).let {
                win.zuoye.dao.data.Ymd.fromEpochDay(it)
            }
    }

    /**
     * 月历的普通版 / 大字版必须渲染出**不同大小**的字。
     *
     * 同 `DaoWeekWidgetProviderTest` 里那条：字号是 RemoteViews 的 action，只有 apply 之后才落到 TextView 上，所以必须真的
     * inflate 一遍再读。标题在根布局里，直接 findViewById 即可。
     */
    @Test
    fun `large month variant renders a bigger title`() {
        val normal =
            applyRemoteViews(
                DaoWidgetProvider.buildViews(context, PlanDocument(), null, scale = 1f)
            )
        val large =
            applyRemoteViews(
                DaoWidgetProvider.buildViews(context, PlanDocument(), null, scale = 1.6f)
            )

        val normalSize = normal.findViewById<TextView>(R.id.widget_title).textSize
        val largeSize = large.findViewById<TextView>(R.id.widget_title).textSize

        assertTrue(
            largeSize > normalSize * 1.4f,
            "large variant title ($largeSize) should be bigger than normal ($normalSize)",
        )
    }
}
