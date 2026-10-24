package app.thelasthit.nrt.ui.lair

import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.domain.game.Attack
import app.thelasthit.nrt.ui.game.GameSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BattleMotionTest {

    private val hit = Strike(1, Attack.HIT, hpBefore = 100, maxHp = 120)
    private val combo = Strike(2, Attack.COMBO, hpBefore = 80, maxHp = 120)
    private val lastHit = Strike(3, Attack.HIT, hpBefore = 10, maxHp = 120)

    private fun BattleMotion.poses(): List<Pose> = (0..length step 10).map { at(it) }

    @Test
    fun `a hit holds the HP through the wind-up, then lands with a flash, a knock-back and -10`() {
        val motion = BattleMotion(hit, reduced = false)
        val windUp = motion.at(BattleMotion.HIT_IMPACT - 10)
        assertEquals(100, windUp.hp)
        assertNotNull(windUp.weapon)
        assertNull(windUp.damage)
        assertFalse(windUp.flash)

        val impact = motion.at(BattleMotion.HIT_IMPACT)
        assertEquals(90, impact.hp)
        assertTrue(impact.flash)
        assertEquals(SpriteFrame.HURT, impact.frame)
        assertEquals(3, impact.knockBack)
        assertEquals(10, impact.damage?.amount)
        assertTrue(impact.sparks.isNotEmpty())

        val after = motion.at(motion.length)
        assertEquals(90, after.hp)
        assertNull(after.damage)
        assertNull(after.frame)
        assertEquals(0, after.knockBack)
        assertNull(after.weapon)
        assertEquals(listOf(GameSound.SWING, GameSound.IMPACT), motion.cues.map { it.second })
    }

    @Test
    fun `the 5x is five quick strikes, then a finisher with a shake and -50`() {
        val motion = BattleMotion(combo, reduced = false)
        val strikes = motion.cues.filter { it.second == GameSound.STRIKE }
        assertEquals(5, strikes.size)
        assertTrue(strikes.all { it.first < BattleMotion.FINISHER_IMPACT })
        assertEquals(BattleMotion.FINISHER_IMPACT to GameSound.FINISHER, motion.cues.last())

        assertEquals(80, motion.at(BattleMotion.FINISHER_IMPACT - 1).hp)
        val finisher = motion.at(BattleMotion.FINISHER_IMPACT)
        assertEquals(30, finisher.hp)
        assertEquals(50, finisher.damage?.amount)
        assertTrue(finisher.damage!!.big)
        assertTrue(finisher.shake != Px(0, 0))
        assertEquals(Px(0, 0), motion.at(motion.length).shake)
        assertTrue(motion.poses().any { it.knockBack == 5 })
    }

    @Test
    fun `the final hit knocks the demon out and breaks it apart, through every defeat frame`() {
        val motion = BattleMotion(lastHit, reduced = false)
        val frames = motion.poses().mapNotNull { it.frame }.distinct()
        assertEquals(listOf(SpriteFrame.HURT) + SpriteFrame.DEFEAT, frames)
        // From the impact on, the demon is always hurt or going down: never back to a resting frame.
        assertTrue(motion.poses().filter { it.hp == 0 }.all { it.frame != null })
        assertTrue(motion.at(motion.length).isDefeated)
        assertEquals(SpriteFrame.DEFEAT_4, motion.at(motion.length).frame)
        assertEquals(
            listOf(GameSound.SWING, GameSound.IMPACT, GameSound.KNOCKOUT, GameSound.BREAK, GameSound.VICTORY),
            motion.cues.map { it.second },
        )
        assertEquals(motion.cues.sortedBy { it.first }, motion.cues)
        assertTrue(motion.cues.last().first < motion.length)
    }

    @Test
    fun `with animations off, nothing moves or flashes, and it's over quickly`() {
        for (strike in listOf(hit, combo, lastHit)) {
            val motion = BattleMotion(strike, reduced = true)
            assertEquals(0L, motion.impactAt)
            assertTrue(motion.length <= 1_000)
            for (pose in motion.poses()) {
                assertEquals(strike.hpAfter, pose.hp)
                assertEquals(0, pose.knockBack)
                assertFalse(pose.flash)
                assertNull(pose.weapon)
                assertTrue(pose.sparks.isEmpty())
                assertEquals(Px(0, 0), pose.shake)
                pose.damage?.let { assertEquals(0, it.rise) }
            }
        }
        // The final hit goes straight to the empty rock.
        assertEquals(SpriteFrame.DEFEAT_4, BattleMotion(lastHit, reduced = true).at(0).frame)
    }

    @Test
    fun `each cue comes once as the clock moves on, however the frames fall`() {
        val motion = BattleMotion(lastHit, reduced = false)
        var last = -1L
        val heard = mutableListOf<GameSound>()
        var now = 0L
        while (last < motion.length) {
            heard += motion.cuesBetween(last, now)
            last = now
            now += 37
        }
        assertEquals(motion.cues.map { it.second }, heard)
    }

    @Test
    fun `zoomed for the shake, the stage covers its bounds wherever the shake moves it`() {
        val (width, height) = 160 to 120
        val zoom = shakeZoom(width, height, BattleMotion.SHAKE_REACH)
        val shakes = BattleMotion(combo, reduced = false).poses().map { it.shake }.filter { it != Px(0, 0) }
        assertTrue(shakes.isNotEmpty())
        for (shake in shakes) {
            // Drawn zoom times larger about the middle, then moved zoom times the shake: its edges stay outside the stage's.
            assertTrue(zoom * (width / 2f - abs(shake.x)) >= width / 2f)
            assertTrue(zoom * (height / 2f - abs(shake.y)) >= height / 2f)
        }
        // And no larger than it needs to be: a few per cent.
        assertTrue(zoom < 1.05f)
    }
}
