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
import android.util.TypedValue
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
import win.zuoye.dao.data.minuteOfDay
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
open class DaoWeekWidgetProvider : AppWidgetProvider() {

    /**
     * 内容缩放：字号和翻页按钮都按这个比例放大。
     *
     * **为什么由子类写死、而不是按组件尺寸算**：线上实测 vivo 的 Launcher 把 `MIN/MAX_WIDTH/HEIGHT` 全填
     * 0，`OPTION_APPWIDGET_SIZES` 报的值也**不随拉伸变化**， 于是「按尺寸自适应字号」永远停在最小档、把组件拉大字号也不变（用户反馈过）。
     * 所以改成做两档固定字号的组件让用户自己挑，因此字号是固定的，不随组件尺寸变化。
     */
    internal open val contentScale: Float
        get() = 1f

    /**
     * 格子里要不要那行班次说明（早/中/夜/休）。
     *
     * **每个组件独立决定**：小版「本周班次」是 4×1，一行只有一格高，加了说明会把日期挤掉，所以默认 不显示；大版（4×2）显式打开。以前这项和排版风格共用一个参数，改一处会动两处 ——
     * 现在拆开了。
     */
    protected open val showShiftLabel: Boolean
        get() = false

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
                    appWidgetManager.updateAppWidget(
                        id,
                        buildViews(context, document, today, id, contentScale, showShiftLabel),
                    )
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
        /** 这个组件有两个档位（普通 / 大字），刷新要一起覆盖 */
        private val WIDGET_PROVIDERS =
            listOf(DaoWeekWidgetProvider::class.java, DaoWeekWidgetLargeProvider::class.java)

        private const val TAG = "DaoWeekWidget"
        /**
         * 跨日刷新的分钟数：比月历那个（00:03）早两分钟，两个组件错开 —— Doze 下同一个 App 每 9 分钟只放行一个 alarm，撞在同一分钟会有一个被推迟到很久以后。
         * 也不能排 23:59：那一刻 `Ymd.today()` 还是前一天。
         */
        private const val MIDNIGHT_MINUTE = 1

        const val ACTION_REFRESH = "win.zuoye.dao.action.WEEK_WIDGET_REFRESH"
        const val ACTION_PAGE_UP = "win.zuoye.dao.action.WEEK_WIDGET_PAGE_UP"
        const val ACTION_PAGE_DOWN = "win.zuoye.dao.action.WEEK_WIDGET_PAGE_DOWN"

        /**
         * 请求重新读取数据并重画所有已放置的「本周班次」小组件。
         *
         * 两种档位（普通 / 大字）是**两个 receiver**，刷新必须一起覆盖，否则大字版永远不更新。 广播本身仍然发给自己这一类：两个类的 `onUpdate`
         * 是同一份代码（子类只改了 `contentScale`）。
         */
        fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            // **必须按 provider 分别广播**：两个档位的 onUpdate 是同一份代码，但 contentScale 不同。
            // 如果像以前那样把所有实例的 id 打包发给"普通版"那个 receiver，普通版就会用 1.0 的
            // 比例把大字版重新渲染一遍 —— 每次刷新都把大字版打回小字（真机上就是这样，用户抓到）。
            WIDGET_PROVIDERS.forEach { providerClass ->
                val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
                if (ids.isEmpty()) return@forEach
                context.sendBroadcast(
                    Intent(context, providerClass).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }

        internal fun buildViews(
            context: Context,
            document: PlanDocument,
            today: Ymd,
            appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
            scale: Float = 1f,
            showShiftLabel: Boolean = false,
        ): RemoteViews {
            // 底部状态行写的是"此刻在不在上班"，所以要当前分钟（与月历同一套 TodayRoster 判定）
            val now = System.currentTimeMillis()
            val roster = Roster.of(document)
            val anchorEpochDay = document.activeScheme()?.defaultGroup()?.anchorEpochDay

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
                // 组件被拉高时整块内容一起放大（子类可以固定一个更大的比例）
                val scale = scale
                setTextViewTextSize(R.id.week_widget_month, TypedValue.COMPLEX_UNIT_SP, 15f * scale)
                setTextViewTextSize(R.id.day_number, TypedValue.COMPLEX_UNIT_SP, 16f * scale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val buttonPx =
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            // 箭头**封顶**：按 scale 一路放大到 75dp 会高出标题行一倍，直接被裁掉（真机反馈"显示不完全"）
                            minOf(30f * scale, 34f),
                            context.resources.displayMetrics,
                        )
                    listOf(R.id.week_widget_page_up, R.id.week_widget_page_down).forEach { id ->
                        setViewLayoutWidth(id, buttonPx, TypedValue.COMPLEX_UNIT_PX)
                        setViewLayoutHeight(id, buttonPx, TypedValue.COMPLEX_UNIT_PX)
                    }
                }
                // 底部状态行：与月历同一套文案（上班中·到 xx:xx 等），走 TodayRoster 的唯一判定
                setTextViewText(
                    R.id.week_widget_phase,
                    DaoWidgetProvider.phaseText(context, document, today, minuteOfDay(now)),
                )
                setTextViewTextSize(
                    R.id.week_widget_phase,
                    TypedValue.COMPLEX_UNIT_SP,
                    // 状态行不必跟着 scale 一路涨：它只是说明文字，放大到 1.5x 就够了，多出来的高度留给格子
                    11f * minOf(scale, 1.5f),
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
                            scale = scale,
                            showShiftLabel = showShiftLabel,
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
 * **尺寸全交给布局**：格子是 `0dp + weight=1`、这一行是 `match_parent`，ViewFlipper 会给它确定宽高， weight 于是能正常等分。不用再按
 * `getAppWidgetOptions` 算像素 —— vivo 的 Launcher 报的尺寸是坏的 （`MIN/MAX_*` 全 0、`SIZES`
 * 偏小一半），算出来的格子只有组件一半宽（真机踩过）。
 */
internal fun weekPageViews(
    context: Context,
    weekStart: Long,
    today: Ymd,
    roster: Roster,
    anchorEpochDay: Long?,
    scale: Float,
    showShiftLabel: Boolean,
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
                compact = true,
                showShiftLabel = showShiftLabel,
                // **scale 必须传**：漏掉它整个组件的字号就永远停在 1.0 档（踩过两次）
                scale = scale,
            ),
        )
    }
    return strip
}

private const val DAYS_PER_WEEK = 7L
