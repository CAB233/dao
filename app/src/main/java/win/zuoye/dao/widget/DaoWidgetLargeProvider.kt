package win.zuoye.dao.widget

/**
 * 「倒班月历 · 大字」：与 DaoWidgetProvider 同一套布局，整套字号固定放大一档。
 *
 * 同 DaoWeekWidgetLargeProvider 的理由：vivo 的 Launcher 不提供可信的组件尺寸， 按尺寸算字号的方案走不通，只能用两个固定档位的组件。
 */
class DaoWidgetLargeProvider : DaoWidgetProvider() {

    override val contentScale: Float
        get() = LARGE_CONTENT_SCALE

    private companion object {
        /** 大字版缩放：标题 15sp→24sp、格子里中文说明 8sp→13sp */
        const val LARGE_CONTENT_SCALE = 1.6f
    }
}
