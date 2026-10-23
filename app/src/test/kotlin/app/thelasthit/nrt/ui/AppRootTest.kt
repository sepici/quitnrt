package app.thelasthit.nrt.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.R
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppRootTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = app.getString(id)

    @Test
    fun `set-up shows until it's done`() {
        compose.setContent { LastHitTheme { AppRoot() } }

        compose.waitUntilExactlyOneExists(hasText(text(R.string.onboarding_placeholder_skip)))
        compose.onNodeWithText(text(R.string.onboarding_placeholder_skip)).performClick()

        compose.waitUntilExactlyOneExists(hasText(text(R.string.nav_history)))
        assertTrue(runBlocking { app.graph.settings.current().onboardingComplete })
    }

    @Test
    fun `goes straight to the app once set up`() {
        runBlocking { app.graph.settings.completeOnboarding() }

        compose.setContent { LastHitTheme { AppRoot() } }

        compose.waitUntilExactlyOneExists(hasText(text(R.string.nav_history)))
        compose.onNodeWithText(text(R.string.onboarding_placeholder_skip)).assertDoesNotExist()
    }
}
