package win.zuoye.dao.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.util.SizeF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme
import win.zuoye.dao.R
import win.zuoye.dao.data.LegalHolidays
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.ThemeMode
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.domain.Roster
import win.zuoye.dao.ui.HolidayPalette
import win.zuoye.dao.ui.ShiftPalette
import win.zuoye.dao.ui.faded

internal data class WidgetRendering(
    val bitmap: Bitmap,
    val description: String,
    val actionColor: Int,
)

/** 班次色淡底的不透明度：与日历格保持一致，13% 在浅色和深色下都太淡。 */
private const val SHIFT_TINT_ALPHA = 0.22f

/** 日期格一行需要的全部数据：底色循环与文字循环共用，不再各查一遍。 */
private data class WidgetDayRow(
    val date: Ymd,
    val template: ShiftTemplate?,
    val holiday: LegalHolidays.HolidayDay?,
    val detail: String?,
    val fade: Float,
)

/** 一次小组件更新里与尺寸无关的部分：排班索引、农历/节日文案、今日班组排班，整个更新只算一次。 */
internal class WidgetRenderInputs(
    val roster: Roster,
    val labels: WidgetDateLabels,
    val todayTemplate: ShiftTemplate?,
    val todayTitle: String,
    val todayTime: String,
    val groupShifts: List<WidgetGroupShift>,
) {
    companion object {
        fun of(context: Context, document: PlanDocument, today: Ymd): WidgetRenderInputs {
            val roster = Roster.of(document)
            val todayTemplate = roster.templateFor(today.epochDay)
            return WidgetRenderInputs(
                roster = roster,
                labels =
                    WidgetDateLabels(
                        context,
                        document.calendarViewMode.showHolidays,
                        document.calendarViewMode.showLunar,
                    ),
                todayTemplate = todayTemplate,
                todayTitle = context.getString(R.string.widget_today_date, today.month, today.day),
                todayTime = todayTemplate.timeText(context),
                groupShifts = widgetGroupShifts(document, today, roster),
            )
        }
    }
}

