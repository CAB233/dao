package win.zuoye.dao.ui.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import win.zuoye.dao.R
import win.zuoye.dao.data.LegalHolidays
import win.zuoye.dao.data.LunarStrings
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.defaultGroup
import win.zuoye.dao.data.holidayNameRes
import win.zuoye.dao.data.lunarDate
import win.zuoye.dao.data.lunarLabel
import win.zuoye.dao.data.stringRes
import win.zuoye.dao.domain.Roster
import win.zuoye.dao.ui.HolidayPalette
import win.zuoye.dao.ui.ShiftPalette
import win.zuoye.dao.ui.components.CornerFab
import win.zuoye.dao.ui.components.localizedTimeRangeText
import win.zuoye.dao.ui.components.rememberHoldDownSource
import win.zuoye.dao.ui.faded
import win.zuoye.dao.ui.scheme.DefaultGroupDialog

/** 可浏览的月份范围：2000-01 .. 2100-12 */
private const val BASE_YEAR = 2000
private const val MONTH_COUNT = 101 * 12

/** 首页：月历视图，按班次颜色着色。月份左右滑动切换。 */
@Composable
fun HomeScreen(
    doc: PlanDocument,
    onSharePlan: () -> Unit,
    onOpenPlan: () -> Unit,
    onDefaultGroupChange: (Long, Long) -> Unit,
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTimeMillis = now
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    val today = remember(currentTimeMillis) { Ymd.today() }
    val currentMinute =
        remember(currentTimeMillis) {
            Calendar.getInstance().run { get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) }
        }
    val initialPage = (today.year - BASE_YEAR) * 12 + today.month - 1
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MONTH_COUNT })
    val coroutineScope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf<Ymd?>(null) }
    val scheduleDate = selectedDate ?: today
    val onDayClick = { date: Ymd -> selectedDate = date.takeUnless { it == today } }

    val settledIndex = pagerState.settledPage
    val viewYear = BASE_YEAR + settledIndex / 12
    val viewMonth = settledIndex % 12 + 1
    val isCurrentMonth = viewYear == today.year && viewMonth == today.month
    val weekdays = stringArrayResource(R.array.weekday_short).toList()
    val weekStartDay = doc.weekStartDay.coerceIn(0, weekdays.lastIndex)
    val showHolidays = doc.calendarViewMode.showHolidays
    val showLunar = doc.calendarViewMode.showLunar
    // 页面每分钟 tick 一次会带动重组；字符串表按资源对象缓存，别每次重建 23 条节日名
    val resources = LocalResources.current
    val calendarStrings =
        remember(resources) {
            CalendarStrings(
                lunar =
                    LunarStrings(
                        months = resources.getStringArray(R.array.lunar_month_names).toList(),
                        days = resources.getStringArray(R.array.lunar_day_names).toList(),
                        leap = resources.getString(R.string.lunar_leap),
                        format =
                            resources.getString(R.string.lunar_date, "%1\$s", "%2\$s", "%3\$s"),
                    ),
                holidayNames = HOLIDAY_NAME_IDS.associateWith { resources.getString(it) },
            )
        }

    // 排班索引整个页面共用一份，预组合的三页不会各建一份
    val roster = remember(doc) { Roster.of(doc) }
    // 文字测量缓存也整页共用：相邻月份的日期/班次名高度重合，命中率很高
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val scrollBehavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()
    val summaryScrollState = rememberScrollState()
    val isAtTop by remember {
        derivedStateOf { scrollState.value == 0 && summaryScrollState.value == 0 }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                largeTitle = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onSharePlan) {
                        Icon(
                            MiuixIcons.Regular.Share,
                            contentDescription = stringResource(R.string.share_plan_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // 查看其它月份或选中非今日日期时，页面在顶部才显示「今」
            CornerFab(
                visible = (!isCurrentMonth || scheduleDate != today) && isAtTop,
                onClick = {
                    selectedDate = null
                    coroutineScope.launch { pagerState.animateScrollToPage(initialPage) }
                },
            ) {
                Text(
                    stringResource(R.string.today_short),
                    fontSize = 22.sp,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        },
        // 底部空间由外层底栏占据，这里不再重复算导航栏内边距
        contentWindowInsets =
            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val calendarPane: @Composable (Modifier) -> Unit = { modifier ->
                HomeCalendarPane(
                    doc = doc,
                    today = today,
                    currentMinute = currentMinute,
                    onOpenPlan = onOpenPlan,
                    onDefaultGroupChange = onDefaultGroupChange,
                    viewYear = viewYear,
                    viewMonth = viewMonth,
                    pagerState = pagerState,
                    roster = roster,
                    textMeasurer = textMeasurer,
                    weekStartDay = weekStartDay,
                    weekdays = weekdays,
                    strings = calendarStrings,
                    showHolidays = showHolidays,
                    showLunar = showLunar,
                    selectedDate = selectedDate,
                    onDayClick = onDayClick,
                    modifier = modifier,
                )
            }
            val summaryPane: @Composable (Modifier) -> Unit = { modifier ->
                HomeSummaryPane(
                    doc = doc,
                    roster = roster,
                    today = today,
                    date = scheduleDate,
                    showHolidays = showHolidays,
                    modifier = modifier,
                )
            }
            if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    calendarPane(
                        Modifier.weight(1.45f)
                            .verticalScroll(scrollState)
                            .scrollEndHaptic()
                            .overScrollVertical()
                    )
                    summaryPane(
                        Modifier.weight(1f)
                            .widthIn(max = 420.dp)
                            .verticalScroll(summaryScrollState)
                            .scrollEndHaptic()
                            .overScrollVertical()
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .scrollEndHaptic()
                        .overScrollVertical()
                ) {
                    calendarPane(Modifier)
                    summaryPane(Modifier)
                }
            }
        }
    }
}

