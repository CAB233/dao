package win.zuoye.dao.widget

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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

    private fun applyRemoteViews(views: RemoteViews) {
        val parent = FrameLayout(context) as ViewGroup
        assertNotNull(views.apply(context, parent), "RemoteViews.apply returned null")
    }

    @Test
    fun `week widget builds and applies without invalid remote views`() {
        val views = DaoWeekWidgetProvider.buildViews(context, PlanDocument(), Ymd.today())

        applyRemoteViews(views)
    }
}