/** RemoteViews 承载位图；使用 miuix 公开的配色、字体尺度和路径 API 绘制日历。 */
internal fun renderRosterWidget(
    context: Context,
    document: PlanDocument,
    today: Ymd,
    month: Int,
    week: Ymd,
    size: SizeF,
    scale: Float,
    layout: WidgetLayout,
    inputs: WidgetRenderInputs,
): WidgetRendering {
    val dark =
        when (document.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM ->
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
        }
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    val bitmap =
        createBitmap(
            (size.width * scale).roundToInt().coerceAtLeast(1),
            (size.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
    val canvas = Canvas(bitmap).apply { scale(scale, scale) }
    val painter = WidgetPainter(canvas, colors, context.resources.configuration.fontScale)
    painter.surface(
        0f,
        0f,
        size.width,
        size.height,
        CardDefaults.CornerRadius.value,
        colors.surfaceContainer,
    )
    val roster = inputs.roster
    val title = inputs.todayTitle
    val time = inputs.todayTime
    val monthly = layout.navigationKind == RosterWidgetKind.MONTH
    val compactWeek = layout == WidgetLayout.COMPACT_WEEK
    val groupShifts = if (layout.hasGroups) inputs.groupShifts else emptyList()
    if (layout.hasHeader) {
        val headerWidth = (size.width - 24f - 80f).coerceAtLeast(1f)
        painter.header(title, time, headerWidth)
    }
    val monthDate = if (monthly) widgetMonthDate(month) else week
    val monthTitle = context.getString(R.string.widget_month_title, monthDate.year, monthDate.month)
    val showMonthBackground = monthly || layout.weeks > 1
    val weekdayY = if (compactWeek) 14f else 57f
    val gridTop = if (compactWeek) 24f else 68f
    val dates =
        widgetDates(
            layout.navigationKind,
            if (compactWeek) today else week,
            month,
            document.weekStartDay,
            layout.weeks,
        )
    val rows = dates.size / 7
    val cellWidth = ((size.width - 24f) / 7f).coerceAtLeast(1f)
    val bottomPadding = if (compactWeek || layout.hasGroups) 6f else 12f
    val gridSpace = (size.height - gridTop - bottomPadding).coerceAtLeast(1f)
    val footerGap = if (layout.hasGroups) 4f else 0f
    // 底部按班组行数和字号预留紧凑高度，其余空间交给日期网格伸缩。
    val footerSpace =
        if (layout.hasGroups)
            minOf(painter.groupShiftsHeight(groupShifts.size, size.width - 24f), gridSpace * 0.4f)
        else 0f
    val cellHeight = ((gridSpace - footerSpace - footerGap) / rows).coerceAtLeast(1f)
    val weekdays = context.resources.getStringArray(R.array.weekday_short)
    val showHolidays = document.calendarViewMode.showHolidays
    // 日期格数据（班次、节日、农历/节日文字、淡化系数）一次算好，两个绘制循环共用
    val dayRows = dates.map { date ->
        val holiday = LegalHolidays.of(date.epochDay).takeIf { showHolidays }
        WidgetDayRow(
            date = date,
            template = roster.templateFor(date.epochDay),
            holiday = holiday,
            detail = inputs.labels.label(date, holiday),
            fade = if (monthly && date.widgetMonth() != month && date != today) 0.35f else 1f,
        )
    }
    val description = StringBuilder().append(title).append(' ').append(time)
    if (showMonthBackground) description.append(' ').append(monthTitle)
    repeat(7) { column ->
        painter.text(
            weekdays[Math.floorMod(document.weekStartDay + column, 7)],
            12f + (column + 0.5f) * cellWidth,
            weekdayY,
            if (compactWeek) 11f else 13f,
            colors.onSurfaceVariantSummary,
            cellWidth - 2f,
        )
    }
    // 日期格底色 → 月份背景数字 → 日期与班次文字，保持前景信息清晰。
    dayRows.forEachIndexed { index, day ->
        painter.dayBackground(
            day.template,
            day.fade,
            12f + index % 7 * cellWidth + 1.5f,
            gridTop + index / 7 * cellHeight + 1.5f,
            (cellWidth - 3f).coerceAtLeast(1f),
            (cellHeight - 3f).coerceAtLeast(1f),
        )
    }
    if (showMonthBackground)
        painter.monthBackground(
            monthDate.month,
            12f,
            gridTop,
            size.width - 24f,
            rows * cellHeight,
        )
    dayRows.forEachIndexed { index, day ->
        painter.dayContent(
            context,
            day.date,
            day.template,
            // 一行高度优先展示日期与班次；拉高组件后恢复农历/节日名称。
            day.detail.takeUnless { compactWeek },
            day.holiday,
            day.date == today,
            day.fade,
            12f + index % 7 * cellWidth + 1.5f,
            gridTop + index / 7 * cellHeight + 1.5f,
            (cellWidth - 3f).coerceAtLeast(1f),
            (cellHeight - 3f).coerceAtLeast(1f),
        )
        description
            .append("; ")
            .append(context.getString(R.string.widget_today_date, day.date.month, day.date.day))
            .append(' ')
            .append(day.template?.name ?: context.getString(R.string.no_schedule))
            .append(' ')
            .append(day.template.timeText(context))
        day.detail?.let { description.append(' ').append(it) }
        day.holiday?.let {
            description
                .append(' ')
                .append(
                    context.getString(
                        if (it.isMakeupWorkday) R.string.holiday_work_badge
                        else R.string.holiday_rest_badge
                    )
                )
        }
    }
    if (layout.hasGroups) {
        val footerTop = gridTop + rows * cellHeight + footerGap
        val footerHeight = (size.height - footerTop - bottomPadding).coerceAtLeast(1f)
        if (groupShifts.isEmpty()) {
            val emptyMessage =
                context.getString(
                    if (document.activeScheme() == null) R.string.no_plan else R.string.no_group
                )
            painter.text(
                emptyMessage,
                size.width / 2f,
                footerTop + footerHeight / 2f,
                13f,
                colors.onSurfaceVariantSummary,
                size.width - 24f,
            )
            description.append("; ").append(emptyMessage)
        } else {
            painter.groupShifts(
                context,
                groupShifts,
                12f,
                footerTop,
                size.width - 24f,
                footerHeight,
            )
            description.append("; ").append(context.getString(R.string.widget_groups_today))
            groupShifts.forEach {
                description
                    .append("; ")
                    .append(it.group.name)
                    .append(' ')
                    .append(it.template?.name ?: context.getString(R.string.no_schedule))
                    .append(' ')
                    .append(it.template.timeText(context))
            }
        }
    }
    return WidgetRendering(bitmap, description.toString(), colors.onSurfaceVariantActions.toArgb())
}

/** 班次色画在卡片底色上的墨色版本：与 onSurface 混 62%，保证小字号对比度。 */
private fun shiftInk(color: Color, onSurface: Color): Color = lerp(color, onSurface, 0.62f)

private fun ShiftTemplate?.timeText(context: Context): String {
    if (this == null) return context.getString(R.string.no_schedule)
    if (isRest) return context.getString(R.string.shift_rest_time)
    val end = ShiftTemplate.format(endMinute)
    return "${ShiftTemplate.format(startMinute)}–${if (crossesMidnight()) context.getString(R.string.shift_next_day, end) else end}"
}

private class WidgetPainter(
    private val canvas: Canvas,
    private val colors: Colors,
    private val fontScale: Float,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dayCornerRadius = 8f
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val typography = defaultTextStyles()
    private val groupLabelSize = typography.footnote2.fontSize.value
    private val groupCaptionSize = 9f
    private val groupContentHeight = groupLabelSize * 1.2f + groupCaptionSize * 1.2f + 2f

    fun surface(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Color,
        stroke: Float = 0f,
    ) {
        val path =
            Path().apply {
                addSquircleRect(
                    width,
                    height,
                    radius,
                    squircleEnabled = Build.VERSION.SDK_INT >= 33,
                )
            }
        paint.color = color.toArgb()
        paint.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke
        canvas.withTranslation(x, y) { drawPath(path.asAndroidPath(), paint) }
    }

    fun header(date: String, time: String, width: Float) {
        val dateSize = typography.title4.fontSize.value
        val timeSize = typography.footnote2.fontSize.value
        val headerScale = minOf(fontScale, 1.2f)
        configureText(dateSize, colors.onSurface, true, headerScale)
        val dateScale =
            headerScale * minOf(1f, width / textPaint.measureText(date).coerceAtLeast(1f))
        configureText(timeSize, colors.onSurfaceVariantSummary, false, headerScale)
        val timeScale =
            headerScale * minOf(1f, width / textPaint.measureText(time).coerceAtLeast(1f))
        text(
            date,
            12f,
            18f,
            dateSize,
            colors.onSurface,
            width,
            true,
            centered = false,
            localScale = dateScale,
        )
        text(
            time,
            12f,
            37f,
            timeSize,
            colors.onSurfaceVariantSummary,
            width,
            centered = false,
            localScale = timeScale,
        )
    }

    fun monthBackground(month: Int, x: Float, y: Float, width: Float, height: Float) {
        val number = month.toString()
        val color = colors.onSurface.faded(0.065f)
        configureText(1f, color, true, localScale = 1f)
        val metrics = textPaint.fontMetrics
        val size =
            minOf(
                width * 0.85f / textPaint.measureText(number).coerceAtLeast(1f),
                height * 0.95f / (metrics.descent - metrics.ascent).coerceAtLeast(1f),
            )
        text(number, x + width / 2f, y + height / 2f, size, color, width, true, localScale = 1f)
    }

    fun text(
        value: String,
        x: Float,
        centerY: Float,
        size: Float,
        color: Color,
        maxWidth: Float,
        bold: Boolean = false,
        centered: Boolean = true,
        localScale: Float = fontScale,
    ) {
        configureText(size, color, bold, localScale)
        val limit = maxWidth.coerceAtLeast(1f)
        // 绝大多数文字都没超宽，先量一次就省掉 ellipsize 的字符串分配
        val fitted =
            if (textPaint.measureText(value) <= limit) value
            else TextUtils.ellipsize(value, textPaint, limit, TextUtils.TruncateAt.END).toString()
        textPaint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        val baseline = centerY - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(fitted, x, baseline, textPaint)
    }

    private fun groupRows(count: Int, width: Float): Int {
        val capacity = (width / (32f * fontScale.coerceAtLeast(1f))).toInt().coerceAtLeast(1)
        return ((count + capacity - 1) / capacity).coerceAtLeast(1)
    }

    fun groupShiftsHeight(count: Int, width: Float): Float =
        if (count == 0) 24f * fontScale
        else groupRows(count, width) * (groupContentHeight * fontScale + 4f)

    fun groupShifts(
        context: Context,
        groups: List<WidgetGroupShift>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        // 每行等分整行宽度；班组较多时换行，保留全部班组。
        val rows = groupRows(groups.size, width)
        val columns = (groups.size + rows - 1) / rows
        val labelSize = groupLabelSize
        val captionSize = groupCaptionSize
        val contentHeight = groupContentHeight
        val rowHeight = minOf(height / rows, contentHeight * fontScale + 4f)
        val fit = minOf(fontScale, ((rowHeight - 4f) / contentHeight).coerceAtLeast(0.1f))
        val contentTop = y + (height - rows * rowHeight) / 2f
        groups.forEachIndexed { index, shift ->
            val row = index / columns
            val rowColumns = minOf(columns, groups.size - row * columns)
            val columnWidth = width / rowColumns
            val top = contentTop + row * rowHeight + (rowHeight - contentHeight * fit) / 2f
            val labelY = top + labelSize * 0.6f * fit
            val shiftY = top + (labelSize * 1.2f + 2f + captionSize * 0.6f) * fit
            val centerX = x + (index % columns + 0.5f) * columnWidth
            text(
                shift.group.name,
                centerX,
                labelY,
                labelSize,
                colors.onSurface,
                columnWidth - 4f,
                localScale = fit,
            )
            val shiftColor =
                shift.template?.let { shiftInk(ShiftPalette.color(it.colorArgb), colors.onSurface) }
                    ?: colors.onSurfaceVariantSummary
            text(
                shift.template?.name ?: context.getString(R.string.no_schedule),
                centerX,
                shiftY,
                captionSize,
                shiftColor,
                columnWidth - 4f,
                localScale = fit,
            )
        }
    }

    fun dayBackground(
        template: ShiftTemplate?,
        fade: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val shift = template?.let { ShiftPalette.color(it.colorArgb) }
        val background =
            shift?.copy(alpha = SHIFT_TINT_ALPHA)?.compositeOver(colors.surfaceContainer)
                ?: colors.surfaceVariant
        surface(
            x,
            y,
            width,
            height,
            dayCornerRadius,
            background.faded(fade),
        )
    }

    fun dayContent(
        context: Context,
        date: Ymd,
        template: ShiftTemplate?,
        detail: String?,
        holiday: LegalHolidays.HolidayDay?,
        today: Boolean,
        fade: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val shift = template?.let { ShiftPalette.color(it.colorArgb) }
        if (today)
            surface(
                x + 1f,
                y + 1f,
                (width - 2f).coerceAtLeast(1f),
                (height - 2f).coerceAtLeast(1f),
                dayCornerRadius - 1f,
                colors.primary,
                2f,
            )
        val lineCount = 1 + (if (template != null) 1 else 0) + (if (detail != null) 1 else 0)
        val daySize = typography.main.fontSize.value
        // 密集日历的辅助字号沿用主页的 9sp；文本整体按格高收缩，避免六周月份截字。
        val captionSize = 9f
        val total = daySize * 1.2f + (lineCount - 1) * (captionSize * 1.2f + 1f)
        val fitScale = minOf(fontScale, ((height - 6f) / total).coerceAtLeast(0.1f))
        var nextY = y + (height - total * fitScale) / 2f
        val dayColor = colors.onSurface.faded(fade)
        text(
            date.day.toString(),
            x + width / 2,
            nextY + daySize * 0.6f * fitScale,
            daySize,
            dayColor,
            width - 4f,
            today,
            localScale = fitScale,
        )
        nextY += (daySize * 1.2f + 1f) * fitScale
        template?.let {
            val nameColor = shiftInk(shift!!, colors.onSurface).faded(fade)
            text(
                it.name,
                x + width / 2,
                nextY + captionSize * 0.6f * fitScale,
                captionSize,
                nameColor,
                width - 4f,
                localScale = fitScale,
            )
            nextY += (captionSize * 1.2f + 1f) * fitScale
        }
        detail?.let {
            text(
                it,
                x + width / 2,
                nextY + captionSize * 0.6f * fitScale,
                captionSize,
                colors.onSurfaceVariantSummary.faded(fade),
                width - 4f,
                localScale = fitScale,
            )
        }
        holiday?.let {
            val badgeSize = minOf(13f, height * 0.32f, width * 0.36f)
            val badgeX = x + width - badgeSize - 2f
            val badgeY = y + 2f
            val backgroundColor =
                if (it.isMakeupWorkday) HolidayPalette.makeupBadge else HolidayPalette.restBadge
            val foregroundColor =
                if (it.isMakeupWorkday) HolidayPalette.makeupOnBadge else HolidayPalette.restOnBadge
            surface(
                badgeX,
                badgeY,
                badgeSize,
                badgeSize,
                3f,
                backgroundColor.faded(fade),
            )
            text(
                context.getString(
                    if (it.isMakeupWorkday) R.string.holiday_work_badge
                    else R.string.holiday_rest_badge
                ),
                badgeX + badgeSize / 2,
                badgeY + badgeSize / 2,
                captionSize,
                foregroundColor.faded(fade),
                badgeSize - 2f,
                true,
                localScale = minOf(1f, badgeSize / 13f),
            )
        }
    }

    private fun configureText(
        size: Float,
        color: Color,
        bold: Boolean,
        localScale: Float = fontScale,
    ) {
        textPaint.textSize = size * localScale
        textPaint.color = color.toArgb()
        textPaint.typeface = if (bold) boldTypeface else normalTypeface
    }

    private companion object {
        // 一个月视图要画近百段文字，Typeface 每次重建既慢又产生垃圾
        private val boldTypeface = Typeface.create("sans-serif", Typeface.BOLD)
        private val normalTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
