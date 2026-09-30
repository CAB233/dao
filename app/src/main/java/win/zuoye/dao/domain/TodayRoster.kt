package win.zuoye.dao.domain

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.Ymd
import win.zuoye.dao.data.defaultGroup

/**
 * 一个人在某个时刻所处的工作阶段。
 *
 * 首页状态卡与桌面小组件共用这一份判定：跨零点夜班归属**开始**日期，所以「当前是否上班」既要看今天的班次， 也要看昨天那班是否还没结束。
 */
enum class ShiftPhase {
    ON_SHIFT,
    RESTING,
    OFF_WORK,
}

/**
 * 某一天某个班组要上的班。模板为 null 表示这一天没有排班（未指派或方案缺失）。
 *
 * [isOverride] 为 true 表示这一天是换班覆盖的结果，而不是按周期推导出来的。
 */
@Immutable
data class TodayShift(
    val groupName: String,
    val template: ShiftTemplate?,
    val isOverride: Boolean = false,
)

/** 小组件与首页状态卡共用的「今天」视图数据；纯数据，不含任何 Android 资源或 Compose 类型。 */
@Immutable
data class TodayRoster(
    /** 当前使用的方案名；没有启用方案时为 null。 */
    val schemeName: String?,
    /** 方案里默认班组名，用于状态卡的班组标签。 */
    val defaultGroupName: String?,
    /** 当前使用方案下所有班组今天的班次，顺序即方案里的班组顺序。 */
    val groups: ImmutableList<TodayShift>,
    /** 当前时刻所处的工作阶段。 */
    val phase: ShiftPhase,
    /**
     * 默认班组今天的班次。
     *
     * 必须与 [phase] 取同一个班组：[phase] 按默认班组的锚点算，这里若图省事取 `groups.first()`，
     * 默认班组不是第一个班组时就会把「此刻在不在班上」配上另一个班组的班次 ——小组件的底部状态行会拿休息班的 `endMinute = 0` 去显示「上班中 · 到 00:00」。
     */
    val defaultShift: TodayShift?,
)

/** 当前是否在上班。[currentMinute] 为当天 0:00 起的分钟数。 */
fun isWorkingAt(
    todayTemplate: ShiftTemplate?,
    previousTemplate: ShiftTemplate?,
    currentMinute: Int,
): Boolean =
    todayTemplate.isWorkingAt(currentMinute) ||
        (previousTemplate?.crossesMidnight() == true &&
            !previousTemplate.isRest &&
            currentMinute < previousTemplate.endMinute)

private fun ShiftTemplate?.isWorkingAt(currentMinute: Int): Boolean {
    if (this == null || isRest) return false
    return if (crossesMidnight()) currentMinute >= startMinute
    else currentMinute in startMinute until endMinute
}

/** [isWorkingAt] 的枚举形式，供状态卡与小组件直接取用。 */
fun shiftPhaseOf(
    todayTemplate: ShiftTemplate?,
    previousTemplate: ShiftTemplate?,
    currentMinute: Int,
): ShiftPhase =
    when {
        isWorkingAt(todayTemplate, previousTemplate, currentMinute) -> ShiftPhase.ON_SHIFT
        todayTemplate?.isRest == true -> ShiftPhase.RESTING
        else -> ShiftPhase.OFF_WORK
    }

/** 汇总今天（及需要时的昨天）的排班，供首页状态卡与桌面小组件渲染。 */
fun todayRoster(doc: PlanDocument, today: Ymd, currentMinute: Int): TodayRoster {
    val scheme = doc.activeScheme()
    val anchor = scheme?.defaultGroup()?.anchorEpochDay
    val todayEpochDay = today.epochDay

    val groups =
        scheme?.groups.orEmpty().map { group ->
            val template = resolveShift(doc, todayEpochDay, group.anchorEpochDay)
            TodayShift(
                groupName = group.name,
                template = template?.template,
                isOverride = template?.isOverride == true,
            )
        }

    val todayShift = anchor?.let { resolveShift(doc, todayEpochDay, it) }
    // 昨天的班次要按同一个班组推导，否则跨零点夜班会认错
    val previousShift = anchor?.let { resolveShift(doc, todayEpochDay - 1, it) }
    // 默认班组在 groups 里的位置（默认班组不一定是第一个，找不到时回落第一个）
    val defaultGroupIndex = scheme?.groups?.indexOfFirst { it.id == scheme.defaultGroupId } ?: -1

    return TodayRoster(
        schemeName = scheme?.name,
        defaultGroupName = scheme?.defaultGroup()?.name,
        groups = groups.toPersistentList(),
        phase =
            shiftPhaseOf(
                todayTemplate = todayShift?.template,
                previousTemplate = previousShift?.template,
                currentMinute = currentMinute,
            ),
        defaultShift = groups.getOrNull(defaultGroupIndex) ?: groups.firstOrNull(),
    )
}

