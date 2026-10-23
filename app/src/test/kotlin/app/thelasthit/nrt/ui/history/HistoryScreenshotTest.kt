package app.thelasthit.nrt.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    @Composable
    private fun HistoryUnderTest() = HistoryScreen(snackbars = remember { SnackbarHostState() })
}
