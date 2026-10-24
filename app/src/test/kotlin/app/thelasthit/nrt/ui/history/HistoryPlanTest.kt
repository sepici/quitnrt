package app.thelasthit.nrt.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.seedPlan
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** History's guide ticks, held back from phase 1 until there's a plan to measure days against. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class HistoryPlanTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun show() {
        compose.setContent { LastHitTheme { HistoryScreen(snackbars = remember { SnackbarHostState() }) } }
    }

    @Test
    fun `days at or under the guide have a tick, and the top line says what it means`() {
        app.setUp()
        app.seedWeek()
        // Wednesday goes over the 14 mg guide.
        runBlocking { app.graph.entries.log(LogRequest(GUM_4, at = london("2026-10-21T20:00"))) }
        app.seedPlan()
        show()

        compose.waitUntilExactlyOneExists(hasText("Each day runs from 04:00. A tick means the day stayed at or under the guide."), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Thursday 22 October, 14 mg, at or under the guide").assertExists()
        compose.onNodeWithContentDescription("Wednesday 21 October, 16 mg").assertExists()
        // Today isn't over yet, so it has no tick.
        compose.onNodeWithContentDescription("Today, 8 mg so far").assertExists()
    }

    @Test
    fun `without a plan there are no ticks`() {
        app.setUp()
        app.seedWeek()
        show()

        compose.waitUntilExactlyOneExists(hasText("Each day runs from 04:00."), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Thursday 22 October, 14 mg").assertExists()
        compose.onNode(hasContentDescription("at or under the guide", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `under a filter a day's total is only its matches, so there are no ticks`() {
        app.setUp()
        app.seedWeek()
        app.seedPlan()
        show()

        compose.waitUntilExactlyOneExists(hasContentDescription("Thursday 22 October, 14 mg, at or under the guide"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Filter by product or tag").performClick()
        compose.waitUntilExactlyOneExists(hasText("Show 9 doses"), timeoutMillis = 5_000)
        compose.onNodeWithText("Gum 4 mg").performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("Show 4 doses"), timeoutMillis = 5_000)
        compose.onNodeWithText("Show 4 doses").performClick()

        compose.waitUntilExactlyOneExists(hasContentDescription("Filtered by Gum 4 mg. Clear filter"), timeoutMillis = 5_000)
        compose.onNodeWithText("Each day runs from 04:00.").assertExists()
        compose.onNode(hasContentDescription("at or under the guide", substring = true)).assertDoesNotExist()
    }
}
