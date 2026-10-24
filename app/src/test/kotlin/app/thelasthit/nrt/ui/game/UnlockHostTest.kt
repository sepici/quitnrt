package app.thelasthit.nrt.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        // The next toast follows, over the lair the first one opened.
        waitFor("1\u00A0breathing session")
        waitFor("Trophy shelf")
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

        compose.onNodeWithText("See the shelf").performClick()
        compose.waitUntilDoesNotExist(hasText("7\u00A0new badges"), timeoutMillis = 5_000)
        waitFor("Trophy shelf")
        assertTrue(app.graph.unlocks.news.value.isEmpty())
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
