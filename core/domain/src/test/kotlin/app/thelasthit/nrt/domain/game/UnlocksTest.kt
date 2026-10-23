package app.thelasthit.nrt.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UnlocksTest {

    /** Six weeks of example 2 met, logged every day from the baseline on. */
    private val sixWeeks: GameRecord by lazy {
        val run = WeekRun(EXAMPLE_2_PLAN)
        repeat(6) { run.result(met = true) }
        val today = PLAN_START.plusWeeks(6).plusDays(1)
        val days = generateSequence(BASELINE_START) { it.plusDays(1) }.takeWhile { it < today }.toList()
        GameRecord(today, days.map { dose("${it}T10:00") }, programmes = listOf(programmeOf(run.weeks)))
    }

    @Test
    fun `gear comes with its achievement, in order, and the stick is there from the start`() {
        assertEquals(setOf(Gear.WOODEN_STICK), Gear.unlockedWith(emptySet()))
        assertEquals(
            listOf(Gear.WOODEN_STICK, Gear.WOODEN_SWORD, Gear.IRON_SWORD),
            Gear.unlockedWith(setOf(Achievement.FIRST_WEEK_MET, Achievement.FIRST_DAY_IN_GUIDE, Achievement.TAGGED_25)).toList(),
        )
        assertEquals(Gear.entries.toSet(), Gear.unlockedWith(Achievement.entries.toSet()))
        assertTrue(Gear.WOODEN_STICK.isStarter)
        assertEquals(Gear.entries.size - 1, Gear.entries.mapNotNull { it.unlockedBy }.toSet().size, "one achievement per piece")
    }

    @Test
    fun `only unlocked gear can be equipped`() {
        val loadout = Loadout.of(setOf(Achievement.FIRST_DAY_IN_GUIDE), equippedKey = null)
        assertEquals(Gear.WOODEN_STICK, loadout.equipped)
        assertEquals(Gear.WOODEN_SWORD, loadout.equip(Gear.WOODEN_SWORD).equipped)
        assertFalse(loadout.canEquip(Gear.AXE))
        assertThrows<IllegalArgumentException> { loadout.equip(Gear.AXE) }
        assertThrows<IllegalArgumentException> { Loadout(setOf(Gear.WOODEN_STICK), Gear.AXE) }
    }

    @Test
    fun `a stored choice that isn't unlocked, or isn't known, falls back to the stick`() {
        assertEquals(Gear.WOODEN_SWORD, Loadout.of(setOf(Achievement.FIRST_DAY_IN_GUIDE), "wooden_sword").equipped)
        assertEquals(Gear.WOODEN_STICK, Loadout.of(setOf(Achievement.FIRST_DAY_IN_GUIDE), "legendary_blade").equipped)
        assertEquals(Gear.WOODEN_STICK, Loadout.of(emptySet(), "spoon").equipped)
    }

    @Test
    fun `with nothing stored, weeks of history come out in one summary`() {
        val new = NewUnlocks.since(emptySet(), emptySet(), sixWeeks)
        assertTrue(new.isSummary)
        assertEquals(AchievementRules.unlocked(sixWeeks).toList(), new.achievements)
        assertTrue(
            new.achievements.containsAll(listOf(Achievement.FIRST_ENTRY, Achievement.BASELINE_DONE, Achievement.WEEKS_MET_6)),
        )
        assertEquals(
            listOf(Gear.WOODEN_SWORD, Gear.IRON_SWORD, Gear.AXE, Gear.WAR_HAMMER),
            new.gear,
            "the stick is never news",
        )
    }

    @Test
    fun `only what isn't stored yet is new, and the usual result is a toast or two`() {
        val all = NewUnlocks.since(emptySet(), emptySet(), sixWeeks)
        val stored = (all.achievements - Achievement.WEEKS_MET_6).map { it.key }.toSet()
        val gear = (all.gear - Gear.WAR_HAMMER).map { it.key }.toSet()
        val new = NewUnlocks.since(stored, gear, sixWeeks)
        assertEquals(listOf(Achievement.WEEKS_MET_6), new.achievements)
        assertEquals(listOf(Gear.WAR_HAMMER), new.gear)
        assertFalse(new.isSummary)
        val everything = NewUnlocks.since(all.achievements.map { it.key }.toSet(), all.gear.map { it.key }.toSet(), sixWeeks)
        assertTrue(everything.isEmpty)
        assertEquals(NewUnlocks.NONE, everything)
    }

    @Test
    fun `gear follows every badge stored, so editing old logs never takes a weapon away`() {
        val nothing = GameRecord(PLAN_START)
        val new = NewUnlocks.since(setOf(Achievement.WEEKS_MET_3.key, "a_key_from_elsewhere"), emptySet(), nothing)
        assertEquals(emptyList<Achievement>(), new.achievements)
        assertEquals(listOf(Gear.AXE), new.gear)
    }
}
