package win.zuoye.dao.widget

import android.content.Context
import win.zuoye.dao.R
import win.zuoye.dao.data.LegalHolidays
import win.zuoye.dao.data.LunarStrings
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.holidayNameRes
import win.zuoye.dao.data.lunarDate
import win.zuoye.dao.data.lunarLabel

/** 与主页一致的农历和节日名称；资源每次绘制只读一份。 */
internal class WidgetDateLabels(
    private val context: Context,
    private val showHolidays: Boolean,
    private val showLunar: Boolean,
) {
    private val strings =
        LunarStrings(
            months = context.resources.getStringArray(R.array.lunar_month_names).toList(),
            days = context.resources.getStringArray(R.array.lunar_day_names).toList(),
            leap = context.getString(R.string.lunar_leap),
            format = context.getString(R.string.lunar_date, "%1\$s", "%2\$s", "%3\$s"),
        )

    /** [holiday] 由调用方传入，避免一天重复查两次节假日表。 */
    fun label(date: Ymd, holiday: LegalHolidays.HolidayDay?): String? {
        if (!showHolidays && !showLunar) return null
        val lunar = lunarDate(date)
        if (showHolidays) {
            holidayNameRes(date, lunar, holiday)?.let {
                return context.getString(it)
            }
        }
        return if (showLunar) lunarLabel(lunar, strings) else null
    }
}
