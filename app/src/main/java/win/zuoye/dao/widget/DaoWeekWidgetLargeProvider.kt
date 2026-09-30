package win.zuoye.dao.widget

/**
 * 「本周班次 · 大」：与 [DaoWeekWidgetProvider] 同一套布局，但**字号放大一档、格子里显示班次说明**。
 *
 * 两个档位的差异只有这两项（其余全共用），各自写在子类里，互不影响：
 * - `contentScale = 1.9`：日期与翻页按钮一起放大
 * - `showShiftLabel = true`：日期下面那行"早/中/夜/休"（小版是 4×1 放不下，所以小版关着）
 *
 * 为什么做成两个组件而不是"一个组件按尺寸自适应"：线上实测 vivo 的 Launcher 把 MIN/MAX_WIDTH/HEIGHT 全填 0、OPTION_APPWIDGET_SIZES
 * 报的值也不随拉伸变化 —— 组件侧的尺寸信息完全不可信，任何"按尺寸算字号" 的方案都会停在最小档（用户反馈"拉大了字也不变"）。两个固定档位是唯一能保证显示效果的做法。
 */
class DaoWeekWidgetLargeProvider : DaoWeekWidgetProvider() {

    override val contentScale: Float
        get() = LARGE_CONTENT_SCALE

    override val showShiftLabel: Boolean
        get() = true

    private companion object {
        /** 大字版缩放：日期 16sp→34sp、班次字 11sp→23sp（组件固定在 2×4，132dp 高，再大格子就装不下两行） */
        const val LARGE_CONTENT_SCALE = 2.1f
    }
}
