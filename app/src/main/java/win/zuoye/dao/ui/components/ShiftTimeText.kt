package win.zuoye.dao.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import win.zuoye.dao.R
import win.zuoye.dao.data.ShiftTemplate

/** 班次时间段文案：休班显示「休息」，跨零点把结束时间标注成「次日 HH:mm」。 */
@Composable
internal fun ShiftTemplate.localizedTimeRangeText(): String {
    if (isRest) return stringResource(R.string.shift_rest_time)
    val end = ShiftTemplate.format(endMinute)
    val localizedEnd =
        if (crossesMidnight()) {
            stringResource(R.string.shift_next_day, end)
        } else {
            end
        }
    return "${ShiftTemplate.format(startMinute)}–$localizedEnd"
}
