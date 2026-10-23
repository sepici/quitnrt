package app.thelasthit.nrt.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * PLAN 20's two 12-week runs: one where every week is met, and one with misses, catch-up, a
 * repeat and overtime. Both end with the demon at exactly 0 HP once the last result is in and
 * every hit earned is spent, and not before.
 */
class TwelveWeekRunsTest {

    /** Earns what the results have earned, then spends as a player might: a hit at a time, or 5× when saving up. */
    private class Player(var ledger: HitLedger, private val saveForCombo: Set<Int> = emptySet()) {
        val hpAfterEachResult = mutableListOf<Int>()

        fun after(programme: GameProgramme, weekNumber: Int, at: java.time.Instant) {
            ledger = programme.copy(ledger = ledger).withEarnedHits().ledger
            var t = at.plus(Duration.ofHours(10))
            if (ledger.canCombo) {
                ledger = ledger.combo(t)
                t = t.plusSeconds(60)
            }
            if (weekNumber !in saveForCombo) {
                while (ledger.canHit) {
                    ledger = ledger.hit(t)
                    t = t.plusSeconds(60)
                }
            }
            hpAfterEachResult += ledger.hp
        }
    }

    @Test
    fun `every week met takes the demon to exactly 0 on the final week`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        val player = Player(HitLedger(120), saveForCombo = setOf(3, 4, 5, 6))
        while (run.next != null) {
            val week = run.next!!
            val at = run.result(met = true)
            player.after(programmeOf(run.weeks), week.number, at)
            if (week.number < 12) assertFalse(player.ledger.isDefeated, "still standing after week ${week.number}")
        }
        assertEquals(12, run.weeks.size)
        assertEquals(listOf(110, 100, 100, 100, 100, 100, 50, 40, 30, 20, 10, 0), player.hpAfterEachResult)
        assertEquals(12, player.ledger.earned)
        assertEquals(0, player.ledger.hp)
        assertTrue(player.ledger.isDefeated)
        assertTrue(player.ledger.defeatedAt!! > resultDue(run.weeks.last()))
        assertEquals(1, player.ledger.rows.count { it.kind == HitLedger.Kind.COMBO })
    }

    @Test
    fun `a run with misses, catch-up, a repeat and overtime also ends at exactly 0`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        val player = Player(HitLedger(120))
        fun result(met: Boolean) {
            val week = run.next!!
            val at = run.result(met)
            val programme = programmeOf(run.weeks)
            if (player.ledger.maxHp != programme.maxHp) player.ledger = player.ledger.copy(maxHp = programme.maxHp)
            player.after(programme, week.number, at)
            if (!(met && run.next == null)) assertFalse(player.ledger.isDefeated, "still standing after week ${week.number}")
        }
        result(met = true)
        result(met = true)
        result(met = false) // week 3: carry on
        result(met = true) // week 4 pays back week 3
        result(met = false) // week 5: repeat it
        run.repeatLastMissed()
        player.ledger = player.ledger.copy(maxHp = programmeOf(run.weeks).maxHp)
        assertEquals(130, player.ledger.maxHp)
        while (run.next != run.weeks.last()) result(met = true) // the repeat pays back week 5
        result(met = false) // the final NRT-free week is missed, so overtime starts
        assertEquals(GameWeek.Kind.OVERTIME, run.weeks.last().kind)
        assertEquals(10, player.ledger.hp, "the missed final week still has its hit on offer")
        result(met = true) // overtime met pays back the final week

        val programme = programmeOf(run.weeks, ledger = player.ledger)
        assertEquals(14, programme.weeks.size)
        assertEquals(13, programme.hitsOnOffer)
        assertEquals(
            listOf(3, 5, 13),
            programme.weeks.filter { it.result == GameWeek.Result.CAUGHT_UP }.map { it.number },
        )
        assertEquals(13, player.ledger.earned)
        assertEquals(3, player.ledger.rows.count { it.kind == HitLedger.Kind.CATCH_UP })
        assertEquals(0, player.ledger.hp)
        assertTrue(player.ledger.isDefeated)
        assertTrue(AchievementRules.holds(Achievement.DEMON_DEFEATED, GameRecord(today = run.weeks.last().endDate.plusDays(2), programmes = listOf(programme))))
    }
}
