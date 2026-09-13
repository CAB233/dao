package win.zuoye.dao.domain

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.ShiftTemplate

/** 导入结果，用于给用户一个明确反馈 */
data class ImportResult(
    val templatesAdded: Int = 0,
    val templatesReused: Int = 0,
    val schemesAdded: Int = 0,
    val schemesSkipped: Int = 0,
    val activated: Boolean = false,
) {
    val changed: Boolean get() = templatesAdded > 0 || schemesAdded > 0

    fun message(): String = when {
        !changed && schemesSkipped > 0 -> "这些方案本机已经有了，没有重复导入"
        !changed -> "没识别到可导入的内容"
        else -> buildString {
            append("已导入 ${templatesAdded} 个班次、${schemesAdded} 个方案")
            if (schemesSkipped > 0) append("，跳过 ${schemesSkipped} 个重复方案")
            if (!activated && schemesAdded > 0) append("；可在「设置」里点「启用」切换")
        }
    }
}

/**
 * 合并别人分享的方案：
 * - 班次按内容（名称+时间+颜色）去重，已存在就复用，方案引用重新指向本机 id
 * - 方案按 名称+周期+锚点+日序 去重，重复的跳过，同名不同内容的自动改名
 * - 本机已有启用方案时不动它；本机没有启用方案才自动启用导入的方案
 */
fun PlanDocument.importPlan(
    payload: PlanShare,
    now: Long = System.currentTimeMillis(),
): Pair<PlanDocument, ImportResult> {
    if (payload.templates.isEmpty() && payload.schemes.isEmpty()) return this to ImportResult()

    val usedIds = (templates.map { it.id } + schemes.map { it.id }).toMutableSet()
    var nextId = maxOf(now, usedIds.maxOrNull() ?: 0L) + 1
    fun newId(): Long {
        while (nextId in usedIds) nextId++
        usedIds += nextId
        return nextId++
    }

    // ---- 班次模板 ----
    // 持久化结构本身就是不可变集合；这里先落到 PersistentList，改完再原样当作 ImmutableList 存回
    var mergedTemplates = templates.toPersistentList()
    val templateIdMap = HashMap<Long, Long>(payload.templates.size)
    var templatesAdded = 0
    var templatesReused = 0
    payload.templates.forEach { incoming ->
        val existing = mergedTemplates.firstOrNull { it.sameContentAs(incoming) }
        if (existing != null) {
            templateIdMap[incoming.id] = existing.id
            templatesReused++
        } else {
            val id = newId()
            mergedTemplates = mergedTemplates.add(incoming.copy(id = id))
            templateIdMap[incoming.id] = id
            templatesAdded++
        }
    }

    // ---- 方案 ----
    var mergedSchemes = schemes.toPersistentList()
    val usedNames = mergedSchemes.mapTo(mutableSetOf()) { it.name }
    val schemeIdMap = HashMap<Long, Long>(payload.schemes.size)
    var schemesAdded = 0
    var schemesSkipped = 0
    payload.schemes.forEach { incoming ->
        val dayIds = incoming.dayTemplateIds.mapNotNull { templateIdMap[it] }
        if (dayIds.size != incoming.dayTemplateIds.size) return@forEach // 引用了缺失的班次，跳过
        val duplicate = mergedSchemes.firstOrNull {
            it.cycleDays == incoming.cycleDays &&
                it.anchorEpochDay == incoming.anchorEpochDay &&
                it.dayTemplateIds == dayIds &&
                it.name == incoming.name
        }
        if (duplicate != null) {
            schemeIdMap[incoming.id] = duplicate.id
            schemesSkipped++
            return@forEach
        }
        val id = newId()
        mergedSchemes = mergedSchemes.add(
            incoming.copy(
                id = id,
                name = uniqueName(incoming.name, usedNames),
                dayTemplateIds = dayIds.toImmutableList(),
                createdAt = now + schemesAdded,
            ),
        )
        schemeIdMap[incoming.id] = id
        schemesAdded++
    }

    // ---- 启用方案 ----
    val importedActiveId = payload.activeSchemeId
        ?.let { schemeIdMap[it] }
        ?: payload.schemes.firstOrNull()?.let { schemeIdMap[it.id] }
    val activated = activeSchemeId == null && importedActiveId != null

    val merged = copy(
        templates = mergedTemplates,
        schemes = mergedSchemes,
        activeSchemeId = if (activated) importedActiveId else activeSchemeId,
    )
    return merged to ImportResult(
        templatesAdded = templatesAdded,
        templatesReused = templatesReused,
        schemesAdded = schemesAdded,
        schemesSkipped = schemesSkipped,
        activated = activated,
    )
}

private fun ShiftTemplate.sameContentAs(other: ShiftTemplate): Boolean =
    name == other.name &&
        startMinute == other.startMinute &&
        endMinute == other.endMinute &&
        colorArgb == other.colorArgb &&
        isRest == other.isRest

private fun uniqueName(base: String, used: MutableSet<String>): String {
    if (used.add(base)) return base
    var index = 2
    while (!used.add("$base ($index)")) index++
    return "$base ($index)"
}
