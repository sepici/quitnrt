package app.thelasthit.nrt.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.seedMockupPlan
import app.thelasthit.nrt.seedPlan
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.history.HistoryScreen
import app.thelasthit.nrt.ui.insights.InsightsScreen
import app.thelasthit.nrt.ui.today.TodayScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Tall enough for the whole of Insights with a plan, Week by week included. */
private const val INSIGHTS_PLAN_PAGE = "en-rGB-w411dp-h4100dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** The same at the large font. */
private const val INSIGHTS_PLAN_PAGE_LARGE = "en-rGB-w411dp-h4900dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * Today, History and Insights once a plan runs, as B's `today.html`, `advice-today.html` and
 * `history.html` show them: week 4 of 12 at a 14 mg guide, Gary at 100 of 120 HP with a hit ready.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val meter = hasContentDescription("mg used. Daily guide", substring = true)

    @Test @Config(qualifiers = FULL_PAGE)
    fun todayPlan() = captureToday()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayPlanLarge() = captureToday()

    private fun captureToday() {
        app.setUp()
        app.seedMockupPlan()
        compose.captureLooks("today-plan", ready = meter) { Today() }
    }

    /** A day over the guide: no tick, and how far over. */
    @Test @Config(qualifiers = FULL_PAGE)
    fun todayPlanOver() {
        app.setUp()
        app.seedMockupPlan()
        runBlocking { app.graph.entries.log(LogRequest(GUM_4, quantity = 2, at = london("2026-10-23T14:30"))) }
        compose.captureLooks("today-plan-over", ready = hasContentDescription("over today's guide", substring = true)) { Today() }
    }

    /** Week 12 of a plan from 7 August: NRT-free, with nothing logged so far. */
    @Test @Config(qualifiers = FULL_PAGE)
    fun todayPlanNrtFree() = captureNrtFree()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayPlanNrtFreeLarge() = captureNrtFree()

    private fun captureNrtFree() {
        app.setUp()
        app.seedPlan(start = LocalDate.parse("2026-08-07"), spend = 8)
        compose.captureLooks("today-plan-nrt-free", ready = meter) { Today() }
    }

    /** A plan from tomorrow: the week is still Monday to Sunday, and Gary is out at full HP. */
    @Test @Config(qualifiers = FULL_PAGE)
    fun todayPlanStartsTomorrow() {
        app.setUp()
        app.seedWeek()
        app.seedPlan(start = LocalDate.parse("2026-10-24"), spend = 0)
        compose.captureLooks("today-plan-tomorrow", ready = hasText("Your plan starts tomorrow.")) { Today() }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun historyPlan() = captureHistory()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun historyPlanLarge() = captureHistory()

    private fun captureHistory() {
        app.setUp()
        app.seedMockupPlan()
        compose.captureLooks("history-plan", ready = hasText("A tick means", substring = true)) {
            HistoryScreen(snackbars = remember { SnackbarHostState() })
        }
    }

    @Test @Config(qualifiers = INSIGHTS_PLAN_PAGE)
    fun insightsPlan() = captureInsights()

    @Test @Config(qualifiers = INSIGHTS_PLAN_PAGE_LARGE, fontScale = LARGE_FONT)
    fun insightsPlanLarge() = captureInsights()

    private fun captureInsights() {
        app.setUp()
        app.seedMockupPlan()
        compose.captureLooks("insights-plan", ready = hasText("Week by week")) { Insights() }
    }

    /** Week 3 tapped in Week by week, and the 60 days' weekly columns with their guide. */
    @Test @Config(qualifiers = INSIGHTS_PLAN_PAGE)
    fun insightsPlanDetail() {
        app.setUp()
        app.seedMockupPlan()
        compose.captureLooks(
            "insights-plan-detail",
            ready = hasText("Week by week"),
            before = {
                onNode(hasText("60 days") and isSelectable()).performClick()
                waitUntil(5_000) { onAllNodes(hasText("Each week")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Show week by week as a table").performScrollTo().performClick()
                waitUntil(5_000) { onAllNodes(hasText("Week 3")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { Insights() }
    }

    @Composable
    private fun Today() = TodayScreen(snackbars = remember { SnackbarHostState() }, onOpenSettings = {}, onOpenLair = {})

    @Composable
    private fun Insights() = InsightsScreen(remember { SnackbarHostState() })
}
