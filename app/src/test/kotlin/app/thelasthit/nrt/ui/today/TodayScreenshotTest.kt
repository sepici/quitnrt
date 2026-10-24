package app.thelasthit.nrt.ui.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.HUGE_FONT
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = FULL_PAGE)
    fun today() = captureToday()

    @Test
    @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayLarge() = captureToday()

    @Test
    @Config(qualifiers = FULL_PAGE)
    fun todayEmpty() = captureEmpty()

    @Test
    @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayEmptyLarge() = captureEmpty()

    @Test
    @Config(qualifiers = PHONE)
    fun logSheet() = captureLogSheet()

    @Test
    @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun logSheetLarge() = captureLogSheet()

    /** At the largest font, where fixed heights around text would clip first. */
    @Test
    @Config(qualifiers = FULL_PAGE, fontScale = HUGE_FONT)
    fun todayX2() = captureToday()

    @Test
    @Config(qualifiers = PHONE, fontScale = HUGE_FONT)
    fun logSheetX2() = captureLogSheet()

    private fun captureToday() {
        app.setUp()
        app.seedWeek()
        compose.captureLooks("today", ready = hasText("Logged today")) { TodayUnderTest() }
    }

    private fun captureEmpty() {
        app.setUp()
        compose.captureLooks("today-empty", ready = hasText("Logged today")) { TodayUnderTest() }
    }

    private fun captureLogSheet() {
        app.setUp()
        app.seedWeek()
        compose.captureLooks(
            "log-sheet",
            ready = hasContentDescription("Log a dose"),
            before = {
                onNodeWithContentDescription("Log a dose").performClick()
                waitUntil(5_000) { onAllNodes(hasText("Planned or craving")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { TodayUnderTest() }
    }

    @Composable
    private fun TodayUnderTest() = TodayScreen(snackbars = remember { SnackbarHostState() }, onOpenSettings = {}, onOpenLair = {})
}
