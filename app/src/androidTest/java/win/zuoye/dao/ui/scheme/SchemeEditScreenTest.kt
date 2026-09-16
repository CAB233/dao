package win.zuoye.dao.ui.scheme

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import win.zuoye.dao.R
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.ui.theme.AppTheme

@Suppress("DEPRECATION")
class SchemeEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tabsSwitchBetweenScheduleAndGroups() {
        val doc = sampleDocument()
        composeRule.setContent {
            AppTheme {
                SchemeEditScreen(
                    doc = doc,
                    scheme = doc.schemes.first(),
                    autoFocusName = false,
                    onBack = {},
                    onSave = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.plan_tab_schedule)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.cycle_days_hint)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.plan_tab_groups)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.group_count)).assertIsDisplayed()
    }

    @Test
    fun planDetailSurvivesSavedStateRestoreAndBackReturnsToList() {
        val doc = sampleDocument()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            AppTheme {
                PlanEditScreen(
                    doc = doc,
                    onBack = {},
                    onMutate = {},
                    onImportPlan = {},
                )
            }
        }

        composeRule.onNodeWithText("Test plan").performClick()
        composeRule.onNodeWithText(context.getString(R.string.plan_edit_title)).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText(context.getString(R.string.plan_edit_title)).assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithText(context.getString(R.string.settings_plans)).assertIsDisplayed()
    }
}

private fun sampleDocument(): PlanDocument {
    val shift = ShiftTemplate(
        id = 1,
        name = "Morning",
        startMinute = 8 * 60,
        endMinute = 16 * 60,
        colorArgb = 0xFF4A90E2.toInt(),
    )
    val scheme = Scheme(
        id = 2,
        name = "Test plan",
        cycleDays = 1,
        dayTemplateIds = persistentListOf(shift.id),
        createdAt = 2,
        groups = persistentListOf(SchemeGroup(3, "Group 1", 20_000)),
        defaultGroupId = 3,
    )
    return PlanDocument(
        templates = persistentListOf(shift),
        schemes = persistentListOf(scheme),
        activeSchemeId = scheme.id,
        onboardingDone = true,
    )
}
