package win.zuoye.dao.data

/** 文档里出现过的全部 id：班次模板、方案、班组。 */
internal fun PlanDocument.usedIds(): Set<Long> = buildSet {
    templates.forEach { add(it.id) }
    schemes.forEach { scheme ->
        add(scheme.id)
        scheme.groups.forEach { add(it.id) }
    }
}

/**
 * 文档内唯一的 id 分配器：以时间戳为起点，遇到已用 id 顺延。
 *
 * 之前各处直接用 `System.currentTimeMillis()`，同一毫秒内连续新建、复制或导入会分到相同 id。
 */
internal class IdSource(
    used: Collection<Long> = emptyList(),
    now: Long = System.currentTimeMillis(),
) {
    private val usedIds = used.toMutableSet()
    private var next = maxOf(now, usedIds.maxOrNull() ?: 0L) + 1

    fun nextId(): Long {
        while (next in usedIds) next++
        usedIds += next
        return next++
    }
}
