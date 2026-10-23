package app.thelasthit.nrt.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.temporal.ChronoUnit

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
    fun `gear comes with its badge or its point in a programme, and the stick is there from the start`() {
        val nothing = GameRecord(PLAN_START)
        assertEquals(setOf(Gear.WOODEN_STICK), Gear.unlockedWith(emptySet(), nothing))
        assertEquals(
            listOf(Gear.WOODEN_STICK, Gear.WOODEN_SWORD, Gear.IRON_SWORD),
            Gear.unlockedWith(setOf(Achievement.FIRST_WEEK_MET, Achievement.FIRST_DAY_IN_GUIDE, Achievement.TAGGED_25), nothing).toList(),
        )
        assertEquals(
            Gear.entries.toSet() - setOf(Gear.WAR_HAMMER, Gear.LIGHTNING_BOW),
            Gear.unlockedWith(Achievement.entries.toSet(), nothing),
            "the war hammer and the lightning bow come with points in a programme",
        )
        assertTrue(Gear.WOODEN_STICK.isStarter)
        val badges = Gear.entries.mapNotNull { it.badge }
        assertEquals(badges.size, badges.toSet().size, "one piece per badge")
    }

    @Test
    fun `the war hammer comes with half a programme's hits, and the lightning bow with its final taper week squared`() {
        fun gear(vararg met: Boolean): Set<Gear> {
            val weeks = WeekRun(EXAMPLE_2_PLAN).apply { met.forEach { result(it) } }.weeks
            return Gear.unlockedWith(emptySet(), GameRecord(PLAN_START.plusWeeks(14), programmes = listOf(programmeOf(weeks))))
        }
        val met = { n: Int -> BooleanArray(n) { true } }
        assertFalse(Gear.WAR_HAMMER in gear(*met(5)))
        assertTrue(Gear.WAR_HAMMER in gear(*met(6)), "6 of 12 hits")
        assertTrue(Gear.WAR_HAMMER in gear(true, false, false, true, true, true), "weeks 2 and 3 paid back by week 4")
        assertFalse(Gear.LIGHTNING_BOW in gear(*met(9)))
        assertTrue(Gear.LIGHTNING_BOW in gear(*met(10)))
        assertFalse(Gear.LIGHTNING_BOW in gear(*met(9), false), "week 10 missed")
        assertTrue(Gear.LIGHTNING_BOW in gear(*met(9), false, true), "week 10 caught up")
    }

    /**
     * Every week met, with each taper day at its guide and each hit spent as it comes, in a
     * programme of [weeks] weeks. Returns the plan day each piece of gear came on.
     */
    private fun gearDays(weeks: Int): Map<Gear, Int> {
        val run = WeekRun(example2Plan(weeks))
        val entries = run.weeks.flatMap { week ->
            week.mix.flatMap { unit -> week.days.map { dose("${it}T10:00", unit.copy(unitsPerDay = 1), unit.unitsPerDay) } }
        }
        var ledger = HitLedger(HitLedger.HP_PER_HIT * weeks)
        while (run.next != null) {
            var t = run.result(met = true).plus(Duration.ofHours(10))
            ledger = ledger.withEarned(programmeOf(run.weeks).earnedHits)
            while (ledger.canHit) ledger = ledger.hit(t).also { t = t.plusSeconds(60) }
        }
        val badges = mutableSetOf<Achievement>()
        val days = linkedMapOf<Gear, Int>()
        var date = PLAN_START
        while (date <= run.weeks.last().endDate.plusDays(2)) {
            val t = startOf(date.plusDays(1)).minusSeconds(1)
            val weeksNow = run.weeks.map { if (it.resultAt!! > t) it.copy(result = GameWeek.Result.PENDING, resultAt = null) else it }
            val programme = programmeOf(weeksNow, ledger = HitLedger(ledger.maxHp, ledger.rows.filter { it.at <= t }))
            val record = GameRecord(date, entries.filter { it.logicalDate <= date }, programmes = listOf(programme))
            badges += AchievementRules.unlocked(record)
            for (gear in Gear.unlockedWith(badges, record)) days.putIfAbsent(gear, ChronoUnit.DAYS.between(PLAN_START, date).toInt())
            date = date.plusDays(1)
        }
        return days
    }

    /** PLAN 8.2: the gear comes roughly in order of difficulty, in each of PLAN 6.2's lengths. */
    @Test
    fun `gear arrives in order in a programme of any length`() {
        assertEquals(EXAMPLE_2_PLAN, example2Plan(12), "the plan fixture matches PLAN's table")
        for (weeks in listOf(8, 12, 16, 24)) {
            val days = gearDays(weeks)
            assertEquals(Gear.entries, days.keys.toList(), "$weeks weeks: every piece comes")
            val arrivals = Gear.entries.drop(1).map { days.getValue(it) }
            assertEquals(arrivals.sorted().distinct(), arrivals, "$weeks weeks: each piece after the one before, on $days")
        }
        // 8 weeks: day 1 in the guide, week 1 met, 3 met, 4 of 8 hits, week 6 reached, then met, and the defeat.
        assertEquals(listOf(0, 2, 8, 22, 29, 35, 43, 57), gearDays(8).values.toList())
    }

    @Test
    fun `only unlocked gear can be equipped`() {
        val loadout = Loadout.of(setOf(Gear.WOODEN_SWORD.key), equippedKey = null)
        assertEquals(Gear.WOODEN_STICK, loadout.equipped)
        assertEquals(Gear.WOODEN_SWORD, loadout.equip(Gear.WOODEN_SWORD).equipped)
        assertFalse(loadout.canEquip(Gear.AXE))
        assertThrows<IllegalArgumentException> { loadout.equip(Gear.AXE) }
        assertThrows<IllegalArgumentException> { Loadout(setOf(Gear.WOODEN_STICK), Gear.AXE) }
    }

    @Test
    fun `a stored choice that isn't unlocked, or isn't known, falls back to the stick`() {
        assertEquals(Gear.WOODEN_SWORD, Loadout.of(setOf("wooden_sword"), "wooden_sword").equipped)
        assertEquals(Gear.WOODEN_STICK, Loadout.of(setOf("wooden_sword"), "legendary_blade").equipped)
        assertEquals(Gear.WOODEN_STICK, Loadout.of(setOf("spoon"), "spoon").equipped)
        assertEquals(setOf(Gear.WOODEN_STICK), Loadout.of(setOf("spoon"), null).unlocked)
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
