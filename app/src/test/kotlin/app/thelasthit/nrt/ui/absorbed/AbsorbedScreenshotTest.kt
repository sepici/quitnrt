package app.thelasthit.nrt.ui.absorbed

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.seedPlan
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.history.HistoryScreen
import app.thelasthit.nrt.ui.settings.SettingsLinks
import app.thelasthit.nrt.ui.settings.SettingsScreen
import app.thelasthit.nrt.ui.today.TodayScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Estimated absorbed amounts on, beside the labelled mg: Today (with and without a plan), History,
 * the log sheet, and Settings with the page that explains it, off and on. The same screens with it
 * off are the existing today, history, log-sheet and settings looks.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AbsorbedScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = FULL_PAGE)
    fun today() = captureToday()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayLarge() = captureToday()

    @Test @Config(qualifiers = FULL_PAGE)
    fun todayWithPlan() = captureTodayWithPlan()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayWithPlanLarge() = captureTodayWithPlan()

    @Test @Config(qualifiers = FULL_PAGE)
    fun history() = captureHistory()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun historyLarge() = captureHistory()

    @Test @Config(qualifiers = PHONE)
    fun logSheet() = captureLogSheet()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun logSheetLarge() = captureLogSheet()

    @Test @Config(qualifiers = FULL_PAGE)
    fun settings() = captureSettings()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun settingsLarge() = captureSettings()

    @Test @Config(qualifiers = FULL_PAGE)
    fun page() = capturePage(on = false)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun pageLarge() = capturePage(on = false)

    @Test @Config(qualifiers = PHONE)
    fun pageOn() = capturePage(on = true)

    private fun captureToday() {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed()
        compose.captureLooks("absorbed-today", ready = hasContentDescription("absorbed", substring = true)) { TodayUnderTest() }
    }

    private fun captureTodayWithPlan() {
        app.setUp()
        app.seedWeek()
        app.seedPlan()
        app.showAbsorbed()
        compose.captureLooks("absorbed-today-plan", ready = hasContentDescription("absorbed", substring = true)) { TodayUnderTest() }
    }

    private fun captureHistory() {
        app.setUp()
        app.seedWeek()
        runBlocking {
            // As History's own look: a late dose, and a dose of more than one.
            app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T01:20"), tagKeys = setOf("before_bed")))
            app.graph.entries.log(LogRequest(QUICKMIST, quantity = 2, at = london("2026-10-22T21:05"), tagKeys = setOf("drink")))
        }
        app.showAbsorbed()
        compose.captureLooks("absorbed-history", ready = hasText("absorbed", substring = true)) {
            HistoryScreen(snackbars = remember { SnackbarHostState() })
        }
    }

    private fun captureLogSheet() {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed()
        compose.captureLooks(
            "absorbed-log-sheet",
            ready = hasContentDescription("Log a dose"),
            before = {
                onNodeWithContentDescription("Log a dose").performClick()
                waitUntil(5_000) { onAllNodes(hasText("absorbed", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { TodayUnderTest() }
    }

    private fun captureSettings() {
        app.setUp()
        app.showAbsorbed()
        compose.captureLooks("absorbed-settings", ready = hasText("Estimated absorbed") and hasText("On")) {
            SettingsScreen(remember { SnackbarHostState() }, onBack = {}, links = SettingsLinks({}, {}, {}, {}, {}))
        }
    }

    private fun capturePage(on: Boolean) {
        app.setUp()
        app.showAbsorbed(on)
        compose.captureLooks(if (on) "absorbed-page-on" else "absorbed-page", ready = hasText("Show estimated absorbed amounts")) {
            AbsorbedScreen(onBack = {})
        }
    }

    @Composable
    private fun TodayUnderTest() = TodayScreen(snackbars = remember { SnackbarHostState() }, onOpenSettings = {}, onOpenLair = {})
}
