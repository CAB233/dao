package win.zuoye.dao.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import win.zuoye.dao.MainActivity
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanRepository
import win.zuoye.dao.data.Ymd

object RosterWidgets {
    internal const val ACTION_REFRESH = "win.zuoye.dao.widget.REFRESH"
    internal const val ACTION_PREVIOUS = "win.zuoye.dao.widget.PREVIOUS"
    internal const val ACTION_NEXT = "win.zuoye.dao.widget.NEXT"
    private val mutex = Mutex()
    private const val PREFERENCES = "roster_widgets"

    /** 排班、主题和日历设置保存后，通知已有小组件重新读取同一个 DataStore。 */
    fun requestRefresh(context: Context) {
        if (widgetIds(context).values.all { it.isEmpty() }) return
        context.sendBroadcast(
            Intent(context, WeekRosterWidgetProvider::class.java).setAction(ACTION_REFRESH)
        )
    }

    internal suspend fun updateAll(context: Context) = mutex.withLock {
        val ids = widgetIds(context)
        if (ids.values.any { it.isNotEmpty() }) {
            val document = PlanRepository.get(context).document.first()
            ids.forEach { (kind, values) -> updateViews(context, document, kind, values) }
        }
        scheduleNextDay(context)
    }

    internal suspend fun update(context: Context, kind: RosterWidgetKind, ids: IntArray) =
        mutex.withLock {
            if (ids.isNotEmpty())
                updateViews(context, PlanRepository.get(context).document.first(), kind, ids)
            scheduleNextDay(context)
        }

    internal suspend fun navigate(context: Context, id: Int, delta: Int) = mutex.withLock {
        val today = Ymd.today()
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = "month_$id"
        val month = moveWidgetMonth(preferences.getInt(key, today.widgetMonth()), delta)
        preferences.edit { if (month == today.widgetMonth()) remove(key) else putInt(key, month) }
        updateViews(
            context,
            PlanRepository.get(context).document.first(),
            RosterWidgetKind.MONTH,
            intArrayOf(id),
        )
    }

    internal fun remove(context: Context, ids: IntArray) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            ids.forEach { remove("month_$it") }
        }
    }

    internal fun restore(context: Context, oldIds: IntArray, newIds: IntArray) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val values = oldIds.map { id ->
            preferences.takeIf { it.contains("month_$id") }?.getInt("month_$id", 0)
        }
        preferences.edit {
            oldIds.forEach { remove("month_$it") }
            newIds.forEachIndexed { index, id ->
                values.getOrNull(index)?.let { putInt("month_$id", it) }
            }
        }
    }

    internal fun scheduleNextDay(context: Context) {
        setWidgetDayAlarm(context, widgetIds(context).values.any { it.isNotEmpty() })
    }

    private fun widgetIds(context: Context): Map<RosterWidgetKind, IntArray> {
        val manager = AppWidgetManager.getInstance(context)
        return mapOf(
            RosterWidgetKind.WEEK to
                manager.getAppWidgetIds(
                    ComponentName(context, WeekRosterWidgetProvider::class.java)
                ),
            RosterWidgetKind.MONTH to
                manager.getAppWidgetIds(
                    ComponentName(context, MonthRosterWidgetProvider::class.java)
                ),
        )
    }

    private fun updateViews(
        context: Context,
        document: PlanDocument,
        kind: RosterWidgetKind,
        ids: IntArray,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val today = Ymd.today()
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        ids.forEach { id ->
            val month =
                preferences
                    .getInt("month_$id", today.widgetMonth())
                    .coerceIn(FIRST_WIDGET_MONTH, LAST_WIDGET_MONTH)
            val options = manager.getAppWidgetOptions(id)
            val fallbackHeight = if (kind == RosterWidgetKind.WEEK) 90f else 320f
            val portrait =
                SizeF(
                    options
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
                        .takeIf { it > 0 }
                        ?.toFloat() ?: 300f,
                    options
                        .getInt(
                            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                            fallbackHeight.toInt(),
                        )
                        .takeIf { it > 0 }
                        ?.toFloat() ?: fallbackHeight,
                )
            val landscape =
                SizeF(
                    options
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, portrait.width.toInt())
                        .takeIf { it > 0 }
                        ?.toFloat() ?: portrait.width,
                    options
                        .getInt(
                            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                            fallbackHeight.toInt(),
                        )
                        .takeIf { it > 0 }
                        ?.toFloat() ?: fallbackHeight,
                )
            val exactSizes =
                if (Build.VERSION.SDK_INT >= 31)
                    BundleCompat.getParcelableArrayList(
                            options,
                            AppWidgetManager.OPTION_APPWIDGET_SIZES,
                            SizeF::class.java,
                        )
                        ?.filter { it.width > 0 && it.height > 0 }
                        ?.distinct()
                        ?.take(4)
                        .orEmpty()
                else emptyList()
            val sizes = exactSizes.ifEmpty { listOf(portrait, landscape).distinct() }
            // 整个更新的位图合计最多约 2MB，控制 Binder 与桌面缓存占用。
            val scale =
                minOf(
                    context.resources.displayMetrics.density,
                    2f,
                    sqrt(500_000f / sizes.sumOf { (it.width * it.height).toDouble() }.toFloat()),
                )
            val views = sizes.associateWith { size ->
                createViews(context, document, kind, id, today, month, size, scale)
            }
            val remoteViews =
                if (Build.VERSION.SDK_INT >= 31 && exactSizes.isNotEmpty()) RemoteViews(views)
                else if (sizes.size == 1) views.getValue(portrait)
                else RemoteViews(views.getValue(landscape), views.getValue(portrait))
            manager.updateAppWidget(id, remoteViews)
        }
    }

    private fun createViews(
        context: Context,
        document: PlanDocument,
        kind: RosterWidgetKind,
        id: Int,
        today: Ymd,
        month: Int,
        size: SizeF,
        scale: Float,
    ): RemoteViews {
        val rendering = renderRosterWidget(context, document, kind, today, month, size, scale)
        return RemoteViews(context.packageName, R.layout.roster_widget).apply {
            setImageViewBitmap(R.id.widget_calendar, rendering.bitmap)
            setViewVisibility(R.id.widget_loading, View.GONE)
            setContentDescription(R.id.widget_calendar, rendering.description)
            val openApp =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            setOnClickPendingIntent(R.id.widget_calendar, openApp)
            setViewVisibility(
                R.id.widget_month_controls,
                if (kind == RosterWidgetKind.MONTH) View.VISIBLE else View.GONE,
            )
            setTextColor(R.id.widget_previous, rendering.actionColor)
            setTextColor(R.id.widget_next, rendering.actionColor)
            setBoolean(R.id.widget_previous, "setEnabled", month > FIRST_WIDGET_MONTH)
            setBoolean(R.id.widget_next, "setEnabled", month < LAST_WIDGET_MONTH)
            setOnClickPendingIntent(
                R.id.widget_previous,
                widgetBroadcast(context, id, ACTION_PREVIOUS),
            )
            setOnClickPendingIntent(R.id.widget_next, widgetBroadcast(context, id, ACTION_NEXT))
        }
    }
}
