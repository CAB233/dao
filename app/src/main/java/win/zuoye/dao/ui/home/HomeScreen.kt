package win.zuoye.dao.ui.home

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone as IcuTimeZone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import win.zuoye.dao.data.LegalHolidays
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.R
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.defaultGroup
import win.zuoye.dao.domain.Roster
import win.zuoye.dao.domain.resolveShift
import win.zuoye.dao.ui.HolidayPalette
import win.zuoye.dao.ui.ShiftPalette
import win.zuoye.dao.ui.common.localizedTimeRangeText
import win.zuoye.dao.ui.common.rememberHoldDownSource
import kotlin.math.roundToInt
import java.util.Locale

/** 可浏览的月份范围：2000-01 .. 2100-12 */
private const val BASE_YEAR = 2000
private const val MONTH_COUNT = 101 * 12
private const val MILLIS_PER_DAY = 86_400_000L

/** 首页：月历视图，按班次颜色着色。月份左右滑动切换。 */
@Composable
fun HomeScreen(
    doc: PlanDocument,
    onExportPlan: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val today = remember { Ymd.today() }
    val initialPage = (today.year - BASE_YEAR) * 12 + today.month - 1
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MONTH_COUNT })
    val coroutineScope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf<Ymd?>(null) }

    val settledIndex = pagerState.settledPage
    val viewYear = BASE_YEAR + settledIndex / 12
    val viewMonth = settledIndex % 12 + 1
    val isCurrentMonth = viewYear == today.year && viewMonth == today.month
    val weekdays = stringArrayResource(R.array.weekday_short).toList()
    val weekStartDay = doc.weekStartDay.coerceIn(0, weekdays.lastIndex)
    val showHolidays = doc.calendarViewMode.showHolidays
    val showLunar = doc.calendarViewMode.showLunar
    val calendarStrings = calendarStrings()

    // 排班索引整个页面共用一份，预组合的三页不会各建一份
    val roster = remember(doc) { Roster.of(doc) }
    // 文字测量缓存也整页共用：相邻月份的日期/班次名高度重合，命中率很高
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val scrollBehavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                largeTitle = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onExportPlan) {
                        Icon(MiuixIcons.Regular.Share, contentDescription = stringResource(R.string.share_plan_description))
                    }
                },
            )
        },
        floatingActionButton = {
            // 滑到别的月份时，右下角出现蓝色圆形「今」，一键回到今天
            // 外边距放在 AnimatedVisibility 之外，保证缩放动画以圆心为中心
            Box(Modifier.padding(end = 8.dp, bottom = 12.dp)) {
                AnimatedVisibility(
                    visible = !isCurrentMonth,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                ) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(initialPage)
                            }
                        },
                        // 关闭阴影：默认的 shadowElevation 会建离屏图层，动画时明显卡顿
                        shadowElevation = 0.dp,
                        minWidth = 54.dp,
                        minHeight = 54.dp,
                    ) {
                        Text(stringResource(R.string.today_short), fontSize = 22.sp, color = MiuixTheme.colorScheme.onPrimary)
                    }
                }
            }
        },
        // 底部空间由外层底栏占据，这里不再重复算导航栏内边距
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .scrollEndHaptic()
                .overScrollVertical(),
        ) {
            RosterStatusCard(
                doc = doc,
                today = today,
                onClick = onOpenPlan,
            )

            // 左对齐月份标题
            Text(
                stringResource(R.string.month_title, viewMonth),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val page = pagerState.currentPage
                val year = BASE_YEAR + page / 12
                val month = page % 12 + 1
                val rowCount = monthRowCount(year, month, weekStartDay)
                val cellWidth = ((maxWidth - 24.dp) / 7f).coerceAtLeast(0.dp)
                val cellHeight = ((cellWidth - 3.dp) / 0.82f) + 3.dp
                val pagerHeight = 24.dp + cellHeight * rowCount + 3.dp
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(pagerHeight),
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    MonthGrid(
                        roster = roster,
                        measurer = textMeasurer,
                        year = BASE_YEAR + pageIndex / 12,
                        month = pageIndex % 12 + 1,
                        today = today,
                        weekStartDay = weekStartDay,
                        weekdays = weekdays,
                        strings = calendarStrings,
                        showHolidays = showHolidays,
                        showLunar = showLunar,
                        selected = selectedDate,
                        onDayClick = { selectedDate = it },
                    )
                }
            }
            TodayGroupsCard(
                doc = doc,
                roster = roster,
                today = today,
            )
            if (doc.activeScheme() == null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.home_empty_plan),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // 对话框必须挂在 Scaffold 内部（依赖 Scaffold 提供的弹层宿主）。
        // 常驻组合、用 show 驱动进出动画；退出动画期间还要继续渲染，所以记住最后点开的那一天。
        var detailDate by remember { mutableStateOf<Ymd?>(null) }
        LaunchedEffect(selectedDate) { selectedDate?.let { detailDate = it } }
        detailDate?.let { date ->
            DayDetailDialog(
                date = date,
                doc = doc,
                roster = roster,
                showHolidays = showHolidays,
                show = selectedDate != null,
                onDismiss = { selectedDate = null },
            )
        }
    }
}

