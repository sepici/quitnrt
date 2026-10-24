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
import java.time.LocalDate

/** Tall enough for the whole of Insights at a Pixel 7's width, at the normal and the large font. */
private const val INSIGHTS_PAGE = "en-rGB-w411dp-h4200dp-normal-long-notround-any-420dpi-keyshidden-nonav"
private const val INSIGHTS_PAGE_LARGE = "en-rGB-w411dp-h5000dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * Advice where it shows, as advice-today.html, advice-after-log.html and advice-insights.html have
 * it: the mockups' fortnight of triggers, a plan in week 4, and today's three doses. Then the
 * looks the mockups don't have: the line after a slip, the tip after a slip and a strategy's tip,
 * and Insights' advice heading the list or with a strategy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdviceTodayScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    /** Today's snackbar, so a tip's shot can clear it from over the card. */
    private var snackbars: SnackbarHostState? = null

    @Test @Config(qualifiers = FULL_PAGE)
    fun todayAdvice() = captureToday("today-advice")

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayAdviceLarge() = captureToday("today-advice")

    /** Thursday 22 October turns to moving the dose, so the line has the time to try. */
    @Test @Config(qualifiers = FULL_PAGE)
    fun todayAdviceTiming() = captureToday("today-advice-timing", thursday = true)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayAdviceTimingLarge() = captureToday("today-advice-timing", thursday = true)

    /** Yesterday went over its 14 mg guide. */
    @Test @Config(qualifiers = PHONE)
    fun todayAdviceSlip() = captureSlip("today-advice-slip") { overYesterday() }

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun todayAdviceSlipLarge() = captureSlip("today-advice-slip") { overYesterday() }

    /** Week 3, 12 to 18 October, went over its allowance. */
    @Test @Config(qualifiers = PHONE)
    fun todayAdviceMissed() = captureSlip("today-advice-missed") { overWeek3() }

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun todayAdviceMissedLarge() = captureSlip("today-advice-missed") { overWeek3() }

    /** At the large font the card is taller than a phone's screen below the hero, so those shots are a whole page. */
    @Test @Config(qualifiers = PHONE)
    fun todayTip() = captureTip("today-tip", tag = "Stressed")

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayTipLarge() = captureTip("today-tip", tag = "Stressed")

    /** "Next time, move the dose to before the meal", with its three sentences and "How to do it". */
    @Test @Config(qualifiers = PHONE)
    fun todayTipStrategy() = captureTip("today-tip-strategy", tag = "After a meal")

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayTipStrategyLarge() = captureTip("today-tip-strategy", tag = "After a meal")

    /** 8 mg so far, and four pieces of 2 mg take today past its 14 mg guide. */
    @Test @Config(qualifiers = PHONE)
    fun todayTipSlip() = captureTip("today-tip-slip", pieces = 4)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayTipSlipLarge() = captureTip("today-tip-slip", pieces = 4)

    /** Friday 23 October, as the mockup: the timing tip under After a meal. */
    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun insightsAdvice() = captureInsights()

    @Test @Config(qualifiers = INSIGHTS_PAGE_LARGE, fontScale = LARGE_FONT)
    fun insightsAdviceLarge() = captureInsights()

    /** After a meal leads the 14 days, but isn't one of the last 7 days' rows, so the advice heads the list. */
    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun insightsAdviceTop() = captureInsightsTop()

    @Test @Config(qualifiers = INSIGHTS_PAGE_LARGE, fontScale = LARGE_FONT)
    fun insightsAdviceTopLarge() = captureInsightsTop()

    /** Stressed leads, with no timing tip, so the card has the strategy and why it helps. */
    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun insightsAdviceStrategy() = captureInsightsStrategy()

    @Test @Config(qualifiers = INSIGHTS_PAGE_LARGE, fontScale = LARGE_FONT)
    fun insightsAdviceStrategyLarge() = captureInsightsStrategy()

    /** The mockups' fortnight, today's three doses unless it's [thursday], [extra] doses, then the plan's results. */
    private fun seed(thursday: Boolean = false, extra: suspend () -> Unit = {}) {
        app.setUp()
        if (thursday) app.clock.now = london("2026-10-22T15:05")
        app.seedAdviceWeeks()
        runBlocking {
            if (!thursday) {
                app.graph.entries.log(LogRequest(GUM_4, at = london("2026-10-23T08:10"), tagKeys = setOf("coffee_tea")))
                app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T10:55"), tagKeys = setOf("work_break")))
                app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T13:40"), tagKeys = setOf("after_meal")))
            }
            extra()
        }
        val now = app.clock.now
        app.seedPlan()
        app.clock.now = now
    }

    private fun captureToday(name: String, thursday: Boolean = false) {
        seed(thursday)
        compose.captureLooks(name, ready = hasText("All advice")) { TodayWithSnackbars() }
    }

    /** Today with a slip's advice in the line, from [slip]'s doses on top of the usual ones. */
    private fun captureSlip(name: String, slip: suspend () -> Unit) {
        seed(extra = slip)
        compose.captureLooks(name, ready = hasText("over its", substring = true)) { TodayWithSnackbars() }
    }

    /** Four more pieces of 2 mg at 20:30 yesterday, which takes it past its 14 mg guide. */
    private suspend fun overYesterday() {
        app.graph.entries.log(LogRequest(GUM_2, quantity = 4, at = london("2026-10-22T20:30")))
    }

    /** Two pieces of 4 mg at 20:30 each day from Monday 12 to Friday 16 October, which takes week 3 past its allowance. */
    private suspend fun overWeek3() {
        for (day in 12..16) app.graph.entries.log(LogRequest(GUM_4, quantity = 2, at = london("2026-10-${day}T20:30")))
    }

    /**
     * Logs [pieces] of 2 mg gum, with [tag] if there is one, from the log sheet, waits for the tip,
     * then clears the snackbar so the card's buttons show.
     */
    private fun captureTip(name: String, tag: String? = null, pieces: Int = 1) {
        seed()
        compose.captureLooks(
            name,
            ready = hasText("All advice"),
            before = {
                onNodeWithContentDescription("Log a dose").performClick()
                waitUntil(5_000) { onAllNodes(hasText("Planned or craving")).fetchSemanticsNodes().isNotEmpty() }
                repeat(pieces - 1) { onNodeWithContentDescription("One more").performClick() }
                tag?.let { onNodeWithText(it).performScrollTo().performClick() }
                onNode(hasText("Log") and hasText(" at ", substring = true)).performClick()
                waitUntil(5_000) { onAllNodes(hasContentDescription("Close tip")).fetchSemanticsNodes().isNotEmpty() }
                waitUntil(5_000) { snackbars?.currentSnackbarData != null }
                snackbars?.currentSnackbarData?.dismiss()
                waitUntil(5_000) { onAllNodes(hasText("Undo")).fetchSemanticsNodes().isEmpty() }
            },
        ) { TodayWithSnackbars() }
    }

    private fun captureInsights() {
        seed()
        compose.captureLooks(
            "insights-advice",
            ready = hasContentDescription("Show each day as a table"),
            before = { waitUntil(5_000) { onAllNodes(hasText("These doses come", substring = true)).fetchSemanticsNodes().isNotEmpty() } },
        ) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    /** After a meal on 9 to 16 October, then five other tags on each of the last 6 days. */
    private fun captureInsightsTop() {
        app.setUp()
        val first = LocalDate.parse("2026-10-09")
        runBlocking {
            for (day in 0L..7L) {
                app.graph.entries.log(LogRequest(GUM_2, at = london("${first.plusDays(day)}T13:00"), tagKeys = setOf("after_meal")))
            }
            for (day in 8L..13L) {
                listOf("woke_up" to "08:00", "coffee_tea" to "10:30", "work_break" to "11:00", "drink" to "19:00", "before_bed" to "22:00")
                    .forEach { (tag, time) -> app.graph.entries.log(LogRequest(GUM_2, at = london("${first.plusDays(day)}T$time"), tagKeys = setOf(tag))) }
            }
        }
        compose.captureLooks(
            "insights-advice-top",
            ready = hasContentDescription("Show each day as a table"),
            before = {
                onNodeWithText("7\u00A0days").performClick()
                waitUntil(5_000) { onAllNodes(hasText("is your top trigger.", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            },
        ) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    /** Stressed at 21:00 every day and at 17:30 on most, with a dose on waking and an untagged one at lunch. */
    private fun captureInsightsStrategy() {
        app.setUp()
        val first = LocalDate.parse("2026-10-09")
        runBlocking {
            for (day in 0L..13L) {
                val date = first.plusDays(day)
                app.graph.entries.log(LogRequest(GUM_4, at = london("${date}T08:30"), tagKeys = setOf("woke_up")))
                app.graph.entries.log(LogRequest(GUM_2, at = london("${date}T12:40")))
                if (day % 3 != 0L) app.graph.entries.log(LogRequest(GUM_2, at = london("${date}T17:30"), tagKeys = setOf("stressed")))
                app.graph.entries.log(LogRequest(GUM_2, at = london("${date}T21:00"), tagKeys = setOf("stressed")))
            }
        }
        compose.captureLooks(
            "insights-advice-strategy",
            ready = hasContentDescription("Show each day as a table"),
            before = { waitUntil(5_000) { onAllNodes(hasText("Try a minute of box breathing.", substring = true)).fetchSemanticsNodes().isNotEmpty() } },
        ) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    @Composable
    private fun TodayWithSnackbars() {
        val snackbars = remember { SnackbarHostState() }.also { this.snackbars = it }
        val colors = LastHit.colors
        Box(Modifier.fillMaxSize()) {
            TodayScreen(snackbars = snackbars, onOpenSettings = {}, onOpenLair = {})
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(8.dp)) { data ->
                Snackbar(data, containerColor = colors.text, contentColor = colors.bg, actionColor = colors.bg)
            }
        }
    }
}
