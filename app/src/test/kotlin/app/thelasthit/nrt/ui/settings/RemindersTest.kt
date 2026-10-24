package app.thelasthit.nrt.ui.settings

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.reminders.allowNotifications
import app.thelasthit.nrt.seedWeek
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

    /** Asked already, here or in set-up, and refused once, so Android would still let the app ask. */
    private fun askedAndRefusedOnce() {
        app.allowNotifications(false)
        runBlocking { app.graph.reminders.markPermissionAsked() }
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
    }

    /** Whether anything opened the phone's notification settings for the app. */
    private fun openedNotificationSettings(): Boolean =
        generateSequence { shadowOf(app).nextStartedActivity }.any { it.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS }

    @Test
    fun onceAskedSwitchingOneOnAsksNothingMore() {
        askedAndRefusedOnce()
        showReminders()
        compose.onNodeWithText("Forgot to log").performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("Forgot to log") and isToggleable() and isOn(), timeoutMillis = 5_000)
        assertTrue(reminders().isOn(Reminder.FORGOT_TO_LOG))
        assertEquals(null, shadowOf(compose.activity).lastRequestedPermission)
        assertFalse(openedNotificationSettings())
        // The note and its link stay, for asking again.
        compose.onNodeWithText("Notifications are off").assertExists()
        compose.onNodeWithText("Allow notifications").assertExists()
    }

    @Test
    fun onceAskedTheLinkStillAsksWhileAndroidLetsIt() {
        askedAndRefusedOnce()
        showReminders()
        compose.onNodeWithText("Allow notifications").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            shadowOf(compose.activity).lastRequestedPermission != null
        }
        val asked = shadowOf(compose.activity).lastRequestedPermission
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in asked.requestedPermissions)
    }

    @Test
    fun aCheckInInQuietHoursSaysItWontShow() {
        app.allowNotifications()
        runBlocking { app.graph.reminders.setCheckInTime(java.time.LocalTime.of(22, 30)) }
        showReminders()
        // On the time's own row, with the time still beside it, so it can't be read as about another.
        compose.onNodeWithText("In your quiet hours, so it won't show").performScrollTo().assertExists()
        compose.onNode(hasText("Check-in time") and hasText("In your quiet hours, so it won't show") and hasText("22:30")).assertExists()
    }

    @Test
    fun forgotToLogSaysAboutWhenToTheQuarterHour() {
        app.allowNotifications()
        // The mockups' week puts the usual first dose at 07:46.
        app.seedWeek()
        showReminders()
        compose.onNodeWithText("When nothing's logged by about 09:45, 2 hours after your usual first dose").performScrollTo().assertExists()
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
    fun beforeTheAppHasAskedTheSettingsRowInvitesRatherThanSaysOff() {
        app.allowNotifications(false)
        compose.setContent {
            LastHitTheme { SettingsScreen(remember { SnackbarHostState() }, onBack = {}, links = SettingsLinks({}, {}, {}, {}, {}, {}, {})) }
        }
        compose.waitUntilAtLeastOneExists(hasText("Allow notifications to get reminders"), timeoutMillis = 5_000)
        // Once asked and refused, they're off.
        runBlocking { app.graph.reminders.markPermissionAsked() }
        compose.waitUntilAtLeastOneExists(hasText("Notifications are off for this app"), timeoutMillis = 5_000)
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
