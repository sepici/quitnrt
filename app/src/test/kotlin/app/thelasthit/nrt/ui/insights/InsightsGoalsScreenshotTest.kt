package app.thelasthit.nrt.ui.insights

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.plan.seedGoalWeeks
import app.thelasthit.nrt.ui.plan.setUpForPlan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tall enough for the whole of Insights with a plan and its goals. */
private const val GOALS_PAGE = "en-rGB-w411dp-h5200dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** The same at the large font. */
private const val GOALS_PAGE_LARGE = "en-rGB-w411dp-h6400dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** Insights with every extra goal on since week 3, as "Your goals" shows them after Habits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsGoalsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = GOALS_PAGE)
    fun goals() = capture()

    @Test @Config(qualifiers = GOALS_PAGE_LARGE, fontScale = LARGE_FONT)
    fun goalsLarge() = capture()

    private fun capture() {
        app.setUpForPlan()
        app.seedGoalWeeks()
        compose.captureLooks("insights-goals", ready = hasText("Your goals")) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }
}
