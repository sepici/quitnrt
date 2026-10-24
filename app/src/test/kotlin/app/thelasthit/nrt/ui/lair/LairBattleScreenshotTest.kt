package app.thelasthit.nrt.ui.lair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.game.Attack
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.game.GameArt
import app.thelasthit.nrt.ui.game.LocalGameSounds
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.LastHitTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The lair's battle in each state, inside the shell so the tab bar and its badge show too. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LairBattleScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    /** Opens the Lair tab and waits for [ready]. */
    private fun captureLair(name: String, ready: String, after: () -> Unit = {}) {
        compose.captureLooks(
            name,
            ready = hasText("Lair"),
            before = {
                onNodeWithText("Lair").performClick()
                waitUntil(5_000) { onAllNodes(hasText(ready)).fetchSemanticsNodes().isNotEmpty() }
                // The gear row and the shelf come in after the demon. Until then a short page leaves
                // the stage its spare height as sky, which on this tall window is most of it.
                waitUntil(5_000) { onAllNodes(hasText("Trophy shelf")).fetchSemanticsNodes().isNotEmpty() }
                after()
            },
        ) {
            CompositionLocalProvider(LocalGameSounds provides RecordedSounds()) { AppShell() }
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun lairBattle() {
        app.battleAsMockup()
        captureLair("lair-battle", ready = "Gary eyes you from the rocks.")
    }

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun lairBattleLarge() {
        app.battleAsMockup()
        captureLair("lair-battle", ready = "Gary eyes you from the rocks.")
    }

    @Test @Config(qualifiers = PHONE)
    fun lairBattleCombo() {
        app.battleWithCombo()
        captureLair("lair-battle-combo", ready = "Gary has seen better weeks.")
    }

    @Test @Config(qualifiers = PHONE)
    fun lairBattleBarely() {
        app.battleBarely()
        captureLair("lair-battle-barely", ready = "Gary is barely standing.")
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun lairVictory() {
        defeated()
        captureLair("lair-victory", ready = "Victory")
    }

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun lairVictoryLarge() {
        defeated()
        captureLair("lair-victory", ready = "Victory")
    }

    @Test @Config(qualifiers = PHONE)
    fun lairRestyle() {
        app.battleAsMockup()
        captureLair("lair-restyle", ready = "Gary eyes you from the rocks.") {
            compose.onNodeWithContentDescription("Rename or restyle Gary").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Rename or restyle")).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun lairRestyleLarge() {
        app.battleAsMockup()
        captureLair("lair-restyle", ready = "Gary eyes you from the rocks.") {
            compose.onNodeWithContentDescription("Rename or restyle Gary").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Rename or restyle")).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    /**
     * The 5× finisher landing, with 9 hits banked: both attack buttons are held down until it's
     * over. Each look starts in its own theme, since the clock stands still at the impact.
     */
    @Test @Config(qualifiers = PHONE)
    fun lairStriking() = captureStriking(dark = false)

    @Test @Config(qualifiers = PHONE)
    fun lairStrikingDark() = captureStriking(dark = true)

    private fun captureStriking(dark: Boolean) {
        app.battleAt(week = 10, spent = 0)
        compose.setContent {
            LastHitTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = LastHit.colors.bg, contentColor = LastHit.colors.text) {
                    CompositionLocalProvider(LocalGameSounds provides RecordedSounds()) { AppShell() }
                }
            }
        }
        compose.onNodeWithText("Lair").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Gary eyes you from the rocks.")).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("5× attack") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(BattleMotion.FINISHER_IMPACT + 40)
        captureScreenRoboImage("build/outputs/roborazzi/lair-striking-${if (dark) "dark" else "light"}.png")
        compose.mainClock.autoAdvance = true
    }

    /**
     * The strikes, a moment of each on its own stage, top to bottom: a hit landing, the 5×
     * finisher's shake, the knockout, and the demon breaking into pixels.
     */
    @Test @Config(qualifiers = FULL_PAGE)
    fun lairStrikes() {
        val hit = BattleMotion(Strike(1, Attack.HIT, hpBefore = 100, maxHp = 120), reduced = false)
        val combo = BattleMotion(Strike(2, Attack.COMBO, hpBefore = 80, maxHp = 120), reduced = false)
        val last = BattleMotion(Strike(3, Attack.HIT, hpBefore = 10, maxHp = 120), reduced = false)
        val moments = listOf(
            hit.at(BattleMotion.HIT_IMPACT + 40),
            combo.at(BattleMotion.FINISHER_IMPACT + 40),
            last.at(BattleMotion.HIT_IMPACT + BattleMotion.KNOCKOUT_AFTER + 100),
            last.at(BattleMotion.HIT_IMPACT + BattleMotion.KNOCKOUT_AFTER + BattleMotion.BREAK_AFTER + 300),
        )
        compose.captureLooks("lair-strikes") {
            Column(Modifier.background(LastHit.colors.bg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pose in moments) {
                    BattleScene(
                        Creature.DRAGON,
                        variant = "forest",
                        frame = pose.frame ?: restingFrame(pose.hp, 120, idleTwo = false),
                        pose = pose,
                        equipped = Gear.IRON_SWORD,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(GameArt.stage.width.toFloat() / GameArt.stage.height),
                    )
                }
            }
        }
    }

    private fun defeated() = app.defeatedWithTriggers()
}
