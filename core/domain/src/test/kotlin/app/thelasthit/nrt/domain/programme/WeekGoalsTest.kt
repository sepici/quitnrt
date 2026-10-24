package app.thelasthit.nrt.domain.programme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

class WeekGoalsTest {

    /** Week n's goals: at most 13 − n pieces of gum, 60 + 10n minutes apart, the first dose at 07:00 plus 15n minutes. */
    private fun goalsFor(number: Int) = WeekGoals(
        maxUnitsPerDay = mapOf(GUM to 13 - number),
        minGapMinutes = 60 + 10 * number,
        firstDoseNotBefore = LocalTime.of(7, 0).plusMinutes(15L * number),
    )

    private val programme = planExample().let { plan -> plan.copy(weeks = plan.weeks.map { it.copy(goals = goalsFor(it.number)) }) }

    @Test
    fun `goals start off`() {
        assertTrue(planExample().weeks.all { it.goals == WeekGoals.NONE })
    }

    @Test
    fun `a result and an edit leave the goals as they were`() {
        val played = play(programme, "mx").programme
        assertEquals(programme.weeks.map { it.goals }, played.weeks.map { it.goals })
        val edited = programme.edit(5, checkNotNull(programme.week(5).target.step) + 1, date("2026-10-23"))
        assertEquals(programme.weeks.map { it.goals }, edited.weeks.map { it.goals })
    }

    @Test
    fun `a repeat takes the missed week's goals, and later weeks keep their own`() {
        val missed = play(programme, "mmx").programme
        val repeated = missed.repeat(3, dueFor(missed.week(3)))
        assertEquals(goalsFor(3), repeated.week(4).goals)
        assertEquals((4..12).map(::goalsFor), repeated.weeks.drop(4).map { it.goals })
    }

    @Test
    fun `overtime has no goals`() {
        val overtime = play(programme, "mmmmmmmmmmmx").programme.weeks.last()
        assertEquals(WeekKind.OVERTIME, overtime.kind)
        assertEquals(WeekGoals.NONE, overtime.goals)
    }

    @Test
    fun `each goal on counts once, however many products are capped`() {
        assertEquals(0, WeekGoals.NONE.onCount)
        assertEquals(1, WeekGoals(maxUnitsPerDay = mapOf(GUM to 5, SPRAY.id to 3)).onCount)
        assertEquals(3, goalsFor(1).onCount)
    }

    @Test
    fun `goals have to make sense`() {
        assertThrows<IllegalArgumentException> { WeekGoals(maxUnitsPerDay = mapOf(GUM to 0)) }
        assertThrows<IllegalArgumentException> { WeekGoals(minGapMinutes = 0) }
        assertThrows<IllegalArgumentException> { WeekGoals(minGapMinutes = 24 * 60 + 1) }
        assertThrows<IllegalArgumentException> { WeekGoals(firstDoseNotBefore = LocalTime.of(7, 30, 15)) }
    }
}
