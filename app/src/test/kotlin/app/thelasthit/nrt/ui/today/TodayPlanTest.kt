package app.thelasthit.nrt.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.seedPlan
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.theme.LastHitTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Today once a plan runs: the meter, the programme week and the demon stepping out. The meter,
 * the columns and the band are each one picture to a screen reader, so they're found by what it
 * hears.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-" + RobolectricDeviceQualifiers.Pixel7)
class TodayPlanTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun showToday(onOpenLair: () -> Unit = {}) {
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    TodayScreen(snackbars = snackbars, onOpenSettings = {}, onOpenLair = onOpenLair)
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    private fun waitForDescription(text: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(text, substring = true), timeoutMillis = 5_000)

    @Test
    fun `the meter, the week and the band follow the plan`() {
        app.setUp()
        app.seedWeek()
        app.seedPlan()
        showToday()

        waitForDescription("8 mg used. Daily guide 14 mg. 6 mg left today.")
        compose.onNodeWithText("of 98 mg this week").assertExists()
        compose.onNodeWithText("Week 4 of 12, day 5. 37 mg left this week.").assertExists()
        // Monday to Thursday are over and at or under the guide; today isn't over yet.
        compose.onNode(
            hasContentDescription(
                "Daily guide 14 mg. Monday 14 mg, at or under the guide, Tuesday 13 mg, at or under the guide, " +
                    "Wednesday 12 mg, at or under the guide, Thursday 14 mg, at or under the guide, Friday 8 mg so far",
            ),
        ).assertExists()
        // Weeks 1 to 3 had nothing logged, so all three are met: 3 hits banked, none spent.
        compose.onNode(hasContentDescription("Gary, 120 of 120 HP, 3 hits ready. Opens the lair.")).assertExists()
    }

    @Test
    fun `spent hits come off Gary, and the one left is ready`() {
        app.setUp()
        app.seedWeek()
        app.seedPlan(spend = 2)
        showToday()

        waitForDescription("Gary, 100 of 120 HP, 1 hit ready. Opens the lair.")
    }

    @Test
    fun `over the guide there's no tick, and the week says what's left`() {
        app.setUp()
        app.seedWeek()
        app.seedPlan()
        runBlocking { app.graph.entries.log(LogRequest(GUM_4, quantity = 2, at = london("2026-10-23T14:00"))) }
        showToday()

        waitForDescription("16 mg used. Daily guide 14 mg. 2 mg over today's guide.")
        compose.onNodeWithText("Week 4 of 12, day 5. 29 mg left this week.").assertExists()
    }

    @Test
    fun `over the week's allowance, the week says by how much`() {
        app.setUp()
        app.seedWeek()
        app.seedPlan()
        runBlocking { app.graph.entries.log(LogRequest(GUM_4, quantity = 10, at = london("2026-10-22T20:00"))) }
        showToday()

        waitForDescription("Thursday 54 mg, over the guide")
        compose.onNodeWithText("Week 4 of 12, day 5. 3 mg over this week's allowance.").assertExists()
    }

    @Test
    fun `programme weeks run from the day the plan started`() {
        app.setUp()
        app.seedWeek()
        // From Saturday 17 October, today is the last day of week 1, at a 20 mg guide.
        app.seedPlan(start = LocalDate.parse("2026-10-17"))
        showToday()

        waitForDescription("Daily guide 20 mg. Saturday 0 mg, at or under the guide, Sunday 0 mg")
        compose.onNodeWithText("of 140 mg this week").assertExists()
        compose.onNodeWithText("Week 1 of 12, day 7. 79 mg left this week.").assertExists()
    }

    @Test
    fun `before the plan starts, Today is as it was, and Gary lurks`() {
        app.setUp()
        app.seedWeek()
        runBlocking {
            val gum = checkNotNull(app.graph.products.get(GUM_2))
            app.graph.programmes.keepUsualDay(UsualDay.typed(gum to 12))
            app.graph.programmes.startPlan(checkNotNull(app.graph.programmes.buildPlan(ProgrammeLength.WEEKS_12)))
        }
        showToday()

        compose.waitUntilAtLeastOneExists(hasText("Your plan starts tomorrow."), timeoutMillis = 5_000)
        compose.onNodeWithText("3 doses so far").assertExists()
        compose.onNode(hasContentDescription("Daily guide", substring = true)).assertDoesNotExist()
        // The demon row is there from the plan's confirmation, but Gary steps out on its first day.
        compose.onNode(hasContentDescription("Gary, lurking until your plan starts. HP unknown. Opens the lair.")).assertExists()
        compose.onNode(hasContentDescription("of 120 HP", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `the day after an overtime week, its result is awaited as overtime`() {
        app.setUp()
        // A plan from Friday 24 July: week 12 is 9 to 15 October, and over its 0 mg guide, so
        // overtime runs from 16 to 22 October. Its result comes in tomorrow morning.
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-10T12:00"))) }
        app.seedPlan(start = LocalDate.parse("2026-07-24"))
        showToday()

        compose.waitUntilAtLeastOneExists(hasText("Overtime's result comes in after today, so late logs still count."), timeoutMillis = 5_000)
        compose.onNode(hasText("Week 13", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `a slip in an NRT-free week is over the 0 mg guide`() {
        app.setUp()
        app.seedPlan(start = LocalDate.parse("2026-08-07"))
        runBlocking { app.graph.entries.log(LogRequest(QUICKMIST, at = london("2026-10-23T14:00"))) }
        showToday()

        waitForDescription("1 mg used. Daily guide 0 mg. 1 mg over the 0 mg guide.")
        compose.onNodeWithText("Week 12 of 12, day 1. An NRT-free week.").assertExists()
    }

    @Test
    fun `an NRT-free week is paced against nothing`() {
        app.setUp()
        // Week 11 of a plan from Friday 7 August runs from 16 to 22 October, and week 12 from today.
        app.seedPlan(start = LocalDate.parse("2026-08-07"))
        showToday()

        waitForDescription("0 mg used. Daily guide 0 mg. At the 0 mg guide.")
        compose.onNodeWithText("Week 12 of 12, day 1. An NRT-free week.").assertExists()
        compose.onNodeWithText("mg this week").assertExists()
    }

    @Test
    fun `tapping the band opens the lair`() {
        app.setUp()
        app.seedPlan()
        var opened = false
        showToday(onOpenLair = { opened = true })

        waitForDescription("Gary, 120 of 120 HP")
        compose.onNode(hasContentDescription("Gary, 120 of 120 HP", substring = true)).performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            opened
        }
    }
}
