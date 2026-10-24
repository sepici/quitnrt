package app.thelasthit.nrt.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BattleTest {

    @Test
    fun `a hit is 10 damage for 1 hit, and the 5x attack 50 for 5`() {
        assertEquals(10, Attack.HIT.damage)
        assertEquals(1, Attack.HIT.hits)
        assertEquals(50, Attack.COMBO.damage)
        assertEquals(5, Attack.COMBO.hits)
    }

    @Test
    fun `an attack needs the hits banked and the HP to take`() {
        assertTrue(Attack.HIT.canUse(banked = 1, hp = 10))
        assertFalse(Attack.HIT.canUse(banked = 0, hp = 120))
        assertFalse(Attack.HIT.canUse(banked = 1, hp = 0))
        assertTrue(Attack.COMBO.canUse(banked = 5, hp = 50))
        assertFalse(Attack.COMBO.canUse(banked = 4, hp = 120))
        assertFalse(Attack.COMBO.canUse(banked = 5, hp = 40))
    }

    @Test
    fun `the line follows the demon's health in thirds`() {
        assertEquals(LairLine.FRESH, LairLine.of(hp = 120, maxHp = 120, banked = 0, lastWeekMissed = false))
        assertEquals(LairLine.FRESH, LairLine.of(hp = 90, maxHp = 120, banked = 1, lastWeekMissed = false))
        assertEquals(LairLine.ROUGH, LairLine.of(hp = 80, maxHp = 120, banked = 1, lastWeekMissed = false))
        assertEquals(LairLine.ROUGH, LairLine.of(hp = 50, maxHp = 120, banked = 0, lastWeekMissed = false))
        assertEquals(LairLine.BARELY, LairLine.of(hp = 40, maxHp = 120, banked = 0, lastWeekMissed = false))
        assertEquals(LairLine.BARELY, LairLine.of(hp = 10, maxHp = 120, banked = 0, lastWeekMissed = false))
    }

    @Test
    fun `one hit from the end, with a hit to land it, the demon knows`() {
        assertEquals(LairLine.LAST_HIT, LairLine.of(hp = 10, maxHp = 120, banked = 1, lastWeekMissed = false))
    }

    @Test
    fun `after a missed week the demon looks smug, until an attack lands`() {
        assertEquals(LairLine.SMUG, LairLine.of(hp = 90, maxHp = 120, banked = 0, lastWeekMissed = true))
        assertEquals(LairLine.SMUG, LairLine.of(hp = 10, maxHp = 120, banked = 1, lastWeekMissed = true))
        assertEquals(LairLine.STAGGERS, LairLine.of(hp = 80, maxHp = 120, banked = 0, lastWeekMissed = true, attack = Attack.HIT))
    }

    @Test
    fun `the demon answers the attack just landed`() {
        assertEquals(LairLine.STAGGERS, LairLine.of(hp = 110, maxHp = 120, banked = 0, lastWeekMissed = false, attack = Attack.HIT))
        assertEquals(LairLine.REELS, LairLine.of(hp = 50, maxHp = 120, banked = 0, lastWeekMissed = false, attack = Attack.COMBO))
    }

    @Test
    fun `at 0 HP it's gone, whatever landed last`() {
        assertEquals(LairLine.GONE, LairLine.of(hp = 0, maxHp = 120, banked = 0, lastWeekMissed = false, attack = Attack.HIT))
        assertEquals(LairLine.GONE, LairLine.of(hp = 0, maxHp = 80, banked = 0, lastWeekMissed = true))
    }
}
