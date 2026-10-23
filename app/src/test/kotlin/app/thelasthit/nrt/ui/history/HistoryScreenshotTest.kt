package app.thelasthit.nrt.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = FULL_PAGE)
    fun history() = captureHistory()

    @Test
    @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun historyLarge() = captureHistory()

    @Test
    @Config(qualifiers = PHONE)
    fun historyEmpty() = captureEmpty()

    @Test
    @Config(qualifiers = PHONE)
    fun historyFilter() = captureFilterSheet()

    @Test
    @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun historyFilterLarge() = captureFilterSheet()

    @Test
    @Config(qualifiers = FULL_PAGE)
    fun historyFiltered() = captureFiltered()

    @Test
    @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun historyFilteredLarge() = captureFiltered()

    @Test
    @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun historyEmptyLarge() = captureEmpty()

    private fun captureHistory() {
        app.setUp()
        app.seedWeek()
        runBlocking {
            // The mockup's late dose, and a dose of more than one.
            app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T01:20"), tagKeys = setOf("before_bed")))
            app.graph.entries.log(LogRequest(QUICKMIST, quantity = 2, at = london("2026-10-22T21:05"), tagKeys = setOf("drink")))
        }
        compose.captureLooks("history", ready = hasText("01:20 Friday, Before bed")) { HistoryUnderTest() }
    }

    private fun captureEmpty() {
        app.setUp()
        compose.captureLooks("history-empty", ready = hasText("Nothing logged yet")) { HistoryUnderTest() }
    }

    /**
     * Two months of use, plus a few tags from other groups so the sheet shows them as the mockup
     * does. Returns how many doses carry "After a meal".
     */
    private fun seedForFilter(): Int {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        runBlocking {
            listOf("stressed", "bored", "socialising", "nicotine_users", "strong_craving", "tired", "autopilot")
                .forEachIndexed { index, tag ->
                    app.graph.entries.log(LogRequest(QUICKMIST, at = london("2026-10-19T%02d:15".format(9 + index)), tagKeys = setOf(tag)))
                }
        }
        return runBlocking { app.graph.tags.observeAllUses().first().getValue("after_meal") }
    }

    /** Opens the filter and picks "After a meal", as the mockup shows it. */
    private fun ComposeContentTestRule.pickAfterMeal(matches: Int) {
        onNodeWithContentDescription("Filter by product or tag").performClick()
        waitUntilAtLeastOneExists(hasText("After a meal"), timeoutMillis = 5_000)
        onNodeWithText("After a meal").performScrollTo().performClick()
        waitUntilExactlyOneExists(hasText("Show $matches\u00A0doses"), timeoutMillis = 5_000)
    }

    private fun captureFilterSheet() {
        val matches = seedForFilter()
        compose.captureLooks(
            "history-filter",
            ready = hasContentDescription("Filter by product or tag"),
            before = { pickAfterMeal(matches) },
        ) { HistoryUnderTest() }
    }

    private fun captureFiltered() {
        val matches = seedForFilter()
        compose.captureLooks(
            "history-filtered",
            ready = hasContentDescription("Filter by product or tag"),
            before = {
                pickAfterMeal(matches)
                onNodeWithText("Show $matches\u00A0doses").performClick()
                waitUntilExactlyOneExists(hasContentDescription("Filtered by After a meal. Clear filter"), timeoutMillis = 5_000)
                waitUntilDoesNotExist(hasText("Show $matches\u00A0doses"), timeoutMillis = 5_000)
            },
        ) { HistoryUnderTest() }
    }

    @Composable
    private fun HistoryUnderTest() = HistoryScreen(snackbars = remember { SnackbarHostState() })
}
