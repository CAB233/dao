package win.zuoye.dao.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Immutable
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import win.zuoye.dao.MainActivity
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanRepository
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.defaultGroup
import win.zuoye.dao.data.minuteOfDay
import win.zuoye.dao.domain.DayVisualState
import win.zuoye.dao.domain.MonthCell
import win.zuoye.dao.domain.MonthGrid
import win.zuoye.dao.domain.Roster
import win.zuoye.dao.domain.ShiftEndMoment
import win.zuoye.dao.domain.ShiftPhase
import win.zuoye.dao.domain.formatShiftEnd
import win.zuoye.dao.domain.hasCarriedOverNightShift
import win.zuoye.dao.domain.monthWithDelta
import win.zuoye.dao.domain.nextShiftBoundaryMinute
import win.zuoye.dao.domain.previousDayTemplate
import win.zuoye.dao.domain.todayRoster
import win.zuoye.dao.ui.ShiftPalette

/**
 * 「倒班月历」桌面小组件：整月网格，每格显示日期与该日班次名，底色是班次色。
 *
 * 用 RemoteViews 而不是 Glance：项目本来就没有 material3 依赖，Glance 会为一个小部件带进整套 Compose for RemoteViews 与
 * Material 3 主题；这里只是几十个纯色文字格子，系统自带的 RemoteViews 足够。
 *
 * 数据直接读 [PlanRepository] 的那份 `plan.json`，与 App 内是同一份，不需要额外持久化或同步。
 *
 * **RemoteViews 的 action 是运行时才校验的**：方法必须真实存在于目标控件的类型上（`@RemotableViewMethod`）， 写错照样编译通过，只在宿主
 * `apply()` 时抛 `ActionException`，界面就一直停在加载占位图。 改完请跑 `DaoWidgetProviderTest`——Robolectric 会真的
 * inflate 并 apply 一遍，编译通过不代表能显示。
 */
class DaoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // goAsync 让进程在 onReceive 返回后继续存活，异步读完 plan.json 再画
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val document = PlanRepository.get(context).document.first()
                appWidgetIds.forEach { id ->
                    // 不吞异常：RemoteViews 里放了非 remotable 的方法是运行时才炸的
                    // （踩过：setColorFilter 打在 TextView 上），吞掉只会看到宿主一直停在加载占位图。
                    appWidgetManager.updateAppWidget(
                        id,
                        buildViews(context, document, refreshPendingIntent(context), id),
                    )
                }
                // 日历是直接 addView 出来的（没有 RemoteViewsService），一次 updateAppWidget 就够
                scheduleNextRefresh(context, document)
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to update the shift widget", error)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        cancelScheduledRefresh(context)
    }

    /** 用户拉伸小组件：列宽是按 widget 宽度算出来的，尺寸一变必须重画，否则列宽还停在旧值上。 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refreshNow(context)
    }

    /**
     * 日期/时间/时区变化、开机、换包 → 重算。
     *
     * 翻月已经改成**手指上下滚动**（月份列表由 `DaoMonthStripService` 提供）， 这里不再需要翻月/回今月的动作 —— 往上滚到顶就是本月。
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val pages = WidgetPageStore(context, WidgetPageStore.KEY_MONTH)
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

    companion object {

        private const val REQUEST_CODE_REFRESH = 1001
        private const val REQUEST_CODE_OPEN = 1002
        private const val REQUEST_CODE_PAGE_UP = 1003
        private const val REQUEST_CODE_PAGE_DOWN = 1004
        /**
         * 跨日刷新的余量：午夜**之后** 3 分钟。
         *
         * 不能排 23:59（那一刻 `Ymd.today()` 还是前一天，刷了等于没刷），也不能卡 00:00 整 —— `setAndAllowWhileIdle` 是非精确闹钟、且
         * RTC 依赖墙上时钟，万一落在 23:59:59 就白刷一次。 月历和单行两个组件必须错开时刻：Doze 下同一个 App 每 9 分钟只放行一个 alarm。
         */
        private const val MIDNIGHT_GRACE_MS = 3 * 60_000L

        /** 描边与文字相对色板原色的压暗/提亮比例 */
        private const val OUTLINE_SHIFT = 0.30f

        private const val TAG = "DaoWidgetProvider"

        const val ACTION_REFRESH = "win.zuoye.dao.action.WIDGET_REFRESH"
        const val ACTION_PAGE_UP = "win.zuoye.dao.action.WIDGET_PAGE_UP"
        const val ACTION_PAGE_DOWN = "win.zuoye.dao.action.WIDGET_PAGE_DOWN"

        /** 请求重新读取数据并重画所有已放置的小组件。 */
        fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DaoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, DaoWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }

        private fun broadcastIntent(context: Context, action: String): Intent =
            Intent(context, DaoWidgetProvider::class.java).setAction(action)

        private fun refreshPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REFRESH,
                broadcastIntent(context, ACTION_REFRESH),
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
                broadcastIntent(context, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /**
         * 排下一次刷新：**班次边界和次日 0:05 谁先到就按谁**。
         *
         * 两个都不能少：
         * - 只按班次边界排，遇到"今天已经下班、明天 08:00 才有班"时，跨午夜那一刻不会刷新， 小组件的"今天"就停在昨天（用户报过：10 月 1 号了还显示 9/30）。
         * - 只按午夜排，底部那行「此刻在不在上班」在班次起止的那一刻就不准。
         *
         * 用 `setAndAllowWhileIdle` 而不是 `set`：夜里手机静止会进 Doze，普通闹钟**会被推迟到维护窗口** （常常是早上），跨日刷新同样会丢。这个
         * API 在 Doze 下也能按点触发，且不需要 SCHEDULE_EXACT_ALARM 权限。
         */
        private fun scheduleNextRefresh(context: Context, document: PlanDocument) {
            val now = System.currentTimeMillis()
            val currentMinute = minuteOfDay(now)
            val midnightAlarm = nextMidnight(now) + MIDNIGHT_GRACE_MS
            val boundaryMinute = nextShiftBoundaryMinute(document, currentMinute)
            val boundaryAlarm =
                if (boundaryMinute != null && boundaryMinute > currentMinute) {
                    now + (boundaryMinute - currentMinute) * 60_000L
                } else {
                    Long.MAX_VALUE
                }

            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                minOf(boundaryAlarm, midnightAlarm),
                refreshPendingIntent(context),
            )
        }

        private fun cancelScheduledRefresh(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(refreshPendingIntent(context))
        }

        private fun nextMidnight(now: Long): Long =
            Calendar.getInstance()
                .apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                .timeInMillis

        /** 组装整个小组件的 RemoteViews。 */
        internal fun buildViews(
            context: Context,
            document: PlanDocument,
            refreshIntent: PendingIntent?,
            appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        ): RemoteViews {
            val now = System.currentTimeMillis()
            val today = Ymd.of(now)
            val roster = Roster.of(document)
            val anchorEpochDay = document.activeScheme()?.defaultGroup()?.anchorEpochDay

            val views = RemoteViews(context.packageName, R.layout.widget_month)
            // 翻页状态存的是**相对今月的偏移**（SharedPreferences），所以跨月后没翻过就还是今月；
            // 页索引由我们自己记着，标题因此能跟着翻页走（滚动那条路做不到这点）。
            val pageOffset = WidgetPageStore(context, WidgetPageStore.KEY_MONTH).read()
            val shownMonth = monthWithDelta(today, pageOffset)
            views.setTextViewText(
                R.id.widget_title,
                context.getString(
                    R.string.widget_month_title,
                    shownMonth.year,
                    shownMonth.month,
                ),
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            if (refreshIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent)
            }
            views.setOnClickPendingIntent(
                R.id.widget_page_up,
                pagePendingIntent(context, ACTION_PAGE_UP, REQUEST_CODE_PAGE_UP),
            )
            views.setOnClickPendingIntent(
                R.id.widget_page_down,
                pagePendingIntent(context, ACTION_PAGE_DOWN, REQUEST_CODE_PAGE_DOWN),
            )

            // 星期表头：按「一周开始日」旋转；列宽与日期格用同一个显式值
            // （`layout_weight` 在被 addView 进来的嵌套 RemoteViews 里不可靠，实测会退化成一个字的宽）
            val columnWidthPx = widgetColumnWidthPx(context, appWidgetId)
            views.removeAllViews(R.id.widget_weekdays)
            val labels = context.resources.getStringArray(R.array.weekday_short)
            repeat(7) { column ->
                val weekdayIndex = (document.weekStartDay + column) % 7
                views.addView(
                    R.id.widget_weekdays,
                    RemoteViews(context.packageName, R.layout.widget_weekday_header)
                        .apply { setTextViewText(R.id.weekday_header, labels[weekdayIndex]) }
                        .applyColumnWidth(R.id.weekday_header, columnWidthPx),
                )
            }

            // 月份页：一页一个整月，靠标题右侧的 ↑↓ 翻（ViewFlipper 的子视图是一次性下发的，
            // 所以 setDisplayedChild 立刻生效，不像 ListView 那样依赖异步 adapter）。
            views.removeAllViews(R.id.widget_month_pager)
            (-WidgetPageStore.MAX_OFFSET..WidgetPageStore.MAX_OFFSET).forEach { delta ->
                views.addView(
                    R.id.widget_month_pager,
                    monthPageViews(
                        context = context,
                        month = monthWithDelta(today, delta),
                        weekStartDay = document.weekStartDay,
                        today = today,
                        roster = roster,
                        anchorEpochDay = anchorEpochDay,
                        columnWidthPx = columnWidthPx,
                        rowHeightPx = widgetRowHeightPx(context, appWidgetId),
                    ),
                )
            }
            views.setDisplayedChild(
                R.id.widget_month_pager,
                pageOffset + WidgetPageStore.MAX_OFFSET,
            )

            views.setTextViewText(
                R.id.widget_phase,
                phaseText(context, document, today, minuteOfDay(now)),
            )
            return views
        }

        /**
         * 一格。状态判定与 App 内月历共用 [DayVisualState]（见 `domain/DayVisualState.kt`）：
         * - **今天**：班次色**实心**（最醒目，一眼找到今天）+ 对比色文字
         * - **有班次的其他日子**：**班次色描边 + 班次色文字**（不给实心底，整月看下来不刺眼）
         * - **已过去**：同上，但描边与文字都走消色版
         * - **没排班**：浅灰底；**上/下月补位**：更浅的灰 + 三级文字色
         */
        internal fun dayCellViews(
            context: Context,
            cell: MonthCell,
            today: Ymd,
            roster: Roster,
            anchorEpochDay: Long?,
        ): RemoteViews {
            val epochDay = cell.epochDay
            val ymd = Ymd.fromEpochDay(epochDay)
            val template = anchorEpochDay?.let { roster.templateFor(epochDay, it) }
            val state = DayVisualState.of(cell, today, template)
            val shiftArgb = template?.colorArgb

            val views = RemoteViews(context.packageName, R.layout.widget_day_cell)
            views.setTextViewText(R.id.day_number, ymd.day.toString())
            views.setTextViewText(R.id.day_shift, template?.name.orEmpty())

            val primary = context.getColor(R.color.widget_text_primary)
            val secondary = context.getColor(R.color.widget_text_secondary)
            val tertiary = context.getColor(R.color.widget_text_tertiary)

            // 文字与框同色：今天用实心底所以取对比色，其余用班次色本身。
            // 已过去的日子**不再淡化**：消色版是给"实心底"设计的（浅底 + 灰字），
            // 换成描边风格后淡描边在浅色卡片上几乎看不见（踩过）。
            val textArgb =
                when (state) {
                    DayVisualState.OutsideMonth -> tertiary
                    DayVisualState.Today ->
                        shiftArgb?.let { ShiftPalette.onColorArgb(it) } ?: primary
                    DayVisualState.Empty -> secondary
                    is DayVisualState.Past ->
                        shiftArgb?.let { outlineTextArgb(context, it) } ?: secondary
                    is DayVisualState.Upcoming ->
                        shiftArgb?.let { outlineTextArgb(context, it) } ?: secondary
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setInt(
                    R.id.day_cell,
                    "setBackgroundResource",
                    cellBackground(state, shiftArgb, context),
                )
            } else {
                views.setInt(
                    R.id.day_cell,
                    "setBackgroundColor",
                    cellFallbackArgb(state, shiftArgb, context),
                )
            }
            views.setTextColor(R.id.day_number, textArgb)
            views.setTextColor(R.id.day_shift, textArgb)
            return views
        }

        /**
         * 圆角底色资源。
         *
         * 之所以按"状态 + 班次色"查表而不是 `setBackgroundColor`：**两者会互相覆盖**， `setBackgroundResource` 一定重置
         * `setBackgroundColor` 设过的颜色，所以圆角与颜色只能由同一张 drawable 提供 —— 这也是 `widget_day_selected_*` /
         * `widget_day_faded_*` / `widget_day_today_*` 每个色板色各一份的原因。**改 `ShiftPalette.presets`
         * 时要同步这三组 xml。**
         */
        private fun cellBackground(
            state: DayVisualState,
            shiftArgb: Int?,
            context: Context,
        ): Int =
            when (state) {
                DayVisualState.OutsideMonth -> R.drawable.widget_day_outside
                DayVisualState.Empty -> R.drawable.widget_day_empty
                DayVisualState.Today ->
                    shiftArgb?.let { solidBackground(it) } ?: R.drawable.widget_day_today_none
                // 有班次的其他日子一律"班次色描边、不填底"，整月扫下来不刺眼；文字另外染成班次色
                is DayVisualState.Past ->
                    shiftArgb?.let { outlinedBackground(it) } ?: R.drawable.widget_day_empty
                is DayVisualState.Upcoming ->
                    shiftArgb?.let { outlinedBackground(it) } ?: R.drawable.widget_day_empty
            }

        /** API < 31 的纯色兜底：圆角会丢，但颜色仍然对 */
        private fun cellFallbackArgb(
            state: DayVisualState,
            shiftArgb: Int?,
            context: Context,
        ): Int =
            when (state) {
                DayVisualState.OutsideMonth -> context.getColor(R.color.widget_day_outside)
                DayVisualState.Empty -> context.getColor(R.color.widget_day_empty)
                DayVisualState.Today -> shiftArgb ?: android.graphics.Color.TRANSPARENT
                is DayVisualState.Past ->
                    shiftArgb?.let { fadeTowardWhite(it) }
                        ?: context.getColor(R.color.widget_day_empty)
                is DayVisualState.Upcoming ->
                    shiftArgb ?: context.getColor(R.color.widget_day_empty)
            }

        /** 班次色 → 实心底色；色板外的自定义颜色退回透明底 */
        private fun solidBackground(argb: Int?): Int =
            when (argb) {
                ShiftPalette.presets[0] -> R.drawable.widget_day_selected_red
                ShiftPalette.presets[1] -> R.drawable.widget_day_selected_orange
                ShiftPalette.presets[2] -> R.drawable.widget_day_selected_yellow
                ShiftPalette.presets[3] -> R.drawable.widget_day_selected_green
                ShiftPalette.presets[4] -> R.drawable.widget_day_selected_cyan
                ShiftPalette.presets[5] -> R.drawable.widget_day_selected_blue
                ShiftPalette.presets[6] -> R.drawable.widget_day_selected_indigo
                ShiftPalette.presets[7] -> R.drawable.widget_day_selected_purple
                ShiftPalette.presets[8] -> R.drawable.widget_day_selected_pink
                ShiftPalette.presets[9] -> R.drawable.widget_day_selected_brown
                ShiftPalette.presets[10] -> R.drawable.widget_day_selected_bluegrey
                ShiftPalette.presets[11] -> R.drawable.widget_day_selected_grass
                else -> R.drawable.widget_day_cell
            }

        /** 班次色 → 消色底（原色混 78% 白，与 App 内月历的"过去"格同一条规则） */
        private fun fadedBackground(argb: Int?): Int =
            when (argb) {
                ShiftPalette.presets[0] -> R.drawable.widget_day_faded_red
                ShiftPalette.presets[1] -> R.drawable.widget_day_faded_orange
                ShiftPalette.presets[2] -> R.drawable.widget_day_faded_yellow
                ShiftPalette.presets[3] -> R.drawable.widget_day_faded_green
                ShiftPalette.presets[4] -> R.drawable.widget_day_faded_cyan
                ShiftPalette.presets[5] -> R.drawable.widget_day_faded_blue
                ShiftPalette.presets[6] -> R.drawable.widget_day_faded_indigo
                ShiftPalette.presets[7] -> R.drawable.widget_day_faded_purple
                ShiftPalette.presets[8] -> R.drawable.widget_day_faded_pink
                ShiftPalette.presets[9] -> R.drawable.widget_day_faded_brown
                ShiftPalette.presets[10] -> R.drawable.widget_day_faded_bluegrey
                ShiftPalette.presets[11] -> R.drawable.widget_day_faded_grass
                else -> R.drawable.widget_day_empty
            }

        /** 班次色 → 格子描边底（drawable/ 是压暗版、drawable-night/ 是提亮版）；色板外的自定义颜色走中性描边 */
        private fun outlinedBackground(argb: Int): Int =
            when (argb) {
                ShiftPalette.presets[0] -> R.drawable.widget_day_outline_red
                ShiftPalette.presets[1] -> R.drawable.widget_day_outline_orange
                ShiftPalette.presets[2] -> R.drawable.widget_day_outline_yellow
                ShiftPalette.presets[3] -> R.drawable.widget_day_outline_green
                ShiftPalette.presets[4] -> R.drawable.widget_day_outline_cyan
                ShiftPalette.presets[5] -> R.drawable.widget_day_outline_blue
                ShiftPalette.presets[6] -> R.drawable.widget_day_outline_indigo
                ShiftPalette.presets[7] -> R.drawable.widget_day_outline_purple
                ShiftPalette.presets[8] -> R.drawable.widget_day_outline_pink
                ShiftPalette.presets[9] -> R.drawable.widget_day_outline_brown
                ShiftPalette.presets[10] -> R.drawable.widget_day_outline_bluegrey
                ShiftPalette.presets[11] -> R.drawable.widget_day_outline_grass
                else -> R.drawable.widget_day_today_none
            }

        /**
         * 描边格子的文字色：与 `widget_day_outline_*` 是同一套色号 —— 浅色卡片下把班次色**压暗 30%**、深色卡片下**提亮 30%**。
         *
         * 直接用色板原色不行：黄、草绿这类很亮的色号画成细描边和小字后在浅色卡片上几乎看不见 （用户反馈"色号看不清"）。压暗/提亮的方向跟着深浅色走，两种模式下都保持"框和字同色"。
         */
        private fun outlineTextArgb(context: Context, argb: Int): Int {
            val night =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            fun shift(channel: Int): Int =
                (if (night) {
                        channel + (255 - channel) * OUTLINE_SHIFT
                    } else {
                        channel * (1f - OUTLINE_SHIFT)
                    })
                    .roundToInt()
                    .coerceIn(0, 255)

            return Color.argb(
                255,
                shift((argb shr 16) and 0xFF),
                shift((argb shr 8) and 0xFF),
                shift(argb and 0xFF),
            )
        }

        /** 班次色 → 消色版 ARGB：混 78% 白（与 `ShiftPalette.pastFadeRatio` 一致） */
        private fun fadeTowardWhite(argb: Int): Int {
            val ratio = ShiftPalette.pastFadeRatio
            fun blend(channel: Int): Int =
                (channel * (1f - ratio) + 255f * ratio).roundToInt().coerceIn(0, 255)

            return Color.argb(
                255,
                blend((argb shr 16) and 0xFF),
                blend((argb shr 8) and 0xFF),
                blend(argb and 0xFF),
            )
        }

        /**
         * 底部那行「今天什么班 / 此刻在不在班上」的文案。
         *
         * 抽成纯逻辑（返回 `string res + 参数`）是为了能直接单测：这里最容易错的就是跨零点夜班的 **结束时刻算在哪一天**——见
         * [carriedOverNightShift] 与 `ShiftEndMoment`。
         */
        internal fun phaseTextParts(
            context: Context,
            document: PlanDocument,
            today: Ymd,
            currentMinute: Int,
        ): PhaseText {
            val roster = todayRoster(document, today, currentMinute)
            val template = roster.defaultShift?.template

            if (hasCarriedOverNightShift(document, today, currentMinute)) {
                // 还在昨天那班上：结束时刻落在**今天**，不加「次日」。
                // 注意取的是**昨天**那班的模板（今天那班可能是早班，结束时间完全不同）。
                val carried = previousDayTemplate(document, today)
                val end =
                    carried
                        ?.let { formatShiftEnd(it, nextDayMarker(context), ShiftEndMoment.TODAY) }
                        .orEmpty()
                return PhaseText(R.string.widget_phase_on_shift, listOf(end))
            }

            if (template == null) {
                return PhaseText(R.string.widget_phase_no_plan, emptyList())
            }

            val marker = nextDayMarker(context)
            val start = ShiftTemplate.format(template.startMinute)
            val end = formatShiftEnd(template, marker)
            return when (roster.phase) {
                ShiftPhase.ON_SHIFT -> PhaseText(R.string.widget_phase_on_shift, listOf(end))
                ShiftPhase.RESTING -> PhaseText(R.string.widget_phase_resting, emptyList())
                ShiftPhase.OFF_WORK ->
                    when {
                        template.isRest -> PhaseText(R.string.widget_phase_resting, emptyList())
                        currentMinute < template.startMinute ->
                            PhaseText(R.string.widget_phase_before_shift, listOf(start))
                        else -> PhaseText(R.string.widget_phase_after_shift, listOf(end))
                    }
            }
        }

        private fun phaseText(
            context: Context,
            document: PlanDocument,
            today: Ymd,
            currentMinute: Int,
        ): String {
            val parts = phaseTextParts(context, document, today, currentMinute)
            return context.getString(parts.resId, *parts.args.toTypedArray())
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
 * 底部状态行的文案：`string` 资源 + 格式化参数。
 *
 * 返回资源而不是最终字符串，是为了单测能断言"用了哪条文案、参数是什么"， 不受运行设备语言（中文/英文）影响。
 */
@Immutable internal data class PhaseText(val resId: Int, val args: List<String>)

/**
 * 一页 = 一个整月（6 行 × 7 格），交给调用方 addView 到 ViewFlipper 里。
 *
 * 行宽 = 7 × 列宽、整块高度 = 6 × 行高，都必须显式给：这几层都是嵌套的 RemoteViews， 测量时父容器给的是 wrap_content，`layout_weight`
 * 算不出等分（踩过：每格退化成一个字的宽度）。
 */
internal fun monthPageViews(
    context: Context,
    month: Ymd,
    weekStartDay: Int,
    today: Ymd,
    roster: Roster,
    anchorEpochDay: Long?,
    columnWidthPx: Int?,
    rowHeightPx: Int?,
): RemoteViews {
    val page = RemoteViews(context.packageName, R.layout.widget_month_item)
    val rowWidthPx = columnWidthPx?.times(MonthGrid.COLUMNS)
    MonthGrid.of(month.year, month.month, weekStartDay).weeks.forEach { week ->
        val row = RemoteViews(context.packageName, R.layout.widget_week_item)
        week.forEach { cell ->
            row.addView(
                R.id.week_item_row,
                DaoWidgetProvider.dayCellViews(
                        context = context,
                        cell = cell,
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
        row.applyCellSize(R.id.week_item_row, rowWidthPx, rowHeightPx)
        page.addView(R.id.month_item, row)
    }
    page.applyCellSize(R.id.month_item, rowWidthPx, rowHeightPx?.times(MonthGrid.ROWS))
    return page
}
