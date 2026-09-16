package win.zuoye.dao.data

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import androidx.compose.runtime.Immutable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 分享/导入用的排班方案载荷：只带"配置"（班次模板 + 倒班方案），
 * 不带本机引导状态、覆盖记录等个人数据。
 */
@Immutable
data class PlanShare(
    val templates: ImmutableList<ShiftTemplate> = persistentListOf(),
    val schemes: ImmutableList<Scheme> = persistentListOf(),
    val activeSchemeId: Long? = null,
) {
    companion object {
        /** 聊天文本里的标记行，便于一眼认出/定位 */
        const val PREFIX = "[DAO-PLAN]"
    }
}

/**
 * 紧凑分享载荷编解码：短字段 JSON → deflate → base64url，
 * 供二维码、剪贴板和系统文本分享使用。
 */
object PlanShareCodec {

    /** 紧凑载荷前缀（二维码内容就是 `DAO1:` 开头的一串） */
    const val PAYLOAD_PREFIX = "DAO1:"

    private const val SCHEME_ID_BASE = 1_000_000L
    private const val MAX_INPUT_CHARS = 1_500_000
    private const val MAX_COMPRESSED_BYTES = 256 * 1024
    private const val MAX_INFLATED_BYTES = 1024 * 1024
    private const val MAX_TEMPLATES = 512
    private const val MAX_SCHEMES = 128
    private const val MAX_GROUPS_PER_SCHEME = 99
    private const val MAX_NAME_LENGTH = 200

    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val fullJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @OptIn(ExperimentalEncodingApi::class)
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** 紧凑载荷：二维码 / 聊天文本里用 */
    fun encodePayload(payload: PlanShare): String {
        require(payload.isValid()) { "倒班方案包含无效数据" }
        val usedIds = payload.schemes.flatMap { it.dayTemplateIds }.toSet()
        val templates = payload.templates.filter { it.id in usedIds }
        val indexOf = templates.withIndex().associate { (index, template) -> template.id to index }
        val compact = CompactPlan(
            templates = templates.map {
                CompactTemplate(it.name, it.startMinute, it.endMinute, it.colorArgb, it.isRest)
            },
            schemes = payload.schemes.map { scheme ->
                val groups = scheme.groups
                val defaultGroup = groups.indexOfFirst { it.id == scheme.defaultGroupId }
                require(defaultGroup >= 0) { "方案缺少默认班组" }
                CompactScheme(
                    name = scheme.name,
                    cycleDays = scheme.cycleDays,
                    days = scheme.dayTemplateIds.map { indexOf[it] ?: -1 },
                    groups = groups.map { group ->
                        CompactGroup(name = group.name, anchor = group.anchorEpochDay)
                    },
                    defaultGroup = defaultGroup,
                )
            },
            active = payload.activeSchemeId
                ?.let { id -> payload.schemes.indexOfFirst { it.id == id }.takeIf { it >= 0 } },
        )
        val raw = compactJson.encodeToString(compact).encodeToByteArray()
        return PAYLOAD_PREFIX + base64.encode(deflate(raw))
    }

    /** 完整 JSON：用于文件互操作和人工备份。 */
    fun encodeJson(payload: PlanShare): String {
        require(payload.isValid()) { "倒班方案包含无效数据" }
        return fullJson.encodeToString(PlanShareSurrogate.from(payload))
    }

    /** 解析紧凑载荷（带不带 `DAO1:` 前缀都行） */
    fun decodePayload(token: String): PlanShare? {
        val trimmed = token.trim().removePrefix(PAYLOAD_PREFIX)
        if (trimmed.isEmpty() || trimmed.length > MAX_INPUT_CHARS) return null
        val compressed = runCatching { base64.decode(trimmed) }.getOrNull() ?: return null
        if (compressed.size > MAX_COMPRESSED_BYTES) return null
        val inflated = inflate(compressed) ?: return null
        val compact = runCatching {
            compactJson.decodeFromString<CompactPlan>(inflated.decodeToString())
        }.getOrNull() ?: return null
        if (!compact.isValid()) return null
        // 用下标当本机 id 重建；导入时会按内容去重并重新分配真实 id
        val templates = compact.templates.mapIndexed { index, t ->
            ShiftTemplate(
                id = index.toLong(),
                name = t.name,
                startMinute = t.start,
                endMinute = t.end,
                colorArgb = t.color,
                isRest = t.isRest,
            )
        }.toImmutableList()
        val schemes = compact.schemes.mapIndexed { index, s ->
            val groups = s.groups.mapIndexed { groupIndex, group ->
                SchemeGroup(
                    id = SCHEME_ID_BASE + index * 1_000L + groupIndex,
                    name = group.name,
                    anchorEpochDay = group.anchor,
                )
            }.toImmutableList()
            val defaultGroup = groups.getOrNull(s.defaultGroup) ?: return null
            Scheme(
                id = SCHEME_ID_BASE + index,
                name = s.name,
                cycleDays = s.cycleDays,
                dayTemplateIds = s.days.map { it.toLong() }.toImmutableList(),
                createdAt = SCHEME_ID_BASE + index,
                groups = groups,
                defaultGroupId = defaultGroup.id,
            )
        }.toImmutableList()
        return PlanShare(
            templates = templates,
            schemes = schemes,
            activeSchemeId = compact.active?.let { schemes.getOrNull(it)?.id },
        ).takeIf { it.isValid() }
    }

