package win.zuoye.dao.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Ymd

/**
 * 把「本周班次」小组件的 RemoteViews 真的 inflate 并 apply 一遍。
 *
 * 这道防线是必要的：RemoteViews 只允许白名单里的控件（`@RemoteView`），放错一个就是运行时 `InflateException`，真机上表现为小组件一直停在加载占位图 ——
 * 编译期一点提示都没有 （「倒班月历」那边就踩过 `HorizontalScrollView` 不在白名单的坑）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoWeekWidgetProviderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun applyRemoteViews(views: RemoteViews): View {
        val parent = FrameLayout(context) as ViewGroup
        val applied = views.apply(context, parent)
        assertNotNull(applied, "RemoteViews.apply returned null")
        return applied
    }

    @Test
    fun `week widget builds and applies without invalid remote views`() {
        val views = DaoWeekWidgetProvider.buildViews(context, PlanDocument(), Ymd.today())

        applyRemoteViews(views)
    }

    /**
     * 大字版的字号必须真的更大。
     *
     * 这个断言值得写：曾经 `weekPageViews` 漏传 scale、`onUpdate` 也漏传 contentScale，
     * 结果两个档位渲染出来一模一样大（真机上被用户抓到）。字号是 RemoteViews 里的 action， 只有 `apply` 之后才落到 TextView 上，所以这里必须真的
     * inflate 一遍再读。
     */
    @Test
    fun `large variant renders noticeably bigger text`() {
        val normal =
            applyRemoteViews(
                DaoWeekWidgetProvider.buildViews(context, PlanDocument(), Ymd.today(), scale = 1f)
            )
        val large =
            applyRemoteViews(
                DaoWeekWidgetProvider.buildViews(context, PlanDocument(), Ymd.today(), scale = 2.5f)
            )

        val normalSize = normal.findViewById<TextView>(R.id.week_widget_month).textSize
        val largeSize = large.findViewById<TextView>(R.id.week_widget_month).textSize

        assertTrue(
            largeSize > normalSize * 2f,
            "large variant header text ($largeSize) should be much bigger than normal ($normalSize)",
        )
    }
}
