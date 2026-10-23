package app.thelasthit.nrt.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class HitLedgerTest {

    private val t0: Instant = Instant.parse("2026-11-17T04:00:00Z")
    private fun at(hours: Long): Instant = t0.plusSeconds(hours * 3600)
    private fun met(week: Int, hours: Long = week.toLong()) = HitLedger.Row(HitLedger.Kind.WEEK_MET, at(hours), week)
    private fun catchUp(week: Int, hours: Long) = HitLedger.Row(HitLedger.Kind.CATCH_UP, at(hours), week)

    @Test
    fun `HP is max less 10 for each hit spent, and banked hits are earned less spent`() {
        val ledger = HitLedger(120, listOf(met(1), met(2), met(3))).hit(at(10))
        assertEquals(3, ledger.earned)
        assertEquals(1, ledger.spent)
        assertEquals(2, ledger.banked)
        assertEquals(110, ledger.hp)
        assertEquals(11, ledger.hpBlocks)
        assertFalse(ledger.isDefeated)
    }

    @Test
    fun `a hit needs one banked, and spending never goes past the banked hits`() {
        val empty = HitLedger(120)
        assertFalse(empty.canHit)
        assertThrows<IllegalArgumentException> { empty.hit(t0) }
        val one = HitLedger(120, listOf(met(1))).hit(at(2))
        assertFalse(one.canHit)
        assertThrows<IllegalArgumentException> { one.hit(at(3)) }
        assertThrows<IllegalArgumentException>("rows that spend before earning") {
            HitLedger(120, listOf(HitLedger.Row(HitLedger.Kind.HIT, t0), met(1)))
        }
    }

    @Test
    fun `the 5x attack needs 5 banked and spends them all at once for 50`() {
        val four = HitLedger(120, (1..4).map { met(it) })
        assertFalse(four.canCombo)
        assertThrows<IllegalArgumentException> { four.combo(at(10)) }
        val five = four.copy(rows = four.rows + met(5))
        assertTrue(five.canCombo)
        val after = five.combo(at(10))
        assertEquals(0, after.banked)
        assertEquals(70, after.hp)
        assertEquals(listOf(-5), after.rows.filter { !it.kind.isEarned }.map { it.hits })
        assertThrows<IllegalArgumentException> { after.combo(at(11)) }
    }

    @Test
    fun `the demon falls at exactly 0, when the final hit lands`() {
        var ledger = HitLedger(30, listOf(met(1), met(2), met(3)))
        ledger = ledger.hit(at(10)).hit(at(11))
        assertFalse(ledger.isDefeated)
        assertNull(ledger.defeatedAt)
        ledger = ledger.hit(at(12))
        assertEquals(0, ledger.hp)
        assertTrue(ledger.isDefeated)
        assertEquals(at(12), ledger.defeatedAt)
        assertFalse(ledger.canHit)
    }

    @Test
    fun `a demon with no weeks yet is not defeated`() {
        assertFalse(HitLedger(0).isDefeated)
    }

    @Test
    fun `a week earns one hit at most, and never more than the HP blocks`() {
        assertThrows<IllegalArgumentException> { HitLedger(120, listOf(met(1), met(1, 2))) }
        assertThrows<IllegalArgumentException> { HitLedger(120, listOf(met(1), catchUp(1, 2))) }
        assertThrows<IllegalArgumentException> { HitLedger(20, listOf(met(1), met(2), met(3))) }
        assertThrows<IllegalArgumentException> { HitLedger(125) }
        assertThrows<IllegalArgumentException>("a spend has no week") { HitLedger.Row(HitLedger.Kind.HIT, t0, 3) }
        assertThrows<IllegalArgumentException>("an earned hit pays for a week") { HitLedger.Row(HitLedger.Kind.WEEK_MET, t0) }
    }

    @Test
    fun `adding earned hits again changes nothing`() {
        val ledger = HitLedger(120, listOf(met(1)))
        assertSame(ledger, ledger.withEarned(listOf(met(1))))
        val more = ledger.withEarned(listOf(met(1), met(2), catchUp(3, 4)))
        assertEquals(listOf(1, 2, 3), more.rows.map { it.week })
        assertEquals(more, more.withEarned(listOf(met(2), catchUp(3, 4))))
        assertThrows<IllegalArgumentException> { ledger.withEarned(listOf(HitLedger.Row(HitLedger.Kind.HIT, t0))) }
    }

    @Test
    fun `only weeks earn hits, so no achievement can deal damage`() {
        assertEquals(
            listOf(HitLedger.Kind.WEEK_MET, HitLedger.Kind.CATCH_UP),
            HitLedger.Kind.entries.filter { it.isEarned },
        )
        assertEquals(listOf(-1, -5), HitLedger.Kind.entries.filterNot { it.isEarned }.map { it.hits })
    }

    @Test
    fun `a week met earns its hit, and catch-up hits arrive with the week met that pays them back`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        run.result(met = true)
        run.result(met = false)
        run.result(met = false)
        val fourth = run.result(met = true)
        val programme = programmeOf(run.weeks)
        assertEquals(
            listOf(
                HitLedger.Kind.WEEK_MET to 1,
                HitLedger.Kind.WEEK_MET to 4,
                HitLedger.Kind.CATCH_UP to 2,
                HitLedger.Kind.CATCH_UP to 3,
            ),
            programme.earnedHits.map { it.kind to it.week },
        )
        assertEquals(listOf(fourth, fourth, fourth), programme.earnedHits.drop(1).map { it.at })
        assertEquals(4, programme.unrecordedHits.size)
        val recorded = programme.withEarnedHits()
        assertEquals(4, recorded.ledger.banked)
        assertEquals(emptyList<HitLedger.Row>(), recorded.unrecordedHits)
    }

    @Test
    fun `a repeat adds a week and 10 HP, and overtime adds none`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        run.result(met = false)
        assertEquals(120, programmeOf(run.weeks).maxHp)
        run.repeatLastMissed()
        assertEquals(13, run.weeks.size)
        assertEquals(130, programmeOf(run.weeks).maxHp)
        assertEquals(GameWeek.Kind.REPEAT, run.weeks[1].kind)
        assertEquals(run.weeks[0].mix, run.weeks[1].mix)
        repeat(11) { run.result(met = true) }
        run.result(met = false)
        assertEquals(GameWeek.Kind.OVERTIME, run.weeks.last().kind)
        assertEquals(130, programmeOf(run.weeks).maxHp)
    }

    @Test
    fun `the final taper week is the last planned week with some NRT, even after a repeat of it`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        assertEquals(10, programmeOf(run.weeks).finalTaperWeek?.number)
        repeat(9) { run.result(met = true) }
        run.result(met = false)
        run.repeatLastMissed()
        val programme = programmeOf(run.weeks)
        assertEquals(10, programme.finalTaperWeek?.number)
        assertTrue(programme.weeks[10].kind == GameWeek.Kind.REPEAT && !programme.weeks[10].isNrtFree)
    }

    @Test
    fun `a programme refuses results that couldn't happen`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        run.result(met = true)
        run.result(met = true)
        val weeks = run.weeks
        val due = resultDue(weeks[2])
        assertThrows<IllegalArgumentException>("a missed week before a week met") {
            programmeOf(weeks.toMutableList().also { it[0] = it[0].copy(result = GameWeek.Result.MISSED) })
        }
        assertThrows<IllegalArgumentException>("a caught-up week with nothing met after it") {
            programmeOf(weeks.toMutableList().also { it[2] = it[2].copy(result = GameWeek.Result.CAUGHT_UP, resultAt = due) })
        }
        assertThrows<IllegalArgumentException>("a result after a week still to come") {
            programmeOf(weeks.toMutableList().also { it[3] = it[3].copy(result = GameWeek.Result.MET, resultAt = due) })
        }
        assertThrows<IllegalArgumentException>("weeks that aren't back to back") {
            programmeOf(weeks.toMutableList().also { it[4] = it[4].copy(startDate = it[4].startDate.plusDays(1)) })
        }
        assertThrows<IllegalArgumentException>("a ledger with the wrong HP") { programmeOf(weeks, ledger = HitLedger(130)) }
        assertThrows<IllegalArgumentException>("a ledger with a hit no week earned") {
            programmeOf(weeks, ledger = HitLedger(120, listOf(met(3))))
        }
    }
}