    /**
     * 从文本解析载荷，三种输入都认：
     * 1) 带 `DAO1:` 的紧凑载荷（二维码 / 剪贴板）；
     * 2) 含紧凑载荷的整段分享文本；
     * 3) 完整的分享 JSON（也兼容包含额外本机字段的 PlanDocument JSON）。
     */
    fun decode(text: String): PlanShare? {
        if (text.length > MAX_INPUT_CHARS) return null
        val marker = text.indexOf(PAYLOAD_PREFIX)
        if (marker >= 0) {
            val token = text.substring(marker + PAYLOAD_PREFIX.length).takeWhile { !it.isWhitespace() }
            return decodePayload(token)
        }
        val surrogate = runCatching {
            fullJson.decodeFromString<PlanShareSurrogate>(text.trim())
        }.getOrNull() ?: return null
        return surrogate.toPlanShare().takeIf { it.isValid() }
    }

    fun shareText(doc: PlanDocument, localizedHeader: String, schemeId: Long? = null): String {
        val payload = doc.toShare(schemeId)
        val sb = StringBuilder()
        sb.append(localizedHeader).append('\n')
        sb.append(encodePayload(payload))
        return sb.toString()
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(256)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(input: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater(true)
        try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream((input.size * 4).coerceAtMost(MAX_INFLATED_BYTES))
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read <= 0) return@runCatching null
                if (out.size() + read > MAX_INFLATED_BYTES) return@runCatching null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }.getOrNull()

    private fun CompactPlan.isValid(): Boolean {
        if (templates.isEmpty() && schemes.isEmpty()) return false
        if (templates.size > MAX_TEMPLATES || schemes.size > MAX_SCHEMES) return false
        if (active != null && active !in schemes.indices) return false
        if (templates.any { !validName(it.name) || it.start !in 0..1439 || it.end !in 0..1439 }) return false
        return schemes.all { scheme ->
            validName(scheme.name) &&
                scheme.cycleDays in 1..99 &&
                scheme.days.size == scheme.cycleDays &&
                scheme.days.all { it in templates.indices } &&
                scheme.groups.size in 1..MAX_GROUPS_PER_SCHEME &&
                scheme.groups.all { validName(it.name) } &&
                scheme.defaultGroup in scheme.groups.indices
        }
    }

    private fun PlanShare.isValid(): Boolean {
        if (templates.isEmpty() && schemes.isEmpty()) return false
        if (templates.size > MAX_TEMPLATES || schemes.size > MAX_SCHEMES) return false
        val templateIds = templates.map { it.id }
        if (templateIds.toSet().size != templateIds.size) return false
        if (templates.any {
                !validName(it.name) || it.startMinute !in 0..1439 || it.endMinute !in 0..1439
            }) return false
        val schemeIds = schemes.map { it.id }
        if (schemeIds.toSet().size != schemeIds.size) return false
        if (activeSchemeId != null && activeSchemeId !in schemeIds) return false
        val knownTemplates = templateIds.toSet()
        return schemes.all { scheme ->
            val groupIds = scheme.groups.map { it.id }
            validName(scheme.name) &&
                scheme.cycleDays in 1..99 &&
                scheme.dayTemplateIds.size == scheme.cycleDays &&
                scheme.dayTemplateIds.all { it in knownTemplates } &&
                scheme.groups.size in 1..MAX_GROUPS_PER_SCHEME &&
                groupIds.toSet().size == groupIds.size &&
                scheme.defaultGroupId in groupIds &&
                scheme.groups.all { validName(it.name) }
        }
    }

    private fun validName(name: String): Boolean = name.isNotBlank() && name.length <= MAX_NAME_LENGTH
}

@Serializable
private data class PlanShareSurrogate(
    val templates: List<ShiftTemplate> = emptyList(),
    val schemes: List<Scheme> = emptyList(),
    val activeSchemeId: Long? = null,
) {
    fun toPlanShare(): PlanShare = PlanShare(
        templates = templates.toImmutableList(),
        schemes = schemes.toImmutableList(),
        activeSchemeId = activeSchemeId,
    )

    companion object {
        fun from(payload: PlanShare): PlanShareSurrogate = PlanShareSurrogate(
            templates = payload.templates,
            schemes = payload.schemes,
            activeSchemeId = payload.activeSchemeId,
        )
    }
}

/** 取出可分享的部分；给定 [schemeId] 时只带这个方案以及它用到的班次 */
fun PlanDocument.toShare(schemeId: Long? = null): PlanShare {
    val picked = if (schemeId == null) schemes else schemes.filter { it.id == schemeId }
    val usedTemplateIds = picked.flatMap { it.dayTemplateIds }.toSet()
    return PlanShare(
        templates = templates.filter { it.id in usedTemplateIds }.toImmutableList(),
        schemes = picked.toImmutableList(),
        activeSchemeId = activeSchemeId?.takeIf { id -> picked.any { it.id == id } },
    )
}

@Serializable
private class CompactTemplate(
    val name: String,
    val start: Int,
    val end: Int,
    val color: Int,
    val isRest: Boolean = false,
)

@Serializable
private class CompactScheme(
    val name: String,
    val cycleDays: Int,
    val days: List<Int>,
    val groups: List<CompactGroup>,
    val defaultGroup: Int,
)

@Serializable
private class CompactGroup(
    val name: String,
    val anchor: Long,
)

@Serializable
private class CompactPlan(
    val version: Int = 1,
    val templates: List<CompactTemplate> = emptyList(),
    val schemes: List<CompactScheme> = emptyList(),
    val active: Int? = null,
)
