package app.thelasthit.nrt.domain.reminders

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.date
import app.thelasthit.nrt.domain.programme.dose
import app.thelasthit.nrt.domain.programme.planExample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AllowanceNudgeTest {

    // PLAN's first example: week 1 runs from Saturday 24 to Friday 30 October, with a 20 mg guide
    // and 140 mg for the week, so 80% is 112 mg.
    private val plan = planExample()
    private val week1 = plan.week(1)
    private val tuesday = date("2026-10-27")

    @BeforeEach
    fun checkThePlan() {
        assertEquals(Dose.mg(20), week1.target.dailyGuide)
        assertEquals(Dose.mg(140), week1.target.weeklyAllowance)
    }

    /** [entry] logged on top of [before], with Tuesday as today unless said otherwise. */
    private fun nudge(entry: Entry, before: List<Entry>, today: java.time.LocalDate = tuesday, status: ProgrammeStatus = ProgrammeStatus.ACTIVE) =
        AllowanceNudge.forLog(entry, plan, status, before + entry, today)

    private fun mg(amount: Int) = Dose.mg(amount)

    @Test
    fun `a log that takes the week past 80 percent`() {
        val before = listOf(dose("2026-10-25T09:00", mg(100)), dose("2026-10-26T09:00", mg(10)))
        val nudge = nudge(dose("2026-10-27T09:00", mg(4)), before)
        assertEquals(AllowanceNudge.WeekNear(week1, mg(114), left = mg(26)), nudge)
    }

    @Test
    fun `the 80 percent nudge comes once, on the log that crosses`() {
        val before = listOf(dose("2026-10-25T09:00", mg(113)))
        assertNull(nudge(dose("2026-10-27T09:00", mg(2)), before))
    }

    @Test
    fun `reaching the allowance exactly is still within it`() {
        val before = listOf(dose("2026-10-25T09:00", mg(110)))
        assertEquals(AllowanceNudge.WeekNear(week1, mg(140), left = Dose.ZERO), nudge(dose("2026-10-26T09:00", mg(30)), before))
        assertNull(nudge(dose("2026-10-26T09:00", mg(4)), listOf(dose("2026-10-25T09:00", mg(136)))))
    }

    @Test
    fun `a log that takes the week past its allowance`() {
        val before = listOf(dose("2026-10-25T09:00", mg(138)))
        assertEquals(AllowanceNudge.WeekOver(week1, mg(142)), nudge(dose("2026-10-27T09:00", mg(4)), before))
    }

    @Test
    fun `one log past both lines brings the bigger nudge`() {
        val before = listOf(dose("2026-10-25T09:00", mg(100)))
        assertEquals(AllowanceNudge.WeekOver(week1, mg(150)), nudge(dose("2026-10-27T09:00", mg(50)), before))
    }

    @Test
    fun `a log that takes today past the guide`() {
        val before = listOf(dose("2026-10-25T09:00", mg(50)), dose("2026-10-27T08:00", mg(18)))
        assertEquals(AllowanceNudge.DayOver(mg(22), mg(20), weekLeft = mg(68)), nudge(dose("2026-10-27T13:00", mg(4)), before))
        // Exactly the guide is within it.
        assertNull(nudge(dose("2026-10-27T13:00", mg(2)), before))
    }

    @Test
    fun `once the week is past its allowance, the nudges stop for the week`() {
        val before = listOf(dose("2026-10-25T09:00", mg(141)), dose("2026-10-27T08:00", mg(18)))
        assertNull(nudge(dose("2026-10-27T13:00", mg(4)), before))
    }

    @Test
    fun `a dose logged for an earlier day counts towards the week but not today`() {
        val before = listOf(dose("2026-10-25T09:00", mg(50)), dose("2026-10-27T08:00", mg(18)))
        assertNull(nudge(dose("2026-10-26T20:00", mg(4)), before))
        val near = listOf(dose("2026-10-25T09:00", mg(110)))
        assertEquals(AllowanceNudge.WeekNear(week1, mg(114), left = mg(26)), nudge(dose("2026-10-26T20:00", mg(4)), near))
    }

    @Test
    fun `a dose after midnight counts towards the evening before`() {
        // 01:30 on Wednesday counts towards Tuesday, which is still today until 04:00.
        val before = listOf(dose("2026-10-27T08:00", mg(18)))
        assertEquals(AllowanceNudge.DayOver(mg(22), mg(20), weekLeft = mg(118)), nudge(dose("2026-10-28T01:30", mg(4)), before))
    }

    @Test
    fun `in an NRT-free week, the first dose is past the allowance and the rest bring nothing`() {
        val nrtFree = plan.week(11)
        val today = nrtFree.startDate.plusDays(2)
        val first = dose(today.atTime(10, 0), mg(2))
        assertEquals(AllowanceNudge.WeekOver(nrtFree, mg(2)), AllowanceNudge.forLog(first, plan, ProgrammeStatus.ACTIVE, listOf(first), today))
        val second = dose(today.atTime(12, 0), mg(2))
        assertNull(AllowanceNudge.forLog(second, plan, ProgrammeStatus.ACTIVE, listOf(first, second), today))
    }

    @Test
    fun `a late log for last week, in its grace period, brings nothing`() {
        val saturday = date("2026-10-31")
        val before = listOf(dose("2026-10-25T09:00", mg(138)))
        assertNull(nudge(dose("2026-10-30T20:00", mg(4)), before, today = saturday))
    }

    @Test
    fun `nothing without a running plan`() {
        val before = listOf(dose("2026-10-25T09:00", mg(138)))
        val entry = dose("2026-10-27T09:00", mg(4))
        assertNull(nudge(entry, before, status = ProgrammeStatus.BASELINE))
        assertNull(nudge(entry, before, status = ProgrammeStatus.COMPLETE))
        assertNull(AllowanceNudge.forLog(entry, null, ProgrammeStatus.ACTIVE, before + entry, tuesday))
        // The day before the plan starts.
        val friday = dose("2026-10-23T09:00", mg(30))
        assertNull(nudge(friday, emptyList(), today = date("2026-10-23")))
    }

    @Test
    fun `today's guide comes from the running week`() {
        assertEquals(mg(20), TodayGuide.on(plan, ProgrammeStatus.ACTIVE, tuesday))
        assertEquals(Dose.ZERO, TodayGuide.on(plan, ProgrammeStatus.OVERTIME, plan.week(12).startDate))
        // The day after the final week, while its result is due, as Today has it.
        assertEquals(Dose.ZERO, TodayGuide.on(plan, ProgrammeStatus.ACTIVE, date("2027-01-16")))
        assertNull(TodayGuide.on(plan, ProgrammeStatus.ACTIVE, date("2026-10-23")))
        assertNull(TodayGuide.on(plan, ProgrammeStatus.BASELINE, tuesday))
        assertNull(TodayGuide.on(plan, ProgrammeStatus.COMPLETE, tuesday))
        assertNull(TodayGuide.on(null, null, tuesday))
    }
}
