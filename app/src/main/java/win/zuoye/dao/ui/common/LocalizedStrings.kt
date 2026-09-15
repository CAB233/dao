package win.zuoye.dao.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import win.zuoye.dao.R
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.domain.ImportResult

@Composable
fun ShiftTemplate.localizedTimeRangeText(): String {
    if (isRest) return stringResource(R.string.shift_rest_time)
    val end = ShiftTemplate.format(endMinute)
    val localizedEnd = if (crossesMidnight()) {
        stringResource(R.string.shift_next_day, end)
    } else {
        end
    }
    return "${ShiftTemplate.format(startMinute)}–$localizedEnd"
}

fun ImportResult.localizedMessage(resources: Resources): String {
    if (!changed && schemesSkipped > 0) return resources.getString(R.string.import_duplicate_only)
    if (!changed) return resources.getString(R.string.import_nothing)
    val shifts = resources.getQuantityString(R.plurals.shift_count, templatesAdded, templatesAdded)
    val plans = resources.getQuantityString(R.plurals.plan_count, schemesAdded, schemesAdded)
    val result = if (schemesSkipped > 0) {
        val duplicates = resources.getQuantityString(
            R.plurals.duplicate_plan_count,
            schemesSkipped,
            schemesSkipped,
        )
        resources.getString(R.string.import_success_skipped, shifts, plans, duplicates)
    } else {
        resources.getString(R.string.import_success, shifts, plans)
    }
    return if (!activated && schemesAdded > 0) {
        result + resources.getString(R.string.import_switch_hint)
    } else {
        result
    }
}
