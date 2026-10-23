package app.thelasthit.nrt.ui.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.ui.AppRoot
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class OnboardingTest {

    @get:Rule
    val compose = createComposeRule()

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
        compose.onNodeWithText("Next: when your day starts").assertIsEnabled().performClick()

        compose.waitUntilExactlyOneExists(hasContentDescription("30\u00A0minutes later"))
        compose.onNodeWithContentDescription("30\u00A0minutes later").performClick()
        compose.waitUntilExactlyOneExists(hasText("04:30"))
        next("Finish set-up")

        compose.waitUntilExactlyOneExists(hasText("Mildred, forest dragon"), timeoutMillis = 5_000)
        compose.onNodeWithText("Gum\u00A02\u00A0mg, Gum\u00A04\u00A0mg, Mouth\u00A0spray\u00A01\u00A0mg").assertExists()
        compose.onNodeWithContentDescription("Mildred is lurking. It shows itself when your plan starts. HP not shown yet.").assertExists()
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
    }

    @Test
    fun `search finds a product and adds it in order`() {
        showAt(OnboardingDraft(step = OnboardingStep.PRODUCTS, country = "GB", picked = listOf(GUM_2)))

        compose.waitUntilExactlyOneExists(hasText("Search, like niquitin minis 4"))
        compose.onNodeWithText("Search, like niquitin minis 4").performTextInput("niquitin minis 4")
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
    fun `back goes to the step before`() {
        showAt(OnboardingDraft(step = OnboardingStep.COUNTRY, country = "GB"))

        compose.waitUntilExactlyOneExists(hasText("Where do you buy your NRT?"))
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilExactlyOneExists(hasText("When did you stop smoking or vaping?"))
    }

    @Test
    fun `somewhere else offers only generic products`() {
        showAt(OnboardingDraft(step = OnboardingStep.PRODUCTS, country = "ZZ"))

        compose.waitUntilAtLeastOneExists(hasText("Any brand"))
        compose.onNodeWithText("Nicorette").assertDoesNotExist()
    }
}
