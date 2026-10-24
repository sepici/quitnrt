package app.thelasthit.nrt.ui.advice

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A window tall enough for the whole advice list, at a Pixel 7's width and density. */
private const val TALL = "en-rGB-w411dp-h5200dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** A taller one for the list at large font. */
private const val TALLER = "en-rGB-w411dp-h6800dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * Each advice mockup's state, from the same two weeks of logs: the list, a strategy's page, and
 * both timers set up, running and done.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdviceScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = TALL)
    fun list() = captureList()

    @Test @Config(qualifiers = TALLER, fontScale = LARGE_FONT)
    fun listLarge() = captureList()

    private fun captureList() {
        app.setUp()
        app.seedAdviceWeeks()
        compose.captureLooks("advice-list", ready = hasText("27% of doses")) {
            AdviceScreen(onBack = {}, onStrategy = {}, onTimer = {})
        }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun detail() = captureDetail()

    @Test @Config(qualifiers = TALL, fontScale = LARGE_FONT)
    fun detailLarge() = captureDetail()

    private fun captureDetail() {
        app.setUp()
        app.seedAdviceWeeks()
        compose.captureLooks("advice-detail", ready = hasText("19 of your 71 doses", substring = true)) {
            StrategyScreen("dose_before_meal", onBack = {}, onAllAdvice = {}, onStrategy = {}, onTimer = {})
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun breathing() = captureBreathing()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun breathingLarge() = captureBreathing()

    private fun captureBreathing() {
        app.setUp()
        app.seedBreathing(4)
        compose.captureLooks("advice-breathing", ready = hasText("4 sessions done")) { BreathingScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun breathingRun() = captureBreathingRun()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun breathingRunLarge() = captureBreathingRun()

    /** Two rounds and a bit in: breathe in, 3, round 3 of 4, with 30 seconds left. */
    private fun captureBreathingRun() {
        app.setUp()
        compose.captureLooks(
            "advice-breathing-run",
            ready = hasText("Start box breathing"),
            before = {
                onNodeWithText("Start box breathing").performClick()
                app.clock.now = app.clock.now.plusMillis(34_200)
                waitUntilExactlyOneExists(hasContentDescription("Breathe in. Round 3 of 4."), timeoutMillis = 5_000)
            },
        ) { BreathingScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun breathingDone() = captureBreathingDone()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun breathingDoneLarge() = captureBreathingDone()

    private fun captureBreathingDone() {
        app.setUp()
        app.seedBreathing(4)
        compose.captureLooks(
            "advice-breathing-done",
            ready = hasText("Start box breathing"),
            before = {
                onNodeWithText("Start box breathing").performClick()
                app.clock.now = app.clock.now.plusSeconds(64)
                waitUntilExactlyOneExists(hasText("Session 5 done"), timeoutMillis = 5_000)
            },
        ) { BreathingScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun delay() = captureDelay()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun delayLarge() = captureDelay()

    private fun captureDelay() {
        app.setUp()
        compose.captureLooks("advice-delay", ready = hasText("Start 5\u00A0minutes")) { DelayScreen(onBack = {}, onBreathe = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun delayRun() = captureDelayRun()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun delayRunLarge() = captureDelayRun()

    /** Started at 15:03:12, so at 15:05 there are 3:12 left. */
    private fun captureDelayRun() {
        app.setUp()
        app.clock.now = london("2026-10-23T15:03:12")
        compose.captureLooks(
            "advice-delay-run",
            ready = hasText("Start 5\u00A0minutes"),
            before = {
                onNodeWithText("Start 5\u00A0minutes").performClick()
                app.clock.now = london("2026-10-23T15:05:00")
                waitUntilExactlyOneExists(hasContentDescription("3 minutes 12 seconds left of 5 minutes"), timeoutMillis = 5_000)
            },
        ) { DelayScreen(onBack = {}, onBreathe = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun delayDone() = captureDelayDone()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun delayDoneLarge() = captureDelayDone()

    private fun captureDelayDone() {
        app.setUp()
        app.clock.now = london("2026-10-23T15:00:00")
        compose.captureLooks(
            "advice-delay-done",
            ready = hasText("Start 5\u00A0minutes"),
            before = {
                onNodeWithText("Start 5\u00A0minutes").performClick()
                app.clock.now = london("2026-10-23T15:05:00")
                waitUntilExactlyOneExists(hasText("5\u00A0minutes done"), timeoutMillis = 5_000)
            },
        ) { DelayScreen(onBack = {}, onBreathe = {}) }
    }
}
