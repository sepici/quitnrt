package app.thelasthit.nrt.ui.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.reminders.allowNotifications
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.captureLooks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings, Reminders and Widget and tile, in the Settings mockups' style. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemindersScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    /** The mockups' week, so "Forgot to log" knows the usual first dose. */
    private fun setUpWithAWeek(allowed: Boolean = true) {
        app.setUp()
        app.seedWeek()
        app.allowNotifications(allowed)
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun reminders() = captureReminders()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun remindersLarge() = captureReminders()

    private fun captureReminders() {
        setUpWithAWeek()
        compose.captureLooks("settings-reminders", ready = hasText("Allowance nudges")) { RemindersScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun remindersBlocked() {
        setUpWithAWeek(allowed = false)
        compose.captureLooks("settings-reminders-blocked", ready = hasText("Notifications are off")) { RemindersScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun widgetAndTile() = captureWidgetAndTile()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun widgetAndTileLarge() = captureWidgetAndTile()

    private fun captureWidgetAndTile() {
        setUpWithAWeek()
        compose.captureLooks("settings-widget-tile", ready = hasText("Quick Settings tile")) { WidgetTileScreen(onBack = {}) }
    }
}
