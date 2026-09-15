package win.zuoye.dao.data

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.Serializable

/** 班次模板（"基本天"）：定义一次、全程复用。时间为当天 0:00 起的分钟数，end < start 表示跨零点夜班。 */
@Immutable
@Serializable
data class ShiftTemplate(
    val id: Long,
    val name: String,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val colorArgb: Int,
    val isRest: Boolean = false,
) {
    fun crossesMidnight(): Boolean = !isRest && endMinute <= startMinute

    fun timeRangeText(): String = when {
        isRest -> "休息"
        else -> {
            val end = if (crossesMidnight()) "次日${format(endMinute)}" else format(endMinute)
            "${format(startMinute)}–$end"
        }
    }

    companion object {
        fun format(minuteOfDay: Int): String =
            "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    }
}

/** 倒班方案：周期 N 天 + 每天挂的模板 id + 锚点日期（周期第 1 天）。 */
@Immutable
@Serializable(with = SchemeSerializer::class)
data class Scheme(
    val id: Long,
    val name: String,
    val cycleDays: Int,
    val anchorEpochDay: Long,
    val dayTemplateIds: ImmutableList<Long>,
    val createdAt: Long,
)

/** 全量持久化文档（单文件 JSON，规模小、无查询需求）。overrides 为换班覆盖，本期 UI 不编辑。 */
@Immutable
@Serializable(with = PlanDocumentJsonSerializer::class)
data class PlanDocument(
    val templates: ImmutableList<ShiftTemplate> = persistentListOf(),
    val schemes: ImmutableList<Scheme> = persistentListOf(),
    val activeSchemeId: Long? = null,
    val overrides: ImmutableMap<String, Long> = persistentMapOf(),
    /** 用户跳过首次引导后置位，避免每次启动都进引导 */
    val onboardingDone: Boolean = false,
    /** 一周第一天：0 = 周一，依次到 6 = 周日 */
    val weekStartDay: Int = 0,
) {
    fun activeScheme(): Scheme? = schemes.firstOrNull { it.id == activeSchemeId }

    fun templateById(id: Long): ShiftTemplate? = templates.firstOrNull { it.id == id }
}