/** 日历下方显示当前方案中各班组今天的排班。 */
@Composable
private fun TodayGroupsCard(
    doc: PlanDocument,
    roster: Roster,
    today: Ymd,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp, bottom = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.today_schedule),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            GroupScheduleRows(doc = doc, roster = roster, epochDay = today.epochDay)
        }
    }
}

/** 当前方案下所有班组在指定日期的排班；首页卡片和日期详情共用同一套布局。 */
@Composable
private fun GroupScheduleRows(
    doc: PlanDocument,
    roster: Roster,
    epochDay: Long,
) {
    val scheme = doc.activeScheme()
    val groups = scheme?.groups.orEmpty()
    if (groups.isEmpty()) {
        Text(
            stringResource(if (scheme == null) R.string.no_plan else R.string.no_group),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        return
    }

    groups.forEachIndexed { index, group ->
        if (index > 0) Spacer(Modifier.height(12.dp))
        val template = roster.templateFor(epochDay, group.anchorEpochDay)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .squircleBackground(
                        color = template?.let { ShiftPalette.color(it.colorArgb) }
                            ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        cornerRadius = 5.dp,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                group.name,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    template?.name ?: stringResource(R.string.no_schedule),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                template?.let {
                    Text(
                        it.localizedTimeRangeText(),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (template == null) {
                    Text(
                        stringResource(R.string.unassigned_shift),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** 主页上的当前倒班状态卡片；点击后进入方案列表，可切换使用中的方案。 */
@Composable
private fun RosterStatusCard(
    doc: PlanDocument,
    today: Ymd,
    onClick: () -> Unit,
) {
    val activeScheme = doc.activeScheme()
    val todayShift = resolveShift(doc, today.epochDay)
    val currentGroupName = activeScheme?.defaultGroup()?.name
    val cardColor = if (isSystemInDarkTheme()) {
        ShiftPalette.statusCardDarkBackground
    } else {
        ShiftPalette.statusCardLightBackground
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = cardColor,
        ),
        // 按压位置决定倾斜方向：左侧与右侧按压会产生不同的反馈。
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = 27.dp, y = 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text("🏝", fontSize = 92.sp)
            }

            Column(
                modifier = Modifier.padding(start = 16.dp, top = 14.dp),
            ) {
                Text(
                    text = stringResource(if (todayShift?.template?.isRest == true) R.string.on_rest else R.string.on_shift),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = stringResource(
                        R.string.plan_label,
                        activeScheme?.name ?: stringResource(R.string.status_not_selected),
                    ),
                    fontSize = 15.sp,
                )
            }

            Text(
                text = currentGroupName ?: stringResource(R.string.no_group),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 10.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    roster: Roster,
    measurer: TextMeasurer,
    year: Int,
    month: Int,
    today: Ymd,
    weekStartDay: Int,
    weekdays: List<String>,
    strings: CalendarStrings,
    showHolidays: Boolean,
    showLunar: Boolean,
    selected: Ymd?,
    onDayClick: (Ymd) -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val slots = remember(roster, year, month, today, weekStartDay, strings, showHolidays, showLunar) {
        buildMonthSlots(
            roster = roster,
            year = year,
            month = month,
            today = today,
            weekStartDay = weekStartDay,
            strings = strings,
            showHolidays = showHolidays,
            showLunar = showLunar,
        )
    }
    val colors = remember(colorScheme) {
        GridColors(
            surfaceVariant = colorScheme.surfaceVariant,
            onSurface = colorScheme.onSurface,
            primary = colorScheme.primary,
            onSurfaceVariantSummary = colorScheme.onSurfaceVariantSummary,
        )
    }
    val baseTextStyle = MiuixTheme.textStyles.main
    val cellText = remember(colors, baseTextStyle) { CellTextStyles(colors, baseTextStyle) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(7) { column ->
                Text(
                    text = weekdays[(weekStartDay + column) % weekdays.size],
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        repeat(slots.size / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val slot = slots[index]
                    CalendarCell(
                        slot = slot,
                        text = cellText,
                        measurer = measurer,
                        // 详情弹窗打开期间，那一格保持按住高亮
                        heldDown = selected != null &&
                            selected.year == slot.year &&
                            selected.month == slot.month &&
                            selected.day == slot.day,
                        onClick = { onDayClick(Ymd(slot.year, slot.month, slot.day)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
    }
}

/** 一格的全部渲染输入，页面构造时算好，重组时直接取用 */
private class DaySlot(
    val year: Int,
    val month: Int,
    val day: Int,
    val template: ShiftTemplate?,
    val isToday: Boolean,
    /** 非当月补位日整体弱化，但今天保持着重 */
    val fade: Float,
    val holiday: LegalHolidays.HolidayDay?,
    val lunarLabel: String,
    val holidayName: String?,
)

/** 日历配色，一屏读一次主题，避免 42 个格子各读一次 CompositionLocal */
private class GridColors(
    val surfaceVariant: Color,
    val onSurface: Color,
    val primary: Color,
    val onSurfaceVariantSummary: Color,
)

/**
 * 单元格文字样式（含淡化变体），随主题预生成。
 * 直接画文字（而不是放 Text 组合项）能把每页多组文字排版压到一次，
 * 让文字测量缓存能跨格/跨月复用缓存。
 */
private class CellTextStyles(colors: GridColors, base: TextStyle) {
    val surfaceVariant = colors.surfaceVariant
    val primary = colors.primary
    val day = base.merge(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
    val dayToday = base.merge(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
    val dayFaded =
        base.merge(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colors.onSurface.faded(0.35f))
    val dayTodayFaded =
        base.merge(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface.faded(0.35f))
    val lunar = base.merge(fontSize = 9.sp, color = colors.onSurfaceVariantSummary)
    val holiday = base.merge(fontSize = 9.sp, color = colors.onSurfaceVariantSummary)
    val name = base.merge(fontSize = 9.sp)
}

private val CELL_RADIUS = 14.dp

private fun monthRowCount(year: Int, month: Int, weekStartDay: Int): Int {
    val firstOffset = Math.floorMod(Ymd(year, month, 1).weekdayIndex - weekStartDay, 7)
    return (firstOffset + Ymd.daysInMonth(year, month) + 6) / 7
}

private fun buildMonthSlots(
    roster: Roster,
    year: Int,
    month: Int,
    today: Ymd,
    weekStartDay: Int,
    strings: CalendarStrings,
    showHolidays: Boolean,
    showLunar: Boolean,
): List<DaySlot> {
    val daysInMonth = Ymd.daysInMonth(year, month)
    val firstOffset = Math.floorMod(Ymd(year, month, 1).weekdayIndex - weekStartDay, 7)
    // 只生成覆盖当前月的最少完整周数，月历高度随月份需要的周数动态变化。
    val cellCount = monthRowCount(year, month, weekStartDay) * 7
    val slots = ArrayList<DaySlot>(cellCount)
    val lunarCalendar = ChineseCalendar(IcuTimeZone.getTimeZone("UTC"), Locale.CHINA)
    for (index in 0 until cellCount) {
        val y: Int
        val m: Int
        val day: Int
        val inMonth = index >= firstOffset && index - firstOffset < daysInMonth
        when {
            inMonth -> {
                y = year
                m = month
                day = index - firstOffset + 1
            }
            // 前月尾部（index - firstOffset + 1 ≤ 0）
            index < firstOffset -> {
                m = if (month == 1) 12 else month - 1
                y = if (month == 1) year - 1 else year
                day = Ymd.daysInMonth(y, m) + (index - firstOffset + 1)
            }
            // 后月头部
            else -> {
                m = if (month == 12) 1 else month + 1
                y = if (month == 12) year + 1 else year
                day = index - firstOffset - daysInMonth + 1
            }
        }
        val date = Ymd(y, m, day)
        val lunar = if (showHolidays || showLunar) {
            lunarDate(lunarCalendar, date.epochDay)
        } else {
            null
        }
        val isToday = y == today.year && m == today.month && day == today.day
        slots += DaySlot(
            year = y,
            month = m,
            day = day,
            template = roster.templateFor(date.epochDay),
            isToday = isToday,
            fade = if (!inMonth && !isToday) 0.35f else 1f,
            holiday = LegalHolidays.of(date.epochDay).takeIf { showHolidays },
            lunarLabel = lunar?.takeIf { showLunar }?.let { lunarLabel(it, strings) }.orEmpty(),
            holidayName = lunar
                ?.takeIf { showHolidays }
                ?.let { holidayName(date, it, today.year) }
                ?.let(strings.holidayNames::get),
        )
    }
    return slots
}

private data class LunarDate(
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
    val daysInMonth: Int,
)

private data class CalendarStrings(
    val lunarMonths: List<String>,
    val lunarDays: List<String>,
    val lunarLeap: String,
    val lunarFormat: String,
    val holidayNames: Map<Int, String>,
)

@Composable
private fun calendarStrings() = CalendarStrings(
    lunarMonths = stringArrayResource(R.array.lunar_month_names).toList(),
    lunarDays = stringArrayResource(R.array.lunar_day_names).toList(),
    lunarLeap = stringResource(R.string.lunar_leap),
    lunarFormat = stringResource(R.string.lunar_date, "%1\$s", "%2\$s", "%3\$s"),
    holidayNames = listOf(
        R.string.holiday_new_year,
        R.string.holiday_spring_festival,
        R.string.holiday_lantern_festival,
        R.string.holiday_dragon_heads_raising,
        R.string.holiday_qingming,
        R.string.holiday_labor_day,
        R.string.holiday_dragon_boat,
        R.string.holiday_qixi,
        R.string.holiday_ghost_festival,
        R.string.holiday_mid_autumn,
        R.string.holiday_double_ninth,
        R.string.holiday_laba,
        R.string.holiday_new_year_eve,
        R.string.holiday_national_day,
        R.string.holiday_valentines,
        R.string.holiday_womens_day,
        R.string.holiday_april_fools,
        R.string.holiday_childrens_day,
        R.string.holiday_halloween,
        R.string.holiday_christmas,
        R.string.holiday_mothers_day,
        R.string.holiday_fathers_day,
        R.string.holiday_thanksgiving,
    ).associateWith { stringResource(it) },
)

private fun lunarDate(calendar: ChineseCalendar, epochDay: Long): LunarDate {
    calendar.setTimeInMillis(epochDay * MILLIS_PER_DAY)
    return LunarDate(
        month = calendar.get(IcuCalendar.MONTH) + 1,
        day = calendar.get(IcuCalendar.DAY_OF_MONTH),
        isLeapMonth = calendar.get(IcuCalendar.IS_LEAP_MONTH) != 0,
        daysInMonth = calendar.getActualMaximum(IcuCalendar.DAY_OF_MONTH),
    )
}

private fun lunarLabel(lunar: LunarDate, strings: CalendarStrings): String {
    val dayName = strings.lunarDays.getOrElse(lunar.day - 1) { lunar.day.toString() }
    if (lunar.day != 1) return dayName
    val leapPrefix = if (lunar.isLeapMonth) strings.lunarLeap else ""
    val monthName = strings.lunarMonths.getOrElse(lunar.month - 1) { lunar.month.toString() }
    return strings.lunarFormat
        .replace("%1\$s", leapPrefix)
        .replace("%2\$s", monthName)
        .replace("%3\$s", dayName)
}

/** 节日信息只在当前年和下一年显示；法定节假日优先于通用节日名称。 */
private fun holidayName(date: Ymd, lunar: LunarDate, currentYear: Int): Int? {
    LegalHolidays.of(date.epochDay)?.takeIf { it.isNameDay }?.let { return it.name.stringRes() }
    if (date.year !in currentYear..(currentYear + 1) || lunar.isLeapMonth) return null

    return when {
        lunar.month == 1 && lunar.day == 1 -> R.string.holiday_spring_festival
        lunar.month == 1 && lunar.day == 15 -> R.string.holiday_lantern_festival
        lunar.month == 2 && lunar.day == 2 -> R.string.holiday_dragon_heads_raising
        lunar.month == 5 && lunar.day == 5 -> R.string.holiday_dragon_boat
        lunar.month == 7 && lunar.day == 7 -> R.string.holiday_qixi
        lunar.month == 7 && lunar.day == 15 -> R.string.holiday_ghost_festival
        lunar.month == 8 && lunar.day == 15 -> R.string.holiday_mid_autumn
        lunar.month == 9 && lunar.day == 9 -> R.string.holiday_double_ninth
        lunar.month == 12 && lunar.day == 8 -> R.string.holiday_laba
        lunar.month == 12 && lunar.day >= lunar.daysInMonth -> R.string.holiday_new_year_eve
        date.month == 2 && date.day == 14 -> R.string.holiday_valentines
        date.month == 3 && date.day == 8 -> R.string.holiday_womens_day
        date.month == 4 && date.day == 1 -> R.string.holiday_april_fools
        date.month == 6 && date.day == 1 -> R.string.holiday_childrens_day
        date.month == 10 && date.day == 31 -> R.string.holiday_halloween
        date.month == 12 && date.day == 25 -> R.string.holiday_christmas
        date.month == 5 && date.weekdayIndex == 6 && date.day in 8..14 -> R.string.holiday_mothers_day
        date.month == 6 && date.weekdayIndex == 6 && date.day in 15..21 -> R.string.holiday_fathers_day
        date.month == 11 && date.weekdayIndex == 3 && date.day in 22..28 -> R.string.holiday_thanksgiving
        else -> null
    }
}

private fun LegalHolidays.HolidayName.stringRes(): Int = when (this) {
    LegalHolidays.HolidayName.NEW_YEAR -> R.string.holiday_new_year
    LegalHolidays.HolidayName.SPRING_FESTIVAL -> R.string.holiday_spring_festival
    LegalHolidays.HolidayName.QINGMING -> R.string.holiday_qingming
    LegalHolidays.HolidayName.LABOR_DAY -> R.string.holiday_labor_day
    LegalHolidays.HolidayName.DRAGON_BOAT -> R.string.holiday_dragon_boat
    LegalHolidays.HolidayName.MID_AUTUMN -> R.string.holiday_mid_autumn
    LegalHolidays.HolidayName.NATIONAL_DAY -> R.string.holiday_national_day
}

private fun darken(c: Color, f: Float = 0.62f) =
    Color(c.red * f, c.green * f, c.blue * f, 1f)

/** 透明度整体乘一个系数（替代 Modifier.alpha，省掉一格一个离屏图层） */
private fun Color.faded(f: Float): Color = copy(alpha = alpha * f)

/**
 * squircle 满格块（对齐 miuix 组件的连续圆角）。
 * 今天 = 常规底色 + 主题色描边着重（不随其他月淡化）；普通日 = 班次浅色底；无班次 = 浅灰块。
 *
 * 底色与涟漪由 [squircleSurface] 负责（不是 RoundedCornerShape 的 clip/background），
 * 多行文字仍在一个 drawWithCache 里直接画：省掉每格多个布局节点，测量结果也能跨格/跨月复用缓存。
 */
@Composable
private fun CalendarCell(
    slot: DaySlot,
    text: CellTextStyles,
    measurer: TextMeasurer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heldDown: Boolean = false,
) {
    val template = slot.template
    val shiftColor = template?.let { ShiftPalette.color(it.colorArgb) }
    val fade = slot.fade
    val container =
        shiftColor?.copy(alpha = 0.13f)?.faded(fade) ?: text.surfaceVariant.faded(fade)
    val dayStyle = when {
        slot.isToday && fade < 1f -> text.dayTodayFaded
        slot.isToday -> text.dayToday
        fade < 1f -> text.dayFaded
        else -> text.day
    }
    val dayText = slot.day.toString()
    val dayLayout = remember(measurer, dayText, dayStyle) { measurer.measure(dayText, dayStyle) }
    val lunarStyle = text.lunar.copy(color = text.lunar.color.faded(fade))
    val lunarLayout = if (slot.holidayName == null && slot.lunarLabel.isNotEmpty()) {
        remember(measurer, slot.lunarLabel, lunarStyle) {
            measurer.measure(slot.lunarLabel, lunarStyle, maxLines = 1)
        }
    } else {
        null
    }
    val holidayStyle = text.holiday.copy(color = text.holiday.color.faded(fade))
    val holidayLayout = if (slot.holidayName != null) {
        val holidayText = slot.holidayName
        remember(measurer, holidayText, holidayStyle) {
            measurer.measure(holidayText, holidayStyle, maxLines = 1)
        }
    } else {
        null
    }
    val nameLayout = if (template != null) {
        val nameColor = darken(shiftColor!!).faded(fade)
        val nameStyle = text.name.copy(color = nameColor)
        remember(measurer, template.name, nameStyle) {
            measurer.measure(template.name, nameStyle, maxLines = 1)
        }
    } else {
        null
    }
    // 今天用主题色描边着重；squircleBorder 自带"描边内缩半个线宽"，与 Modifier.border 一致
    val todayBorder = if (slot.isToday) {
        Modifier.squircleBorder(width = 2.dp, color = text.primary, cornerRadius = CELL_RADIUS)
    } else {
        Modifier
    }
    val interactionSource = rememberHoldDownSource(heldDown)
    val lineSpacing = with(LocalDensity.current) { 1.dp.toPx() }

    Box(
        modifier
            .padding(1.5.dp)
            .aspectRatio(0.82f)
            .squircleSurface(color = container, cornerRadius = CELL_RADIUS)
            .then(todayBorder)
            .drawWithCache {
                val totalHeight = dayLayout.size.height +
                    (nameLayout?.let { lineSpacing + it.size.height } ?: 0f) +
                    (lunarLayout?.let { lineSpacing + it.size.height } ?: 0f) +
                    (holidayLayout?.let { lineSpacing + it.size.height } ?: 0f)
                // 取整到整像素，贴近 Compose 布局的整数摆放（半像素会带来可见的字形差异）
                val top = ((size.height - totalHeight) / 2f).roundToInt().toFloat()
                val dayX = ((size.width - dayLayout.size.width) / 2f).roundToInt().toFloat()
                val lunarX = lunarLayout?.let {
                    ((size.width - it.size.width) / 2f).roundToInt().toFloat()
                }
                var nextY = top + dayLayout.size.height
                val nameY = nameLayout?.let {
                    val y = nextY + lineSpacing
                    nextY = y + it.size.height
                    y
                }
                val lunarY = lunarLayout?.let {
                    val y = nextY + lineSpacing
                    nextY = y + it.size.height
                    y
                }
                val holidayY = holidayLayout?.let {
                    val y = nextY + lineSpacing
                    nextY = y + it.size.height
                    y
                }

                onDrawBehind {
                    drawText(dayLayout, topLeft = Offset(dayX, top))
                    nameLayout?.let {
                        val nameX = ((size.width - it.size.width) / 2f).roundToInt().toFloat()
                        drawText(it, topLeft = Offset(nameX, nameY!!))
                    }
                    lunarLayout?.let {
                        drawText(it, topLeft = Offset(lunarX!!, lunarY!!))
                    }
                    holidayLayout?.let {
                        val holidayX = ((size.width - it.size.width) / 2f).roundToInt().toFloat()
                        drawText(it, topLeft = Offset(holidayX, holidayY!!))
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 法定节假日角标：颜色预乘淡化系数，避免离屏 alpha 图层
        slot.holiday?.let { holiday ->
            val badge = remember(holiday.isMakeupWorkday, fade) {
                if (holiday.isMakeupWorkday) {
                    HolidayPalette.makeupBadge.faded(fade) to HolidayPalette.makeupOnBadge.faded(fade)
                } else {
                    HolidayPalette.restBadge.faded(fade) to HolidayPalette.restOnBadge.faded(fade)
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .squircleBackground(color = badge.first, cornerRadius = 3.dp)
                    .padding(horizontal = 2.5.dp, vertical = 0.5.dp),
            ) {
                Text(
                    stringResource(
                        if (holiday.isMakeupWorkday) R.string.holiday_work_badge else R.string.holiday_rest_badge,
                    ),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = badge.second,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DayDetailDialog(
    date: Ymd,
    doc: PlanDocument,
    roster: Roster,
    showHolidays: Boolean,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val holiday = remember(date, showHolidays) {
        LegalHolidays.of(date.epochDay).takeIf { showHolidays }
    }
    val isOverride = remember(date, doc.overrides, doc.templates) {
        doc.overrides[date.epochDay.toString()]?.let(doc::templateById) != null
    }
    val weekdays = stringArrayResource(R.array.weekday_short)
    OverlayDialog(
        show = show,
        title = stringResource(
            R.string.date_dialog_title,
            date.year,
            date.month,
            date.day,
            weekdays[date.weekdayIndex],
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            holiday?.let {
                Text(
                    stringResource(
                        if (it.isMakeupWorkday) R.string.makeup_workday else R.string.legal_holiday,
                        stringResource(it.name.stringRes()),
                    ),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
            GroupScheduleRows(doc = doc, roster = roster, epochDay = date.epochDay)
            if (isOverride) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.shift_override),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
