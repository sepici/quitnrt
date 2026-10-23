package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProgrammeEditTest {

    /** PLAN's second example: 8 pieces of 4 mg gum, with weeks at steps 2, 4, then 5 to 12. */
    private val programme = Programme.create(ladder(usual(GUM_4 to 8)), ProgrammeLength.WEEKS_12, START)
    private val friday = date("2026-10-23")

    @Test
    fun `a week that hasn't started moves along the ladder, no higher than the week before`() {
        assertEquals(4..12, programme.editableSteps(3, friday))
        // Up to week 2's step: the same 16 mg as 4 pieces of 4 mg rather than 8 of 2 mg.
        val edited = programme.edit(3, 4, friday)
        assertEquals(mix(GUM_4 to 4), edited.week(3).target.mix)
        assertEquals(Dose.mg(16), edited.week(3).target.dailyGuide)
        assertEquals(Dose.mg(112), edited.week(3).target.weeklyAllowance)
        assertEquals(programme.weeks.filter { it.number != 3 }, edited.weeks.filter { it.number != 3 })
    }

    @Test
    fun `later weeks above an edited week move down to it`() {
        // Week 2 down to week 7's 4 pieces of 2 mg: weeks 3 to 6 were above that, so they move too.
        val edited = programme.edit(2, 9, friday)
        assertEquals(listOf(2, 9, 9, 9, 9, 9, 9, 10, 11, 12, null, null), edited.steps)
        assertEquals(List(6) { mix(GUM_2 to 4) }, edited.mixes.subList(1, 7))
        assertEquals(mg(168, 56, 56, 56, 56, 56, 56, 42, 28, 14, 0, 0), edited.allowances)
    }

    @Test
    fun `an edit above the week before or off the ladder is refused`() {
        assertThrows<IllegalArgumentException> { programme.edit(3, 3, friday) }
        assertThrows<IllegalArgumentException> { programme.edit(10, 13, friday) }
        assertThrows<IllegalArgumentException> { programme.edit(1, -1, friday) }
    }

    @Test
    fun `the first week can go as high as the usual day, and any taper week down to the last step`() {
        assertEquals(0..12, programme.editableSteps(1, friday))
        assertEquals(mix(GUM_4 to 8), programme.edit(1, 0, friday).week(1).target.mix)
        assertEquals(Dose.mg(224), programme.edit(1, 0, friday).week(1).target.weeklyAllowance)
        assertEquals(11..12, programme.editableSteps(10, friday))
        assertEquals(mix(GUM_2 to 1), programme.edit(10, 12, friday).week(10).target.mix)
    }

    @Test
    fun `the first week can't go back above a label limit`() {
        val limited = Programme.create(ladder(usual(GUM_2 to 20)), ProgrammeLength.WEEKS_12, START)
        assertEquals(1..15, limited.editableSteps(1, friday))
        assertEquals(mix(GUM_2 to 15), limited.edit(1, 1, friday).week(1).target.mix)
        assertThrows<IllegalArgumentException> { limited.edit(1, 0, friday) }
    }

    @Test
    fun `NRT-free weeks and weeks that have started can't be edited`() {
        assertNull(programme.editableSteps(11, friday))
        assertNull(programme.editableSteps(12, friday))
        assertNull(programme.editableSteps(13, friday))
        // Week 3 runs from Saturday 7 November.
        val inWeek3 = date("2026-11-09")
        assertNull(programme.editableSteps(1, inWeek3))
        assertNull(programme.editableSteps(3, inWeek3))
        assertNotNull(programme.editableSteps(4, inWeek3))
        assertNull(programme.editableSteps(4, date("2026-11-14")))
        assertThrows<IllegalArgumentException> { programme.edit(3, 6, inWeek3) }
        assertThrows<IllegalArgumentException> { programme.edit(11, 12, friday) }
    }

    @Test
    fun `a ladder with no steps leaves nothing to move`() {
        val spray = Programme.create(ladder(usual(SPRAY to 1)), ProgrammeLength.WEEKS_8, START)
        assertEquals(0..0, spray.editableSteps(1, friday))
        assertEquals(spray, spray.edit(4, 0, friday))
    }

    @Test
    fun `any run of edits leaves allowances that never go up`() {
        var edited = programme
        // Each week as low as it can go, from the last taper week back, then week 1 back up to the usual day.
        for (number in 10 downTo 1) edited = edited.edit(number, edited.editableSteps(number, friday)!!.last, friday)
        assertEquals(List(10) { 12 } + listOf(null, null), edited.steps)
        edited = edited.edit(1, 0, friday)
        assertEquals(listOf(224) + List(9) { 14 } + listOf(0, 0), edited.allowances.map { (it.micrograms / 1000).toInt() })
        // Then weeks 2, 3 and 6 as high as they can go: 2 and 3 to the usual day, 6 no higher than 5.
        for (number in listOf(2, 3, 6)) edited = edited.edit(number, edited.editableSteps(number, friday)!!.first, friday)
        assertEquals(listOf(0, 0, 0) + List(7) { 12 }, edited.steps.take(10))
        assertTrue(edited.allowances.zipWithNext().all { (week, next) -> next <= week })
    }

    private fun mg(vararg values: Int) = values.map(Dose::mg)
}
