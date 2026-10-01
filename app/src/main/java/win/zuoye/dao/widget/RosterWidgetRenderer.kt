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

internal data class WidgetRendering(
    val bitmap: Bitmap,
    val description: String,
    val actionColor: Int,
)

/** RemoteViews 承载位图；使用 miuix 公开的配色、字体尺度和路径 API 绘制日历。 */
internal fun renderRosterWidget(
    context: Context,
    document: PlanDocument,
    kind: RosterWidgetKind,
    today: Ymd,
    month: Int,
    size: SizeF,
    scale: Float,
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
    val roster = Roster.of(document)
    val currentTemplate = roster.templateFor(today.epochDay)
    val title = context.getString(R.string.widget_today_date, today.month, today.day)
    val time = currentTemplate.timeText(context)
    val monthly = kind == RosterWidgetKind.MONTH
    val compactWeek = !monthly && size.height < 130f
    if (!compactWeek) {
        val headerWidth = (size.width - 24f - if (monthly) 80f else 0f).coerceAtLeast(1f)
        painter.header(title, time, headerWidth, centerY = 29f)
    }
    val monthDate = widgetMonthDate(month)
    val monthTitle = context.getString(R.string.widget_month_title, monthDate.year, monthDate.month)
    if (monthly)
        painter.text(
            monthTitle,
            size.width / 2f,
            62f,
            13f,
            colors.onSurfaceVariantSummary,
            size.width - 24f,
        )
    val weekdayY =
        when {
            monthly -> 84f
            compactWeek -> 14f
            else -> 62f
        }
    val gridTop =
        when {
            monthly -> 98f
            compactWeek -> 24f
            else -> 76f
        }
    val dates = widgetDates(kind, today, month, document.weekStartDay)
    val rows = dates.size / 7
    val cellWidth = ((size.width - 24f) / 7f).coerceAtLeast(1f)
    val bottomPadding = if (compactWeek) 6f else 12f
    val cellHeight = ((size.height - gridTop - bottomPadding) / rows).coerceAtLeast(1f)
    val weekdays = context.resources.getStringArray(R.array.weekday_short)
    val labels =
        WidgetDateLabels(
            context,
            document.calendarViewMode.showHolidays,
            document.calendarViewMode.showLunar,
            today.year,
        )
    val description = StringBuilder().append(title).append(' ').append(time)
    if (monthly) description.append(' ').append(monthTitle)
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
    dates.forEachIndexed { index, date ->
        val template = roster.templateFor(date.epochDay)
        val holiday =
            LegalHolidays.of(date.epochDay).takeIf { document.calendarViewMode.showHolidays }
        val detail = labels.label(date)
        painter.day(
            context,
            date,
            template,
            // 一行高度优先展示日期与班次；拉高组件后恢复农历/节日名称。
            detail.takeUnless { compactWeek },
            holiday,
            date == today,
            if (monthly && date.widgetMonth() != month && date != today) 0.35f else 1f,
            12f + index % 7 * cellWidth + 1.5f,
            gridTop + index / 7 * cellHeight + 1.5f,
            (cellWidth - 3f).coerceAtLeast(1f),
            (cellHeight - 3f).coerceAtLeast(1f),
        )
        description
            .append("; ")
            .append(context.getString(R.string.widget_today_date, date.month, date.day))
            .append(' ')
            .append(template?.name ?: context.getString(R.string.no_schedule))
            .append(' ')
            .append(template.timeText(context))
        detail?.let { description.append(' ').append(it) }
        holiday?.let {
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
    return WidgetRendering(bitmap, description.toString(), colors.onSurfaceVariantActions.toArgb())
}

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
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val typography = defaultTextStyles()

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

    fun header(date: String, time: String, width: Float, centerY: Float) {
        val dateSize = typography.title4.fontSize.value
        configureText(dateSize, colors.onSurface, true)
        // 根据可用宽度一起缩放日期和时间，保留跨夜班次的完整时间范围。
        val dateWidth = textPaint.measureText(date)
        configureText(typography.footnote2.fontSize.value, colors.onSurfaceVariantSummary, false)
        val timeWidth = textPaint.measureText(time)
        val fit =
            minOf(1f, (width - 8f).coerceAtLeast(1f) / (dateWidth + timeWidth).coerceAtLeast(1f))
        text(
            date,
            12f,
            centerY,
            dateSize,
            colors.onSurface,
            dateWidth * fit,
            true,
            centered = false,
            localScale = fontScale * fit,
        )
        text(
            time,
            12f + dateWidth * fit + 8f,
            centerY,
            typography.footnote2.fontSize.value,
            colors.onSurfaceVariantSummary,
            (width - dateWidth * fit - 8f).coerceAtLeast(1f),
            centered = false,
            localScale = fontScale * fit,
        )
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
        val fitted =
            TextUtils.ellipsize(
                    value,
                    textPaint,
                    maxWidth.coerceAtLeast(1f),
                    TextUtils.TruncateAt.END,
                )
                .toString()
        textPaint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        val baseline = centerY - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(fitted, x, baseline, textPaint)
    }

    fun day(
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
        val background =
            shift?.copy(alpha = 0.13f)?.compositeOver(colors.surfaceContainer)
                ?: colors.surfaceVariant
        surface(x, y, width, height, 14f, background.copy(alpha = background.alpha * fade))
        if (today)
            surface(
                x + 1f,
                y + 1f,
                (width - 2f).coerceAtLeast(1f),
                (height - 2f).coerceAtLeast(1f),
                13f,
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
        val dayColor = colors.onSurface.copy(alpha = colors.onSurface.alpha * fade)
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
            val nameColor =
                lerp(shift!!, colors.onSurface, 0.62f).let { color ->
                    color.copy(alpha = color.alpha * fade)
                }
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
                colors.onSurfaceVariantSummary.copy(
                    alpha = colors.onSurfaceVariantSummary.alpha * fade
                ),
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
                backgroundColor.copy(alpha = backgroundColor.alpha * fade),
            )
            text(
                context.getString(
                    if (it.isMakeupWorkday) R.string.holiday_work_badge
                    else R.string.holiday_rest_badge
                ),
                badgeX + badgeSize / 2,
                badgeY + badgeSize / 2,
                captionSize,
                foregroundColor.copy(alpha = foregroundColor.alpha * fade),
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
        textPaint.typeface =
            if (bold) Typeface.create("sans-serif", Typeface.BOLD)
            else Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
