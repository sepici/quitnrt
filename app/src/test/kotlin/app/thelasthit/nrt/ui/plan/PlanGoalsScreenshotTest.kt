package app.thelasthit.nrt.ui.plan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.captureLooks
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** As tall as the Plan tab's week with its schedule. */
private const val WEEK_PAGE = "en-rGB-w411dp-h2600dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** The same at the large font. */
private const val WEEK_PAGE_LARGE = "en-rGB-w411dp-h3300dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** The Plan tab's week with every extra goal on, light and dark, at normal and large font. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanGoalsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpApp() {
        app.setUpForPlan()
    }

    @Test @Config(qualifiers = WEEK_PAGE)
    fun weekWithGoals() = capture()

    @Test @Config(qualifiers = WEEK_PAGE_LARGE, fontScale = LARGE_FONT)
    fun weekWithGoalsLarge() = capture()

    private fun capture() {
        app.seedGoalWeeks()
        compose.captureLooks(
            "plan-week-goals",
            ready = planTab,
            before = {
                onNode(planTab).performClick()
                waitUntilAtLeastOneExists(hasText("Extra goals this week"), timeoutMillis = 10_000)
            },
        ) { AppShell() }
    }
}
