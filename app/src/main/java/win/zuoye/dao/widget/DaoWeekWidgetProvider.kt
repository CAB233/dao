package win.zuoye.dao.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import win.zuoye.dao.MainActivity
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanRepository
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.defaultGroup
import win.zuoye.dao.domain.MonthCell
import win.zuoye.dao.domain.Roster
import win.zuoye.dao.domain.weekStartOf

/**
 * 「**本周班次**」桌面小组件：头部（月份 + 星期表头）固定，下面一行 7 天，靠 ↑↓ 翻页换周。
 *
 * 与「倒班月历」是两个独立的小组件，用户自己在选择器里挑 —— 一个看整月，一个盯本周。
 *
 * **为什么是翻页而不是滚动**：RemoteViews 收不到自定义手势，能滑的只有 ListView 这类集合控件， 而 ListView
 * **没有吸附**，滑完会停在两行之间（实测过，体验不行）。`ViewFlipper` 一页就是一整行， 翻页由点击驱动；它的子视图是**一次性 addView
 * 进去的**，`setDisplayedChild` 立刻生效， 每次重画也都会按 widget 当前尺寸重算格子大小 —— 缩放后表头和格子不会再对不上。
 *
 * 当前停在第几页存在 [WidgetPageStore]（相对今天那一页的偏移，跨日/跨月都自动跟着走）。
 */
class DaoWeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val document = PlanRepository.get(context).document.first()
                val today = Ymd.today()
                appWidgetIds.forEach { id ->
                    // 不吞异常：RemoteViews 里放了非法控件/方法时是运行时才炸的
                    // （踩过：HorizontalScrollView 不在白名单里，宿主会一直停在加载占位图）
                    appWidgetManager.updateAppWidget(id, buildViews(context, document, today, id))
                }
                scheduleNextRefresh(context)
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to update the week widget", error)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // 列宽/行高都是按 widget 尺寸算的，拉伸后必须重画
        refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val pages = WidgetPageStore(context, WidgetPageStore.KEY_WEEK)
        when (intent.action) {
            ACTION_PAGE_UP -> {
                pages.shift(-1)
                refreshNow(context)
            }
            ACTION_PAGE_DOWN -> {
                pages.shift(1)
                refreshNow(context)
            }
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> refreshNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        refreshNow(context)
    }

    companion object {

        private const val REQUEST_CODE_REFRESH = 2001
        private const val REQUEST_CODE_OPEN = 2002
        private const val REQUEST_CODE_PAGE_UP = 2003
        private const val REQUEST_CODE_PAGE_DOWN = 2004
        private const val TAG = "DaoWeekWidget"
        /**
         * 跨日刷新的分钟数：比月历那个（00:03）早两分钟，两个组件错开 —— Doze 下同一个 App 每 9 分钟只放行一个 alarm，撞在同一分钟会有一个被推迟到很久以后。
         * 也不能排 23:59：那一刻 `Ymd.today()` 还是前一天。
         */
        private const val MIDNIGHT_MINUTE = 1

        const val ACTION_REFRESH = "win.zuoye.dao.action.WEEK_WIDGET_REFRESH"
        const val ACTION_PAGE_UP = "win.zuoye.dao.action.WEEK_WIDGET_PAGE_UP"
        const val ACTION_PAGE_DOWN = "win.zuoye.dao.action.WEEK_WIDGET_PAGE_DOWN"

        /** 请求重新读取数据并重画所有已放置的「本周班次」小组件。 */
        fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids =
                manager.getAppWidgetIds(ComponentName(context, DaoWeekWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, DaoWeekWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }

        internal fun buildViews(
            context: Context,
            document: PlanDocument,
            today: Ymd,
            appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        ): RemoteViews {
            val roster = Roster.of(document)
            val anchorEpochDay = document.activeScheme()?.defaultGroup()?.anchorEpochDay
            val columnWidthPx = widgetColumnWidthPx(context, appWidgetId)
            val rowHeightPx = widgetStripHeightPx(context, appWidgetId)

            // 页偏移是**相对今天那周**的，所以跨日之后没翻过就还是"今天这周"
            val pageOffset = WidgetPageStore(context, WidgetPageStore.KEY_WEEK).read()
            val anchorWeekStart = weekStartOf(today.epochDay, document.weekStartDay)
            val shownWeekStart = anchorWeekStart + pageOffset.toLong() * DAYS_PER_WEEK
            // 标题写哪个月：停在本周时写"今天所在月"，跨月的那一周（如 9/27–10/3）才不会显示成上个月；
            // 翻到别周时没有"今天"可依据，就写那一周起始日所在的月。
            val shown = if (pageOffset == 0) today else Ymd.fromEpochDay(shownWeekStart)

            return RemoteViews(context.packageName, R.layout.widget_week_view).apply {
                setTextViewText(
                    R.id.week_widget_month,
                    context.getString(R.string.widget_month_title, shown.year, shown.month),
                )
                setOnClickPendingIntent(R.id.week_widget_root, openAppIntent(context))
                setOnClickPendingIntent(
                    R.id.week_widget_page_up,
                    pagePendingIntent(context, ACTION_PAGE_UP, REQUEST_CODE_PAGE_UP),
                )
                setOnClickPendingIntent(
                    R.id.week_widget_page_down,
                    pagePendingIntent(context, ACTION_PAGE_DOWN, REQUEST_CODE_PAGE_DOWN),
                )

                // 星期表头：不随日期行翻页；列宽与日期格用同一个显式值
                removeAllViews(R.id.week_widget_weekdays)
                val labels = context.resources.getStringArray(R.array.weekday_short)
                repeat(DAYS_PER_WEEK.toInt()) { column ->
                    val weekdayIndex = (document.weekStartDay + column) % DAYS_PER_WEEK.toInt()
                    addView(
                        R.id.week_widget_weekdays,
                        RemoteViews(context.packageName, R.layout.widget_weekday_header)
                            .apply { setTextViewText(R.id.weekday_header, labels[weekdayIndex]) }
                            .applyColumnWidth(R.id.weekday_header, columnWidthPx),
                    )
                }

                // 一周一页，一次全部下发；setDisplayedChild 立刻生效（不经过 adapter）
                removeAllViews(R.id.week_widget_pager)
                (-WidgetPageStore.MAX_OFFSET..WidgetPageStore.MAX_OFFSET).forEach { delta ->
                    addView(
                        R.id.week_widget_pager,
                        weekPageViews(
                            context = context,
                            weekStart = anchorWeekStart + delta.toLong() * DAYS_PER_WEEK,
                            today = today,
                            roster = roster,
                            anchorEpochDay = anchorEpochDay,
                            columnWidthPx = columnWidthPx,
                            rowHeightPx = rowHeightPx,
                        ),
                    )
                }
                setDisplayedChild(
                    R.id.week_widget_pager,
                    pageOffset + WidgetPageStore.MAX_OFFSET,
                )
            }
        }

        private fun refreshPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REFRESH,
                Intent(context, DaoWeekWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** 翻页按钮的 PendingIntent；requestCode 不同才不会互相覆盖。 */
        private fun pagePendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, DaoWeekWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /**
         * 跨日时重排一次：页偏移以"今天那周"为基准，过了午夜基准就变了。
         *
         * 用 `setAndAllowWhileIdle` 而不是 `set`：夜里手机静止会进 Doze，普通闹钟**会被推迟到维护窗口**
         * （常常是早上），跨日刷新会丢，小组件的"今天"就停在昨天（用户报过）。 这个 API 在 Doze 下也能按点触发，且不需要 SCHEDULE_EXACT_ALARM 权限。
         */
        private fun scheduleNextRefresh(context: Context) {
            val triggerAt =
                Calendar.getInstance()
                    .apply {
                        add(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, MIDNIGHT_MINUTE)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    .timeInMillis
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                refreshPendingIntent(context),
            )
        }

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/**
 * 一页 = 一周（7 格），交给调用方 addView 到 ViewFlipper 里。
 *
 * 行宽 = 7 × 列宽、高度都显式给：嵌套 RemoteViews 测量时父容器给的是 wrap_content， `layout_weight` 算不出等分（踩过：每格退化成一个字的宽度）。
 */
internal fun weekPageViews(
    context: Context,
    weekStart: Long,
    today: Ymd,
    roster: Roster,
    anchorEpochDay: Long?,
    columnWidthPx: Int?,
    rowHeightPx: Int?,
): RemoteViews {
    val strip = RemoteViews(context.packageName, R.layout.widget_week_strip)
    repeat(DAYS_PER_WEEK.toInt()) { column ->
        strip.addView(
            R.id.week_strip,
            DaoWidgetProvider.dayCellViews(
                    context = context,
                    cell =
                        MonthCell(
                            epochDay = weekStart + column,
                            // 一行视图里没有"上下月补位"的概念，每天都按自己的班次正常着色
                            inCurrentMonth = true,
                        ),
                    today = today,
                    roster = roster,
                    anchorEpochDay = anchorEpochDay,
                )
                .applyCellBox(
                    R.id.day_cell,
                    columnWidthPx,
                    rowHeightPx,
                    context.resources.displayMetrics.density,
                ),
        )
    }
    strip.applyCellSize(R.id.week_strip, columnWidthPx?.times(DAYS_PER_WEEK.toInt()), rowHeightPx)
    return strip
}

private const val DAYS_PER_WEEK = 7L
