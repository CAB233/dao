package win.zuoye.dao.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
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
import kotlinx.coroutines.launch
import win.zuoye.dao.data.LegalHolidays
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.R
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.domain.Roster
import win.zuoye.dao.domain.resolveShift
import win.zuoye.dao.ui.HolidayPalette
import win.zuoye.dao.ui.ShiftPalette
import win.zuoye.dao.ui.common.WEEKDAY_LABELS
import win.zuoye.dao.ui.common.rememberHoldDownSource
import kotlin.math.roundToInt

/** 可浏览的月份范围：2000-01 .. 2100-12 */
private const val BASE_YEAR = 2000
private const val MONTH_COUNT = 101 * 12

/** 首页：月历视图，按班次颜色着色。月份左右滑动切换（对齐小米日历）。 */
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
    val weekStartDay = doc.weekStartDay.coerceIn(0, WEEKDAY_LABELS.lastIndex)

    // 排班索引整个页面共用一份，预组合的三页不会各建一份
    val roster = remember(doc) { Roster.of(doc) }
    // 文字测量缓存也整页共用：相邻月份的日期/班次名高度重合，命中率很高
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = onExportPlan) {
                        Icon(MiuixIcons.Regular.Share, contentDescription = "分享排班方案")
                    }
                },
            )
        },
        floatingActionButton = {
            // 小米日历风格：滑到别的月份时，右下角出现蓝色圆形「今」，一键回到今天
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
                        Text("今", fontSize = 22.sp, color = MiuixTheme.colorScheme.onPrimary)
                    }
                }
            }
        },
        // 底部空间由外层底栏占据，这里不再重复算导航栏内边距
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize(),
        ) {
            RosterStatusCard(
                doc = doc,
                today = today,
                onClick = onOpenPlan,
            )

            // 左对齐月份标题（小米日历式）：大字月份 + 相对今天的天数
            val targetDay = minOf(today.day, Ymd.daysInMonth(viewYear, viewMonth))
            val diffDays = (Ymd(viewYear, viewMonth, targetDay).epochDay - today.epochDay).toInt()
            val relativeLabel = when {
                diffDays > 0 -> "${diffDays}天后"
                diffDays < 0 -> "${-diffDays}天前"
                else -> ""
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("${viewMonth}月", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (relativeLabel.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        relativeLabel,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                val year = BASE_YEAR + page / 12
                val month = page % 12 + 1
                Column(Modifier.fillMaxSize()) {
                    MonthGrid(
                        roster = roster,
                        measurer = textMeasurer,
                        year = year,
                        month = month,
                        today = today,
                        weekStartDay = weekStartDay,
                        selected = selectedDate,
                        onDayClick = { selectedDate = it },
                    )
                    if (doc.activeScheme() == null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "尚未配置倒班方案：点底部「设置」新建一个，日历会按周期自动着色。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }

        // 对话框必须挂在 Scaffold 内部（依赖 Scaffold 提供的弹层宿主）。
        // 常驻组合、用 show 驱动进出动画；退出动画期间还要继续渲染，所以记住最后点开的那一天。
        var detailDate by remember { mutableStateOf<Ymd?>(null) }
        LaunchedEffect(selectedDate) { selectedDate?.let { detailDate = it } }
        detailDate?.let { date ->
            DayDetailDialog(
                date = date,
                doc = doc,
                show = selectedDate != null,
                onDismiss = { selectedDate = null },
            )
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
                    text = "倒班中",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "方案：${activeScheme?.name ?: "未选择"}",
                    fontSize = 15.sp,
                )
            }

            Text(
                text = todayShift?.template?.name ?: "暂无班次",
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
    selected: Ymd?,
    onDayClick: (Ymd) -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val slots = remember(roster, year, month, today, weekStartDay) {
        buildMonthSlots(roster, year, month, today, weekStartDay)
    }
    val colors = remember(colorScheme) {
        GridColors(
            surfaceVariant = colorScheme.surfaceVariant,
            onSurface = colorScheme.onSurface,
            primary = colorScheme.primary,
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
                    text = "周${WEEKDAY_LABELS[(weekStartDay + column) % WEEKDAY_LABELS.size]}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        repeat(CELL_ROWS) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val slot = slots[index]
                    CalendarCell(
                        slot = slot,
                        text = cellText,
                        measurer = measurer,
                        // 详情弹窗打开期间，那一格保持按住高亮（MIUI 惯例）
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

/** 固定 6 行（42 格），保证每一页大小一致；空白位由前后月补位 */
private const val CELL_ROWS = 6

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
)

/** 日历配色，一屏读一次主题，避免 42 个格子各读一次 CompositionLocal */
private class GridColors(
    val surfaceVariant: Color,
    val onSurface: Color,
    val primary: Color,
)

/**
 * 单元格文字样式（含淡化变体），随主题预生成。
 * 直接画文字（而不是放 Text 组合项）能把每页 84 次文字排版压到一次，
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
    val name = base.merge(fontSize = 9.sp)
}

private val CELL_RADIUS = 14.dp

private fun buildMonthSlots(
    roster: Roster,
    year: Int,
    month: Int,
    today: Ymd,
    weekStartDay: Int,
): List<DaySlot> {
    val daysInMonth = Ymd.daysInMonth(year, month)
    val firstOffset = Math.floorMod(Ymd(year, month, 1).weekdayIndex - weekStartDay, 7)
    val slots = ArrayList<DaySlot>(CELL_ROWS * 7)
    for (index in 0 until CELL_ROWS * 7) {
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
        val isToday = y == today.year && m == today.month && day == today.day
        slots += DaySlot(
            year = y,
            month = m,
            day = day,
            template = roster.templateFor(Ymd.ymdToEpochDay(y, m, day)),
            isToday = isToday,
            fade = if (!inMonth && !isToday) 0.35f else 1f,
            holiday = LegalHolidays.of(Ymd.ymdToEpochDay(y, m, day)),
        )
    }
    return slots
}
private fun darken(c: Color, f: Float = 0.62f) =
    Color(c.red * f, c.green * f, c.blue * f, 1f)

/** 透明度整体乘一个系数（替代 Modifier.alpha，省掉一格一个离屏图层） */
private fun Color.faded(f: Float): Color = copy(alpha = alpha * f)

/**
 * 单元格样式对齐小米日历：squircle 满格块（对齐 miuix 组件的连续圆角）。
 * 今天 = 常规底色 + 主题色描边着重（不随其他月淡化）；普通日 = 班次浅色底；无班次 = 浅灰块。
 *
 * 底色与涟漪由 [squircleSurface] 负责（不是 RoundedCornerShape 的 clip/background），
 * 两行文字仍在一个 drawWithCache 里直接画：省掉每格两个布局节点，测量结果也能跨格/跨月复用缓存。
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

    Box(
        modifier
            .padding(1.5.dp)
            .aspectRatio(0.9f)
            .squircleSurface(color = container, cornerRadius = CELL_RADIUS)
            .then(todayBorder)
            .drawWithCache {
                val totalHeight = dayLayout.size.height + (nameLayout?.size?.height ?: 0)
                // 取整到整像素，贴近 Compose 布局的整数摆放（半像素会带来可见的字形差异）
                val top = ((size.height - totalHeight) / 2f).roundToInt().toFloat()
                val dayX = ((size.width - dayLayout.size.width) / 2f).roundToInt().toFloat()
                val nameX =
                    nameLayout?.let { ((size.width - it.size.width) / 2f).roundToInt().toFloat() } ?: 0f
                val nameY = top + dayLayout.size.height

                onDrawBehind {
                    drawText(dayLayout, topLeft = Offset(dayX, top))
                    if (nameLayout != null) {
                        drawText(nameLayout, topLeft = Offset(nameX, nameY))
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
        // 法定节假日角标（小米日历式，右上角「休」/「班」）：颜色预乘淡化系数，避免离屏 alpha 图层
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
                    if (holiday.isMakeupWorkday) "班" else "休",
                    fontSize = 8.sp,
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
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val resolved = resolveShift(doc, date.epochDay)
    val holiday = remember(date) { LegalHolidays.of(date.epochDay) }
    OverlayDialog(
        show = show,
        title = "${date.year}年${date.month}月${date.day}日 · 周${WEEKDAY_LABELS[date.weekdayIndex]}",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            holiday?.let {
                Text(
                    if (it.isMakeupWorkday) "${it.name} · 调休上班" else "${it.name} · 法定假日",
                    fontSize = 13.sp,
                    color = if (it.isMakeupWorkday) HolidayPalette.makeupBadge else HolidayPalette.restBadge,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (resolved == null) {
                Text("该日期暂无排班信息。", fontSize = 15.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(16.dp)
                            .background(ShiftPalette.color(resolved.template.colorArgb), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(resolved.template.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (!resolved.template.isRest) {
                        Text(
                            resolved.template.timeRangeText(),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (resolved.isOverride) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "（换班覆盖）",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
