package win.zuoye.dao.data

import com.tyme.holiday.LegalHoliday
import com.tyme.solar.SolarDay

/**
 * 法定节假日由 Tyme 内置数据提供。
 * Tyme 的 LegalHoliday 同时包含放假日与调休上班日。
 */
object LegalHolidays {

    enum class HolidayName {
        NEW_YEAR,
        SPRING_FESTIVAL,
        QINGMING,
        LABOR_DAY,
        DRAGON_BOAT,
        MID_AUTUMN,
        NATIONAL_DAY,
    }

    /** 某天的节假日属性；[isMakeupWorkday] 为 true 时显示「班」，否则显示「休」。 */
    data class HolidayDay(
        val name: HolidayName,
        val isMakeupWorkday: Boolean,
        /** 仅节日本日显示名称，连续假期的其他日期只显示休/班角标。 */
        val isNameDay: Boolean = false,
    )

    fun of(epochDay: Long): HolidayDay? {
        val date = Ymd.fromEpochDay(epochDay)
        val solar = SolarDay.fromYmd(date.year, date.month, date.day)
        val holiday = solar.getLegalHoliday() ?: return null
        val name = holiday.getName().toHolidayName() ?: return null
        return HolidayDay(
            name = name,
            isMakeupWorkday = holiday.isWork(),
            isNameDay = !holiday.isWork() && isHolidayNameDay(solar, holiday),
        )
    }

    private fun isHolidayNameDay(solar: SolarDay, holiday: LegalHoliday): Boolean = when (holiday.getName()) {
        "元旦" -> solar.month == 1 && solar.day == 1
        "春节" -> solar.getLunarDay().let { it.month == 1 && it.day == 1 }
        "清明节" -> solar.getTermDay().let { it.getDayIndex() == 0 && it.getSolarTerm().getName() == "清明" }
        "劳动节" -> solar.month == 5 && solar.day == 1
        "端午节" -> solar.getLunarDay().let { it.month == 5 && it.day == 5 }
        "中秋节", "国庆中秋" -> solar.getLunarDay().let { it.month == 8 && it.day == 15 }
        "国庆节" -> solar.month == 10 && solar.day == 1
        else -> false
    }

    private fun String.toHolidayName(): HolidayName? = when (this) {
        "元旦" -> HolidayName.NEW_YEAR
        "春节" -> HolidayName.SPRING_FESTIVAL
        "清明节" -> HolidayName.QINGMING
        "劳动节" -> HolidayName.LABOR_DAY
        "端午节" -> HolidayName.DRAGON_BOAT
        "中秋节" -> HolidayName.MID_AUTUMN
        "国庆节", "国庆中秋" -> HolidayName.NATIONAL_DAY
        else -> null
    }
}
