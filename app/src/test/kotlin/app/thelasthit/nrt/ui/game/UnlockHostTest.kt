package app.thelasthit.nrt.ui.game

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.UnlockNews
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class UnlockHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun showApp() = compose.setContent { LastHitTheme { AppShell() } }

    private fun waitFor(text: String) = compose.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    /** Waits for the lair to have scrolled the trophy shelf's heading into the top half of the screen. */
    private fun waitForShelfInView() {
        waitFor("Trophy shelf")
        compose.waitUntil(5_000) {
            val heading = compose.onNode(hasText("Trophy shelf")).fetchSemanticsNode().boundsInRoot
            val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
            heading.top >= 0f && heading.bottom < screen.height / 2
        }
    }

    /** How many lines [text] takes, as laid out. */
    private fun lineCount(text: String): Int {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    private fun boundsOf(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    @Test
    fun `a new badge slides in as a toast, then goes by itself`() {
        app.setUp()
        runBlocking { app.graph.entries.log(LogRequest(GUM_4)) }
        assertEquals(listOf(Achievement.FIRST_ENTRY), app.checkUnlocks().achievements)
        showApp()

        waitFor("First entry")
        compose.onNodeWithText("New badge").assertExists()
        compose.mainClock.advanceTimeBy(BADGE_TOAST_MILLIS + 1_000)
        compose.waitUntilDoesNotExist(hasText("First entry"), timeoutMillis = 5_000)
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            app.graph.unlocks.news.value.isEmpty()
        }
    }

    @Test
    fun `toasts come one at a time, and a tap opens the lair`() {
        app.setUp()
        runBlocking {
            app.graph.entries.log(LogRequest(GUM_4))
            app.graph.breathing.record(
                BreathingPattern.BOX,
                london("2026-10-23T14:00"),
                Duration.ofMinutes(2),
                rounds = 7,
                ranToEnd = true,
            )
        }
        assertEquals(listOf(Achievement.FIRST_ENTRY, Achievement.BREATHING_1), app.checkUnlocks().achievements)
        showApp()

        waitFor("First entry")
        compose.onNodeWithText("1\u00A0breathing session").assertDoesNotExist()
        compose.onNodeWithText("First entry").performClick()

        // The next toast follows, over the lair the first one opened, at the shelf.
        waitFor("1\u00A0breathing session")
        waitForShelfInView()
    }

    @Test
    fun `a toast stays as long as the phone's accessibility settings ask`() {
        app.setUp()
        runBlocking { app.graph.entries.log(LogRequest(GUM_4)) }
        app.checkUnlocks()
        val slow = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long,
                containsIcons: Boolean,
                containsText: Boolean,
                containsControls: Boolean,
            ): Long = if (containsControls) 20_000L else originalTimeoutMillis
        }
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides slow) { LastHitTheme { AppShell() } }
        }

        waitFor("First entry")
        compose.mainClock.advanceTimeBy(BADGE_TOAST_MILLIS + 1_000)
        compose.waitForIdle()
        compose.onNodeWithText("First entry").assertExists()
        compose.mainClock.advanceTimeBy(20_000)
        compose.waitUntilDoesNotExist(hasText("First entry"), timeoutMillis = 5_000)
    }

    @Test
    fun `many badges at once come in one summary card, not a toast each`() {
        app.setUp()
        app.seedFortnight()
        val found = app.checkUnlocks()
        assertEquals(7, found.achievements.size)
        showApp()

        waitFor("7\u00A0new badges")
        compose.onNodeWithText("New badge").assertDoesNotExist()
        compose.onNodeWithText("First entry").assertDoesNotExist()
        // The card waits to be closed, rather than going by itself.
        compose.mainClock.advanceTimeBy(BADGE_TOAST_MILLIS * 2)
        waitFor("7\u00A0new badges")

        // Its buttons sit side by side.
        assertEquals(boundsOf("See the shelf").top, boundsOf("Close").top)

        // "See the shelf" opens the lair with the shelf in view, not at the top under the stage.
        compose.onNodeWithText("See the shelf").performClick()
        compose.waitUntilDoesNotExist(hasText("7\u00A0new badges"), timeoutMillis = 5_000)
        waitForShelfInView()
        assertTrue(app.graph.unlocks.news.value.isEmpty())
    }

    // Native graphics, so text is measured as on a phone.
    @Test
    @Config(qualifiers = NARROW_PHONE, fontScale = LARGE_FONT)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `on a narrow phone at a large font, the summary card's buttons stack rather than wrap`() {
        app.setUp()
        app.seedFortnight()
        app.checkUnlocks()
        showApp()

        waitFor("See the shelf")
        assertEquals(1, lineCount("See the shelf"))
        assertEquals(1, lineCount("Close"))
        assertTrue(boundsOf("Close").top >= boundsOf("See the shelf").bottom)
    }

    @Test
    fun `the summary card closes`() {
        app.setUp()
        app.seedFortnight()
        app.checkUnlocks()
        showApp()

        waitFor("7\u00A0new badges")
        compose.onNodeWithText("Close").performClick()
        compose.waitUntilDoesNotExist(hasText("7\u00A0new badges"), timeoutMillis = 5_000)
        compose.onNodeWithText("Trophy shelf").assertDoesNotExist()
    }

    @Test
    fun `new gear gets its card, and Equip equips it`() {
        app.setUp()
        app.meetFirstWeek()
        val found = app.checkUnlocks()
        assertEquals(listOf(Achievement.FIRST_WEEK_MET), found.achievements)
        assertEquals(listOf(Gear.IRON_SWORD), found.gear)
        // The badge's toast has been and gone.
        app.graph.unlocks.shown(UnlockNews.Badge(Achievement.FIRST_WEEK_MET))
        showApp()

        waitFor("Iron sword")
        compose.onNodeWithText("New gear").assertExists()
        compose.onNodeWithText("For the first week met.").assertExists()
        compose.onNodeWithText("Equip").performClick()

        compose.waitUntilDoesNotExist(hasText("Iron sword"), timeoutMillis = 5_000)
        assertEquals("iron_sword", runBlocking { app.graph.settings.current().equippedGear })
    }

    @Test
    fun `new gear can wait`() {
        app.setUp()
        app.meetFirstWeek()
        app.checkUnlocks()
        app.graph.unlocks.shown(UnlockNews.Badge(Achievement.FIRST_WEEK_MET))
        showApp()

        waitFor("Iron sword")
        compose.onNodeWithText("Not now").performClick()
        compose.waitUntilDoesNotExist(hasText("Iron sword"), timeoutMillis = 5_000)
        assertEquals(null, runBlocking { app.graph.settings.current().equippedGear })
    }
}
