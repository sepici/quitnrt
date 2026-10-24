package app.thelasthit.nrt.ui.advice

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.domain.advice.DelayRun
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The timers' settings and notices: the breathing buzz kept with the phone's other settings,
 * and the delay timer booking, clearing and handing over its end.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class TimerSettingsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpApp() = app.setUp()

    private val buzzSwitch = hasText("Buzz at each change") and isToggleable()

    @Test
    fun `the breathing buzz is kept on this phone and read back by the next session`() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            LastHitTheme {
                // A new view model for each page, as a new visit to the timer would have, on the app's own store.
                key(page) {
                    val graph = app.graph
                    BreathingScreen(
                        onBack = {},
                        viewModel = remember { BreathingViewModel(graph.clock, SavedStateHandle(), graph.breathing, FakeBuzzer(), graph.reminders) },
                    )
                }
            }
        }
        compose.waitUntilExactlyOneExists(buzzSwitch, timeoutMillis = 5_000)
        compose.onNode(buzzSwitch).assertIsOn()

        compose.onNode(buzzSwitch).performClick()
        compose.onNode(buzzSwitch).assertIsOff()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking { !app.graph.reminders.timers.first().breathingBuzz }
        }

        compose.runOnIdle { page++ }
        compose.waitUntilExactlyOneExists(buzzSwitch and isOff(), timeoutMillis = 5_000)
    }

    @Test
    fun `the delay timer books its end as it starts, and takes it back when stopped`() {
        val notices = FakeDelayEnding()
        showDelay(notices)
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.waitUntilExactlyOneExists(hasText("Stop the timer"), timeoutMillis = 5_000)
        assertEquals(listOf(DelayRun(TestApp.NOW, 5)), notices.booked)

        compose.onNodeWithText("Stop the timer").performClick()
        compose.waitUntilExactlyOneExists(hasText("Start 5\u00A0minutes"), timeoutMillis = 5_000)
        assertEquals(1, notices.cleared)
    }

    @Test
    fun `at the end, the app buzzes only when its notices say it's on screen`() {
        val buzzer = FakeBuzzer()
        val notices = FakeDelayEnding(onScreen = false)
        showDelay(notices, buzzer)
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.waitUntilExactlyOneExists(hasText("Stop the timer"), timeoutMillis = 5_000)

        app.clock.now = london("2026-10-23T15:10:00")
        compose.waitUntilExactlyOneExists(hasText("5\u00A0minutes done"), timeoutMillis = 5_000)
        assertEquals(listOf(DelayRun(TestApp.NOW, 5)), notices.ended)
        assertEquals(0, buzzer.alarms)
        // On screen, the end frame clears the notice.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            notices.cleared > 0
        }
        assertFalse(notices.booked.isEmpty())
    }

    private fun showDelay(notices: DelayEnding, buzzer: Buzzer = FakeBuzzer()) {
        compose.setContent {
            LastHitTheme {
                DelayScreen(
                    onBack = {},
                    onBreathe = {},
                    viewModel = timerViewModel { graph, saved, _ -> DelayViewModel(graph.clock, saved, buzzer, FakeWakeHold(), notices) },
                )
            }
        }
        compose.waitUntilExactlyOneExists(hasText("Start 5\u00A0minutes"), timeoutMillis = 5_000)
    }
}

/** Notices that remember what they were asked to do. */
class FakeDelayEnding(private val onScreen: Boolean = true) : DelayEnding {
    val booked = mutableListOf<DelayRun>()
    val ended = mutableListOf<DelayRun>()
    var cleared = 0

    override fun book(run: DelayRun) {
        booked += run
    }

    override fun clear() {
        cleared++
    }

    override fun ended(run: DelayRun, now: Instant): Boolean {
        ended += run
        return onScreen && run.buzzesAt(now)
    }
}
