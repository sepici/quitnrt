package app.thelasthit.nrt.ui.lair

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.R
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class LairTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `the demon lurks with unknown HP`() {
        app.setUp()

        compose.setContent { LastHitTheme { LairScreen() } }

        compose.waitUntilExactlyOneExists(hasText("Gary watches from the rocks until your plan starts."))
        compose.onNodeWithText("Gary").assertExists()
        compose.onNodeWithContentDescription(app.getString(R.string.lair_hp_unknown)).assertExists()
        compose.onNodeWithContentDescription(app.getString(R.string.lair_stage_lurking, "Gary")).assertExists()
        compose.onNodeWithText(app.getString(R.string.lair_no_hits)).assertExists()
    }

    @Test
    fun `a demon with no name yet is called your demon`() {
        runBlocking { app.graph.settings.completeOnboarding() }

        compose.setContent { LastHitTheme { LairScreen() } }

        compose.waitUntilExactlyOneExists(hasText("Your demon watches from the rocks until your plan starts."))
    }

    @Test
    fun `the band on Today opens the lair`() {
        app.setUp()

        compose.setContent { LastHitTheme { AppShell() } }

        val band = hasContentDescription(app.getString(R.string.demon_band_description, "Gary"))
        compose.waitUntilAtLeastOneExists(hasScrollToNodeAction(), timeoutMillis = 5_000)
        // Today is a lazy list, and the band starts below the fold.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(band)
        compose.onNode(band).performClick()
        compose.waitUntilExactlyOneExists(hasText(app.getString(R.string.lair_no_hits)), timeoutMillis = 5_000)
    }
}
