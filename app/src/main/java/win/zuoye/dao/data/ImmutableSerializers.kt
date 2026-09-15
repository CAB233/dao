package win.zuoye.dao.data

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * kotlinx.serialization 没有给 kotlinx.collections.immutable 的集合提供序列化器：
 * 直接给接口类型（`ImmutableList` / `ImmutableMap`）挂 `@Serializable` 只会拿到一个
 * 多态序列化器，写盘时就抛 `SerializationException: Serializer for subclass … is not found`。
 *
 * 所以这里给持久化类型各配一个"代理"序列化器：**线上格式不变**（还是普通数组/对象），
 * 只在编解码边界把普通集合和不可变集合互转。数据模型可以全程用不可变类型（可 skip 的状态）。
 */

@Serializable
private class SchemeSurrogate(
    val id: Long,
    val name: String,
    val cycleDays: Int,
    val dayTemplateIds: List<Long>,
    val createdAt: Long,
    val groups: List<SchemeGroup>,
    val defaultGroupId: Long,
)

object SchemeSerializer : KSerializer<Scheme> {
    private val surrogate = SchemeSurrogate.serializer()

    override val descriptor: SerialDescriptor = surrogate.descriptor

    override fun serialize(encoder: Encoder, value: Scheme) {
        surrogate.serialize(
            encoder,
            SchemeSurrogate(
                id = value.id,
                name = value.name,
                cycleDays = value.cycleDays,
                dayTemplateIds = value.dayTemplateIds,
                createdAt = value.createdAt,
                groups = value.groups,
                defaultGroupId = value.defaultGroupId,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): Scheme {
        val surrogate = surrogate.deserialize(decoder)
        return Scheme(
            id = surrogate.id,
            name = surrogate.name,
            cycleDays = surrogate.cycleDays,
            dayTemplateIds = surrogate.dayTemplateIds.toImmutableList(),
            createdAt = surrogate.createdAt,
            groups = surrogate.groups.toImmutableList(),
            defaultGroupId = surrogate.defaultGroupId,
        )
    }
}

@Serializable
private class PlanDocumentSurrogate(
    val templates: List<ShiftTemplate> = emptyList(),
    val schemes: List<Scheme> = emptyList(),
    val activeSchemeId: Long? = null,
    val overrides: Map<String, Long> = emptyMap(),
    val onboardingDone: Boolean = false,
    val weekStartDay: Int = 0,
    val calendarViewMode: CalendarViewMode = CalendarViewMode.ALL,
)

object PlanDocumentJsonSerializer : KSerializer<PlanDocument> {
    private val surrogate = PlanDocumentSurrogate.serializer()

    override val descriptor: SerialDescriptor = surrogate.descriptor

    override fun serialize(encoder: Encoder, value: PlanDocument) {
        surrogate.serialize(
            encoder,
            PlanDocumentSurrogate(
                templates = value.templates,
                schemes = value.schemes,
                activeSchemeId = value.activeSchemeId,
                overrides = value.overrides,
                onboardingDone = value.onboardingDone,
                weekStartDay = value.weekStartDay,
                calendarViewMode = value.calendarViewMode,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): PlanDocument {
        val surrogate = surrogate.deserialize(decoder)
        return PlanDocument(
            templates = surrogate.templates.toImmutableList(),
            schemes = surrogate.schemes.toImmutableList(),
            activeSchemeId = surrogate.activeSchemeId,
            overrides = surrogate.overrides.toImmutableMap(),
            onboardingDone = surrogate.onboardingDone,
            weekStartDay = surrogate.weekStartDay,
            calendarViewMode = surrogate.calendarViewMode,
        )
    }
}
