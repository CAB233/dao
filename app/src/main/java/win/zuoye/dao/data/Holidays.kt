package win.zuoye.dao.data

import java.util.Calendar

/**
 * 法定节假日离线数据。国务院办公厅每年 11 月前后公布次年安排，届时在此追加一年即可；
 * 运行时只使用当前年和下一年的数据，角标与详情都由它驱动，超界日期自然无提示。
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

    /** 某天的节假日属性：[name] 节日名；[isMakeupWorkday] = true 表示"调休上班"（角标显示「班」），否则是放假日（「休」） */
    data class HolidayDay(
        val name: HolidayName,
        val isMakeupWorkday: Boolean,
        /** 只有对应的节日当天为 true，避免连续放假日每天重复显示节日名。 */
        val isNameDay: Boolean = false,
    )

    /** 一条年度安排：放假日区间 + 调休上班日 */
    private class Arrangement(
        val name: HolidayName,
        /** 放假日期（闭区间，IntArray=年月日） */
        val rest: List<IntArray>,
        val restEnd: List<IntArray>,
        /** 节日名称对应的当天（IntArray=年月日） */
        val nameDate: IntArray,
        /** 调休上班的单日列表 */
        val work: List<IntArray>,
    )

    // 2026：国办发明电〔2025〕7号；后续年度安排公布后追加到这里
    private val arrangements = listOf(
        Arrangement(
            HolidayName.NEW_YEAR, listOf(intArrayOf(2026, 1, 1)), listOf(intArrayOf(2026, 1, 3)),
            intArrayOf(2026, 1, 1),
            listOf(intArrayOf(2026, 1, 4)),
        ),
        Arrangement(
            HolidayName.SPRING_FESTIVAL, listOf(intArrayOf(2026, 2, 15)), listOf(intArrayOf(2026, 2, 23)),
            intArrayOf(2026, 2, 17),
            listOf(intArrayOf(2026, 2, 14), intArrayOf(2026, 2, 28)),
        ),
        Arrangement(
            HolidayName.QINGMING, listOf(intArrayOf(2026, 4, 4)), listOf(intArrayOf(2026, 4, 6)),
            intArrayOf(2026, 4, 5),
            emptyList(),
        ),
        Arrangement(
            HolidayName.LABOR_DAY, listOf(intArrayOf(2026, 5, 1)), listOf(intArrayOf(2026, 5, 5)),
            intArrayOf(2026, 5, 1),
            listOf(intArrayOf(2026, 5, 9)),
        ),
        Arrangement(
            HolidayName.DRAGON_BOAT, listOf(intArrayOf(2026, 6, 19)), listOf(intArrayOf(2026, 6, 21)),
            intArrayOf(2026, 6, 19),
            emptyList(),
        ),
        Arrangement(
            HolidayName.MID_AUTUMN, listOf(intArrayOf(2026, 9, 25)), listOf(intArrayOf(2026, 9, 27)),
            intArrayOf(2026, 9, 25),
            emptyList(),
        ),
        Arrangement(
            HolidayName.NATIONAL_DAY, listOf(intArrayOf(2026, 10, 1)), listOf(intArrayOf(2026, 10, 7)),
            intArrayOf(2026, 10, 1),
            listOf(intArrayOf(2026, 9, 20), intArrayOf(2026, 10, 10)),
        ),
    )

    private val byEpochDay: Map<Long, HolidayDay> by lazy {
        val map = HashMap<Long, HolidayDay>()
        for (a in arrangements) {
            a.rest.indices.forEach { i ->
                var day = Ymd(a.rest[i][0], a.rest[i][1], a.rest[i][2])
                val end = Ymd(a.restEnd[i][0], a.restEnd[i][1], a.restEnd[i][2])
                val nameDay = Ymd(a.nameDate[0], a.nameDate[1], a.nameDate[2])
                while (day.epochDay <= end.epochDay) {
                    // 同日既有放假又有调休（不应发生）时以调休为准，便于单测暴露数据错误
                    if (!map.containsKey(day.epochDay)) {
                        map[day.epochDay] = HolidayDay(
                            name = a.name,
                            isMakeupWorkday = false,
                            isNameDay = day == nameDay,
                        )
                    }
                    day = Ymd.fromEpochDay(day.epochDay + 1)
                }
            }
            for (w in a.work) {
                map[Ymd(w[0], w[1], w[2]).epochDay] = HolidayDay(a.name, isMakeupWorkday = true)
            }
        }
        map
    }

    fun of(epochDay: Long): HolidayDay? {
        val year = Ymd.fromEpochDay(epochDay).year
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (year !in currentYear..(currentYear + 1)) return null
        return byEpochDay[epochDay]
    }
}
