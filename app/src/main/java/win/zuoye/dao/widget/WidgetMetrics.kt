package win.zuoye.dao.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import kotlin.math.roundToInt
import win.zuoye.dao.domain.MonthGrid

/**
 * 小组件里所有"要跟着 widget 尺寸变"的数字都在这儿算：列宽、行高。
 *
 * 为什么要显式算，而不是交给 `layout_weight`：格子和行都是**嵌套的 RemoteViews** （`addView` 进来的），它们测量时父容器给的是
 * wrap_content，等分算不出来 —— 实测每格会退化成一个字的 宽度（21px），整行挤在左边。所以列宽、行高都按 widget 的实际尺寸算好，用
 * `setViewLayoutWidth` / `setViewLayoutHeight`（**API 31+**）显式下发；低版本退回布局里的固定 dp。
 *
 * 尺寸取自 `AppWidgetManager.getAppWidgetOptions`，用户拉伸小组件会触发 `onAppWidgetOptionsChanged` →
 * 重画，于是格子跟着变大变小（这也是"能缩放"的关键：内容不再把 widget 撑爆）。
 */
internal fun widgetColumnWidthPx(
    context: Context,
    appWidgetId: Int,
): Int? {
    val widthDp =
        widgetSizeDp(
            context,
            appWidgetId,
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        )
    if (widthDp <= 0) return null

    val contentDp = widthDp - WIDGET_HORIZONTAL_PADDING_DP * 2
    return ((contentDp.toFloat() / MonthGrid.COLUMNS) * context.resources.displayMetrics.density)
        .roundToInt()
}

/**
 * 日期格自己的尺寸 = 列宽/行高各减掉 [WIDGET_CELL_GAP_DP]（四边都向里缩）。
 *
 * 格子是**描边**的，尺寸取满时一排框会首尾相接、上下也顶满，看着糊成一片； 缩进一圈之后每个日子才是一个独立的小块，中间的缝就是卡片底色。 表头仍用满列宽（文字居中，差这几像素看不出来）。
 */
internal fun RemoteViews.applyCellBox(
    viewId: Int,
    columnWidthPx: Int?,
    rowHeightPx: Int?,
    density: Float,
): RemoteViews = apply {
    val gap = (WIDGET_CELL_GAP_DP * density).roundToInt()
    columnWidthPx?.let {
        if (it > gap) {
            setViewLayoutWidth(viewId, (it - gap).toFloat(), TypedValue.COMPLEX_UNIT_PX)
        }
    }
    rowHeightPx?.let {
        if (it > gap) {
            setViewLayoutHeight(viewId, (it - gap).toFloat(), TypedValue.COMPLEX_UNIT_PX)
        }
    }
}

/**
 * 每一行的高度：拿 widget 的高度减掉标题/表头/状态行，再按 6 行等分。
 *
 * 留了一个下限（[MIN_ROW_HEIGHT_DP]）：再挤下去两行字就叠在一起了，宁可让内容被裁一点。
 */
internal fun widgetRowHeightPx(
    context: Context,
    appWidgetId: Int,
): Int? {
    val heightDp =
        widgetSizeDp(
            context,
            appWidgetId,
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        )
    if (heightDp <= 0) return null

    val gridDp = heightDp - WIDGET_CHROME_HEIGHT_DP
    val rowDp = (gridDp.toFloat() / MonthGrid.ROWS).coerceAtLeast(MIN_ROW_HEIGHT_DP.toFloat())
    return (rowDp * context.resources.displayMetrics.density).roundToInt()
}

/**
 * 「本周班次」小组件里一项（一行 7 天）的高度：整块 widget 减掉上下 padding。
 *
 * 必须显式给：ListView 的 item 用 `match_parent` 只会被当成 wrap_content，一行撑不满 widget 高度时
 * 列表会一次露出三四行（实测踩过），而那个小组件的意义就是"只有一行"。
 */
internal fun widgetStripHeightPx(
    context: Context,
    appWidgetId: Int,
): Int? {
    val heightDp =
        widgetSizeDp(
            context,
            appWidgetId,
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        )
    if (heightDp <= 0) return null

    val contentDp = heightDp - WIDGET_VERTICAL_PADDING_DP * 2 - WIDGET_WEEK_HEADER_DP
    return (contentDp * context.resources.displayMetrics.density).roundToInt()
}

