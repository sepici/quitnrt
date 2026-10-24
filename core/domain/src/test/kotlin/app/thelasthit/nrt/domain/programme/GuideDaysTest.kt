package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuideDaysTest {

    // PLAN's first example from Saturday 24 October: guides of 20, 18 and so on down to 2 mg,
    // then two NRT-free weeks, the last ending on Friday 15 January.
    private val programme = planExample()

    @Test
    fun `a day within or over its guide`() {
        val under = AgainstGuide(Dose.mg(8), Dose.mg(14))
        assertTrue(under.isWithin)
        assertEquals(Dose.mg(6), under.left)
        assertEquals(Dose.ZERO, under.over)

        val at = AgainstGuide(Dose.mg(14), Dose.mg(14))
        assertTrue(at.isWithin)
        assertEquals(Dose.ZERO, at.left)

        val over = AgainstGuide(Dose(14_001), Dose.mg(14))
        assertFalse(over.isWithin)
        assertEquals(Dose(1), over.over)
        assertEquals(Dose.ZERO, over.left)
    }

    @Test
    fun `each day has its week's guide, and none before the plan starts`() {
        assertNull(programme.guideOn(date("2026-10-23")))
        assertEquals(Dose.mg(20), programme.guideOn(date("2026-10-24")))
        assertEquals(Dose.mg(20), programme.guideOn(date("2026-10-30")))
        assertEquals(Dose.mg(18), programme.guideOn(date("2026-10-31")))
        assertEquals(Dose.ZERO, programme.guideOn(date("2027-01-15")))
    }

    @Test
    fun `after the final week the guide stays at nothing until its result is in`() {
        // The day after week 12, its result hasn't come in: missing it would start overtime today.
        val waiting = checkNotNull(programme.guideWeekOn(date("2027-01-16")))
        assertEquals(13, waiting.number)
        assertEquals(WeekKind.OVERTIME, waiting.kind)
        assertEquals(Dose.ZERO, waiting.target.dailyGuide)

        // Once it's met, the programme is complete and the days after it have no guide.
        val done = play(programme, "mmmmmmmmmmmm").programme
        assertEquals(ProgrammeStatus.COMPLETE, done.status)
        assertNull(done.guideOn(date("2027-01-16")))

        // Missed, overtime is one of the weeks, from the same day.
        val missed = play(programme, "mmmmmmmmmmmx").programme
        assertEquals(missed.weeks.last(), missed.guideWeekOn(date("2027-01-16")))
    }

    @Test
    fun `a repeat moves the guide on a week`() {
        val repeated = play(programme, "r").programme
        assertEquals(Dose.mg(20), repeated.guideOn(date("2026-10-31")))
        assertEquals(Dose.mg(18), repeated.guideOn(date("2026-11-07")))
    }

    @Test
    fun `only a finished day within its guide gets a tick`() {
        val today = date("2026-10-28")
        assertTrue(programme.isWithinGuide(date("2026-10-27"), Dose.mg(20), today))
        assertFalse(programme.isWithinGuide(date("2026-10-27"), Dose(20_001), today))
        // Today isn't over, and the day before the plan has no guide.
        assertFalse(programme.isWithinGuide(today, Dose.mg(2), today))
        assertFalse(programme.isWithinGuide(date("2026-10-23"), Dose.ZERO, today))
        // A day with nothing logged is within it.
        assertTrue(programme.isWithinGuide(date("2026-10-26"), Dose.ZERO, today))
    }

    @Test
    fun `today in the plan`() {
        assertNull(PlanToday.of(programme, date("2026-10-23")))

        val first = checkNotNull(PlanToday.of(programme, date("2026-10-24")))
        assertEquals(1, first.week.number)
        assertEquals(1, first.day)
        assertEquals(12, first.plannedWeeks)
        assertEquals(Dose.mg(20), first.guide)
        assertEquals(Dose.mg(140), first.allowance)
        assertNull(first.awaiting)
        assertEquals(Dose.mg(20), first.scale)

        // Thursday 19 November is day 6 of week 4, from Saturday 14 November, at 14 mg a day.
        val fourth = checkNotNull(PlanToday.of(programme, date("2026-11-19")))
        assertEquals(4, fourth.week.number)
        assertEquals(6, fourth.day)
        assertEquals(Dose.mg(14), fourth.guide)
        val week = fourth.week(Dose.mg(61))
        assertEquals(Dose.mg(37), week.left)
        assertEquals(Dose.mg(6), fourth.today(Dose.mg(8)).left)
    }

    @Test
    fun `a repeat adds a week to the count, and overtime doesn't`() {
        val repeated = play(programme, "r").programme
        assertEquals(13, checkNotNull(PlanToday.of(repeated, date("2026-11-02"))).plannedWeeks)

        val overtime = play(programme, "mmmmmmmmmmmx").programme
        val today = checkNotNull(PlanToday.of(overtime, date("2027-01-18")))
        assertTrue(today.isOvertime)
        assertTrue(today.isNrtFree)
        assertEquals(12, today.plannedWeeks)
        assertEquals(3, today.day)
        assertNull(today.awaiting)
    }

    @Test
    fun `waiting for the final result, today is the overtime that would follow`() {
        val waiting = checkNotNull(PlanToday.of(programme, date("2027-01-16")))
        assertEquals(12, waiting.awaiting?.number)
        assertEquals(WeekKind.PLANNED, waiting.awaiting?.kind)
        assertTrue(waiting.isOvertime)
        assertEquals(1, waiting.day)
        assertEquals(Dose.ZERO, waiting.guide)
    }

    @Test
    fun `waiting for an overtime week's result, it's overtime that's awaited`() {
        // Week 12 missed, so overtime runs from 16 to 22 January. The day after, its result is due.
        val overtime = play(programme, "mmmmmmmmmmmx").programme
        val waiting = checkNotNull(PlanToday.of(overtime, date("2027-01-23")))
        assertEquals(13, waiting.awaiting?.number)
        assertEquals(WeekKind.OVERTIME, waiting.awaiting?.kind)
        assertEquals(1, overtime.overtimeNumber(checkNotNull(waiting.awaiting)))
        // Today is the overtime that would follow, the second.
        assertEquals(14, waiting.week.number)
        assertEquals(2, overtime.overtimeNumber(waiting.week))
        assertNull(overtime.overtimeNumber(overtime.week(12)))
    }

    @Test
    fun `an NRT-free week is drawn against the usual day, so a slip draws small`() {
        val nrtFree = checkNotNull(PlanToday.of(programme, date("2027-01-04")))
        assertTrue(nrtFree.isNrtFree)
        assertEquals(Dose.ZERO, nrtFree.guide)
        // 12 pieces of 2 mg gum.
        assertEquals(Dose.mg(24), nrtFree.usualDay)
        assertEquals(Dose.mg(24), nrtFree.scale)
        assertEquals(Dose.mg(14), checkNotNull(PlanToday.of(programme, date("2026-11-19"))).scale)
    }

    @Test
    fun `a plan from a Saturday has weeks from Saturdays`() {
        val today = checkNotNull(PlanToday.of(programme, date("2026-10-30")))
        assertEquals(date("2026-10-24"), today.week.startDate)
        assertEquals(7, today.day)
        assertNotNull(programme.weekOn(date("2026-10-30")))
    }
}
