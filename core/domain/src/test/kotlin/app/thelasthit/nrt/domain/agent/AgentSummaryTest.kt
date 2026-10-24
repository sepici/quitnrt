package app.thelasthit.nrt.domain.agent

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.GUM_2
import app.thelasthit.nrt.domain.programme.GUM_4
import app.thelasthit.nrt.domain.programme.PATCH_21
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.date
import app.thelasthit.nrt.domain.programme.planExample
import app.thelasthit.nrt.domain.programme.use
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSummaryTest {

    /** Friday 23 October 2026, as in the app's tests. */
    private val today = date("2026-10-23")

    private val week = listOf(
        use("2026-10-19T08:00", GUM_4, 2),
        use("2026-10-20T07:30", PATCH_21),
        use("2026-10-21T09:00", GUM_2, 3),
        use("2026-10-22T21:40", GUM_4),
        // 01:20 on Friday counts towards Thursday.
        use("2026-10-23T01:20", GUM_2),
        use("2026-10-23T07:30", PATCH_21),
        use("2026-10-23T10:55", GUM_2),
        use("2026-10-23T13:40", GUM_2, 2),
    )

    @Test
    fun `with no plan, today and Monday to Sunday, with no guide or allowance`() {
        val summary = AgentSummary.of(today, week, PlanStage.NONE, plan = null, demon = null)

        assertEquals(Dose.mg(21 + 2 + 4), summary.todayTotal)
        assertEquals(3, summary.todayDoses)
        assertNull(summary.dailyGuide)
        assertNull(summary.withinGuide)
        assertEquals(AgentWeek(null, date("2026-10-19"), date("2026-10-25"), Dose.mg(8 + 21 + 6 + 4 + 2 + 21 + 2 + 4), allowance = null), summary.week)
        assertNull(summary.week.withinAllowance)
        assertFalse(summary.week.isNrtFree)
        assertNull(summary.demon)
    }

    @Test
    fun `the last dose leaves out patches, and can be from an earlier day`() {
        assertEquals(week.last(), AgentSummary.of(today, week, PlanStage.NONE, null, null).lastDose)

        val patchOnly = listOf(use("2026-10-21T09:00", GUM_4), use("2026-10-23T07:30", PATCH_21))
        assertEquals(patchOnly.first(), AgentSummary.of(today, patchOnly, PlanStage.NONE, null, null).lastDose)
    }

    @Test
    fun `entries from before the 30 days it looks at are left out`() {
        val old = listOf(use("2026-09-22T09:00", GUM_4), use("2026-09-23T09:00", GUM_2))
        val summary = AgentSummary.of(today, old, PlanStage.NONE, null, null)
        assertEquals(old.last(), summary.lastDose)
        assertNull(AgentSummary.of(today, old.take(1), PlanStage.NONE, null, null).lastDose)
        assertEquals(date("2026-09-23"), AgentSummary.from(today))
    }

    @Test
    fun `while a plan runs, today's guide and the programme week's allowance`() {
        // Started on Wednesday, so week 1 is Wednesday 21 to Tuesday 27 October.
        val plan = planExample(start = date("2026-10-21"))
        val first = plan.week(1)
        val demon = AgentDemon("Gary", hp = 120, maxHp = 120, bankedHits = 0)
        val summary = AgentSummary.of(today, week, PlanStage.ACTIVE, plan, demon)

        assertEquals(first.target.dailyGuide, summary.dailyGuide)
        assertEquals(AgentWeek(1, date("2026-10-21"), date("2026-10-27"), Dose.mg(6 + 4 + 2 + 21 + 2 + 4), first.target.weeklyAllowance), summary.week)
        assertEquals(summary.todayTotal <= first.target.dailyGuide, summary.withinGuide)
        assertEquals(true, summary.week.withinAllowance)
        assertEquals(demon, summary.demon)
    }

    @Test
    fun `over the guide or the allowance says so`() {
        val plan = planExample(start = date("2026-10-21"))
        val guide = plan.week(1).target.dailyGuide
        val heavy = listOf(use("2026-10-23T09:00", GUM_4, (guide.micrograms / 4000 + 1).toInt()))
        val summary = AgentSummary.of(today, heavy, PlanStage.ACTIVE, plan, null)
        assertEquals(false, summary.withinGuide)
        assertEquals(true, summary.week.withinAllowance)

        val heavier = List(8) { use("2026-10-2${1 + it % 3}T0$it:00", GUM_4, 15) }
        assertEquals(false, AgentSummary.of(today, heavier, PlanStage.ACTIVE, plan, null).week.withinAllowance)
    }

    @Test
    fun `before the plan's first day, it's the calendar week with no guide`() {
        val plan = planExample(start = date("2026-10-24"))
        val summary = AgentSummary.of(today, week, PlanStage.ACTIVE, plan, null)
        assertNull(summary.dailyGuide)
        assertNull(summary.week.number)
        assertEquals(date("2026-10-19"), summary.week.start)
    }

    @Test
    fun `an NRT-free week has a guide and allowance of zero`() {
        // Ten weeks in, today falls in week 11, the first NRT-free week.
        val plan = planExample(start = date("2026-08-14"))
        val summary = AgentSummary.of(today, week, PlanStage.ACTIVE, plan, null)
        assertEquals(11, summary.week.number)
        assertEquals(Dose.ZERO, summary.dailyGuide)
        assertTrue(summary.week.isNrtFree)
        assertEquals(false, summary.withinGuide)
        assertEquals(true, AgentSummary.of(today, emptyList(), PlanStage.ACTIVE, plan, null).withinGuide)
    }

    @Test
    fun `a baseline or a finished plan has no guide`() {
        val plan = planExample(start = date("2026-10-21"))
        assertNull(AgentSummary.of(today, week, PlanStage.BASELINE, null, null).dailyGuide)
        assertNull(AgentSummary.of(today, week, PlanStage.COMPLETE, plan, null).dailyGuide)
    }

    @Test
    fun `stages from the programme's status`() {
        assertEquals(PlanStage.NONE, PlanStage.of(null))
        assertEquals(PlanStage.NONE, PlanStage.of(ProgrammeStatus.ABANDONED))
        assertEquals(PlanStage.BASELINE, PlanStage.of(ProgrammeStatus.BASELINE))
        assertEquals(PlanStage.ACTIVE, PlanStage.of(ProgrammeStatus.ACTIVE))
        assertEquals(PlanStage.OVERTIME, PlanStage.of(ProgrammeStatus.OVERTIME))
        assertEquals(PlanStage.COMPLETE, PlanStage.of(ProgrammeStatus.COMPLETE))
    }
}
