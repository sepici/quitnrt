package app.thelasthit.nrt.ui.advice

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The calls Today, the tip after logging and Insights use to open advice. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class AdviceNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private lateinit var nav: NavHostController

    @Before
    fun setUpApp() {
        app.setUp()
        compose.setContent {
            LastHitTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = "start") {
                    composable("start") {}
                    advice(nav)
                }
            }
        }
    }

    private fun go(action: NavHostController.() -> Unit) = compose.runOnIdle { nav.action() }

    @Test
    fun `they open the list, a strategy and both timers`() {
        go { openAdvice() }
        compose.waitUntilExactlyOneExists(hasText("Every strategy"), timeoutMillis = 5_000)

        go { openStrategy("four_ds") }
        compose.waitUntilExactlyOneExists(hasText("The 4 Ds") and isHeading(), timeoutMillis = 5_000)

        go { openTimer(Interactive.BREATHING_TIMER) }
        compose.waitUntilExactlyOneExists(hasText("Start box breathing"), timeoutMillis = 5_000)

        go { openTimer(Interactive.DELAY_TIMER) }
        compose.waitUntilExactlyOneExists(hasText("Start 5\u00A0minutes"), timeoutMillis = 5_000)
    }

    @Test
    fun `the tip after logging can start a minute of breathing at once`() {
        go { openBreathing(minutes = 1, pattern = BreathingPattern.FOUR_SEVEN_EIGHT, start = true) }

        compose.waitUntilExactlyOneExists(hasContentDescription("Breathe in. Round 1 of 3."), timeoutMillis = 5_000)
    }

    @Test
    fun `opening the delay timer again goes back to the one running`() {
        go { openDelay() }
        compose.waitUntilExactlyOneExists(hasText("Start 5\u00A0minutes"), timeoutMillis = 5_000)
        compose.onNodeWithText("Start 5\u00A0minutes").performClick()
        compose.onNodeWithText("Breathe with the timer").performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("Start box breathing"), timeoutMillis = 5_000)

        go { openDelay() }
        compose.waitUntilExactlyOneExists(hasContentDescription("5 minutes left of 5 minutes"), timeoutMillis = 5_000)
    }
}
