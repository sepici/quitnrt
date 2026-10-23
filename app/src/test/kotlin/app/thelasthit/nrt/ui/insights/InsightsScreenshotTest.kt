package app.thelasthit.nrt.ui.insights

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tall enough for the whole of Insights, every section down to the last, at a Pixel 7's width. */
private const val INSIGHTS_PAGE = "en-rGB-w411dp-h4200dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** The same at the large font, which makes the page longer still. */
private const val INSIGHTS_PAGE_LARGE = "en-rGB-w411dp-h5000dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * Insights as the mockups show it: two months logged, a tapped hour and day, gum only, patches
 * only, a first week, the long range and nothing yet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun insights() = captureMonths()

    @Test @Config(qualifiers = INSIGHTS_PAGE_LARGE, fontScale = LARGE_FONT)
    fun insightsLarge() = captureMonths()

    private fun captureMonths() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        compose.captureLooks("insights", ready = hasText("90 days opens tomorrow", substring = true)) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    /** Thursdays at 08:00 tapped on the hour map, shaded by mg, and Tuesday the 13th on the calendar. */
    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun detail() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        compose.captureLooks(
            "insights-detail",
            ready = hasContentDescription("Most often", substring = true),
            before = {
                onNode(hasText("mg") and isSelectable()).performScrollTo().performClick()
                waitUntil(5_000) { onAllNodes(hasContentDescription("The most is on", substring = true)).fetchSemanticsNodes().isNotEmpty() }
                tapHour(column = 3, row = 4)
                waitUntil(5_000) { onAllNodes(hasText("Thursdays, 08:00 to 09:00")).fetchSemanticsNodes().isNotEmpty() }
                tapCalendarDay(column = 1, row = 3)
                waitUntil(5_000) { onAllNodes(hasText("Tuesday 13 October")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { InsightsScreen(remember { SnackbarHostState() }) }
    }

    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun gum() = captureType("insights-gum", "Gum")

    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun patches() = captureType("insights-patches", "Patch")

    private fun captureType(name: String, type: String) {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        compose.captureLooks(
            name,
            ready = hasText(type) and isSelectable(),
            before = {
                onNodeWithText(type).performClick()
                waitUntil(5_000) { onAllNodes(hasText(type) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { InsightsScreen(remember { SnackbarHostState() }) }
    }

    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun firstWeek() {
        app.setUp()
        app.seedWeek()
        compose.captureLooks("insights-new", ready = hasText("The 7-day average line starts on 26 October.")) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    /** A first week's hours and days as tables. */
    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun tables() {
        app.setUp()
        app.seedWeek()
        compose.captureLooks(
            "insights-tables",
            ready = hasContentDescription("Show when you use as a table"),
            before = {
                onNode(hasContentDescription("Show when you use as a table")).performScrollTo().performClick()
                waitUntil(5_000) { onAllNodes(hasText("Wednesdays, 08:00 to 09:00")).fetchSemanticsNodes().isNotEmpty() }
                onNode(hasContentDescription("Show calendar as a table")).performScrollTo().performClick()
                waitUntil(5_000) { onAllNodes(hasText("Monday 19 October")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { InsightsScreen(remember { SnackbarHostState() }) }
    }

    @Test @Config(qualifiers = INSIGHTS_PAGE)
    fun weeks() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        compose.captureLooks(
            "insights-weeks",
            ready = hasText("60 days") and isSelectable(),
            before = {
                onNode(hasText("60 days") and isSelectable()).performClick()
                waitUntil(5_000) { onAllNodes(hasText("Each week")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { InsightsScreen(remember { SnackbarHostState() }) }
    }

    @Test @Config(qualifiers = PHONE)
    fun empty() {
        app.setUp()
        compose.captureLooks("insights-empty", ready = hasText("Nothing logged yet")) { InsightsScreen(remember { SnackbarHostState() }) }
    }
}
