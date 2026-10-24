package app.thelasthit.nrt.ui.lair

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.game.GameSound
import app.thelasthit.nrt.ui.game.LocalGameSounds
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class LairBattleTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val sounds = RecordedSounds()

    private fun showLair() = compose.setContent {
        LastHitTheme { CompositionLocalProvider(LocalGameSounds provides sounds) { LairScreen() } }
    }

    private fun hp(hp: Int, max: Int = 120) = hasContentDescription("$hp of $max HP")

    private fun demon() = runBlocking { checkNotNull(app.graph.game.demon()) }

    @After
    fun animationsBackOn() = app.setAnimatorScale(1f)

    @Test
    fun `the battle shows the week, the HP, the hit banked, and Hit ready but not the 5x`() {
        app.battleAsMockup()
        showLair()

        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)
        compose.onNodeWithText("100/120", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Week 4 of 12").assertExists()
        compose.onNodeWithText("Gary eyes you from the rocks.").assertExists()
        compose.onNodeWithContentDescription("Gary the dragon, standing on a rock").assertExists()
        compose.onNodeWithContentDescription("1 hit banked", substring = true).assertExists()
        compose.onNode(hasText("Hit") and hasClickAction()).assertIsEnabled()
        compose.onNode(hasText("Uses 1 hit")).assertExists()
        compose.onNode(hasText("5× attack") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("Needs 5 hits")).assertExists()
    }

    @Test
    fun `a hit takes 10 HP with its sounds, and the demon staggers`() {
        app.battleAsMockup()
        showLair()

        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)
        compose.onNode(hasText("Hit") and hasClickAction()).performClick()

        compose.waitUntilExactlyOneExists(hasText("Gary staggers."), timeoutMillis = 5_000)
        compose.onNode(hp(90)).assertExists()
        compose.onNodeWithText("No hits banked").assertExists()
        compose.onNode(hasText("Hit") and hasClickAction()).assertIsNotEnabled()
        assertEquals(90, demon().balance.hp)
        assertEquals(0, demon().balance.banked)
        assertEquals(listOf(GameSound.SWING, GameSound.IMPACT), sounds.snapshot())
    }

    @Test
    fun `the 5x attack spends five hits for 50 HP in one combo`() {
        app.battleWithCombo()
        showLair()

        compose.waitUntilExactlyOneExists(hp(80), timeoutMillis = 5_000)
        compose.onNodeWithText("Gary has seen better weeks.").assertExists()
        compose.onNodeWithContentDescription("Gary the dragon, roughed up, on a rock").assertExists()
        compose.onNode(hasText("Uses 5 hits")).assertExists()
        compose.onNode(hasText("5× attack") and hasClickAction()).assertIsEnabled().performClick()

        compose.waitUntilExactlyOneExists(hasText("Gary reels. It didn't see five coming."), timeoutMillis = 5_000)
        compose.onNode(hp(30)).assertExists()
        compose.onNodeWithContentDescription("Gary the dragon, barely standing on a rock").assertExists()
        assertEquals(30, demon().balance.hp)
        assertEquals(0, demon().balance.banked)
        val played = sounds.snapshot()
        assertEquals(5, played.count { it == GameSound.STRIKE })
        assertEquals(GameSound.FINISHER, played.last())
    }

    @Test
    fun `the final hit plays the defeat, then the victory sums up the run`() {
        app.battleLastHit()
        showLair()

        compose.waitUntilExactlyOneExists(hp(10), timeoutMillis = 5_000)
        compose.onNodeWithText("Programme done").assertExists()
        compose.onNodeWithText("Gary has one hit left in it.").assertExists()
        compose.onNode(hasText("Hit") and hasClickAction()).performClick()

        compose.waitUntilExactlyOneExists(hasText("Victory"), timeoutMillis = 10_000)
        compose.onNode(hp(0)).assertExists()
        compose.onNodeWithText("Nothing left of Gary but a few stray pixels.").assertExists()
        compose.onNodeWithContentDescription("An empty rock, with the last of Gary drifting off as pixels").assertExists()
        compose.onNodeWithText("Weeks").assertExists()
        compose.onNodeWithText("12").assertExists()
        compose.onNodeWithText("32 mg, down to none").assertExists()
        compose.onNodeWithText("Hits on Gary").assertExists()
        compose.onNodeWithText("12, the last one today").performScrollTo()
        // The battle's buttons and the rename are gone with the demon.
        compose.onNode(hasText("Hit") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Rename or restyle Gary").assertDoesNotExist()
        assertNotNull(demon().demon.defeatedAt)
        val played = sounds.snapshot()
        assertEquals(listOf(GameSound.SWING, GameSound.IMPACT, GameSound.KNOCKOUT, GameSound.BREAK, GameSound.VICTORY), played)
    }

    @Test
    fun `a demon already defeated shows the empty rock and the victory, with the triggers got on top of`() {
        app.defeatedWithTriggers()
        showLair()

        compose.waitUntilExactlyOneExists(hasText("Victory"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("An empty rock, with the last of Gary drifting off as pixels").assertExists()
        compose.onNodeWithText("Triggers you got on top of").assertExists()
        compose.onNodeWithText("After a meal, With coffee or tea").assertExists()
        assertTrue(sounds.snapshot().isEmpty())
    }

    @Test
    fun `renaming and restyling keeps the HP and hits`() {
        app.battleAsMockup()
        showLair()

        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Rename or restyle Gary").performClick()
        compose.waitUntilExactlyOneExists(hasText("Rename or restyle"), timeoutMillis = 5_000)
        compose.onNodeWithText("HP and hits stay as they are.").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("Mildred")
        compose.onNode(hasText("Ember") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("Save") and hasClickAction()).performClick()

        compose.waitUntilExactlyOneExists(hasText("Mildred eyes you from the rocks."), timeoutMillis = 5_000)
        compose.onNode(hp(100)).assertExists()
        val demon = demon()
        assertEquals("Mildred", demon.demon.name)
        assertEquals("ember", demon.demon.variant)
        assertEquals(Creature.DRAGON, demon.demon.creature)
        assertEquals(1, demon.balance.banked)
    }

    @Test
    fun `renaming while the demon lurks names the one the plan starts with`() {
        app.setUp()
        showLair()

        compose.waitUntilExactlyOneExists(hasText("Gary watches from the rocks until your plan starts."), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Rename or restyle Gary").performClick()
        compose.waitUntilExactlyOneExists(hasText("Rename or restyle"), timeoutMillis = 5_000)
        compose.onNodeWithText("HP and hits stay as they are.").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("Mildred")
        compose.onNode(hasText("Save") and hasClickAction()).performClick()

        compose.waitUntilExactlyOneExists(hasText("Mildred watches from the rocks until your plan starts."), timeoutMillis = 5_000)
        assertEquals("Mildred", runBlocking { app.graph.settings.settings.first().demonName })
    }

    @Test
    fun `the creatures not drawn yet can't be picked when restyling`() {
        app.battleAsMockup()
        showLair()

        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Rename or restyle Gary").performClick()
        compose.waitUntilExactlyOneExists(hasText("Rename or restyle"), timeoutMillis = 5_000)
        for (creature in Creature.entries.filterNot { it.isDrawn }) {
            val name = creature.name.lowercase().replaceFirstChar { it.uppercase() }
            compose.onNodeWithContentDescription("$name, not drawn yet").assertExists()
        }
    }

    @Test
    fun `with the sound off, a hit makes no sound`() {
        app.battleAsMockup()
        runBlocking { app.graph.settings.setSoundOn(false) }
        showLair()

        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)
        compose.onNode(hasText("Hit") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasText("Gary staggers."), timeoutMillis = 5_000)
        assertTrue(sounds.snapshot().isEmpty())
    }

    @Test
    fun `with animations off, the hit lands at once`() {
        app.battleAsMockup()
        app.setAnimatorScale(0f)
        showLair()

        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Hit") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(hp(90)).assertExists()
        // And it doesn't keep the buttons waiting for long.
        compose.mainClock.advanceTimeBy(BattleMotion.REDUCED_LENGTH + 100)
        compose.mainClock.autoAdvance = true
        compose.waitUntilExactlyOneExists(hasText("Gary staggers."), timeoutMillis = 5_000)
        assertEquals(listOf(GameSound.IMPACT), sounds.snapshot())
    }

    @Test
    fun `at normal speed the HP holds until the hit lands`() {
        app.battleAsMockup()
        showLair()

        compose.waitUntilExactlyOneExists(hp(100), timeoutMillis = 5_000)
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Hit") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(BattleMotion.HIT_IMPACT / 2)
        compose.onNode(hp(100)).assertExists()
        compose.mainClock.advanceTimeBy(BattleMotion.HIT_IMPACT)
        compose.onNode(hp(90)).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitUntilExactlyOneExists(hasText("Gary staggers."), timeoutMillis = 5_000)
    }
}
