package app.thelasthit.nrt.ui.today

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.foundation.layout.fillMaxSize
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-" + RobolectricDeviceQualifiers.Pixel7)
class TodayTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.parse("2026-10-23")

    @Before
    fun setUpApp() {
        app.setUp()
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    TodayScreen(snackbars = snackbars, onOpenSettings = {}, onOpenLair = {})
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasContentDescription("Log a dose"))
    }

    /**
     * Reads the database directly. It blocks the main thread, so only call it once the screen
     * shows the change; polling with it starves the save it's waiting for.
     */
    private fun entriesToday(): List<Entry> = runBlocking { app.graph.entries.observeDay(today).first().entries }

    @Test
    fun `a quick-log pill logs one now and Undo takes it back`() {
        compose.onNodeWithContentDescription("Log Nicorette gum 2\u00A0mg").performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 2\u00A0mg Nicorette gum"))
        val entry = entriesToday().single()
        assertEquals(GUM_2, entry.productId)
        assertEquals(TestApp.NOW, entry.occurredAt)

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilExactlyOneExists(hasText("Nothing logged yet today"))
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun `the log sheet saves a backdated craving with tags`() {
        compose.onNodeWithContentDescription("Log a dose").performClick()
        compose.waitUntilExactlyOneExists(hasText("Log a dose"))

        compose.onNodeWithText("4\u00A0mg").performClick()
        compose.onNodeWithContentDescription("One more").performClick()
        compose.onNodeWithText("1\u00A0h ago").performScrollTo().performClick()
        compose.onNodeWithText("Craving").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Craving strength 3 of 5").performScrollTo().performClick()
        compose.onNodeWithText("After a meal").performScrollTo().performClick()
        compose.onNodeWithText("8\u00A0mg at 14:05").assertExists()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 8\u00A0mg Nicorette gum"))
        val entry = entriesToday().single()
        assertEquals(GUM_4, entry.productId)
        assertEquals(2, entry.quantity)
        assertEquals(london("2026-10-23T14:05"), entry.occurredAt)
        assertEquals(EntryKind.CRAVING, entry.kind)
        assertEquals(3, entry.cravingStrength)
        assertEquals(setOf("after_meal"), entry.tagKeys)
    }

    @Test
    fun `yesterday counts toward yesterday`() {
        compose.onNodeWithContentDescription("Log a dose").performClick()
        compose.waitUntilExactlyOneExists(hasText("Log a dose"))
        compose.onNodeWithText("Yesterday").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 2\u00A0mg Nicorette gum"))
        assertTrue(entriesToday().isEmpty())
        val yesterday = runBlocking { app.graph.entries.observeDay(today.minusDays(1)).first().entries }.single()
        assertEquals(london("2026-10-22T15:05"), yesterday.occurredAt)
    }

    @Test
    fun `a long press opens the sheet on that product`() {
        compose.onNodeWithContentDescription("Log QuickMist 1\u00A0mg").performTouchInput { longClick() }
        compose.waitUntilExactlyOneExists(hasText("1\u00A0spray"))
        compose.waitForIdle()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 1\u00A0mg QuickMist"))
        assertEquals(QUICKMIST, entriesToday().single().productId)
    }
}
