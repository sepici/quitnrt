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

/**
 * The Plan tab's week with every extra goal on, light and dark, at normal and large font: part
 * way through, on its first day, and the day a missed week was carried on.
 */
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
    fun weekWithGoals() = capture("plan-week-goals") { app.seedGoalWeeks() }

    @Test @Config(qualifiers = WEEK_PAGE_LARGE, fontScale = LARGE_FONT)
    fun weekWithGoalsLarge() = capture("plan-week-goals") { app.seedGoalWeeks() }

    /** The week's first day: each goal named, and when they count said once. */
    @Test @Config(qualifiers = WEEK_PAGE)
    fun firstDay() = capture("plan-week-goals-first-day") { app.seedGoalFirstDay() }

    @Test @Config(qualifiers = WEEK_PAGE_LARGE, fontScale = LARGE_FONT)
    fun firstDayLarge() = capture("plan-week-goals-first-day") { app.seedGoalFirstDay() }

    /** The day a missed week's result came in, carried on: the choice still open sits above the goals. */
    @Test @Config(qualifiers = WEEK_PAGE)
    fun carriedOn() = capture("plan-week-goals-carried-on") { app.seedGoalsCarriedOn() }

    @Test @Config(qualifiers = WEEK_PAGE_LARGE, fontScale = LARGE_FONT)
    fun carriedOnLarge() = capture("plan-week-goals-carried-on") { app.seedGoalsCarriedOn() }

    private fun capture(name: String, seed: () -> Unit) {
        seed()
        compose.captureLooks(
            name,
            ready = planTab,
            before = {
                onNode(planTab).performClick()
                waitUntilAtLeastOneExists(hasText("Extra goals this week"), timeoutMillis = 10_000)
            },
        ) { AppShell() }
    }
}
