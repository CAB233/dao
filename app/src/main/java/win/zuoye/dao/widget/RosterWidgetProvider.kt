package win.zuoye.dao.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class WeekRosterWidgetProvider : RosterWidgetProvider(RosterWidgetKind.WEEK)

class MonthRosterWidgetProvider : RosterWidgetProvider(RosterWidgetKind.MONTH)

/** 广播期间保留 PendingResult，数据读取与位图绘制在后台完成。 */
abstract class RosterWidgetProvider(private val kind: RosterWidgetKind) : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RosterWidgets.ACTION_PREVIOUS,
            RosterWidgets.ACTION_NEXT -> {
                val id =
                    intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID,
                    )
                val manager = AppWidgetManager.getInstance(context)
                if (manager.getAppWidgetInfo(id)?.provider != ComponentName(context, javaClass))
                    return
                val navigationKind =
                    RosterWidgetKind.entries.firstOrNull {
                        it.name == intent.getStringExtra(RosterWidgets.EXTRA_NAVIGATION_KIND)
                    } ?: kind
                updateAsync {
                    RosterWidgets.navigate(
                        context,
                        kind,
                        id,
                        if (intent.action == RosterWidgets.ACTION_PREVIOUS) -1 else 1,
                        navigationKind,
                    )
                }
            }
            RosterWidgets.ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> updateAsync { RosterWidgets.updateAll(context) }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAsync { RosterWidgets.update(context, kind, ids) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        updateAsync { RosterWidgets.update(context, kind, intArrayOf(id)) }
    }

    override fun onEnabled(context: Context) {
        RosterWidgets.scheduleNextDay(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        RosterWidgets.remove(context, ids)
    }

    override fun onDisabled(context: Context) {
        RosterWidgets.scheduleNextDay(context)
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        RosterWidgets.restore(context, oldIds, newIds)
        if (Build.VERSION.SDK_INT >= 30) {
            val manager = AppWidgetManager.getInstance(context)
            newIds.forEach { id ->
                manager.updateAppWidgetOptions(
                    id,
                    Bundle().apply {
                        putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                    },
                )
            }
        }
    }

    private fun updateAsync(block: suspend () -> Unit) {
        val result = goAsync()
        scope.launch {
            try {
                withTimeout(9_000L) { block() }
            } catch (error: Exception) {
                Log.e("RosterWidget", "Unable to update widget", error)
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** 每日刷新使用系统普通闹钟；桌面周期更新继续负责省电模式下的补刷新。 */
internal fun nextWidgetDayMillis(): Long =
    Calendar.getInstance()
        .apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        .timeInMillis

internal fun widgetBroadcast(
    context: Context,
    kind: RosterWidgetKind,
    navigationKind: RosterWidgetKind,
    id: Int,
    action: String,
): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        0,
        Intent(
                context,
                if (kind == RosterWidgetKind.WEEK) WeekRosterWidgetProvider::class.java
                else MonthRosterWidgetProvider::class.java,
            )
            .setAction(action)
            .setData("dao-widget://${kind.name}/${navigationKind.name}/$id/$action".toUri())
            .putExtra(RosterWidgets.EXTRA_NAVIGATION_KIND, navigationKind.name)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun midnightWidgetAlarm(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WeekRosterWidgetProvider::class.java)
            .setAction(RosterWidgets.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun setWidgetDayAlarm(context: Context, enabled: Boolean) {
    val alarm = context.getSystemService(AlarmManager::class.java)
    val intent = midnightWidgetAlarm(context)
    if (enabled) alarm.set(AlarmManager.RTC, nextWidgetDayMillis(), intent)
    else alarm.cancel(intent)
}
