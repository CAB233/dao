package win.zuoye.dao.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import androidx.datastore.core.CorruptionException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanDocumentSerializerTest {
    @Test
    fun immutableCollectionsRoundTrip() = runBlocking {
        val template = ShiftTemplate(1, "白班", 480, 960, 0xff123456.toInt())
        val group = SchemeGroup(3, "一组", 20_000)
        val document = PlanDocument(
            templates = persistentListOf(template),
            schemes = persistentListOf(
                Scheme(
                    id = 2,
                    name = "方案",
                    cycleDays = 1,
                    dayTemplateIds = persistentListOf(template.id),
                    createdAt = 2,
                    groups = persistentListOf(group),
                    defaultGroupId = group.id,
                ),
            ),
            activeSchemeId = 2,
            overrides = persistentMapOf("20001" to template.id),
            onboardingDone = true,
        )
        val output = ByteArrayOutputStream()

        PlanDocumentSerializer.writeTo(document, output)
        val restored = PlanDocumentSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))

        assertEquals(document, restored)
    }

    @Test
    fun oldDocumentUsesDefaultsForNewFields() = runBlocking {
        val oldJson = """{"templates":[],"schemes":[],"activeSchemeId":null,"overrides":{},"onboardingDone":true}"""

        val restored = PlanDocumentSerializer.readFrom(ByteArrayInputStream(oldJson.encodeToByteArray()))

        assertEquals(0, restored.weekStartDay)
        assertEquals(CalendarViewMode.ALL, restored.calendarViewMode)
        assertEquals(UpdateChannel.GITHUB, restored.updateChannel)
        assertFalse(restored.templates.isNotEmpty())
    }

    @Test
    fun malformedDocumentIsReportedAsCorruption() {
        assertThrows(CorruptionException::class.java) {
            runBlocking {
                PlanDocumentSerializer.readFrom(ByteArrayInputStream("not-json".encodeToByteArray()))
            }
        }
    }
}
