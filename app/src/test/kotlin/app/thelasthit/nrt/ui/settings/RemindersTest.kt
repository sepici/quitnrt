package app.thelasthit.nrt.ui.settings

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.reminders.allowNotifications
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Reminders page: its switches, the permission question, and the rows that lead there. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class RemindersTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpApp() {
        app.setUp()
    }

    private fun showReminders() {
        compose.setContent { LastHitTheme { RemindersScreen(onBack = {}) } }
        compose.waitUntilAtLeastOneExists(hasText("Allowance nudges"), timeoutMillis = 5_000)
    }

    private fun reminders() = runBlocking { app.graph.reminders.currentReminders() }

    @Test
    fun aSwitchTurnsAReminderOnAndOff() {
        app.allowNotifications()
        showReminders()
        compose.onNodeWithText("Forgot to log").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            reminders().isOn(Reminder.FORGOT_TO_LOG)
        }
        compose.onNodeWithText("Weekly results").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            !reminders().isOn(Reminder.WEEKLY_RESULT)
        }
        // With notifications allowed, nothing is asked.
        assertEquals(null, shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test
    fun switchingOneOnAsksForNotificationsTheFirstTime() {
        app.allowNotifications(false)
        showReminders()
        compose.onNodeWithText("Notifications are off").assertExists()
        compose.onNodeWithText("Forgot to log").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking { app.graph.reminders.currentMarks().permissionAsked }
        }
        val asked = shadowOf(compose.activity).lastRequestedPermission
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in asked.requestedPermissions)
        assertTrue(reminders().isOn(Reminder.FORGOT_TO_LOG))
    }

    @Test
    fun switchingOneOffAsksNothing() {
        app.allowNotifications(false)
        showReminders()
        compose.onNodeWithText("Allowance nudges").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            !reminders().isOn(Reminder.ALLOWANCE)
        }
        assertEquals(null, shadowOf(compose.activity).lastRequestedPermission)
        assertFalse(runBlocking { app.graph.reminders.currentMarks().permissionAsked })
    }

    @Test
    fun aCheckInInQuietHoursSaysItWontShow() {
        app.allowNotifications()
        runBlocking { app.graph.reminders.setCheckInTime(java.time.LocalTime.of(22, 30)) }
        showReminders()
        compose.onNodeWithText("22:30 is in your quiet hours, so the check-in won't show.").performScrollTo().assertExists()
    }

    @Test
    fun quietHoursSwitchOff() {
        app.allowNotifications()
        showReminders()
        compose.onNodeWithText("Nothing shows from 22:00 to 07:00. Results and badges wait until 07:00.").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Reminders can show at any time"), timeoutMillis = 5_000)
        assertFalse(reminders().quietHours.enabled)
    }

    @Test
    fun settingsLeadsToRemindersAndTheWidgetAndTile() {
        app.allowNotifications()
        var opened = ""
        compose.setContent {
            LastHitTheme {
                SettingsScreen(
                    remember { SnackbarHostState() },
                    onBack = {},
                    links = SettingsLinks({}, {}, {}, {}, {}, reminders = { opened = "reminders" }, widgetTile = { opened = "widget" }),
                )
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("4 of 5 on, quiet from 22:00 to 07:00"), timeoutMillis = 5_000)
        compose.onNodeWithText("Reminders").performScrollTo().performClick()
        assertEquals("reminders", opened)
        compose.onNodeWithText("The tile logs Nicorette gum 2${Char(0xA0)}mg").performScrollTo().performClick()
        assertEquals("widget", opened)
    }

    @Test
    fun theTileCanBeSetToOpenTheLogSheet() {
        compose.setContent { LastHitTheme { WidgetTileScreen(onBack = {}) } }
        compose.waitUntilAtLeastOneExists(hasText("Open the log sheet"), timeoutMillis = 5_000)
        compose.onNodeWithText("Open the log sheet").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking { app.graph.reminders.currentQuickLog().tileOpensSheet }
        }
        compose.onNodeWithText("Pick tags after a tap").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking { app.graph.reminders.currentQuickLog().widgetAsksForTags }
        }
    }
}
