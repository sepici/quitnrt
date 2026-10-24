package app.thelasthit.nrt.ui.lair

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.game.GameSound
import app.thelasthit.nrt.ui.game.LocalGameSounds
import app.thelasthit.nrt.ui.theme.LastHitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lair between visits: leaving it for another tab and coming back. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class LairVisitTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val sounds = RecordedSounds()

    private fun showShell() = compose.setContent {
        LastHitTheme { CompositionLocalProvider(LocalGameSounds provides sounds) { AppShell() } }
    }

    private fun hp(hp: Int, max: Int = 120) = hasContentDescription("$hp of $max HP")

    private fun openTab(label: String) = compose.onNodeWithText(label).performClick()

    @Test
    fun `back in the lair after a hit, the demon's line follows its HP again`() {
        // 80 of 120 HP, with 5 hits banked.
        app.battleWithCombo()
        showShell()
        openTab("Lair")
        compose.waitUntilExactlyOneExists(hp(80), timeoutMillis = 5_000)
        compose.onNode(hasText("Hit") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasText("Gary staggers."), timeoutMillis = 5_000)

        openTab("History")
        compose.waitUntilDoesNotExist(hasText("Gary staggers."), timeoutMillis = 5_000)
        openTab("Lair")

        compose.waitUntilExactlyOneExists(hp(70), timeoutMillis = 5_000)
        compose.waitUntilExactlyOneExists(hasText("Gary has seen better weeks."), timeoutMillis = 5_000)
        compose.onNodeWithText("Gary staggers.").assertDoesNotExist()
    }

    @Test
    fun `leaving mid-strike ends it, so back in the lair it isn't played again`() {
        // 100 of 120 HP, with 1 hit banked.
        app.battleAsMockup()
        showShell()
        openTab("Lair")
        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)

        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Hit") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(BattleMotion.HIT_IMPACT / 3)
        openTab("History")
        compose.mainClock.autoAdvance = true
        compose.waitUntilDoesNotExist(hasText("Hit") and hasClickAction(), timeoutMillis = 5_000)

        openTab("Lair")
        compose.waitUntilExactlyOneExists(hp(90), timeoutMillis = 5_000)
        compose.waitUntilExactlyOneExists(hasText("No hits banked"), timeoutMillis = 5_000)
        compose.waitForIdle()
        // The swing played once, at the tap, and nothing played again on the way back.
        assertEquals(1, sounds.snapshot().count { it == GameSound.SWING })
        compose.onNode(hp(100)).assertDoesNotExist()
    }
}
