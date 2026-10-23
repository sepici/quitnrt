package app.thelasthit.nrt.ui.insights

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedWeek
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

/** Insights as the mockups show it: two months logged, a first week, the long range and nothing yet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = FULL_PAGE)
    fun insights() = captureMonths()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun insightsLarge() = captureMonths()

    private fun captureMonths() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        compose.captureLooks("insights", ready = hasText("90 days opens tomorrow", substring = true)) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun firstWeek() {
        app.setUp()
        app.seedWeek()
        compose.captureLooks("insights-new", ready = hasText("The 7-day average line starts on 26 October.")) {
            InsightsScreen(remember { SnackbarHostState() })
        }
    }

    @Test @Config(qualifiers = FULL_PAGE)
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
