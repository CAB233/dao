package win.zuoye.dao.domain

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate

class RosterTest {
    private val day = ShiftTemplate(1, "白班", 8 * 60, 16 * 60, 0xff336699.toInt())
    private val night = ShiftTemplate(2, "夜班", 20 * 60, 8 * 60, 0xff663399.toInt())
    private val override = ShiftTemplate(3, "临时班", 9 * 60, 17 * 60, 0xff996633.toInt())

    @Test
    fun resolveShiftRepeatsCycleOnBothSidesOfAnchor() {
        val doc = document()

        assertEquals(day.id, resolveShift(doc, 100)?.template?.id)
        assertEquals(night.id, resolveShift(doc, 101)?.template?.id)
        assertEquals(night.id, resolveShift(doc, 99)?.template?.id)
        assertEquals(day.id, resolveShift(doc, 98)?.template?.id)
    }

    @Test
    fun overrideWinsAndIsMarked() {
        val doc = document().copy(overrides = persistentMapOf("101" to override.id))

        val resolved = resolveShift(doc, 101)

        assertEquals(override.id, resolved?.template?.id)
        assertTrue(resolved?.isOverride == true)
        assertEquals(override.id, Roster.of(doc).templateFor(101)?.id)
    }

    @Test
    fun groupAnchorCanResolveSameCycleForDifferentTeams() {
        val roster = Roster.of(document())

        assertEquals(day.id, roster.templateFor(epochDay = 102, anchorEpochDay = 100)?.id)
        assertEquals(night.id, roster.templateFor(epochDay = 102, anchorEpochDay = 101)?.id)
    }

    @Test
    fun missingOrInvalidActiveSchemeReturnsNull() {
        assertNull(resolveShift(PlanDocument(), 100))
        assertNull(Roster.of(PlanDocument()).templateFor(100))
        assertFalse(resolveShift(document().copy(activeSchemeId = 999), 100)?.isOverride == true)
    }

    private fun document(): PlanDocument {
        val group = SchemeGroup(10, "一组", 100)
        val scheme = Scheme(
            id = 20,
            name = "两天轮班",
            cycleDays = 2,
            dayTemplateIds = persistentListOf(day.id, night.id),
            createdAt = 20,
            groups = persistentListOf(group),
            defaultGroupId = group.id,
        )
        return PlanDocument(
            templates = persistentListOf(day, night, override),
            schemes = persistentListOf(scheme),
            activeSchemeId = scheme.id,
        )
    }
}
