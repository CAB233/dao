package win.zuoye.dao.domain

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.primaryAnchorEpochDay

/** 某一天的最终排班结果 */
@Immutable
data class ResolvedShift(val template: ShiftTemplate, val isOverride: Boolean)

/**
 * 任意日期 → 班次：换班覆盖优先，其次当前方案按 (日期 − 锚点) mod 周期 推导。
 */
fun resolveShift(doc: PlanDocument, epochDay: Long): ResolvedShift? {
    doc.overrides[epochDay.toString()]?.let { overrideId ->
        val template = doc.templateById(overrideId)
        if (template != null) return ResolvedShift(template, isOverride = true)
    }
    val scheme = doc.activeScheme() ?: return null
    if (scheme.cycleDays <= 0) return null
    val index = Math.floorMod(epochDay - scheme.primaryAnchorEpochDay(), scheme.cycleDays.toLong()).toInt()
    val templateId = scheme.dayTemplateIds.getOrNull(index) ?: return null
    val template = doc.templateById(templateId) ?: return null
    return ResolvedShift(template, isOverride = false)
}

/**
 * 高频查询用的排班索引：把激活方案和模板摊成字段/哈希表，
 * 查一天只做一次 floorMod + 一次 Map 查询，不分配对象、不扫列表。
 *
 * 月历一屏要解 42 天，直接用 [resolveShift] 会产生 42 次 `epochDay.toString()`
 * 和 42 个 [ResolvedShift]，首次进入某个月份时就是掉帧的来源。
 */
@Immutable
class Roster private constructor(
    private val scheme: Scheme?,
    private val templatesById: ImmutableMap<Long, ShiftTemplate>,
    private val overrides: ImmutableMap<String, Long>,
) {
    /** 该日期的班次模板；只在需要区分"换班覆盖"时用 [resolveShift]。 */
    fun templateFor(epochDay: Long): ShiftTemplate? {
        val scheme = scheme ?: return null
        return templateFor(epochDay, scheme.primaryAnchorEpochDay())
    }

    /** 使用指定班组的基准日期查询该日期的班次。 */
    fun templateFor(epochDay: Long, anchorEpochDay: Long): ShiftTemplate? {
        // 覆盖通常是空的，先判空可以省掉一次字符串分配
        if (overrides.isNotEmpty()) {
            overrides[epochDay.toString()]?.let { id -> templatesById[id]?.let { return it } }
        }
        val scheme = scheme ?: return null
        if (scheme.cycleDays <= 0) return null
        val index = Math.floorMod(epochDay - anchorEpochDay, scheme.cycleDays.toLong()).toInt()
        val templateId = scheme.dayTemplateIds.getOrNull(index) ?: return null
        return templatesById[templateId]
    }

    companion object {
        fun of(doc: PlanDocument): Roster = Roster(
            scheme = doc.activeScheme(),
            templatesById = doc.templates.associateBy { it.id }.toImmutableMap(),
            overrides = doc.overrides,
        )
    }
}
