package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgrammeFactsTest {

    /** PLAN's third example: 8 pieces of 4 mg gum and 3 sprays, 12 weeks. */
    private val mixed = Programme.create(ladder(usual(GUM_4 to 8, SPRAY to 3)), ProgrammeLength.WEEKS_12, START)

    @Test
    fun `the switch to 2 mg gum is marked in its own week, as a new product`() {
        assertEquals(listOf(Dose.mg(27), Dose.mg(19), Dose.mg(19)), mixed.guides.take(3))
        assertEquals(listOf(StrengthSwitch(3, GUM_2, isNew = true)), mixed.switches())
    }

    @Test
    fun `a lower strength already in the user's products isn't new`() {
        val own = Programme.create(ladder(usual(GUM_4 to 8, SPRAY to 3), own = listOf(GUM_4, GUM_2, SPRAY)), ProgrammeLength.WEEKS_12, START)
        assertEquals(listOf(StrengthSwitch(3, GUM_2, isNew = false)), own.switches())
    }

    @Test
    fun `patches switch at each strength`() {
        val patches = Programme.create(ladder(usual(PATCH_21 to 1)), ProgrammeLength.WEEKS_8, START)
        assertEquals(listOf(PATCH_14, PATCH_7), patches.switches().map { it.product })
    }

    @Test
    fun `a plan with nothing lower has no switches`() {
        assertTrue(planExample().switches().isEmpty())
    }

    @Test
    fun `two steps a week while the dose is high, and one at the switch`() {
        assertEquals(mapOf(1 to 2, 2 to 2, 3 to 1, 4 to 2, 5 to 2, 6 to 2, 7 to 1, 8 to 1, 9 to 1, 10 to 1), mixed.stepsTaken())
        // 12 pieces down to 1 is 11 steps for 10 weeks, so week 1 takes two.
        assertEquals(mapOf(1 to 2) + (2..10).associateWith { 1 }, planExample().stepsTaken())
    }

    @Test
    fun `a met week pays back the missed weeks before it`() {
        val played = play(planExample(), "xxmxm").programme
        assertEquals(listOf(1, 2), played.paidBackBy(3))
        assertEquals(listOf(4), played.paidBackBy(5))
        assertTrue(played.paidBackBy(1).isEmpty())
        assertEquals(3, played.caughtUpIn(1))
        assertEquals(5, played.caughtUpIn(4))
        assertNull(played.caughtUpIn(3))
        assertEquals(3, played.hitsEarnedBy(3))
        assertEquals(0, played.hitsEarnedBy(4))
    }

    @Test
    fun `missed weeks are owed until a met one pays them back`() {
        val played = play(planExample(), "mxx").programme
        assertEquals(listOf(2, 3), played.owed)
        assertTrue(play(planExample(), "mxxm").programme.owed.isEmpty())
    }

    @Test
    fun `overtime owes the missed final week, with no hit of its own`() {
        val overtime = play(planExample(), "mmmmmmmmmmmx").programme
        assertEquals(listOf(12), overtime.owed)
        assertEquals(12, overtime.plannedWeeks)
        assertEquals(1, overtime.overtimeWeeks)
        val stillOver = play(overtime, "x").programme
        assertEquals(listOf(12), stillOver.owed)
        assertEquals(listOf(12, 13), stillOver.unpaid)
    }

    @Test
    fun `a week waits for its result for a day after it ends`() {
        val week1 = planExample().week(1)
        assertNull(planExample().awaitingResult(clockAt("2026-10-30T20:00")))
        assertEquals(week1, planExample().awaitingResult(clockAt("2026-10-31T09:00")))
        val settled = planExample().settle(meeting(week1), dueFor(week1)).programme
        assertNull(settled.awaitingResult(dueFor(week1)))
    }

    @Test
    fun `days of a week count from 1`() {
        val week = planExample().week(1)
        assertEquals(1, week.dayOf(START))
        assertEquals(7, week.dayOf(date("2026-10-30")))
        assertFalse(week.dayOf(date("2026-10-31")) in 1..7)
    }
}
