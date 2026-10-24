package app.thelasthit.nrt.domain.programme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

class ExtraGoalsTest {

    /** PLAN's third example: 8 pieces of 4 mg gum and 3 sprays, 12 weeks from Saturday 24 October. */
    private val plan = Programme.create(ladder(usual(GUM_4 to 8, SPRAY to 3)), ProgrammeLength.WEEKS_12, START)

    private val goals = WeekGoals(
        maxUnitsPerDay = mapOf(GUM_4.id to 5, SPRAY.id to 4),
        minGapMinutes = 90,
        firstDoseNotBefore = LocalTime.of(9, 0),
    )

    @Test
    fun `the goals are set for the week running, or week 1 before the plan starts`() {
        assertEquals(1, ExtraGoals.weekToSet(plan, date("2026-10-23"))?.number)
        assertEquals(1, ExtraGoals.weekToSet(plan, START)?.number)
        assertEquals(4, ExtraGoals.weekToSet(plan, date("2026-11-18"))?.number)
        // NRT-free weeks have nothing to pace, and after the last week there's nothing to set.
        assertNull(ExtraGoals.weekToSet(plan, date("2027-01-05")))
        assertNull(ExtraGoals.weekToSet(plan, date("2027-02-01")))
    }

    @Test
    fun `goals carry into every later taper week, and past weeks keep their own`() {
        val earlier = WeekGoals(minGapMinutes = 60)
        val set = ExtraGoals.apply(ExtraGoals.apply(plan, 1, earlier), 2, goals)

        assertEquals(earlier, set.week(1).goals)
        assertEquals(goals, set.week(2).goals)
        assertEquals((3..10).map { 90 }, set.weeks.subList(2, 10).map { it.goals.minGapMinutes })
        assertEquals(WeekGoals.NONE, set.week(11).goals)
        assertEquals(WeekGoals.NONE, set.week(12).goals)
    }

    @Test
    fun `the first dose moves 15 minutes later each week`() {
        val set = ExtraGoals.apply(plan, 1, goals)
        assertEquals(
            listOf("09:00", "09:15", "09:30", "09:45", "10:00", "10:15", "10:30", "10:45", "11:00", "11:15").map(LocalTime::parse),
            set.weeks.take(10).map { it.goals.firstDoseNotBefore },
        )
    }

    @Test
    fun `the first dose never moves past midnight`() {
        val late = ExtraGoals.apply(plan, 1, WeekGoals(firstDoseNotBefore = LocalTime.of(23, 0)))
        assertEquals(LocalTime.of(23, 45), late.week(10).goals.firstDoseNotBefore)
        assertEquals(LocalTime.of(23, 45), ExtraGoals.later(LocalTime.of(23, 30), 4))
        assertEquals(LocalTime.of(8, 30), ExtraGoals.later(LocalTime.of(8, 0), 2))
    }

    @Test
    fun `a cap carries with its product, and a lower strength starts at its own mix`() {
        val set = ExtraGoals.apply(plan, 1, goals)
        assertEquals(mapOf(GUM_4.id to 5, SPRAY.id to 4), set.week(2).goals.maxUnitsPerDay)
        // Week 3 switches to 8 pieces of 2 mg gum, which has no cap of its own yet.
        assertEquals(mapOf(GUM_2.id to 8, SPRAY.id to 4), set.week(3).goals.maxUnitsPerDay)
        // Once the gum has gone, so has its cap.
        val sprayOnly = set.weeks.first { week -> week.target.mix.products.none { it.form == GUM_2.form } && !week.isNrtFree }
        assertEquals(mapOf(SPRAY.id to 4), sprayOnly.goals.maxUnitsPerDay)
    }

    @Test
    fun `a week edited past the switch has caps for its new mix, and so do the weeks that come down with it`() {
        val set = ExtraGoals.apply(plan, 1, goals)
        val friday = date("2026-10-23")

        // Week 2 edited to week 3's switch: 8 pieces of 2 mg gum and 3 sprays. The 2 mg gum starts at
        // its mix, the spray keeps its cap, and the 4 mg gum it no longer has loses its own.
        val switched = set.edit(2, checkNotNull(plan.week(3).target.step), friday)
        assertEquals(plan.week(3).target.mix, switched.week(2).target.mix)
        assertEquals(mapOf(GUM_2.id to 8, SPRAY.id to 4), switched.week(2).goals.maxUnitsPerDay)
        assertEquals(90, switched.week(2).goals.minGapMinutes)
        assertEquals(LocalTime.of(9, 15), switched.week(2).goals.firstDoseNotBefore)
        assertEquals(set.week(1), switched.week(1))
        assertEquals(set.week(3), switched.week(3))

        // Down to the first week of sprays alone: every week brought down with it drops its gum cap.
        val sprays = plan.weeks.first { week -> !week.isNrtFree && week.target.mix.products.none { it.form == GUM_2.form } }
        val lowered = set.edit(2, checkNotNull(sprays.target.step), friday)
        (2..sprays.number).forEach { number ->
            assertEquals(mapOf(SPRAY.id to 4), lowered.week(number).goals.maxUnitsPerDay, "week $number")
        }
        assertEquals(LocalTime.of(9, 30), lowered.week(3).goals.firstDoseNotBefore)
    }

    @Test
    fun `an edited week with its caps off keeps them off`() {
        val gapOnly = ExtraGoals.apply(plan, 1, WeekGoals(minGapMinutes = 60))
        val edited = gapOnly.edit(2, checkNotNull(plan.week(3).target.step), date("2026-10-23"))
        assertEquals(WeekGoals(minGapMinutes = 60), edited.week(2).goals)
    }

    @Test
    fun `switching a goal off clears it from then on`() {
        val on = ExtraGoals.apply(plan, 1, goals)
        val off = ExtraGoals.apply(on, 4, goals.copy(maxUnitsPerDay = emptyMap(), firstDoseNotBefore = null))
        assertEquals(goals.maxUnitsPerDay.keys.size, off.week(3).goals.maxUnitsPerDay.size)
        assertEquals(LocalTime.of(9, 30), off.week(3).goals.firstDoseNotBefore)
        assertEquals((4..10).map { WeekGoals(minGapMinutes = 90) }, off.weeks.subList(3, 10).map { it.goals })
    }

    @Test
    fun `the starting caps are the week's own mix`() {
        assertEquals(mapOf(GUM_4.id to 6, SPRAY.id to 3), ExtraGoals.startingCaps(plan.week(1)))
    }

    @Test
    fun `an NRT-free week can't take goals`() {
        assertThrows<IllegalArgumentException> { ExtraGoals.apply(plan, 11, goals) }
    }

    @Test
    fun `goals don't change a result`() {
        val set = ExtraGoals.apply(plan, 1, goals)
        val week = set.week(1)
        assertEquals(plan.week(1).isMetBy(week.target.weeklyAllowance), week.isMetBy(week.target.weeklyAllowance))
        assertEquals(play(plan, "mxm").programme.weeks.map { it.result }, play(set, "mxm").programme.weeks.map { it.result })
    }
}
