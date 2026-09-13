package win.zuoye.dao.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 数据模型全程用不可变集合（`ImmutableList` / `ImmutableMap`），
 * 但 kotlinx.serialization 对这两个接口只会给出多态序列化器（写盘直接抛异常），
 * 所以 `ImmutableSerializers.kt` 里挂了代理序列化器。这里守住"落盘 / 分享文本能原样读回"。
 */
class PlanSerializationTest {

    private val template = ShiftTemplate(
        id = 1,
        name = "早班",
        startMinute = 8 * 60,
        endMinute = 15 * 60,
        colorArgb = 0xFF1E88E5.toInt(),
    )

    private fun doc(): PlanDocument = PlanDocument(
        templates = listOf(template).toImmutableList(),
        schemes = listOf(
            Scheme(
                id = 7,
                name = "四班三倒",
                cycleDays = 2,
                anchorEpochDay = Ymd(2026, 9, 14).epochDay,
                dayTemplateIds = listOf(1L, 1L).toImmutableList(),
                createdAt = 7,
            ),
        ).toImmutableList(),
        activeSchemeId = 7,
        overrides = mapOf("20710" to 1L).toImmutableMap(),
        onboardingDone = true,
    )

    @Test
    fun planDocumentSurvivesDataStoreRoundTrip() = runBlocking {
        val original = doc()

        val out = ByteArrayOutputStream()
        PlanDocumentSerializer.writeTo(original, out)
        val restored = PlanDocumentSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertEquals(original, restored)
        assertEquals(listOf(1L, 1L), restored.schemes.first().dayTemplateIds)
        assertEquals(1L, restored.overrides["20710"])
        assertEquals("早班", restored.templateById(1L)?.name)
    }

    @Test
    fun readableJsonDecodesBack() {
        val json = PlanShareCodec.encode(doc().toShare())

        val decoded = PlanShareCodec.decode(json)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.templates.size)
        assertEquals("四班三倒", decoded.schemes.first().name)
        assertEquals(listOf(1L, 1L), decoded.schemes.first().dayTemplateIds)
    }

    @Test
    fun shareTextDecodesBack() {
        val text = PlanShareCodec.shareText(doc(), "倒班表")

        val decoded = PlanShareCodec.decode(text)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.templates.size)
        assertEquals("四班三倒", decoded.schemes.first().name)
    }
}
