package win.zuoye.dao.widget

import kotlin.test.assertEquals
import org.junit.Test

/**
 * 两个档位（普通 / 大字）靠 `contentScale` 区分，这里把它钉住。
 *
 * 背景：这两个值曾经"看起来配好了"但真机上完全没效果 —— 因为 `refreshNow` 会把**所有**实例的 appWidgetId 打包发给普通版那个 receiver，普通版于是用
 * 1.0 的比例把大字版重新渲染了一遍， 每次刷新都把大字版打回小字。现在 `refreshNow` 按 provider 分别广播，这条测试保证两档的值 不会被人手滑改成一样。
 */
class LargeVariantScaleTest {

    @Test
    fun `week widget has two distinct scales`() {
        assertEquals(1f, DaoWeekWidgetProvider().contentScale)
        assertEquals(2.1f, DaoWeekWidgetLargeProvider().contentScale)
    }

    @Test
    fun `month widget has two distinct scales`() {
        assertEquals(1f, DaoWidgetProvider().contentScale)
        assertEquals(1.6f, DaoWidgetLargeProvider().contentScale)
    }
}