/**
 * 从 widget 的 options 里取尺寸（dp）：**按顺序取第一个有值的 key**。
 *
 * 调用方一律「竖屏的 key 在前、横屏的在后」：竖屏/横屏是两套约定（`MIN_WIDTH`/`MAX_HEIGHT` 与
 * `MAX_WIDTH`/`MIN_HEIGHT`），**不能取最大值** —— 横屏那套数值更大，混进来会把格子撑出屏幕 （踩过：一行只显示得下 4 天）。但也不能只认一个
 * key：线上真机遇到过 `MAX_HEIGHT` 报 0 而宽度 key 有值的 Launcher，那时必须能退回另一个，否则尺寸全算不出来、内容塌成一条。
 *
 * 返回 0 表示都拿不到（调用方会 `null` 掉尺寸，退回布局里的固定 dp）。
 */
private fun widgetSizeDp(
    context: Context,
    appWidgetId: Int,
    vararg options: String,
): Int {
    if (!canSetExplicitWidth()) return 0
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
        Log.w(TAG, "no appWidgetId, falling back to the layout's fixed sizes")
        return 0
    }
    val bundle = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val values = options.map { bundle.getInt(it) }
    val size = values.firstOrNull { it > 0 } ?: 0
    if (size <= 0) {
        Log.w(TAG, "widget $appWidgetId has no usable size in ${options.toList()}: $values")
    }
    return size
}

/** 给一个格子/表头设置显式宽度；拿不到宽度（API < 31）时保留布局里的固定 dp。 */
internal fun RemoteViews.applyColumnWidth(viewId: Int, widthPx: Int?): RemoteViews = apply {
    if (widthPx != null && widthPx > 0) {
        setViewLayoutWidth(viewId, widthPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
    }
}

/** 给一整行/一整块设置显式宽高；同上，拿不到尺寸就不设。 */
internal fun RemoteViews.applyCellSize(
    viewId: Int,
    widthPx: Int?,
    heightPx: Int?,
): RemoteViews = apply {
    if (widthPx != null && widthPx > 0) {
        setViewLayoutWidth(viewId, widthPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
    }
    if (heightPx != null && heightPx > 0) {
        setViewLayoutHeight(viewId, heightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
    }
}

/** `setViewLayoutWidth` / `setViewLayoutHeight` 都是 API 31 的方法，低版本调用直接抛异常。 */
internal fun canSetExplicitWidth(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** `widget_month.xml` 的 paddingStart / paddingEnd */
private const val WIDGET_HORIZONTAL_PADDING_DP = 8

/**
 * 标题行 + 星期表头 + 底部状态行 + 内外边距，留给网格之外的高度。
 *
 * 这个值是**照着实机实测校准**的：`getAppWidgetOptions` 报的高度与 Launcher 实际分配的差一截， 按布局里各行的名义高度加起来（约 90dp）算会偏大，一个月的
 * item 就比列表矮 40px 左右、 底部会露出下个月的一条（踩过）。改布局的间距/字号要一起重新校准。
 */
private const val WIDGET_CHROME_HEIGHT_DP = 76

/** 一行至少这么高，否则日期与班次名会叠在一起 */
private const val MIN_ROW_HEIGHT_DP = 24

/** `widget_week_view.xml` 的 paddingTop / paddingBottom */
private const val WIDGET_VERTICAL_PADDING_DP = 4

/** 日期格之间的横向缝隙（格子宽度 = 列宽 − 这个值） */
private const val WIDGET_CELL_GAP_DP = 6

/**
 * 「本周班次」头部（月份 + 星期表头）占掉的高度，日期行要减掉它。
 *
 * 和 [WIDGET_CHROME_HEIGHT_DP] 一样是**照着实测校准**的：按布局里的名义高度（月份 13sp + 星期 11sp 两行约 37dp）
 * 算会偏大，日期行矮一截、底部就漏出下一周的一条（踩过）。改布局要重新校准。
 */
private const val WIDGET_WEEK_HEADER_DP = 42

private const val TAG = "DaoWidgetProvider"
