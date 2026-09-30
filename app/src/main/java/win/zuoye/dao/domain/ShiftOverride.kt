package win.zuoye.dao.domain

import kotlinx.collections.immutable.toImmutableMap
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.defaultGroup

/**
 * 换班覆盖：给个别日期手动指定班次，**优先于周期推导**。
 *
 * `PlanDocument.overrides` 一直是 `epochDay -> templateId`，[resolveShift] 与 [Roster] 也早就在读它， 缺的只是写入口
 * —— 这个文件补上。
 *
 * 覆盖是"本机个人数据"，所以**不进分享载荷**（`PlanShare` 只带模板和方案）。
 *
 * 注意 `ImmutableMap` 没有 `put`/`remove`，要经 `toMutableMap()` 绕一下。
 */
object ShiftOverride {

    /**
     * 把 [epochDay] 那天改成 [templateId]。
     *
     * 与周期推导结果相同的覆盖会被**清掉**而不是存下来：留着只会让"这天被手动改过"的标记一直亮着， 用户却看不出任何区别。
     */
    fun set(doc: PlanDocument, epochDay: Long, templateId: Long): PlanDocument =
        if (cycleTemplateId(doc, epochDay) == templateId) {
            clear(doc, epochDay)
        } else {
            doc.copy(
                overrides =
                    doc.overrides
                        .toMutableMap()
                        .apply { put(epochDay.toString(), templateId) }
                        .toImmutableMap()
            )
        }

    /** 撤销某天的覆盖，回到周期推导的结果。 */
    fun clear(doc: PlanDocument, epochDay: Long): PlanDocument {
        if (!doc.overrides.containsKey(epochDay.toString())) return doc
        return doc.copy(
            overrides =
                doc.overrides.toMutableMap().apply { remove(epochDay.toString()) }.toImmutableMap()
        )
    }

    /** 这天是不是被手动改过。 */
    fun isOverridden(doc: PlanDocument, epochDay: Long): Boolean =
        doc.overrides.containsKey(epochDay.toString())

    /**
     * 按周期推导（**忽略覆盖**）这天应该是哪个模板。
     *
     * 用来判断"选中的正好等于周期值"——那种情况下不该留下覆盖记录。 与 [resolveShift] 的周期分支保持同一套算法（默认班组锚点 + floorMod）。
     */
    private fun cycleTemplateId(doc: PlanDocument, epochDay: Long): Long? {
        val scheme = doc.activeScheme() ?: return null
        if (scheme.cycleDays <= 0) return null
        val anchor = scheme.defaultGroup()?.anchorEpochDay ?: return null
        val index = Math.floorMod(epochDay - anchor, scheme.cycleDays.toLong()).toInt()
        return scheme.dayTemplateIds.getOrNull(index)
    }
}
