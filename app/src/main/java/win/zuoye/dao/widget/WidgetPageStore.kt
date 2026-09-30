package win.zuoye.dao.widget

import android.content.Context

/**
 * 小组件"现在停在第几页"（页 = 一周 / 一个月），存的是**相对今天那一页的偏移**。
 *
 * 存偏移而不是绝对周/月：跨日、跨月之后"今天那一页"自己会变，存绝对值就得在读取时把过去的页拉回来，
 * 代价是往回翻不动。偏移则两种情形都对：没翻过就永远跟着今天，翻到上一页就一直是"今天的前一页"。
 *
 * 为什么需要它：翻页入口是点击（RemoteViews 收不到手势），而点击只是一次广播 —— 组件侧必须自己记住当前页，才能算出下一页是哪个。 放在 SharedPreferences 里而不是
 * `plan.json`：翻页是**看**的行为，不是排班数据。
 */
internal class WidgetPageStore(
    context: Context,
    private val key: String,
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 与"今天那一页"的偏移；没存过就是 0 */
    fun read(): Int = prefs.getInt(key, 0).coerceIn(-MAX_OFFSET, MAX_OFFSET)

    fun write(offset: Int) {
        prefs.edit().putInt(key, offset.coerceIn(-MAX_OFFSET, MAX_OFFSET)).apply()
    }

    /** 往后（正数）/ 往前（负数）翻一页 */
    fun shift(delta: Int) {
        write(read() + delta)
    }

    /** 回到今天所在的那一页 */
    fun reset() {
        write(0)
    }

    companion object {
        /** 「本周班次」当前停在第几周 */
        const val KEY_WEEK = "week_page"

        /** 「倒班月历」当前停在第几个月 */
        const val KEY_MONTH = "month_page"

        /**
         * 前后各留多少页。页是**一次性全部下发**的（ViewFlipper 的子视图不和 adapter 打交道）， 所以这个数字直接决定 RemoteViews 有多大：一个月 =
         * 42 格 × 每格几个 action，别开太大 （9 页 ≈ 380 格，实测流畅；再往上就没必要了）。
         */
        const val MAX_OFFSET = 4

        private const val PREFS = "dao_widget"
    }
}
