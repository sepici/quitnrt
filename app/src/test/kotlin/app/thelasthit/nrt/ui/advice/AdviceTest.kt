package app.thelasthit.nrt.ui.advice

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The full advice list and a strategy's page. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = FULL_PAGE)
class AdviceTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val opened = mutableListOf<String>()
    private val timers = mutableListOf<Interactive>()

    private fun showList() {
        compose.setContent {
            LastHitTheme {
                AdviceScreen(onBack = {}, onStrategy = { opened += it }, onTimer = { timers += it })
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithText(text).performScrollTo()
    }

    @Test
    fun `your top triggers come first, with their share and the time to try from your logs`() {
        app.setUp()
        app.seedAdviceWeeks()
        showList()

        compose.waitUntilExactlyOneExists(hasText("27% of doses"), timeoutMillis = 5_000)
        compose.onNodeWithText("15% of doses").assertExists()
        compose.onNodeWithText("10% of doses").assertExists()
        // Each trigger's name heads its strategies.
        for (trigger in listOf("After a meal", "Just woke up", "With coffee or tea")) {
            compose.onNode(hasText(trigger) and isHeading()).assertExists()
        }
        compose.onNodeWithText("From your logs: try 12:30, before lunch").assertExists()
        compose.onNodeWithText("From your logs: try 09:15 this week, 15\u00A0minutes later").assertExists()
        // A strategy that isn't from the logs shows its first step.
        compose.onNodeWithText("Get up as soon as you've finished.").assertExists()
    }

    @Test
    fun `with too little logged the list says how the top triggers come`() {
        app.setUp()
        showList()

        compose.waitUntilExactlyOneExists(hasText("Tag doses as you log them", substring = true), timeoutMillis = 5_000)
        compose.onNodeWithText("Every strategy").assertExists()
    }

    @Test
    fun `a strategy opens its page and a timer opens the timer`() {
        app.setUp()
        app.seedAdviceWeeks()
        showList()

        compose.waitUntilExactlyOneExists(hasText("27% of doses"), timeoutMillis = 5_000)
        // Listed under After a meal, and again under Routine.
        compose.onAllNodesWithText("Move the dose to before the meal").onFirst().performClick()
        assertEquals(listOf("dose_before_meal"), opened)

        scrollTo("Delay")
        compose.onNodeWithText("Delay").performClick()
        scrollTo("Deep breaths")
        compose.onNodeWithText("Deep breaths").performClick()
        scrollTo("Drink water")
        compose.onNodeWithText("Drink water").performClick()
        assertEquals(listOf(Interactive.DELAY_TIMER, Interactive.BREATHING_TIMER), timers)
        assertEquals(listOf("dose_before_meal", "four_ds"), opened)
    }

    @Test
    fun `every strategy is listed once under the kind of trigger it suits`() {
        app.setUp()
        showList()

        compose.waitUntilExactlyOneExists(hasText("Every strategy"), timeoutMillis = 5_000)
        for (heading in listOf("Any time", "Feelings", "Routine", "People", "Body", "Habit")) {
            scrollTo(heading)
        }
        scrollTo("Make each dose a decision")
        compose.onNodeWithText("On autopilot, Bored, Screen time").assertExists()
        scrollTo("Ride out the urge")
        compose.onNodeWithText("Strong craving, Withdrawal, Stressed").assertExists()
    }

    @Test
    fun `free help dials the quitline in the phone's own app`() {
        app.setUp()
        showList()

        compose.waitUntilExactlyOneExists(hasText("Free help"), timeoutMillis = 5_000)
        scrollTo("Smokefree National Helpline")
        compose.onNodeWithText("England, 0300 123 1044").assertExists()
        compose.onNode(hasContentDescription("Call Smokefree National Helpline")).performScrollTo().performClick()

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:0300%20123%201044", intent.data.toString())
    }

    @Test
    fun `a renamed tag shows by its own name`() {
        app.setUp()
        app.seedAdviceWeeks()
        runBlocking {
            val tag = app.graph.tags.all.first().first { it.key == "after_meal" }
            app.graph.tags.rename(tag, "Lunch and dinner")
        }
        showList()

        compose.waitUntilExactlyOneExists(hasText("Lunch and dinner") and isHeading(), timeoutMillis = 5_000)
    }

    @Test
    fun `a strategy's page has what the logs say, how to do it, more ideas and its sources`() {
        app.setUp()
        app.seedAdviceWeeks()
        var allAdvice = 0
        compose.setContent {
            LastHitTheme {
                StrategyScreen(
                    strategyKey = "dose_before_meal",
                    onBack = {},
                    onAllAdvice = { allAdvice++ },
                    onStrategy = { opened += it },
                    onTimer = { timers += it },
                )
            }
        }

        compose.waitUntilExactlyOneExists(hasText("19 of your 71 doses", substring = true), timeoutMillis = 5_000)
        compose.onNodeWithText("Routine").assertExists()
        compose.onNodeWithText("19 of your 71 doses were tagged After a meal. Most came around 13:00 and 19:00.").assertExists()
        compose.onNodeWithText("Try having your dose at 12:30 and 18:30, before you eat, instead of after.").assertExists()
        compose.onNode(hasContentDescription("After a meal doses by hour", substring = true)).assertExists()
        scrollTo("How to do it")
        compose.onNode(hasContentDescription("Step 1. Have your usual after-meal dose before the meal instead.")).assertExists()
        scrollTo("Also for after a meal")
        compose.onNodeWithText("Get up from the table").performScrollTo().performClick()
        assertEquals(listOf("leave_the_table"), opened)
        scrollTo("Based on")
        compose.onNodeWithText("Timing: this app's own suggestion, from your logs.").assertExists()
        compose.onNodeWithText("Checked October 2026.", substring = true).assertExists()

        compose.onNodeWithText("All advice").performClick()
        assertEquals(1, allAdvice)
    }

    @Test
    fun `a strategy with a timer opens it from its page`() {
        app.setUp()
        compose.setContent {
            LastHitTheme {
                StrategyScreen("breathing_timer", onBack = {}, onAllAdvice = {}, onStrategy = { opened += it }, onTimer = { timers += it })
            }
        }

        compose.waitUntilExactlyOneExists(hasText("Slow your breathing"), timeoutMillis = 5_000)
        compose.onNodeWithText("Open the timer").performClick()
        assertEquals(listOf(Interactive.BREATHING_TIMER), timers)
    }

    @Test
    fun `a strategy the library doesn't have goes back`() {
        app.setUp()
        var back = 0
        compose.setContent {
            LastHitTheme { StrategyScreen("no_such_thing", onBack = { back++ }, onAllAdvice = {}, onStrategy = {}, onTimer = {}) }
        }
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            back == 1
        }
    }
}
