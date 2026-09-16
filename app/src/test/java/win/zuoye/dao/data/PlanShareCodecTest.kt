package win.zuoye.dao.data

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanShareCodecTest {
    @Test
    fun compactPayloadRoundTripsReferencedData() {
        val payload = payload()

        val decoded = PlanShareCodec.decodePayload(PlanShareCodec.encodePayload(payload))

        requireNotNull(decoded)
        assertEquals(payload.templates.map { it.name }, decoded.templates.map { it.name })
        assertEquals(payload.schemes.single().name, decoded.schemes.single().name)
        assertEquals(payload.schemes.single().cycleDays, decoded.schemes.single().cycleDays)
        assertEquals(listOf(0L, 1L), decoded.schemes.single().dayTemplateIds)
        assertEquals(decoded.schemes.single().id, decoded.activeSchemeId)
    }

    @Test
    fun shareTextCanBeDecoded() {
        val document = payload().let {
            PlanDocument(
                templates = it.templates,
                schemes = it.schemes,
                activeSchemeId = it.activeSchemeId,
            )
        }

        val decoded = PlanShareCodec.decode(PlanShareCodec.shareText(document, "倒班方案"))

        assertEquals("两天轮班", decoded?.schemes?.single()?.name)
    }

    @Test
    fun fullJsonRoundTripsAndPlanDocumentJsonIsAccepted() {
        val payload = payload()

        val decoded = PlanShareCodec.decode(PlanShareCodec.encodeJson(payload))
        val documentJson = PlanDocumentSerializer.run {
            kotlinx.serialization.json.Json.encodeToString(
                PlanDocument.serializer(),
                PlanDocument(
                    templates = payload.templates,
                    schemes = payload.schemes,
                    activeSchemeId = payload.activeSchemeId,
                    onboardingDone = true,
                ),
            )
        }

        assertEquals(payload, decoded)
        assertEquals(payload, PlanShareCodec.decode(documentJson))
    }

    @Test
    fun malformedPayloadReturnsNull() {
        assertNull(PlanShareCodec.decode("not a plan"))
        assertNull(PlanShareCodec.decodePayload("DAO1:not-base64"))
        assertNull(PlanShareCodec.decode("DAO1:"))
        assertNull(PlanShareCodec.decode("x".repeat(1_500_001)))
    }

    @Test
    fun invalidModelIsRejectedBeforeEncoding() {
        val invalid = payload().let { original ->
            original.copy(
                schemes = persistentListOf(original.schemes.single().copy(cycleDays = 3)),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            PlanShareCodec.encodePayload(invalid)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlanShareCodec.encodeJson(invalid)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun compressedPayloadCannotExpandPastLimit() {
        val raw = """{"version":1,"templates":[],"schemes":[],"padding":"${"x".repeat(1_100_000)}"}"""
            .encodeToByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val compressed = try {
            deflater.setInput(raw)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
        val token = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(compressed)

        assertNull(PlanShareCodec.decodePayload(token))
    }

    @Test
    fun unreferencedTemplatesAreNotShared() {
        val unused = ShiftTemplate(3, "未使用", 0, 0, 0xff000000.toInt())
        val original = payload()
        val payload = original.copy(templates = original.templates.toPersistentList().add(unused))

        val decoded = PlanShareCodec.decodePayload(PlanShareCodec.encodePayload(payload))

        assertTrue(decoded?.templates?.none { it.name == unused.name } == true)
    }

    private fun payload(): PlanShare {
        val day = ShiftTemplate(10, "白班", 480, 960, 0xff123456.toInt())
        val night = ShiftTemplate(11, "夜班", 1_200, 480, 0xff654321.toInt())
        val group = SchemeGroup(30, "一组", 20_000)
        val scheme = Scheme(
            id = 20,
            name = "两天轮班",
            cycleDays = 2,
            dayTemplateIds = persistentListOf(day.id, night.id),
            createdAt = 20,
            groups = persistentListOf(group),
            defaultGroupId = group.id,
        )
        return PlanShare(
            templates = persistentListOf(day, night),
            schemes = persistentListOf(scheme),
            activeSchemeId = scheme.id,
        )
    }
}
