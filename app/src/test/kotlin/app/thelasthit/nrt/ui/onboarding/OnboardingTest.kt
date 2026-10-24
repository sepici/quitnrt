package app.thelasthit.nrt.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.SetUpReminders
import app.thelasthit.nrt.reminders.allowNotifications
import app.thelasthit.nrt.ui.AppRoot
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class OnboardingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun tags() = runBlocking { app.graph.tags.ensureBuiltIns() }

    private fun showAt(draft: OnboardingDraft) {
        val viewModel = OnboardingViewModel(app.graph, region = "GB", initial = draft)
        compose.setContent { LastHitTheme { OnboardingScreen(viewModel) } }
    }

    private fun next(label: String) {
        compose.waitUntilExactlyOneExists(hasText(label))
        compose.onNodeWithText(label).performClick()
    }

    @Test
    fun `set-up stores every choice and opens the app`() {
        compose.setContent { LastHitTheme { AppRoot() } }

        next("Accept and set up")
        compose.waitUntilExactlyOneExists(hasText("Skip"))
        compose.onNodeWithText("Skip").performClick()

        compose.waitUntilExactlyOneExists(hasText("Your phone's region"))
        next("Next: your products")

        compose.waitUntilExactlyOneExists(hasContentDescription("Nicorette gum 2\u00A0mg"))
        compose.onNodeWithContentDescription("Nicorette gum 2\u00A0mg").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Nicorette gum 4\u00A0mg").performScrollTo().performClick()
        compose.onNodeWithContentDescription("QuickMist 1\u00A0mg").performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("3 picked"))
        next("Next: name your demon")

        compose.waitUntilExactlyOneExists(hasText("Next: when your day starts"))
        compose.onNodeWithText("Next: when your day starts").assertIsNotEnabled()
        compose.onNodeWithText("Mildred").performScrollTo().performClick()
        // The state is built off the main thread, so wait for the button rather than assert at once.
        compose.waitUntilExactlyOneExists(hasText("Next: when your day starts") and isEnabled())
        compose.onNodeWithText("Next: when your day starts").performClick()

        compose.waitUntilExactlyOneExists(hasContentDescription("Start 30\u00A0minutes later"))
        compose.onNodeWithContentDescription("Start 30\u00A0minutes later").performClick()
        compose.waitUntilExactlyOneExists(hasText("04:30"))
        next("Next: your reminders")

        // The reminders step, then the phone's question, allowed.
        compose.waitUntilExactlyOneExists(hasText("Which reminders do you want?"))
        compose.onNodeWithText("Evening check-in").performClick()
        next("Allow notifications and finish")
        answerTheQuestion(allow = true)
        compose.waitUntilExactlyOneExists(hasText("3 reminders on, quiet from 22:00 to 07:00"), timeoutMillis = 5_000)

        compose.waitUntilExactlyOneExists(hasText("Mildred, forest dragon"), timeoutMillis = 5_000)
        compose.onNodeWithText("Gum\u00A02\u00A0mg, Gum\u00A04\u00A0mg, Mouth\u00A0spray\u00A01\u00A0mg").assertExists()
        compose.onNodeWithContentDescription("Mildred is lurking. It shows itself when your plan starts. HP unknown.").assertExists()
        next("Start logging")

        compose.waitUntilExactlyOneExists(hasText("You've had"), timeoutMillis = 5_000)
        val settings = runBlocking { app.graph.settings.current() }
        assertTrue(settings.onboardingComplete)
        assertNotNull(settings.disclaimerAcceptedAt)
        assertNull(settings.stoppedSmokingOn)
        assertEquals("GB", settings.country)
        assertEquals("Mildred", settings.demonName)
        assertEquals("forest", settings.demonVariant)
        assertEquals(LocalTime.of(4, 30), settings.dayStart)
        assertEquals(listOf(GUM_2, GUM_4, QUICKMIST), runBlocking { app.graph.products.quickLog.first() }.map { it.id })
        assertEquals(setOf(Reminder.ALLOWANCE, Reminder.WEEKLY_RESULT, Reminder.ACHIEVEMENTS), reminders().on)
        assertTrue(permissionAsked())
    }

    // The reminders step.

    private fun reminders() = runBlocking { app.graph.reminders.currentReminders() }

    private fun permissionAsked() = runBlocking { app.graph.reminders.currentMarks().permissionAsked }

    private fun showReminders() {
        showAt(OnboardingDraft(step = OnboardingStep.REMINDERS, country = "GB", demonName = "Gary"))
        compose.waitUntilExactlyOneExists(hasText("Which reminders do you want?"), timeoutMillis = 5_000)
    }

    /** Answers the phone's notification question, once the step has asked it, as the phone would. */
    private fun answerTheQuestion(allow: Boolean) {
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            shadowOf(compose.activity).lastRequestedPermission != null
        }
        val asked = shadowOf(compose.activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), asked.requestedPermissions.toList())
        app.allowNotifications(allow)
        val answer = if (allow) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions, intArrayOf(answer))
        }
    }

    @Test
    fun `the day start leads to the reminders, all four on, with the steps bar at 7 of 8`() {
        showAt(OnboardingDraft(step = OnboardingStep.DAY_START, country = "GB", demonName = "Gary"))

        next("Next: your reminders")
        compose.waitUntilExactlyOneExists(hasText("Which reminders do you want?"))
        compose.onNodeWithContentDescription("Step 7 of 8").assertExists()
        listOf("Allowance nudges", "Evening check-in", "Weekly results", "Badges and gear").forEach {
            compose.onNode(hasText(it) and isToggleable()).assertIsOn()
        }
        // In the Reminders page's own words, so each reads the same in both places.
        compose.onNodeWithText("Today's total against the guide, with buttons for Log and All good").assertExists()
        compose.onNodeWithText("When a week's result comes in, with the hits it earned").assertExists()
        compose.onNodeWithText("Forgot to log").assertDoesNotExist()
        compose.onNodeWithText("Your phone asks next. Set-up finishes either way.").assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilExactlyOneExists(hasText("When does your day start?"))
    }

    @Test
    fun `not allowing still finishes set-up, and the question counts as asked`() {
        app.allowNotifications(false)
        showReminders()

        next("Allow notifications and finish")
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            permissionAsked()
        }
        answerTheQuestion(allow = false)

        compose.waitUntilExactlyOneExists(hasText("Notifications are off for this app"), timeoutMillis = 5_000)
        assertEquals(SetUpReminders.initial, reminders().on)
    }

    @Test
    fun `skip finishes with the switches as shown and asks nothing`() {
        app.allowNotifications(false)
        showReminders()

        compose.onNodeWithText("Weekly results").performClick()
        compose.waitUntilExactlyOneExists(hasText("Weekly results") and isToggleable() and isOff())
        compose.onNodeWithText("Skip").performClick()

        compose.waitUntilExactlyOneExists(hasText("Allow notifications to get reminders"), timeoutMillis = 5_000)
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        assertFalse(permissionAsked())
        assertEquals(setOf(Reminder.ALLOWANCE, Reminder.CHECK_IN, Reminder.ACHIEVEMENTS), reminders().on)
    }

    @Test
    fun `with every switch off there's nothing to ask`() {
        app.allowNotifications(false)
        showReminders()

        listOf("Allowance nudges", "Evening check-in", "Weekly results", "Badges and gear").forEach {
            compose.onNodeWithText(it).performClick()
            compose.waitUntilExactlyOneExists(hasText(it) and isToggleable() and isOff())
        }
        compose.onNodeWithText("Allow notifications and finish").assertDoesNotExist()
        compose.onNodeWithText("Your phone asks next. Set-up finishes either way.").assertDoesNotExist()
        next("Finish set-up")

        compose.waitUntilExactlyOneExists(hasText("All off"), timeoutMillis = 5_000)
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        assertEquals(emptySet<Reminder>(), reminders().on)
    }

    @Test
    fun `with notifications allowed already, as below Android 13, the button only finishes`() {
        app.allowNotifications()
        showReminders()

        compose.waitUntilExactlyOneExists(hasText("Finish set-up"))
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Allow notifications and finish").assertDoesNotExist()
        next("Finish set-up")

        compose.waitUntilExactlyOneExists(hasText("4 reminders on, quiet from 22:00 to 07:00"), timeoutMillis = 5_000)
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        assertFalse(permissionAsked())
    }

    @Test
    fun `search finds a product and adds it in order`() {
        showAt(OnboardingDraft(step = OnboardingStep.PRODUCTS, country = "GB", picked = listOf(GUM_2)))

        compose.waitUntilExactlyOneExists(hasText("Search, like NiQuitin minis 4"))
        compose.onNodeWithText("Search, like NiQuitin minis 4").performTextInput("niquitin minis 4")
        compose.waitUntilExactlyOneExists(hasText("NiQuitin Minis 4\u00A0mg"))
        compose.onNodeWithText("Add").performClick()

        compose.waitUntilExactlyOneExists(hasContentDescription("NiQuitin Minis 4\u00A0mg, number 2 on your quick log"))
        compose.onNodeWithText("2 picked").assertExists()
    }

    @Test
    fun `a recent stop date mentions the usual course, never blocking`() {
        showAt(OnboardingDraft(step = OnboardingStep.STOPPED, country = "GB", stoppedOn = LocalDate.parse("2026-09-07")))

        compose.waitUntilExactlyOneExists(hasText("Most NRT courses run 8 to 12\u00A0weeks"))
        compose.onNodeWithText("6\u00A0weeks ago").assertExists()
        compose.onNodeWithText("Next: your country").assertIsEnabled()
    }

    @Test
    fun `a stop date months ago has no note`() {
        showAt(OnboardingDraft(step = OnboardingStep.STOPPED, country = "GB", stoppedOn = LocalDate.parse("2026-03-02")))

        compose.waitUntilExactlyOneExists(hasText("Monday 2 March"))
        compose.onNodeWithText("Most NRT courses run 8 to 12\u00A0weeks").assertDoesNotExist()
    }

    @Test
    fun `skip is underlined, as the app's other links are`() {
        showAt(OnboardingDraft(step = OnboardingStep.STOPPED, country = "GB"))

        compose.waitUntilExactlyOneExists(hasText("Skip"))
        val node = compose.onNodeWithText("Skip", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals(TextDecoration.Underline, layouts.single().layoutInput.style.textDecoration)
    }

    @Test
    fun `back goes to the step before`() {
        showAt(OnboardingDraft(step = OnboardingStep.COUNTRY, country = "GB"))

        compose.waitUntilExactlyOneExists(hasText("Where do you buy your NRT?"))
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Where do you buy your NRT?")).assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilExactlyOneExists(hasText("When did you stop smoking or vaping?"))
        // The step before is told as a new pane, so a screen reader isn't left on a button that's gone.
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "When did you stop smoking or vaping?")).assertExists()
    }

    @Test
    fun `somewhere else offers only generic products`() {
        showAt(OnboardingDraft(step = OnboardingStep.PRODUCTS, country = "ZZ"))

        compose.waitUntilAtLeastOneExists(hasText("Any brand"))
        compose.onNodeWithText("Nicorette").assertDoesNotExist()
    }
}
