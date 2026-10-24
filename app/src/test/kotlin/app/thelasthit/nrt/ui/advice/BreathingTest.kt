package app.thelasthit.nrt.ui.advice

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import app.thelasthit.nrt.ui.lair.setAnimatorScale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The breathing timer, on the test clock: set-up, a session by the clock, pausing, the end and the badges. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class BreathingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val buzzer = FakeBuzzer()
    private lateinit var view: View
    private lateinit var viewModel: BreathingViewModel
    private var backs = 0

    @Before
    fun setUpApp() = app.setUp()

    private fun show(vararg args: Pair<String, Any>) {
        compose.setContent {
            view = LocalView.current
            LastHitTheme {
                BreathingScreen(
                    onBack = { backs++ },
                    viewModel = timerViewModel { graph, saved, _ ->
                        args.forEach { (key, value) -> saved[key] = value }
                        BreathingViewModel(graph.clock, saved, graph.breathing, buzzer, prefs = null).also { viewModel = it }
                    },
                )
            }
        }
    }

    private fun later(millis: Long) {
        app.clock.now = app.clock.now.plusMillis(millis)
    }

    private fun waitFor(description: String) =
        compose.waitUntilExactlyOneExists(hasContentDescription(description), timeoutMillis = 5_000)

    @Test
    fun `the set-up offers both patterns for 1 to 3 minutes, in whole rounds`() {
        show()

        compose.waitUntilExactlyOneExists(hasText("No sessions yet"), timeoutMillis = 5_000)
        compose.onNodeWithText("4 rounds, about 1\u00A0minute").assertExists()
        compose.onNodeWithText("Your first finished session earns a breathing badge.").assertExists()

        compose.onNodeWithText("2\u00A0min").performClick()
        compose.waitUntilExactlyOneExists(hasText("8 rounds, about 2\u00A0minutes"))
        compose.onNodeWithText("4-7-8 breathing").performClick()
        compose.waitUntilExactlyOneExists(hasText("6 rounds, about 2\u00A0minutes"))
        compose.onNodeWithText("Start 4-7-8 breathing").performClick()

        waitFor("Breathe in. Round 1 of 6.")
        compose.onNodeWithText("1:54 left").assertExists()
    }

    @Test
    fun `a session follows the clock, counting each step and round`() {
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")

        later(34_200)
        waitFor("Breathe in. Round 3 of 4.")
        compose.onNodeWithText("0:30 left").assertExists()
        compose.onNodeWithContentDescription("30 seconds left").assertExists()

        later(4_000)
        waitFor("Hold. Round 3 of 4.")
        later(4_000)
        waitFor("Breathe out. Round 3 of 4.")
    }

    @Test
    fun `with the phone's animations off, the steps still change and are read out`() {
        app.setAnimatorScale(0f)
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")

        later(4_000)
        waitFor("Hold. Round 1 of 4.")
        later(4_000)
        waitFor("Breathe out. Round 1 of 4.")
        compose.onNodeWithContentDescription("Breathe out. Round 1 of 4.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `a session run to the end is saved and counts towards the badges`() {
        app.seedBreathing(4)
        show()
        compose.waitUntilExactlyOneExists(hasText("4 sessions done"), timeoutMillis = 5_000)
        compose.onNodeWithText("The next breathing badge comes at 10.").assertExists()
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")

        later(64_000)
        compose.waitUntilExactlyOneExists(hasText("Session 5 done"), timeoutMillis = 5_000)
        // The page changes in place as the last round ends, so it's told as a new pane.
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Session 5 done")).assertExists()
        compose.onNodeWithText("Session 5 done").assert(isHeading())
        compose.onNodeWithText("1\u00A0minute of box breathing, 4 rounds.").assertExists()
        compose.onNodeWithText("The next breathing badge comes at 10.").assertExists()
        assertEquals(5, runBlocking { app.graph.breathing.completedCount() })

        compose.onNodeWithText("Done").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the session that earns a badge says so`() {
        app.seedBreathing(9)
        show()
        compose.waitUntilExactlyOneExists(hasText("9 sessions done"), timeoutMillis = 5_000)
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")

        later(64_000)
        compose.waitUntilExactlyOneExists(hasText("Session 10 done"), timeoutMillis = 5_000)
        compose.onNodeWithText("That earns a breathing badge. The next comes at 25.").assertExists()
    }

    @Test
    fun `pausing stops the clock, and the session ends on the breathing done`() {
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        later(10_000)
        waitFor("Breathe out. Round 1 of 4.")

        compose.onNodeWithText("Pause").performClick()
        waitFor("Paused. Round 1 of 4.")
        later(100_000)
        compose.onNodeWithText("Resume").performClick()
        compose.onNodeWithText("0:54 left").assertExists()

        later(53_000)
        waitFor("Hold. Round 4 of 4.")
        later(1_000)
        compose.waitUntilExactlyOneExists(hasText("Session 1 done"), timeoutMillis = 5_000)
    }

    @Test
    fun `another minute starts a new session of the same pattern`() {
        show()
        compose.onNodeWithText("4-7-8 breathing").performClick()
        compose.onNodeWithText("Start 4-7-8 breathing").performClick()
        later(57_000)
        compose.waitUntilExactlyOneExists(hasText("Session 1 done"), timeoutMillis = 5_000)
        compose.onNodeWithText("3 rounds", substring = true).assertExists()

        compose.onNodeWithText("Breathe for another minute").performClick()
        waitFor("Breathe in. Round 1 of 3.")
    }

    @Test
    fun `stopping part way goes back, and the session doesn't count`() {
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        later(40_000)
        waitFor("Breathe out. Round 3 of 4.")

        compose.onNodeWithContentDescription("Stop and go back").performClick()
        assertEquals(1, backs)
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking { app.graph.breathing.completedCount() } == 0
        }
    }

    @Test
    fun `it buzzes at each change of step, and twice at the end`() {
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")
        assertEquals(1, buzzer.ticks)

        later(4_000)
        waitFor("Hold. Round 1 of 4.")
        later(4_000)
        waitFor("Breathe out. Round 1 of 4.")
        assertEquals(3, buzzer.ticks)

        later(60_000)
        compose.waitUntilExactlyOneExists(hasText("Session 1 done"), timeoutMillis = 5_000)
        assertEquals(3, buzzer.ticks)
        assertEquals(1, buzzer.finishes)
    }

    @Test
    fun `with buzzing off it stays still`() {
        show()
        compose.onNodeWithText("Buzz at each change").performClick()
        compose.onNodeWithText("Start box breathing").performClick()
        later(64_000)
        compose.waitUntilExactlyOneExists(hasText("Session 1 done"), timeoutMillis = 5_000)
        assertEquals(0, buzzer.ticks)
        assertEquals(0, buzzer.finishes)
    }

    @Test
    fun `opened from a tip, it starts at once`() {
        show(AdviceRoutes.START to true, AdviceRoutes.MINUTES to 2)

        waitFor("Breathe in. Round 1 of 8.")
    }

    @Test
    fun `the screen stays on while a session runs`() {
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            view.keepScreenOn
        }

        later(64_000)
        compose.waitUntilExactlyOneExists(hasText("Session 1 done"), timeoutMillis = 5_000)
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            !view.keepScreenOn
        }
    }

    @Test
    fun `leaving the app pauses the session`() {
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        later(30_000)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        waitFor("Paused. Round 1 of 4.")
        compose.onNodeWithText("1:04 left").assertExists()
        compose.onNodeWithText("Resume").assertExists()
    }

    @Test
    fun `turning the phone doesn't pause the session`() {
        show()
        compose.onNodeWithText("Start box breathing").performClick()
        waitFor("Breathe in. Round 1 of 4.")

        // Turning the phone, or the system switching to dark mode, rebuilds the activity.
        compose.activityRule.scenario.recreate()

        val stage = viewModel.stage.value as BreathingStage.Running
        assertFalse(stage.run.isPaused)
    }
}
