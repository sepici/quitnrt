package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProgrammeEditTest {

    private val programme = planExample()
    private val friday = date("2026-10-23")

    @Test
    fun `a week that hasn't started can be set between the weeks either side`() {
        assertEquals(Dose.mg(112)..Dose.mg(140), programme.editableRange(2, friday))
        val edited = programme.edit(2, Dose.mg(119), friday)
        assertEquals(Dose.mg(119), edited.week(2).target.weeklyAllowance)
        assertEquals(Dose.mg(17), edited.week(2).target.dailyGuide)
        assertEquals(programme.weeks.filter { it.number != 2 }, edited.weeks.filter { it.number != 2 })
    }

    @Test
    fun `an edit that would make an allowance go up is refused`() {
        // Above week 1, or below week 3, which would then go up.
        assertThrows<IllegalArgumentException> { programme.edit(2, Dose.mg(141), friday) }
        assertThrows<IllegalArgumentException> { programme.edit(2, Dose.mg(111), friday) }
    }

    @Test
    fun `the first week can go as high as the baseline, and the last taper week down to zero`() {
        assertEquals(Dose.mg(126)..Dose.mg(168), programme.editableRange(1, friday))
        assertEquals(Dose.ZERO..Dose.mg(28), programme.editableRange(10, friday))
    }

    @Test
    fun `NRT-free weeks and weeks that have started can't be edited`() {
        assertNull(programme.editableRange(11, friday))
        assertNull(programme.editableRange(12, friday))
        // Week 3 runs from Saturday 7 November.
        val inWeek3 = date("2026-11-09")
        assertNull(programme.editableRange(1, inWeek3))
        assertNull(programme.editableRange(3, inWeek3))
        assertNotNull(programme.editableRange(4, inWeek3))
        assertNull(programme.editableRange(4, date("2026-11-14")))
        assertThrows<IllegalArgumentException> { programme.edit(3, Dose.mg(105), inWeek3) }
    }

    @Test
    fun `any run of edits leaves allowances that never go up`() {
        var edited = programme
        // Each week as low as it can go, from the last taper week back, then week 1 back up to the baseline.
        for (number in 10 downTo 1) edited = edited.edit(number, edited.editableRange(number, friday)!!.start, friday)
        edited = edited.edit(1, edited.editableRange(1, friday)!!.endInclusive, friday)
        assertEquals(listOf(168) + List(11) { 0 }, edited.allowances.map { (it.micrograms / 1000).toInt() })
        assertTrue(edited.allowances.zipWithNext().all { (week, next) -> next <= week })
    }
}
