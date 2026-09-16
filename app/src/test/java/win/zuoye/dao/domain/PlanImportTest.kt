package win.zuoye.dao.domain

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate

class PlanImportTest {
    private val day = ShiftTemplate(1, "白班", 480, 960, 0xff123456.toInt())

    @Test
    fun reusesTemplateAndActivatesFirstImportedSchemeWhenLocalHasNone() {
        val local = PlanDocument(templates = persistentListOf(day))
        val incomingTemplate = day.copy(id = 99)
        val payload = PlanShare(
            templates = persistentListOf(incomingTemplate),
            schemes = persistentListOf(scheme(50, "方案", incomingTemplate.id)),
            activeSchemeId = 50,
        )

        val (merged, result) = local.importPlan(payload, now = 1_000)

        assertEquals(1, merged.templates.size)
        assertEquals(day.id, merged.schemes.single().dayTemplateIds.single())
        assertEquals(merged.schemes.single().id, merged.activeSchemeId)
        assertEquals(1, result.templatesReused)
        assertTrue(result.activated)
    }

    @Test
    fun keepsExistingActiveSchemeAndRenamesDifferentPlanWithSameName() {
        val localScheme = scheme(10, "方案", day.id)
        val local = PlanDocument(
            templates = persistentListOf(day),
            schemes = persistentListOf(localScheme),
            activeSchemeId = localScheme.id,
        )
        val payload = PlanShare(
            templates = persistentListOf(day.copy(id = 99)),
            schemes = persistentListOf(scheme(50, "方案", 99, anchor = 200)),
            activeSchemeId = 50,
        )

        val (merged, result) = local.importPlan(payload, now = 1_000)

        assertEquals(localScheme.id, merged.activeSchemeId)
        assertEquals("方案 (2)", merged.schemes.last().name)
        assertFalse(result.activated)
    }

    @Test
    fun skipsExactDuplicateScheme() {
        val localScheme = scheme(10, "方案", day.id)
        val local = PlanDocument(
            templates = persistentListOf(day),
            schemes = persistentListOf(localScheme),
            activeSchemeId = localScheme.id,
        )
        val payload = PlanShare(
            templates = persistentListOf(day.copy(id = 99)),
            schemes = persistentListOf(scheme(50, "方案", 99)),
        )

        val (merged, result) = local.importPlan(payload, now = 1_000)

        assertEquals(local.schemes, merged.schemes)
        assertEquals(1, result.schemesSkipped)
        assertFalse(result.changed)
    }

    @Test
    fun skipsSchemeThatReferencesMissingTemplate() {
        val payload = PlanShare(
            templates = persistentListOf(day),
            schemes = persistentListOf(scheme(50, "损坏方案", templateId = 404)),
        )

        val (merged, result) = PlanDocument().importPlan(payload, now = 1_000)

        assertTrue(merged.schemes.isEmpty())
        assertEquals(0, result.schemesAdded)
        assertNotEquals(404, merged.activeSchemeId)
    }

    private fun scheme(id: Long, name: String, templateId: Long, anchor: Long = 100): Scheme {
        val group = SchemeGroup(id + 1, "一组", anchor)
        return Scheme(
            id = id,
            name = name,
            cycleDays = 1,
            dayTemplateIds = persistentListOf(templateId),
            createdAt = id,
            groups = persistentListOf(group),
            defaultGroupId = group.id,
        )
    }
}
