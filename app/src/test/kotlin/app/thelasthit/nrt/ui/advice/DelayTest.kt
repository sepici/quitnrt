package app.thelasthit.nrt.ui.advice

import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibratorManager
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPowerManager
import java.time.Duration

/** The delay timer, on the test clock: the length, counting from its start, the end, and breathing on top. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class DelayTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val buzzer = FakeBuzzer()
    private val awake = FakeWakeHold()
    private lateinit var view: View
    private var backs = 0
    private var breathes = 0

    @Before
    fun setUpApp() = app.setUp()

    private fun show(vararg saved: Pair<String, Any>) {
        compose.setContent {
            view = LocalView.current
            LastHitTheme {
                DelayScreen(
                    onBack = { backs++ },
                    onBreathe = { breathes++ },
                    viewModel = timerViewModel { graph, state, _ ->
                        saved.forEach { (key, value) -> state[key] = value }
                        DelayViewModel(graph.clock, state, buzzer, awake)
                    },
                )
            }
        }
    }

    private fun waitForLeft(description: String) =
        compose.waitUntilExactlyOneExists(hasContentDescription(description), timeoutMillis = 5_000)

    private fun at(time: String) {
        app.clock.now = london("2026-10-23T$time")
    }

    @Test
    fun `it runs for 5 to 10 minutes`() {
        show()

        compose.waitUntilExactlyOneExists(hasText("5\u00A0min"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("One minute less").assertIsNotEnabled()
        repeat(5) { compose.onNodeWithContentDescription("One minute more").performClick() }
        compose.waitUntilExactlyOneExists(hasText("10\u00A0min"))
        compose.onNodeWithContentDescription("One minute more").assertIsNotEnabled()
        compose.onNodeWithText("Start 10\u00A0minutes").assertExists()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `at 5 minutes the minus fades, as a disabled button does in the mockup`() {
        show()
        compose.waitUntilExactlyOneExists(hasText("5\u00A0min"), timeoutMillis = 5_000)

        // The fill, left of the icon: the surface on the plus, and faded towards the grey behind on the minus.
        fun fillOf(description: String): Color {
            val pixels = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
            return pixels[pixels.width / 5, pixels.height / 2]
        }
        val plus = fillOf("One minute more")
        val minus = fillOf("One minute less")
        assertEquals(Color.White, plus)
        assertNotEquals(plus, minus)
    }

    @Test
    fun `it counts down from its start`() {
        at("15:03:12")
        show()
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        waitForLeft("5 minutes left of 5 minutes")

        at("15:05:00")
        waitForLeft("3 minutes 12 seconds left of 5 minutes")
        compose.onNodeWithText("Started 15:03").assertExists()
        compose.onNodeWithText("Ends 15:08").assertExists()
    }

    @Test
    fun `it ends on time with a buzz, and can go again`() {
        at("15:00:00")
        show()
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        waitForLeft("5 minutes left of 5 minutes")

        at("15:05:00")
        compose.waitUntilExactlyOneExists(hasText("5\u00A0minutes done"), timeoutMillis = 5_000)
        compose.onNodeWithText("Started 15:00, ended 15:05.").assertExists()
        compose.onNodeWithText("If the urge is still there, give it another 5\u00A0minutes. If it's gone, you rode it out.").assertExists()
        assertEquals(1, buzzer.alarms)

        compose.onNodeWithText("Another 5\u00A0minutes").performClick()
        waitForLeft("5 minutes left of 5 minutes")
        compose.onNodeWithText("Ends 15:10").assertExists()
    }

    @Test
    fun `while it runs the phone stays awake until just after the end, so it buzzes with the screen off`() {
        at("15:00:00")
        show()
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        waitForLeft("5 minutes left of 5 minutes")
        assertEquals(Duration.ofMinutes(6), awake.heldFor)

        at("15:05:00")
        compose.waitUntilExactlyOneExists(hasText("5\u00A0minutes done"), timeoutMillis = 5_000)
        assertNull(awake.heldFor)
    }

    @Test
    fun `stopping lets the phone sleep again`() {
        show()
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.waitUntilExactlyOneExists(hasText("Stop the timer"), timeoutMillis = 5_000)
        assertEquals(Duration.ofMinutes(6), awake.heldFor)

        compose.onNodeWithText("Stop the timer").performClick()
        compose.waitUntilExactlyOneExists(hasText("Start 5\u00A0minutes"))
        assertNull(awake.heldFor)
    }

    @Test
    fun `opened again after the app was closed, it counts from its start`() {
        at("15:05:00")
        show(
            DelayViewModel.STAGE to DelayViewModel.RUNNING,
            DelayViewModel.MINUTES to 5,
            DelayViewModel.STARTED_AT to london("2026-10-23T15:01:00").toEpochMilli(),
        )

        waitForLeft("1 minute left of 5 minutes")
        compose.onNodeWithText("Started 15:01").assertExists()
        // Awake for the minute left, and the minute after the end that it can still buzz in.
        assertEquals(Duration.ofMinutes(2), awake.heldFor)
    }

    @Test
    fun `found over long after the end, it says so without a buzz`() {
        at("15:05:00")
        show(
            DelayViewModel.STAGE to DelayViewModel.RUNNING,
            DelayViewModel.MINUTES to 10,
            DelayViewModel.STARTED_AT to london("2026-10-23T14:00:00").toEpochMilli(),
        )

        compose.waitUntilExactlyOneExists(hasText("10\u00A0minutes done"), timeoutMillis = 5_000)
        compose.onNodeWithText("Started 14:00, ended 14:10.").assertExists()
        assertEquals(0, buzzer.alarms)
    }

    @Test
    fun `stopping goes back to choosing, and the cross leaves`() {
        show()
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.waitUntilExactlyOneExists(hasText("Stop the timer"), timeoutMillis = 5_000)

        compose.onNodeWithText("Stop the timer").performClick()
        compose.waitUntilExactlyOneExists(hasText("Start 5\u00A0minutes"))
        assertEquals(0, backs)

        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.onNodeWithContentDescription("Stop and go back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `breathe with the timer opens breathing`() {
        show()
        compose.onNodeWithText("Breathe with the timer").performScrollTo().performClick()
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.onNodeWithText("Breathe with the timer").performScrollTo().performClick()
        assertEquals(2, breathes)
    }

    @Test
    fun `the screen stays on while it runs`() {
        show()
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            view.keepScreenOn
        }

        compose.onNodeWithText("Stop the timer").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            !view.keepScreenOn
        }
    }

    @Test
    fun `under the breathing timer the delay keeps running, buzzes at the end, and is done on the way back`() {
        lateinit var nav: NavHostController
        compose.setContent {
            view = LocalView.current
            LastHitTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = AdviceRoutes.LIST) { advice(nav) }
            }
        }
        compose.waitUntilExactlyOneExists(hasText("Every strategy"), timeoutMillis = 5_000)
        compose.runOnIdle { nav.openDelay() }
        compose.waitUntilExactlyOneExists(hasText("Start 5\u00A0minutes"), timeoutMillis = 5_000)
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        at("15:06:00")
        waitForLeft("4 minutes left of 5 minutes")
        // The real wake lock: the processor stays on, with the screen off, until the end.
        val lock = checkNotNull(ShadowPowerManager.getLatestWakeLock())
        assertTrue(lock.isHeld)

        compose.onNodeWithText("Breathe with the timer").performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("Start box breathing"), timeoutMillis = 5_000)
        // The delay still holds the screen on under the breathing set-up.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            view.keepScreenOn
        }

        // The phone's clock reaches the end with breathing on top.
        val vibrator = app.getSystemService(VibratorManager::class.java).defaultVibrator
        shadowOf(vibrator).setHasVibrator(true)
        at("15:10:01")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_ALARM, attributes.usage)
        assertFalse(lock.isHeld)

        compose.runOnIdle { nav.popBackStack() }
        compose.waitUntilExactlyOneExists(hasText("5\u00A0minutes done"), timeoutMillis = 5_000)
        compose.onNodeWithText("Started 15:05, ended 15:10.").assertExists()
    }

    @Test
    fun `the advice pages hide the tab bar`() {
        listOf(AdviceRoutes.LIST, AdviceRoutes.STRATEGY, AdviceRoutes.BREATHING, AdviceRoutes.DELAY).forEach {
            assertEquals(it, true, AdviceRoutes.isAdvice(it))
        }
        listOf("today", "insights", "advice-x", null).forEach { assertEquals("$it", false, AdviceRoutes.isAdvice(it)) }
    }
}
