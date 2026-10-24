package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.domain.programme.date
import app.thelasthit.nrt.domain.programme.dose
import app.thelasthit.nrt.domain.programme.planExample
import app.thelasthit.nrt.domain.programme.play
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SlipsTest {

    // PLAN's first example from Saturday 24 October: a 20 mg guide in week 1 (24 to 30 October),
    // then 18 mg in week 2 (31 October to 6 November).
    private val plan = planExample()

    @Test
    fun `no plan, or one that hasn't started, has no slips`() {
        val over = listOf(dose("2026-10-22T12:00", Dose.mg(40)))
        assertNull(Slips.onToday(null, date("2026-10-23"), over))
        assertNull(Slips.onToday(plan, date("2026-10-23"), over))
        assertNull(Slips.onToday(plan, date("2026-10-24"), listOf(dose("2026-10-23T12:00", Dose.mg(40)))))
        assertNull(Slips.afterLog(null, over.single(), over, date("2026-10-22")))
    }

    @Test
    fun `yesterday over its guide is a slip on today, and at the guide isn't`() {
        val today = date("2026-10-27")
        assertEquals(Slip.OVER_GUIDE, Slips.onToday(plan, today, listOf(dose("2026-10-26T09:00", Dose.mg(12)), dose("2026-10-26T20:00", Dose.mg(9)))))
        assertNull(Slips.onToday(plan, today, listOf(dose("2026-10-26T09:00", Dose.mg(12)), dose("2026-10-26T20:00", Dose.mg(8)))))
        // A dose after midnight counts towards the evening before, with the 04:00 day start.
        assertEquals(Slip.OVER_GUIDE, Slips.onToday(plan, today, listOf(dose("2026-10-26T12:00", Dose.mg(20)), dose("2026-10-27T01:30", Dose.mg(2)))))
    }

    @Test
    fun `today's doses don't change today's slip, which the date settles`() {
        val today = date("2026-10-27")
        assertNull(Slips.onToday(plan, today, listOf(dose("2026-10-27T09:00", Dose.mg(40)))))
        assertNull(Slips.onToday(plan, today, listOf(dose("2026-10-25T09:00", Dose.mg(40)))))
    }

    @Test
    fun `a missed week is a slip through the week after it, once its result is in`() {
        val missed = play(plan, "x").programme
        assertEquals(WeekResult.MISSED, missed.week(1).result)

        assertEquals(Slip.MISSED_WEEK, Slips.onToday(missed, date("2026-10-31"), emptyList()))
        assertEquals(Slip.MISSED_WEEK, Slips.onToday(missed, date("2026-11-06"), emptyList()))
        assertNull(Slips.onToday(missed, date("2026-11-07"), emptyList()))
        // Before its result comes in, the week isn't missed yet.
        assertNull(Slips.onToday(plan, date("2026-10-31"), emptyList()))
    }

    @Test
    fun `a missed week comes before a day over the guide`() {
        val missed = play(plan, "x").programme
        assertEquals(Slip.MISSED_WEEK, Slips.onToday(missed, date("2026-11-02"), listOf(dose("2026-11-01T12:00", Dose.mg(30)))))
    }

    @Test
    fun `a week caught up since is no longer a slip, and a met week never was`() {
        val caughtUp = play(plan, "xm").programme
        assertEquals(WeekResult.CAUGHT_UP, caughtUp.week(1).result)
        assertNull(Slips.onToday(caughtUp, date("2026-11-08"), emptyList()))

        val met = play(plan, "m").programme
        assertNull(Slips.onToday(met, date("2026-11-01"), emptyList()))
    }

    @Test
    fun `a dose that takes today over its guide is a slip straight after it's logged`() {
        val today = date("2026-10-27")
        val morning = dose("2026-10-27T09:00", Dose.mg(16))
        val crossing = dose("2026-10-27T15:00", Dose.mg(6))
        assertEquals(Slip.OVER_GUIDE, Slips.afterLog(plan, crossing, listOf(morning, crossing), today))
        // The dose is counted even when the day it's given doesn't have it yet.
        assertEquals(Slip.OVER_GUIDE, Slips.afterLog(plan, crossing, listOf(morning), today))

        val within = dose("2026-10-27T15:00", Dose.mg(4))
        assertNull(Slips.afterLog(plan, within, listOf(morning, within), today))
    }

    @Test
    fun `a dose logged for another day brings no slip`() {
        val yesterday = dose("2026-10-26T15:00", Dose.mg(30))
        assertNull(Slips.afterLog(plan, yesterday, listOf(yesterday), date("2026-10-27")))
    }
}
