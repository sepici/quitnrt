package app.thelasthit.nrt.ui.insights

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.advice.AdviceLinks
import app.thelasthit.nrt.ui.advice.seedAdviceWeeks
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The advice in Insights' triggers: from the last 14 days, under the top trigger's row or at the top of the list. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class TriggerAdviceTest {

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

    @Before
    fun setUpApp() = app.setUp()

    private fun show() {
        compose.setContent {
            LastHitTheme { InsightsScreen(remember { SnackbarHostState() }, adviceLinks = links) }
        }
    }

    private fun waitFor(text: String) = compose.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    @Test
    fun `under the top trigger, the strategy and why it helps, with the two links`() {
        app.seedAdviceWeeks()
        show()

        waitFor("From your last 14\u00A0days")
        waitFor(
            "Get up from the table. The end of a meal, or of a coffee or tea, is a common cue. " +
                "Doing something different straight away breaks it.",
        )
        compose.onNodeWithText("After a meal is your top trigger.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("How to do it").performScrollTo().performClick()
        compose.onNodeWithText("All advice").performScrollTo().performClick()
        assertEquals(listOf("strategy:leave_the_table", "all"), opened)
    }

    @Test
    fun `with a timing tip, when these doses come and the time to try`() {
        app.clock.now = london("2026-10-22T15:05")
        app.seedAdviceWeeks()
        show()

        waitFor("These doses come mostly around 13:00. Try having your dose at 12:30, before lunch.")
    }

    @Test
    fun `when the top trigger isn't one of the rows, the advice heads the list and names it`() {
        // After a meal leads the 14 days, but stopped a week ago, so the last 7 days don't list it.
        val first = LocalDate.parse("2026-10-09")
        runBlocking {
            for (day in 0L..7L) {
                app.graph.entries.log(LogRequest(GUM_2, at = london("${first.plusDays(day)}T13:00"), tagKeys = setOf("after_meal")))
            }
            for (day in 8L..13L) {
                listOf("woke_up" to "08:00", "coffee_tea" to "10:30", "work_break" to "11:00", "drink" to "19:00", "before_bed" to "22:00")
                    .forEach { (tag, time) -> app.graph.entries.log(LogRequest(GUM_2, at = london("${first.plusDays(day)}T$time"), tagKeys = setOf(tag))) }
            }
        }
        show()
        waitFor("From your last 14\u00A0days")
        compose.onNodeWithText("7\u00A0days").performClick()

        waitFor(
            "After a meal is your top trigger. Get up from the table. The end of a meal, or of a coffee or tea, is a common cue. " +
                "Doing something different straight away breaks it.",
        )
    }

    @Test
    fun `with too little logged there's no advice`() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-22T13:00"), tagKeys = setOf("after_meal"))) }
        show()

        waitFor("Triggers")
        compose.waitForIdle()
        compose.onNodeWithText("From your last 14\u00A0days").assertDoesNotExist()
    }
}
