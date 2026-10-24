package app.thelasthit.nrt.ui.today

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.foundation.layout.fillMaxSize
import app.thelasthit.nrt.data.repo.LogRequest
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

    /** The sheet's "Log" button, told apart from the Log pill behind the sheet by its "2 mg at 15:05". */
    private val logButton = hasText("Log") and hasText(" at ", substring = true)
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
     * Reads the database directly, so only call it once the screen shows the change. A wait that
     * polls it never syncs with Compose, so the main thread never runs the save being waited for.
     */
    private fun entriesToday(): List<Entry> = runBlocking { app.graph.entries.observeDay(today).first().entries }

    @Test
    fun `a quick-log pill logs one now and Undo takes it back`() {
        // The pill shows "Gum 2 mg", and its spoken name starts with those words (label in name).
        val pill = compose.onNode(hasContentDescription("Gum 2\u00A0mg", substring = true) and hasClickAction())
        pill.assert(hasContentDescription("Log Gum 2\u00A0mg, Nicorette gum"))
        pill.performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 2\u00A0mg of Nicorette gum"))
        val entry = entriesToday().single()
        assertEquals(GUM_2, entry.productId)
        assertEquals(TestApp.NOW, entry.occurredAt)
        // The dose reads as one node: its name, time and mg together.
        compose.onNode(hasText("Nicorette gum") and hasText("15:05") and hasText("2\u00A0mg")).assertExists()

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilExactlyOneExists(hasText("Nothing logged yet today"))
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun `the log sheet saves a backdated craving with tags`() {
        compose.onNodeWithContentDescription("Log a dose").performClick()
        compose.waitUntilExactlyOneExists(hasText("Log a dose"))
        // Opening the sheet says its title, and the title and each field's name are headings.
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Log a dose")).assertExists()
        compose.onNode(hasText("Log a dose")).assert(isHeading())
        compose.onNodeWithText("Time").assert(isHeading())
        // The quick times are one group of choices.
        compose.onNodeWithText("1\u00A0h ago").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))

        compose.onNodeWithText("4\u00A0mg").performClick()
        compose.onNodeWithContentDescription("One more").performClick()
        compose.onNodeWithText("1\u00A0h ago").performScrollTo().performClick()
        compose.onNodeWithText("Craving").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Craving strength 3 of 5").performScrollTo().performClick()
        compose.onNodeWithText("After a meal").performScrollTo().performClick()
        compose.onNodeWithText("8\u00A0mg at 14:05").assertExists()
        compose.onNode(logButton).performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 8\u00A0mg of Nicorette gum"))
        val entry = entriesToday().single()
        assertEquals(GUM_4, entry.productId)
        assertEquals(2, entry.quantity)
        assertEquals(london("2026-10-23T14:05"), entry.occurredAt)
        assertEquals(EntryKind.CRAVING, entry.kind)
        assertEquals(3, entry.cravingStrength)
        assertEquals(setOf("after_meal"), entry.tagKeys)
    }

    @Test
    fun `yesterday counts towards yesterday`() {
        compose.onNodeWithContentDescription("Log a dose").performClick()
        compose.waitUntilExactlyOneExists(hasText("Log a dose"))
        compose.onNodeWithText("This time yesterday").performScrollTo().performClick()
        compose.onNode(logButton).performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 2\u00A0mg of Nicorette gum"))
        assertTrue(entriesToday().isEmpty())
        val yesterday = runBlocking { app.graph.entries.observeDay(today.minusDays(1)).first().entries }.single()
        assertEquals(london("2026-10-22T15:05"), yesterday.occurredAt)
    }

    @Test
    fun `a dose over the pack's limit brings a calm note, read out after the snackbar`() {
        // 15 pieces this morning, off the main thread, so the 16th from a pill is over the pack's 15.
        Thread { runBlocking { app.graph.entries.log(LogRequest(GUM_2, 15, at = london("2026-10-23T09:00"))) } }.start()
        compose.waitUntilExactlyOneExists(hasContentDescription("30\u00A0mg today"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Log Gum 2\u00A0mg, Nicorette gum").performClick()

        val note = hasText("That's more than the pack's daily limit of 15. Worth a word with a pharmacist.")
        compose.waitUntilExactlyOneExists(note, timeoutMillis = 5_000)
        compose.onNode(note).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `a long press opens the sheet on that product`() {
        val pill = compose.onNodeWithContentDescription("Log Mouth spray 1\u00A0mg, QuickMist")
        // A screen reader names what the long press does.
        pill.assert(SemanticsMatcher("long press says what it does") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Log with details" })
        pill.performTouchInput { longClick() }
        compose.waitUntilExactlyOneExists(hasText("1\u00A0spray"))
        compose.waitForIdle()
        compose.onNode(logButton).performClick()

        compose.waitUntilExactlyOneExists(hasText("Logged 1\u00A0mg of QuickMist"))
        assertEquals(QUICKMIST, entriesToday().single().productId)
    }
}
