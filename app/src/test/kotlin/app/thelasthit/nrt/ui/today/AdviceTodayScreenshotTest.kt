package app.thelasthit.nrt.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.seedPlan
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.advice.seedAdviceWeeks
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.insights.InsightsScreen
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tall enough for the whole of Insights at a Pixel 7's width, at the normal and the large font. */
private const val INSIGHTS_PAGE = "en-rGB-w411dp-h4200dp-normal-long-notround-any-420dpi-keyshidden-nonav"
private const val INSIGHTS_PAGE_LARGE = "en-rGB-w411dp-h5000dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * Advice where it shows, as advice-today.html, advice-after-log.html and advice-insights.html have
 * it: the mockups' fortnight of triggers, a plan in week 4, and today's three doses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdviceTodayScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = FULL_PAGE)
    fun todayAdvice() = captureToday("today-advice")

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayAdviceLarge() = captureToday("today-advice")

    /** Thursday 22 October turns to moving the dose, so the line has the time to try. */
    @Test @Config(qualifiers = FULL_PAGE)
    fun todayAdviceTiming() = captureToday("today-advice-timing", thursday = true)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayAdviceTimingLarge() = captureToday("today-advice-timing", thursday = true)

    @Test @Config(qualifiers = PHONE)
    fun todayTip() = captureTip()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun todayTipLarge() = captureTip()

    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun insightsAdvice() = captureInsights()

    @Test @Config(qualifiers = INSIGHTS_PAGE_LARGE, fontScale = LARGE_FONT)
    fun insightsAdviceLarge() = captureInsights()

    private fun seed(thursday: Boolean = false) {
        app.setUp()
        if (thursday) app.clock.now = london("2026-10-22T15:05")
        app.seedAdviceWeeks()
        if (!thursday) {
            runBlocking {
                app.graph.entries.log(LogRequest(GUM_4, at = london("2026-10-23T08:10"), tagKeys = setOf("coffee_tea")))
                app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T10:55"), tagKeys = setOf("work_break")))
                app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T13:40"), tagKeys = setOf("after_meal")))
            }
        }
        val now = app.clock.now
        app.seedPlan()
        app.clock.now = now
    }

    private fun captureToday(name: String, thursday: Boolean = false) {
        seed(thursday)
        compose.captureLooks(name, ready = hasText("All advice")) { TodayWithSnackbars() }
    }

    private fun captureTip() {
        seed()
        compose.captureLooks(
            "today-tip",
            ready = hasText("All advice"),
            before = {
                onNodeWithContentDescription("Log a dose").performClick()
                waitUntil(5_000) { onAllNodes(hasText("Planned or craving")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Stressed").performScrollTo().performClick()
                onNode(hasText("Log") and hasText(" at ", substring = true)).performClick()
                waitUntil(5_000) { onAllNodes(hasText("Try box breathing next time")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { TodayWithSnackbars() }
    }

    private fun captureInsights() {
        seed(thursday = true)
        compose.captureLooks("insights-advice", ready = hasContentDescription("Show each day as a table")) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    @Composable
    private fun TodayWithSnackbars() {
        val snackbars = remember { SnackbarHostState() }
        val colors = LastHit.colors
        Box(Modifier.fillMaxSize()) {
            TodayScreen(snackbars = snackbars, onOpenSettings = {}, onOpenLair = {})
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(8.dp)) { data ->
                Snackbar(data, containerColor = colors.text, contentColor = colors.bg, actionColor = colors.bg)
            }
        }
    }
}
