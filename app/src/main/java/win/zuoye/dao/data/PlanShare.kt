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

    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @OptIn(ExperimentalEncodingApi::class)
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** 紧凑载荷：二维码 / 聊天文本里用 */
    fun encodePayload(payload: PlanShare): String {
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

    /** 解析紧凑载荷（带不带 `DAO1:` 前缀都行） */
    fun decodePayload(token: String): PlanShare? {
        val trimmed = token.trim().removePrefix(PAYLOAD_PREFIX)
        val inflated = runCatching { base64.decode(trimmed) }.getOrNull()?.let(::inflate) ?: return null
        val compact = runCatching {
            compactJson.decodeFromString<CompactPlan>(inflated.decodeToString())
        }.getOrNull() ?: return null
        if (compact.templates.isEmpty() && compact.schemes.isEmpty()) return null
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
        )
    }

    /**
     * 从文本解析载荷，两种输入都认：
     * 1) 带 `DAO1:` 的紧凑载荷（二维码 / 剪贴板）；
     * 2) 含紧凑载荷的整段分享文本。
     */
    fun decode(text: String): PlanShare? {
        val marker = text.indexOf(PAYLOAD_PREFIX)
        if (marker < 0) return null
        val token = text.substring(marker + PAYLOAD_PREFIX.length).takeWhile { !it.isWhitespace() }
        return decodePayload(token)
    }

    fun shareText(doc: PlanDocument, appName: String, schemeId: Long? = null): String {
        val payload = doc.toShare(schemeId)
        val sb = StringBuilder()
        sb.append("【$appName】排班方案\n")
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
        inflater.setInput(input)
        val out = ByteArrayOutputStream(input.size * 4)
        val buffer = ByteArray(256)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0 && inflater.needsInput()) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        out.toByteArray()
    }.getOrNull()
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
