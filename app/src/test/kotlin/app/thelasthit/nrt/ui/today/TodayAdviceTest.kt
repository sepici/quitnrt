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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.seedPlan
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.advice.AdviceLinks
import app.thelasthit.nrt.ui.advice.seedAdviceWeeks
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Today's advice line, the tip after logging in its place, and a slip's advice. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class TodayAdviceTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<String>()
    private val links = AdviceLinks(
        all = { opened += "all" },
        strategy = { opened += "strategy:$it" },
        timer = { opened += "timer:$it" },
        breatheNow = { opened += "breathe" },
    )

    /** The sheet's "Log" button, told apart from the Log pill behind the sheet by its "2 mg at 15:05". */
    private val logButton = hasText("Log") and hasText(" at ", substring = true)

    @Before
    fun setUpApp() = app.setUp()

    private fun show() {
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    TodayScreen(snackbars = snackbars, onOpenSettings = {}, onOpenLair = {}, adviceLinks = links)
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasContentDescription("Log a dose"), timeoutMillis = 5_000)
    }

    private fun logWithTag(tag: String) {
        compose.onNodeWithContentDescription("Log a dose").performClick()
        compose.waitUntilExactlyOneExists(hasText("Log a dose"), timeoutMillis = 5_000)
        compose.onNodeWithText(tag).performScrollTo().performClick()
        compose.onNode(logButton).performClick()
    }

    @Test
    fun `the line names the top trigger of the last 14 days and opens the advice`() {
        app.seedAdviceWeeks()
        show()

        // 23 October turns to the second of After a meal's three.
        compose.waitUntilExactlyOneExists(hasText("After a meal is your top trigger this fortnight. Get up from the table."), timeoutMillis = 5_000)
        compose.onNodeWithText("How to do it").performScrollTo().performClick()
        compose.onNodeWithText("All advice").performScrollTo().performClick()
        assertEquals(listOf("strategy:leave_the_table", "all"), opened)
    }

    @Test
    fun `on a day the timing tip's strategy comes round, the line has the time to try`() {
        app.clock.now = london("2026-10-22T15:05")
        app.seedAdviceWeeks()
        show()

        compose.waitUntilExactlyOneExists(
            hasText("After a meal is your top trigger this fortnight. Try having your dose at 12:30, before lunch."),
            timeoutMillis = 5_000,
        )
    }

    @Test
    fun `with too little logged there's no line`() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-22T13:00"), tagKeys = setOf("after_meal"))) }
        show()

        compose.waitUntilExactlyOneExists(hasText("Logged today"), timeoutMillis = 5_000)
        compose.waitForIdle()
        compose.onNodeWithText("How to do it").assertDoesNotExist()
    }

    @Test
    fun `a dose tagged Stressed brings a tip in the line's place until it's closed`() {
        app.seedAdviceWeeks()
        show()
        compose.waitUntilExactlyOneExists(hasText("All advice"), timeoutMillis = 5_000)

        logWithTag("Stressed")
        compose.waitUntilExactlyOneExists(hasText("Try box breathing next time"), timeoutMillis = 5_000)
        compose.onNodeWithText("You tagged that dose Stressed. Slow breathing eases both stress and cravings.").assertExists()
        // The snackbar keeps only the amount and Undo.
        compose.onNodeWithText("Logged 2\u00A0mg of Nicorette gum").assertExists()
        compose.onNodeWithText("All advice").assertDoesNotExist()

        compose.onNodeWithText("Breathe for 1\u00A0minute").performScrollTo().performClick()
        compose.onNodeWithText("Other ideas").performScrollTo().performClick()
        assertEquals(listOf("breathe", "all"), opened)

        compose.onNodeWithContentDescription("Close tip").performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("All advice"), timeoutMillis = 5_000)
        compose.onNodeWithText("Try box breathing next time").assertDoesNotExist()

        // Stressed has had today's tip, closed or not.
        logWithTag("Stressed")
        compose.waitUntilExactlyOneExists(hasText("2\u00A0doses, 4\u00A0mg"), timeoutMillis = 5_000)
        compose.waitForIdle()
        compose.onNodeWithText("Try box breathing next time").assertDoesNotExist()
        compose.onNodeWithText("All advice").assertExists()
    }

    @Test
    fun `a strategy without a timer offers how to do it`() {
        show()
        logWithTag("After a meal")

        compose.waitUntilExactlyOneExists(hasText("Next time, move the dose to before the meal"), timeoutMillis = 5_000)
        compose.onNodeWithText("How to do it").performScrollTo().performClick()
        assertEquals(listOf("strategy:dose_before_meal"), opened)
    }

    @Test
    fun `a dose on autopilot gets the delay timer, which the tip opens`() {
        show()
        logWithTag("On autopilot")

        compose.waitUntilExactlyOneExists(hasText("Try the delay timer next time"), timeoutMillis = 5_000)
        compose.onNodeWithText("Open the timer").performScrollTo().performClick()
        assertEquals(listOf("timer:${Interactive.DELAY_TIMER}"), opened)
    }

    @Test
    fun `Undo takes the tip back with the dose`() {
        show()
        logWithTag("Stressed")
        compose.waitUntilExactlyOneExists(hasText("Try box breathing next time"), timeoutMillis = 5_000)

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilExactlyOneExists(hasText("Nothing logged yet today"), timeoutMillis = 5_000)
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasText("Try box breathing next time")).fetchSemanticsNodes().isEmpty()
        }
        assertNull(runBlocking { app.graph.reminders.adviceTip.first() }?.showing)
    }

    @Test
    fun `yesterday over its guide brings the advice for after a slip`() {
        app.seedPlan()
        // Week 4's guide is 14 mg: 16 mg yesterday is over it.
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, quantity = 8, at = london("2026-10-22T13:00"))) }
        show()

        compose.waitUntilExactlyOneExists(
            hasText("Yesterday went over its guide. One day over doesn't undo what you've done."),
            timeoutMillis = 5_000,
        )
        compose.onNodeWithText("How to do it").performScrollTo().performClick()
        assertEquals(listOf("strategy:after_a_slip"), opened)
    }

    @Test
    fun `a dose that takes today over its guide brings a tip after a slip`() {
        app.seedPlan()
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, quantity = 6, at = london("2026-10-23T09:00"))) }
        show()

        // 12 mg so far, and 4 more takes today past the 14 mg guide.
        compose.onNodeWithContentDescription("Log a dose").performClick()
        compose.waitUntilExactlyOneExists(hasText("Log a dose"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("One more").performClick()
        compose.onNode(logButton).performClick()

        compose.waitUntilExactlyOneExists(hasText("Today is over its guide"), timeoutMillis = 5_000)
        compose.onNodeWithText("That doesn't undo what you've done. It shows you where the hard moments are.").assertExists()
    }
}
