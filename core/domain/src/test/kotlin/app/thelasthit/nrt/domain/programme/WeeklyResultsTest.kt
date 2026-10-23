package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeeklyResultsTest {

    private val programme = planExample()

    // Week 1 runs from Saturday 24 to Friday 30 October, with 140 mg. Its last logical day ends
    // at 04:00 on Saturday 31 October, so the result comes in at 04:00 on Sunday 1 November.
    private val due = clockAt("2026-11-01T04:00")

    private fun week1(vararg doses: Pair<String, Dose>): WeekResult {
        val entries = doses.map { (at, total) -> dose(at, total) }
        return programme.settle(entries, due).programme.week(1).result
    }

    @Test
    fun `a week is met at its allowance, and missed a microgram over`() {
        assertEquals(WeekResult.MET, week1("2026-10-26T09:00" to Dose.mg(140)))
        assertEquals(WeekResult.MET, week1())
        assertEquals(WeekResult.MISSED, week1("2026-10-26T09:00" to Dose.mg(140), "2026-10-27T09:00" to Dose(1)))
    }

    @Test
    fun `an NRT-free week is met only with nothing logged`() {
        val nrtFree = programme.week(11)
        assertTrue(nrtFree.isNrtFree)
        assertTrue(nrtFree.isMetBy(Dose.ZERO))
        assertFalse(nrtFree.isMetBy(Dose(1)))
        assertEquals(WeekResult.MISSED, play(programme, "mmmmmmmmmmx").programme.week(11).result)
        assertEquals(WeekResult.MET, play(programme, "mmmmmmmmmmm").programme.week(11).result)
    }

    @Test
    fun `the result comes in 24 hours after the week ends`() {
        val entries = listOf(dose("2026-10-26T09:00", Dose.mg(100)))
        assertEquals(WeekResult.PENDING, programme.settle(entries, clockAt("2026-10-31T03:59")).programme.week(1).result)
        assertEquals(WeekResult.PENDING, programme.settle(entries, clockAt("2026-11-01T03:59")).programme.week(1).result)
        val settled = programme.settle(entries, due).programme.week(1)
        assertEquals(WeekResult.MET, settled.result)
        assertEquals(due.now, settled.resultAt)
        assertEquals(due.now, ProgrammeClock(START.atStartOfDay(ZONE).toInstant(), ZONE).resultDue(programme.week(1)))
    }

    @Test
    fun `24 hours stay 24 hours when the clocks go forward`() {
        // A week from Saturday 20 March 2027 ends at 04:00 GMT on the 27th. The clocks go forward
        // early on the 28th, so its result comes in at 05:00 BST.
        val spring = planExample(start = date("2027-03-20"))
        assertEquals(WeekResult.PENDING, spring.settle(emptyList(), clockAt("2027-03-28T04:59")).programme.week(1).result)
        assertEquals(WeekResult.MET, spring.settle(emptyList(), clockAt("2027-03-28T05:00")).programme.week(1).result)
    }

    @Test
    fun `doses logged late, in the 24 hours after the week, still count`() {
        // On Saturday, a dose from Friday evening is added afterwards.
        val onTime = dose("2026-10-26T09:00", Dose.mg(140))
        val late = dose("2026-10-30T20:00", Dose.mg(2))
        assertEquals(WeekResult.MISSED, programme.settle(listOf(onTime, late), due).programme.week(1).result)
    }

    @Test
    fun `a dose after midnight on the last night counts toward the week`() {
        assertEquals(WeekResult.MISSED, week1("2026-10-26T09:00" to Dose.mg(140), "2026-10-31T01:30" to Dose.mg(2)))
        assertEquals(WeekResult.MET, week1("2026-10-26T09:00" to Dose.mg(140), "2026-10-31T04:30" to Dose.mg(2)))
    }

    @Test
    fun `once a result is in, editing old entries doesn't change it`() {
        val entries = listOf(dose("2026-10-26T09:00", Dose.mg(140)))
        val first = programme.settle(entries, due)
        assertEquals(1, first.newHits.size)
        val later = first.programme.settle(entries + dose("2026-10-27T10:00", Dose.mg(10)), clockAt("2026-11-03T12:00"))
        assertEquals(WeekResult.MET, later.programme.week(1).result)
        assertEquals(due.now, later.programme.week(1).resultAt)
        assertEquals(emptyList<EarnedHit>(), later.newHits)
    }

    @Test
    fun `results depend only on the time and the logs`() {
        // Looking once, weeks later, brings in the same results, timed as they came due.
        val played = play(programme, "mxxm")
        val lookedLate = programme.settle(played.entries, dueFor(played.programme.week(4)))
        assertEquals(played.programme, lookedLate.programme)
        assertEquals(played.hits, lookedLate.newHits)
        // Looking again changes nothing.
        assertEquals(lookedLate.programme, lookedLate.programme.settle(played.entries, dueFor(played.programme.week(4))).programme)
    }

    @Test
    fun `a result counts the entries it's given, so it's settled before a later write`() {
        // Three days after week 1's result was due, a dose is added to its Tuesday.
        val onTime = listOf(dose("2026-10-26T09:00", Dose.mg(140)))
        val backdated = dose("2026-10-27T10:00", Dose.mg(10))
        val later = clockAt("2026-11-04T12:00")
        // Passed in with the rest, it would still count.
        assertEquals(WeekResult.MISSED, programme.settle(onTime + backdated, later).programme.week(1).result)
        // Settled before the write, the result is in and stays as it was.
        val settledFirst = programme.settle(onTime, later).programme
        val afterWrite = settledFirst.settle(onTime + backdated, later)
        assertEquals(WeekResult.MET, afterWrite.programme.week(1).result)
        assertEquals(due.now, afterWrite.programme.week(1).resultAt)
        assertEquals(emptyList<EarnedHit>(), afterWrite.newHits)
    }

    @Test
    fun `weeks come in one at a time, in order`() {
        val entries = listOf(dose("2026-10-26T09:00", Dose.mg(10)))
        val settled = programme.settle(entries, clockAt("2026-11-08T03:59")).programme
        assertEquals(listOf(WeekResult.MET, WeekResult.PENDING), settled.weeks.take(2).map { it.result })
        val next = settled.settle(entries, clockAt("2026-11-08T04:00")).programme
        assertEquals(listOf(WeekResult.MET, WeekResult.MET, WeekResult.PENDING), next.weeks.take(3).map { it.result })
    }

    @Test
    fun `progress through a week counts its logical days`() {
        val week = programme.week(1)
        val entries = listOf(
            dose("2026-10-24T03:30", Dose.mg(4)),
            dose("2026-10-24T09:00", Dose.mg(2)),
            dose("2026-10-31T01:30", Dose.mg(2)),
            dose("2026-10-31T09:00", Dose.mg(2)),
        )
        // The 03:30 dose counts toward the Friday before the start, and the 01:30 one toward the last day.
        assertEquals(Dose.mg(4), week.totalOf(entries))
        assertEquals(programme.week(1), programme.weekOn(date("2026-10-30")))
    }
}