@Composable
private fun HomeCalendarPane(
    doc: PlanDocument,
    today: Ymd,
    currentMinute: Int,
    onOpenPlan: () -> Unit,
    onDefaultGroupChange: (Long, Long) -> Unit,
    viewYear: Int,
    viewMonth: Int,
    pagerState: PagerState,
    roster: Roster,
    textMeasurer: TextMeasurer,
    weekStartDay: Int,
    weekdays: List<String>,
    strings: CalendarStrings,
    showHolidays: Boolean,
    showLunar: Boolean,
    selectedDate: Ymd?,
    onDayClick: (Ymd) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        RosterStatusCard(
            doc = doc,
            roster = roster,
            today = today,
            currentMinute = currentMinute,
            onClick = onOpenPlan,
            onDefaultGroupChange = onDefaultGroupChange,
        )
        Text(
            text =
                if (viewYear == today.year) {
                    stringResource(R.string.month_title, viewMonth)
                } else {
                    stringResource(R.string.month_title_year, viewYear, viewMonth)
                },
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
                    strings = strings,
                    showHolidays = showHolidays,
                    showLunar = showLunar,
                    selected = selectedDate,
                    onDayClick = onDayClick,
                )
            }
        }
    }
}

@Composable
private fun HomeSummaryPane(
    doc: PlanDocument,
    roster: Roster,
    today: Ymd,
    date: Ymd,
    showHolidays: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        GroupsScheduleCard(
            doc = doc,
            roster = roster,
            today = today,
            date = date,
            showHolidays = showHolidays,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 显示当前方案中各班组在所选日期的排班，默认显示今日。 */
@Composable
private fun GroupsScheduleCard(
    doc: PlanDocument,
    roster: Roster,
    today: Ymd,
    date: Ymd,
    showHolidays: Boolean,
) {
    val holiday =
        remember(date, showHolidays) { LegalHolidays.of(date.epochDay).takeIf { showHolidays } }
    val isOverride =
        remember(date, doc.overrides, doc.templates) {
            doc.overrides[date.epochDay.toString()]?.let(doc::templateById) != null
        }
    val weekdays = stringArrayResource(R.array.weekday_short)
    Card(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 12.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text =
                    if (date == today) {
                        stringResource(R.string.today_schedule)
                    } else {
                        stringResource(
                            R.string.date_schedule,
                            date.year,
                            date.month,
                            date.day,
                            weekdays[date.weekdayIndex],
                        )
                    },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            holiday?.let {
                Text(
                    stringResource(
                        if (it.isMakeupWorkday) R.string.makeup_workday else R.string.legal_holiday,
                        stringResource(it.name.stringRes()),
                    ),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
        }
    }
}

/** 当前方案下所有班组在指定日期的排班。 */
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
                Modifier.size(10.dp)
                    .squircleBackground(
                        color =
                            template?.let { ShiftPalette.color(it.colorArgb) }
                                ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        cornerRadius = 5.dp,
                    )
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

/** 当前倒班状态卡片；点击编辑方案，长按快速选择默认班组。 */
@Composable
private fun RosterStatusCard(
    doc: PlanDocument,
    roster: Roster,
    today: Ymd,
    currentMinute: Int,
    onClick: () -> Unit,
    onDefaultGroupChange: (Long, Long) -> Unit,
) {
    val activeScheme = doc.activeScheme()
    var showDefaultGroupDialog by remember(activeScheme?.id) { mutableStateOf(false) }
    // 保留打开时的方案，供弹窗关闭动画继续显示，并固定本次选择对应的方案 id。
    var shownScheme by remember { mutableStateOf<Scheme?>(null) }
    // 页面每分钟 tick 一次，这里跟着重组；排班推导只依赖方案与日期，缓存起来别每次重算
    val (todayShift, previousShift) =
        remember(roster, today) {
            roster.resolve(today.epochDay) to roster.resolve(today.epochDay - 1)
        }
    val isWorking =
        todayShift?.template?.let { template ->
            !template.isRest &&
                if (template.crossesMidnight()) {
                    currentMinute >= template.startMinute
                } else {
                    currentMinute in template.startMinute until template.endMinute
                }
        } == true ||
            previousShift?.template?.let { template ->
                !template.isRest && template.crossesMidnight() && currentMinute < template.endMinute
            } == true
    val status =
        when {
            isWorking -> RosterStatus.ON_SHIFT
            todayShift?.template?.isRest == true -> RosterStatus.RESTING
            else -> RosterStatus.OFF_WORK
        }
    val currentGroupName = activeScheme?.defaultGroup()?.name
    val cycleDay = roster.cycleDay(today.epochDay)
    val hasActiveScheme = activeScheme != null
    val isDarkCard = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val cardColor =
        when {
            !hasActiveScheme && isDarkCard -> ShiftPalette.statusCardInactiveDarkBackground
            !hasActiveScheme -> ShiftPalette.statusCardInactiveLightBackground
            isDarkCard -> ShiftPalette.statusCardDarkBackground
            else -> ShiftPalette.statusCardLightBackground
        }
    Card(
        modifier =
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
        // 按压位置决定倾斜方向：左侧与右侧按压会产生不同的反馈。
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        holdDownState = showDefaultGroupDialog,
        onClick = onClick,
        onLongPress =
            if (activeScheme?.groups?.isNotEmpty() == true) {
                {
                    shownScheme = activeScheme
                    showDefaultGroupDialog = true
                }
            } else {
                null
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.matchParentSize().offset(x = 52.dp, y = 32.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    imageVector =
                        if (hasActiveScheme) {
                            Icons.Rounded.CheckCircleOutline
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                    contentDescription = null,
                    modifier = Modifier.size(170.dp),
                    tint =
                        if (hasActiveScheme) {
                            ShiftPalette.statusCardIcon
                        } else {
                            ShiftPalette.statusCardIconInactive
                        },
                )
            }

            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text =
                        stringResource(
                            when {
                                !hasActiveScheme -> R.string.status_no_config
                                status == RosterStatus.ON_SHIFT -> R.string.on_shift
                                status == RosterStatus.RESTING -> R.string.on_rest
                                else -> R.string.off_work
                            }
                        ),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text =
                        cycleDay?.let {
                            stringResource(R.string.cycle_progress, it, roster.cycleLength)
                        } ?: stringResource(R.string.status_no_config_hint),
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = currentGroupName ?: stringResource(R.string.no_group),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    shownScheme?.let { scheme ->
        DefaultGroupDialog(
            groups = scheme.groups,
            currentId = scheme.defaultGroupId,
            show = showDefaultGroupDialog,
            onSelect = { groupId ->
                onDefaultGroupChange(scheme.id, groupId)
                showDefaultGroupDialog = false
            },
            onDismiss = { showDefaultGroupDialog = false },
        )
    }
}

private enum class RosterStatus {
    ON_SHIFT,
    RESTING,
    OFF_WORK,
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
    val slots =
        remember(roster, year, month, today, weekStartDay, strings, showHolidays, showLunar) {
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
    // MiuixTheme 会保留同一个 Colors 实例并更新内部字段，不能只用对象身份作为缓存键。
    val colors =
        remember(
            colorScheme.surfaceVariant,
            colorScheme.onSurface,
            colorScheme.primary,
            colorScheme.onSurfaceVariantSummary,
        ) {
            GridColors(
                surfaceVariant = colorScheme.surfaceVariant,
                onSurface = colorScheme.onSurface,
                primary = colorScheme.primary,
                onSurfaceVariantSummary = colorScheme.onSurfaceVariantSummary,
            )
        }
    val baseTextStyle = MiuixTheme.textStyles.main
    val cellText = remember(colors, baseTextStyle) { CellTextStyles(colors, baseTextStyle) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
                        // 当前选中的日期保持高亮，与排班卡片对应
                        heldDown =
                            selected != null &&
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

/** 单元格文字样式（含淡化变体），随主题预生成。 直接画文字（而不是放 Text 组合项）能把每页多组文字排版压到一次， 让文字测量缓存能跨格/跨月复用缓存。 */
private class CellTextStyles(colors: GridColors, base: TextStyle) {
    val surfaceVariant = colors.surfaceVariant
    val onSurface = colors.onSurface
    val primary = colors.primary
    val day = base.merge(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
    val dayToday =
        base.merge(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
    val dayFaded =
        base.merge(
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface.faded(0.35f),
        )
    val dayTodayFaded =
        base.merge(
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface.faded(0.35f),
        )
    val lunar = base.merge(fontSize = 9.sp, color = colors.onSurfaceVariantSummary)
    val holiday = base.merge(fontSize = 9.sp, color = colors.onSurfaceVariantSummary)
    val name = base.merge(fontSize = 9.sp)
}

private val CELL_RADIUS = 8.dp

/** 班次色淡底的不透明度：太淡在浅色/深色下都看不出配色，22% 时名称与日期仍分别有 ≥6.4:1、≥7.7:1 */
private const val CELL_TINT_ALPHA = 0.22f

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
        val lunar =
            if (showHolidays || showLunar) {
                lunarDate(date)
            } else {
                null
            }
        val isToday = y == today.year && m == today.month && day == today.day
        slots +=
            DaySlot(
                year = y,
                month = m,
                day = day,
                template = roster.templateFor(date.epochDay),
                isToday = isToday,
                fade = if (!inMonth && !isToday) 0.35f else 1f,
                holiday = LegalHolidays.of(date.epochDay).takeIf { showHolidays },
                lunarLabel =
                    lunar?.takeIf { showLunar }?.let { lunarLabel(it, strings.lunar) }.orEmpty(),
                holidayName =
                    lunar
                        ?.takeIf { showHolidays }
                        ?.let { holidayNameRes(date, it) }
                        ?.let(strings.holidayNames::get),
            )
    }
    return slots
}

private data class CalendarStrings(
    val lunar: LunarStrings,
    val holidayNames: Map<Int, String>,
)

/** 需要按名字显示的节日；顺序只影响缓存表的构建，不影响显示。 */
private val HOLIDAY_NAME_IDS =
    listOf(
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
    )

/**
 * squircle 满格块（对齐 miuix 组件的连续圆角）。 今天 = 常规底色 + 主题色描边着重（不随其他月淡化）；普通日 = 班次浅色底；无班次 = 浅灰块。
 *
 * 底色与涟漪由 [squircleSurface] 负责（不是 RoundedCornerShape 的 clip/background）， 多行文字仍在一个 drawWithCache
 * 里直接画：省掉每格多个布局节点，测量结果也能跨格/跨月复用缓存。
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
        shiftColor?.copy(alpha = CELL_TINT_ALPHA)?.faded(fade) ?: text.surfaceVariant.faded(fade)
    val dayStyle =
        when {
            slot.isToday && fade < 1f -> text.dayTodayFaded
            slot.isToday -> text.dayToday
            fade < 1f -> text.dayFaded
            else -> text.day
        }
    val dayText = slot.day.toString()
    val dayLayout = remember(measurer, dayText, dayStyle) { measurer.measure(dayText, dayStyle) }
    val lunarStyle = text.lunar.copy(color = text.lunar.color.faded(fade))
    val lunarLayout =
        if (slot.holidayName == null && slot.lunarLabel.isNotEmpty()) {
            remember(measurer, slot.lunarLabel, lunarStyle) {
                measurer.measure(slot.lunarLabel, lunarStyle, maxLines = 1)
            }
        } else {
            null
        }
    val holidayStyle = text.holiday.copy(color = text.holiday.color.faded(fade))
    val holidayLayout =
        if (slot.holidayName != null) {
            val holidayText = slot.holidayName
            remember(measurer, holidayText, holidayStyle) {
                measurer.measure(holidayText, holidayStyle, maxLines = 1)
            }
        } else {
            null
        }
    val nameLayout =
        if (template != null) {
            // 跟小组件同一套墨色：亮色主题往黑偏、深色主题往白偏，两种主题都保得住对比度
            val nameColor = lerp(shiftColor!!, text.onSurface, 0.62f).faded(fade)
            val nameStyle = text.name.copy(color = nameColor)
            remember(measurer, template.name, nameStyle) {
                measurer.measure(template.name, nameStyle, maxLines = 1)
            }
        } else {
            null
        }
    // 今天用主题色描边着重；squircleBorder 自带"描边内缩半个线宽"，与 Modifier.border 一致
    val todayBorder =
        if (slot.isToday) {
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
                val totalHeight =
                    dayLayout.size.height +
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
                    lunarLayout?.let { drawText(it, topLeft = Offset(lunarX!!, lunarY!!)) }
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
            val badge =
                remember(holiday.isMakeupWorkday, fade) {
                    if (holiday.isMakeupWorkday) {
                        HolidayPalette.makeupBadge.faded(fade) to
                            HolidayPalette.makeupOnBadge.faded(fade)
                    } else {
                        HolidayPalette.restBadge.faded(fade) to
                            HolidayPalette.restOnBadge.faded(fade)
                    }
                }
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(3.dp)
                    .squircleBackground(color = badge.first, cornerRadius = 3.dp)
                    .padding(horizontal = 2.5.dp, vertical = 0.5.dp)
            ) {
                Text(
                    stringResource(
                        if (holiday.isMakeupWorkday) R.string.holiday_work_badge
                        else R.string.holiday_rest_badge
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
