package win.zuoye.dao.domain

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.primaryAnchorEpochDay

/** 某一天的最终排班结果 */
@Immutable data class ResolvedShift(val template: ShiftTemplate, val isOverride: Boolean)

/**
 * 高频查询用的排班索引：把激活方案的周期、逐日模板和默认班组锚点在 [of] 里摊成字段， 查一天只做一次 floorMod + 一次 Map 查询，不分配对象、不扫列表。
 *
 * 月历一屏要解 42 天，桌面小组件一次更新还要再乘最多 4 个尺寸；如果每次都 `associateBy` 建表、 每天都 `groups.firstOrNull`
 * 找默认班组，就是掉帧和耗电的来源。
 */
@Immutable
class Roster
private constructor(
    private val templatesById: ImmutableMap<Long, ShiftTemplate>,
    private val overrides: ImmutableMap<String, Long>,
    private val cycleDays: Int,
    private val dayTemplateIds: ImmutableList<Long>,
    private val defaultAnchorEpochDay: Long?,
) {
    /** 默认班组下该日期的班次模板；需要区分"换班覆盖"时用 [resolve]。 */
    fun templateFor(epochDay: Long): ShiftTemplate? = defaultAnchorEpochDay?.let {
        templateFor(epochDay, it)
    }

    /** 使用指定班组的基准日期查询该日期的班次。 */
    fun templateFor(epochDay: Long, anchorEpochDay: Long): ShiftTemplate? {
        // 覆盖通常是空的，先判空可以省掉一次字符串分配
        if (overrides.isNotEmpty()) {
            overrides[epochDay.toString()]?.let { id ->
                templatesById[id]?.let {
                    return it
                }
            }
        }
        if (cycleDays <= 0) return null
        val index = Math.floorMod(epochDay - anchorEpochDay, cycleDays.toLong()).toInt()
        val templateId = dayTemplateIds.getOrNull(index) ?: return null
        return templatesById[templateId]
    }

    /** 任意日期 → 班次结果：换班覆盖优先，其次按 (日期 − 锚点) mod 周期 推导。 */
    fun resolve(epochDay: Long): ResolvedShift? {
        if (overrides.isNotEmpty()) {
            overrides[epochDay.toString()]?.let { id ->
                templatesById[id]?.let {
                    return ResolvedShift(it, isOverride = true)
                }
            }
        }
        val template = templateFor(epochDay) ?: return null
        return ResolvedShift(template, isOverride = false)
    }

    /** 当天在本周期中的序号（1 起）；没有周期或默认班组锚点时为 null。 */
    fun cycleDay(epochDay: Long): Int? {
        val anchor = defaultAnchorEpochDay ?: return null
        if (cycleDays <= 0) return null
        return Math.floorMod(epochDay - anchor, cycleDays.toLong()).toInt() + 1
    }

    /** 周期总天数；没有激活方案时为 0。 */
    val cycleLength: Int
        get() = cycleDays

    companion object {
        fun of(doc: PlanDocument): Roster {
            val scheme = doc.activeScheme()
            return Roster(
                templatesById = doc.templates.associateBy { it.id }.toImmutableMap(),
                overrides = doc.overrides,
                cycleDays = scheme?.cycleDays ?: 0,
                dayTemplateIds = scheme?.dayTemplateIds ?: persistentListOf(),
                defaultAnchorEpochDay = scheme?.primaryAnchorEpochDay(),
            )
        }
    }
}
