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
import app.thelasthit.nrt.TestApp
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Estimated absorbed amounts on and off, side by side: Today (and with a plan, on), History at a
 * Pixel 7's width and a 360 dp phone's, the log sheet, Settings, and the page that explains it,
 * whole. Looks with it on are named `absorbed-...`, and with it off `absorbed-off-...`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AbsorbedScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = FULL_PAGE)
    fun today() = captureToday(on = true)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayLarge() = captureToday(on = true)

    @Test @Config(qualifiers = FULL_PAGE)
    fun todayOff() = captureToday(on = false)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayOffLarge() = captureToday(on = false)

    @Test @Config(qualifiers = FULL_PAGE)
    fun todayWithPlan() = captureTodayWithPlan()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun todayWithPlanLarge() = captureTodayWithPlan()

    @Test @Config(qualifiers = FULL_PAGE)
    fun history() = captureHistory(on = true)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun historyLarge() = captureHistory(on = true)

    @Test @Config(qualifiers = FULL_PAGE)
    fun historyOff() = captureHistory(on = false)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun historyOffLarge() = captureHistory(on = false)

    @Test @Config(qualifiers = NARROW_PAGE)
    fun historyNarrow() = captureHistory(on = true, narrow = true)

    @Test @Config(qualifiers = NARROW_PAGE, fontScale = LARGE_FONT)
    fun historyNarrowLarge() = captureHistory(on = true, narrow = true)

    @Test @Config(qualifiers = NARROW_PAGE)
    fun historyNarrowOff() = captureHistory(on = false, narrow = true)

    @Test @Config(qualifiers = NARROW_PAGE, fontScale = LARGE_FONT)
    fun historyNarrowOffLarge() = captureHistory(on = false, narrow = true)

    @Test @Config(qualifiers = PHONE)
    fun logSheet() = captureLogSheet(on = true)

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun logSheetLarge() = captureLogSheet(on = true)

    @Test @Config(qualifiers = PHONE)
    fun logSheetOff() = captureLogSheet(on = false)

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun logSheetOffLarge() = captureLogSheet(on = false)

    @Test @Config(qualifiers = FULL_PAGE)
    fun settings() = captureSettings(on = true)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun settingsLarge() = captureSettings(on = true)

    @Test @Config(qualifiers = FULL_PAGE)
    fun settingsOff() = captureSettings(on = false)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun settingsOffLarge() = captureSettings(on = false)

    @Test @Config(qualifiers = TALL_PAGE)
    fun page() = capturePage(on = false)

    @Test @Config(qualifiers = TALL_PAGE, fontScale = LARGE_FONT)
    fun pageLarge() = capturePage(on = false)

    @Test @Config(qualifiers = PHONE)
    fun pageOn() = capturePage(on = true)

    private fun look(name: String, on: Boolean) = if (on) "absorbed-$name" else "absorbed-off-$name"

    private fun captureToday(on: Boolean) {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed(on)
        val ready = if (on) hasContentDescription("absorbed", substring = true) else hasContentDescription("8${NB}mg today")
        compose.captureLooks(look("today", on), ready = ready) { TodayUnderTest() }
    }

    private fun captureTodayWithPlan() {
        app.setUp()
        app.seedWeek()
        app.seedPlan()
        app.showAbsorbed()
        compose.captureLooks("absorbed-today-plan", ready = hasContentDescription("absorbed", substring = true)) { TodayUnderTest() }
    }

    private fun captureHistory(on: Boolean, narrow: Boolean = false) {
        app.setUp()
        app.seedWeekForHistory()
        app.showAbsorbed(on)
        val ready = hasContentDescription(if (on) "Today, 8${NB}mg so far, about 4${NB}mg absorbed" else "Today, 8${NB}mg so far")
        compose.captureLooks(look(if (narrow) "history-360" else "history", on), ready = ready) {
            HistoryScreen(snackbars = remember { SnackbarHostState() })
        }
    }

    private fun captureLogSheet(on: Boolean) {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed(on)
        compose.captureLooks(
            look("log-sheet", on),
            ready = hasContentDescription("Log a dose"),
            before = {
                onNodeWithContentDescription("Log a dose").performClick()
                val amount = hasText(if (on) "absorbed" else "this dose", substring = true)
                waitUntil(5_000) { onAllNodes(amount).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { TodayUnderTest() }
    }

    private fun captureSettings(on: Boolean) {
        app.setUp()
        app.showAbsorbed(on)
        compose.captureLooks(look("settings", on), ready = hasText("Absorbed amounts") and hasText(if (on) "On" else "Off")) {
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

/** Tall enough for the whole of the page that explains the estimate, sources and all, at the large font. */
private const val TALL_PAGE = "en-rGB-w411dp-h2400dp-normal-long-notround-any-420dpi-keyshidden-nonav"