/**
 * 下一次需要重画小组件的分钟数（当天 0:00 起）。
 *
 * 小组件画的是「此刻状态」，只在午夜自己更新是不够的——班次开始/结束的那一刻也要跟着变。
 *
 * 判定必须带上「这个时刻属于哪一天」：昨天那班的**开始**时刻早于今天，早就过去了，不能当成下一个边界 （只看分钟数会把昨天 22:00 的开始误判成今晚
 * 22:00）。所以昨天只贡献「跨零点后的结束时刻」。 没有任何边界时返回 null，交给下一次午夜兜底。
 */
fun nextShiftBoundaryMinute(doc: PlanDocument, currentMinute: Int): Int? {
    val scheme = doc.activeScheme() ?: return null
    val anchorEpochDay = scheme.defaultGroup()?.anchorEpochDay ?: return null
    val todayEpochDay = Ymd.today().epochDay

    val todayTemplate = resolveShift(doc, todayEpochDay, anchorEpochDay)?.template
    val previousTemplate = resolveShift(doc, todayEpochDay - 1, anchorEpochDay)?.template

    // 今天的班次：起止都落在今天
    val todayBoundary =
        todayTemplate
            ?.takeUnless { it.isRest }
            ?.let { template ->
                listOf(template.startMinute, template.endMinute)
                    .filter { it > currentMinute && it <= MINUTES_PER_DAY }
                    .minOrNull()
            }
    // 昨天那班只有跨零点时才会延伸到今天，且只有结束时刻可能还在当前时刻之后
    val carriedOverBoundary =
        previousTemplate
            ?.takeIf { it.crossesMidnight() && !it.isRest }
            ?.endMinute
            ?.takeIf { it > currentMinute }

    return listOfNotNull(todayBoundary, carriedOverBoundary).minOrNull()
}

/** 昨天那班挂的模板（按默认班组的锚点推导）。跨零点判定要用它，**不能用今天那班**。 */
fun previousDayTemplate(doc: PlanDocument, today: Ymd): ShiftTemplate? {
    val anchor = doc.activeScheme()?.defaultGroup()?.anchorEpochDay ?: return null
    return resolveShift(doc, today.epochDay - 1, anchor)?.template
}

/**
 * 此刻是不是还挂在**昨天**那班跨零点夜班上（已过零点、那班还没结束）。
 *
 * 这是「结束时刻算今天还是次日」的唯一判据：成立时结束就在今天，不成立时才说「次日」。
 *
 * 判定必须用**昨天**那班：昨天 22:30 开始、今天 07:30 结束，所以凌晨时"此刻在班上"成立、 而今天那班还没开始。
 */
fun hasCarriedOverNightShift(
    doc: PlanDocument,
    today: Ymd,
    currentMinute: Int,
): Boolean {
    if (todayRoster(doc, today, currentMinute).phase != ShiftPhase.ON_SHIFT) return false
    val previous = previousDayTemplate(doc, today) ?: return false
    return previous.crossesMidnight() && !previous.isRest && currentMinute < previous.endMinute
}

private const val MINUTES_PER_DAY = 24 * 60

/**
 * 跨零点班次的结束时刻**落在哪一天**，决定要不要标「次日」。
 *
 * 同一个夜班（22:30–07:30）在一天的开始和结束时说法不同：
 * - 当天 23:00 还没上班 → 结束在**明天**早上 →「次日07:30」
 * - 次日 06:00 还在班上 → 结束就在**今天**早上 →「07:30」
 *
 * 一律标「次日」会在次日凌晨显示成「到 00:00」那样离谱（那个 00:00 其实是"次日"标记丢了/读错导致的）， 用户明确要求按这个区分来。
 */
@Immutable
enum class ShiftEndMoment {
    /** 结束时刻就在今天（跨零点班次已经跨过来了） */
    TODAY,

    /** 结束时刻在次日 */
    NEXT_DAY;

    companion object {
        /** 同一个班次，班还没上（或刚开始、还在当天）时结束在次日 */
        fun forTemplate(template: ShiftTemplate): ShiftEndMoment =
            if (template.crossesMidnight()) NEXT_DAY else TODAY
    }
}

/**
 * 班次**结束时刻**的显示文本：`HH:mm`，[moment] 为 [ShiftEndMoment.NEXT_DAY] 时前面加 `nextDayMarker`。
 *
 * `nextDayMarker` 是 `R.string.shift_next_day_marker`（`次日` / `Next day `），由 UI 层传入， domain 不持有
 * Android 资源。
 */
fun formatShiftEnd(
    template: ShiftTemplate,
    nextDayMarker: String,
    moment: ShiftEndMoment = ShiftEndMoment.forTemplate(template),
): String {
    val end = ShiftTemplate.format(template.endMinute)
    return if (moment == ShiftEndMoment.NEXT_DAY) "$nextDayMarker$end" else end
}
