package app.thelasthit.nrt.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class HistoryTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.parse("2026-10-23")

    private fun show() {
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    HistoryScreen(snackbars = snackbars)
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    private fun log(at: String, vararg tags: String) = runBlocking {
        app.graph.entries.log(LogRequest(GUM_2, at = london(at), tagKeys = tags.toSet()))
    }

    /** Reads the database. Only call it once the screen shows the change, as in TodayTest. */
    private fun entriesOn(date: LocalDate): List<Entry> = runBlocking { app.graph.entries.observeDay(date).first().entries }

    @Test
    fun `days show newest first with their totals`() {
        app.setUp()
        app.seedWeek()
        show()

        compose.waitUntilExactlyOneExists(hasContentDescription("Today, 8\u00A0mg so far"))
        compose.onNodeWithContentDescription("Thursday 22 October, 14\u00A0mg").assertExists()
        compose.onNodeWithText("Each day runs from 04:00.").assertExists()
    }

    @Test
    fun `swiping a dose away deletes it and Undo brings it back`() {
        app.setUp()
        app.seedWeek()
        show()

        compose.waitUntilExactlyOneExists(hasText("10:55, Work break"))
        compose.onNodeWithText("10:55, Work break").performTouchInput { swipeLeft() }

        compose.waitUntilExactlyOneExists(hasText("Deleted 2\u00A0mg Nicorette gum from 10:55"))
        assertEquals(2, entriesOn(today).size)

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilExactlyOneExists(hasText("10:55, Work break"))
        assertEquals(3, entriesOn(today).size)
    }

    @Test
    fun `screen readers can delete without swiping`() {
        app.setUp()
        app.seedWeek()
        show()

        compose.waitUntilExactlyOneExists(hasText("13:40, After a meal"))
        compose.onNodeWithText("13:40, After a meal").performCustomAccessibilityActionWithLabel("Delete")

        compose.waitUntilExactlyOneExists(hasText("Deleted 2\u00A0mg Nicorette gum from 13:40"))
        assertTrue(entriesOn(today).none { it.tagKeys == setOf("after_meal") })
    }

    @Test
    fun `tapping a dose edits it and keeps its time`() {
        app.setUp()
        app.seedWeek()
        show()

        compose.waitUntilExactlyOneExists(hasText("13:40, After a meal"))
        compose.onNodeWithText("13:40, After a meal").performClick()
        compose.waitUntilExactlyOneExists(hasText("Edit dose"))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("One more").performClick()
        compose.onNodeWithText("Save changes").performClick()

        compose.waitUntilExactlyOneExists(hasText("Changes saved"))
        val edited = entriesOn(today).single { it.tagKeys == setOf("after_meal") }
        assertEquals(2, edited.quantity)
        assertEquals(london("2026-10-23T13:40"), edited.occurredAt)
        compose.onNode(hasText("Nicorette gum, 2\u00A0pieces") and hasText("13:40, After a meal")).assertExists()
    }

    @Test
    fun `a dose after midnight shows under the evening before`() {
        app.setUp()
        log("2026-10-23T01:20", "before_bed")
        show()

        compose.waitUntilExactlyOneExists(hasText("01:20 Friday, Before bed"))
        compose.onNodeWithContentDescription("Thursday 22 October, 2\u00A0mg").assertExists()
    }

    @Test
    fun `earlier days load when asked`() {
        app.setUp()
        log("2026-09-01T09:00", "bored")
        show()

        compose.waitUntilExactlyOneExists(hasText("Show earlier days"))
        compose.onNodeWithText("09:00, Bored").assertDoesNotExist()
        compose.onNodeWithText("Show earlier days").performClick()
        compose.waitUntilExactlyOneExists(hasText("09:00, Bored"))
    }

    @Test
    fun `with nothing logged it offers the log sheet`() {
        app.setUp()
        show()

        compose.waitUntilExactlyOneExists(hasText("Nothing logged yet"))
        compose.onNodeWithText("Log a dose").performClick()
        compose.waitUntilExactlyOneExists(hasText("Planned or craving"))
    }
}
