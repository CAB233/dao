package win.zuoye.dao.domain

import androidx.compose.runtime.Immutable
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd

/**
 * 日历里一天该怎么画。**App 内月历和桌面小组件共用这一份判定**，两处不会各写一套规则。
 *
 * 优先级（从高到低）：
 * 1. 补位日（上/下月）——整体压到背景里，不参与"过去/未来"的着色
 * 2. 今天——只留描边，中间透出卡片底色
 * 3. 没排班——中性浅灰
 * 4. 已过去——班次色的消色版 + 灰字，表示"已经上过了"
 * 5. 今天之后——班次色实心 + 对比色文字
 */
@Immutable
sealed interface DayVisualState {

    /** 上/下月补位日：只显示日期，整体弱化 */
    data object OutsideMonth : DayVisualState

    /** 今天：描边着重（有班次用班次色，没班次用中性灰） */
    data object Today : DayVisualState

    /** 本月但没排班：中性浅灰底 */
    data object Empty : DayVisualState

    /** 本月 · 已过去：班次色消色版 */
    data class Past(val template: ShiftTemplate) : DayVisualState

    /** 本月 · 今天之后：班次色实心 */
    data class Upcoming(val template: ShiftTemplate) : DayVisualState

    companion object {
        fun of(
            inCurrentMonth: Boolean,
            isToday: Boolean,
            isPast: Boolean,
            template: ShiftTemplate?,
        ): DayVisualState =
            when {
                !inCurrentMonth -> OutsideMonth
                isToday -> Today
                template == null -> Empty
                isPast -> Past(template)
                else -> Upcoming(template)
            }

        fun of(cell: MonthCell, today: Ymd, template: ShiftTemplate?): DayVisualState =
            of(
                inCurrentMonth = cell.inCurrentMonth,
                isToday = cell.epochDay == today.epochDay,
                isPast = cell.epochDay < today.epochDay,
                template = template,
            )

        /**
         * 整体淡化系数（App 内月历用它乘颜色，不额外加 `Modifier.alpha`）。
         *
         * 只有补位日淡化；过去的日子靠"消色版"表达，不再额外压整体透明度—— 否则和补位日的弱化叠在一起，分不出哪一格是"过去的班"、哪一格是"别的月份"。
         */
        const val OUTSIDE_MONTH_FADE = 0.35f

        fun fadeFor(state: DayVisualState): Float =
            if (state is OutsideMonth) OUTSIDE_MONTH_FADE else 1f
    }
}
