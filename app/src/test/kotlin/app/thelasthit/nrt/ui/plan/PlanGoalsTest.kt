package app.thelasthit.nrt.ui.plan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.programme.WeekGoals
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/** The no-break space that keeps a count such as "3 of 4 days" on one line. */
private val NB = Char(0xA0).toString()

/** The extra goals on the Plan tab's week: a short line for each, counted over the days that are over. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class PlanGoalsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpApp() {
        app.setUpForPlan()
    }

    private fun showPlanTab() {
        compose.setContent { LastHitTheme { AppShell() } }
        compose.waitUntilAtLeastOneExists(planTab, timeoutMillis = 5_000)
        compose.onNode(planTab).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Schedule"), timeoutMillis = 10_000)
    }

    @Test
    fun `each goal has a line of how it's gone this week so far`() {
        app.seedGoalWeeks()
        showPlanTab()

        compose.onNodeWithText("Extra goals this week").assertExists()
        compose.onNode(hasText("gum a day: kept on 3${NB}of${NB}4", substring = true)).assertExists()
        compose.onNode(hasText("At most 3", substring = true) and hasText("sprays a day: kept on 4${NB}of${NB}4", substring = true)).assertExists()
        compose.onNode(hasText("between doses: kept for 26${NB}of${NB}28", substring = true)).assertExists()
        compose.onNode(hasText("First dose from 09:00: kept on 3${NB}of${NB}4", substring = true)).assertExists()
    }

    @Test
    fun `on the week's first day the goals count once it's over`() {
        app.startMockupPlan()
        app.setTime("2026-10-24T10:00")
        runBlocking { app.graph.programmes.setGoals(WeekGoals(minGapMinutes = 90, firstDoseNotBefore = LocalTime.of(8, 0))) }
        showPlanTab()

        compose.onNode(hasText("between doses: counts once today is over", substring = true)).assertExists()
        compose.onNodeWithText("First dose from 08:00: counts once today is over").assertExists()
    }

    @Test
    fun `a day with one dose has no gaps to count`() {
        app.startMockupPlan()
        app.setTime("2026-10-25T10:00")
        runBlocking { app.graph.programmes.setGoals(WeekGoals(minGapMinutes = 90)) }
        app.logDoses("2026-10-24", "09:00 G", now = "2026-10-25T10:00")
        showPlanTab()

        compose.onNode(hasText("between doses: no gaps yet", substring = true)).assertExists()
    }

    @Test
    fun `with every goal off there are no lines`() {
        app.seedWeekFour()
        showPlanTab()

        compose.onNodeWithText("Extra goals").assertExists()
        compose.onNodeWithText("Extra goals this week").assertDoesNotExist()
    }
}
