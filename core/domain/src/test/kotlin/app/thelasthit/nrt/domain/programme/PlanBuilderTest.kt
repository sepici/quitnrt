package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlanBuilderTest {

    private fun guides(baselineMg: Double, unitMg: Double, length: ProgrammeLength = ProgrammeLength.WEEKS_12): List<Dose> =
        PlanBuilder.targets(Dose.mg(baselineMg), Dose.mg(unitMg), length).map { it.dailyGuide }

    private fun mg(vararg values: Int) = values.map(Dose::mg)

    @Test
    fun `PLAN's worked example, 24 mg a day of 2 mg gum over 12 weeks`() {
        val targets = PlanBuilder.targets(Dose.mg(24), Dose.mg(2), ProgrammeLength.WEEKS_12)
        assertEquals(mg(20, 18, 16, 14, 12, 10, 8, 6, 4, 2, 0, 0), targets.map { it.dailyGuide })
        assertEquals(mg(140, 126, 112, 98, 84, 70, 56, 42, 28, 14, 0, 0), targets.map { it.weeklyAllowance })
    }

    @Test
    fun `every length ends with 2 NRT-free weeks`() {
        assertEquals(ProgrammeLength.WEEKS_12, ProgrammeLength.DEFAULT)
        assertEquals(listOf(8, 12, 16, 24), ProgrammeLength.entries.map { it.weeks })
        for (length in ProgrammeLength.entries) {
            val programme = Programme.create(Baseline.typed(Dose.mg(24)), length, Dose.mg(2), START)
            assertEquals(length.weeks, programme.weeks.size)
            assertEquals(List(length.taperWeeks) { false } + listOf(true, true), programme.weeks.map { it.isNrtFree })
            assertEquals(listOf(Dose.ZERO, Dose.ZERO), programme.allowances.takeLast(2))
            assertTrue(programme.allowances.first() > Dose.ZERO)
        }
    }

    @Test
    fun `extra units come off in the first weeks, while they're a smaller share`() {
        // 28 mg is 14 pieces to drop over 11 steps: a piece a week, and a piece more in the first 3.
        assertEquals(mg(24, 20, 16, 14, 12, 10, 8, 6, 4, 2, 0, 0), guides(28.0, 2.0))
        // 8 weeks: 12 pieces over 7 steps, so 5 steps drop 2.
        assertEquals(mg(20, 16, 12, 8, 4, 2, 0, 0), guides(24.0, 2.0, ProgrammeLength.WEEKS_8))
    }

    @Test
    fun `with fewer units than weeks, the drops are spread evenly through the taper`() {
        // 3 pieces of gum to drop over 11 steps.
        assertEquals(mg(4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 0, 0), guides(6.0, 2.0))
        // A 21 mg patch a day, stepping down in 7 mg patches.
        assertEquals(mg(14, 14, 14, 14, 14, 7, 7, 7, 7, 7, 0, 0), guides(21.0, 7.0))
        // 12 pieces over 23 steps in a 24-week programme.
        val long = guides(24.0, 2.0, ProgrammeLength.WEEKS_24)
        assertEquals(mg(22, 22, 20, 20, 18, 18, 16, 16, 14, 14, 12, 12, 10, 10, 8, 8, 6, 6, 4, 4, 2, 2, 0, 0), long)
        // 10 pieces over 11 steps: the one week that holds comes late.
        assertEquals(mg(18, 16, 14, 12, 10, 8, 6, 4, 2, 2, 0, 0), guides(20.0, 2.0))
    }

    @Test
    fun `a baseline of a single unit is refused, since it has nothing to step down through`() {
        // One 21 mg patch a day, and a patch with a little gum: both come to one patch.
        // One 4 mg lozenge a day is one lozenge.
        for ((baseline, unit) in listOf(Dose.mg(21) to Dose.mg(21), Dose.mg(25) to Dose.mg(21), Dose.mg(4) to Dose.mg(4))) {
            assertEquals(1, PlanBuilder.units(baseline, unit))
            assertFalse(PlanBuilder.canStepDown(baseline, unit))
            assertThrows<IllegalArgumentException> { PlanBuilder.targets(baseline, unit, ProgrammeLength.WEEKS_12) }
            assertThrows<IllegalArgumentException> { Programme.create(Baseline.typed(baseline), ProgrammeLength.WEEKS_12, unit, START) }
        }
        assertFalse(PlanBuilder.canStepDown(Dose.mg(1.5), Dose.mg(4)))
        assertFalse(PlanBuilder.canStepDown(Dose.ZERO, Dose.mg(2)))
        assertFalse(PlanBuilder.canStepDown(Dose.mg(24), Dose.ZERO))
        // A smaller product steps them down: the patches in 7 mg ones, the lozenge in 2 mg ones.
        assertTrue(PlanBuilder.canStepDown(Dose.mg(21), Dose.mg(7)))
        assertEquals(mg(14, 14, 14, 14, 14, 7, 7, 7, 7, 7, 0, 0), guides(21.0, 7.0))
        assertTrue(PlanBuilder.canStepDown(Dose.mg(25), Dose.mg(7)))
        assertEquals(mg(21, 21, 21, 14, 14, 14, 7, 7, 7, 7, 0, 0), guides(25.0, 7.0))
        assertTrue(PlanBuilder.canStepDown(Dose.mg(4), Dose.mg(2)))
        assertEquals(mg(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0), guides(4.0, 2.0))
        // A unit and a half rounds up to two.
        assertTrue(PlanBuilder.canStepDown(Dose.mg(6), Dose.mg(4)))
    }

    @Test
    fun `the baseline becomes whole units, to the nearest one`() {
        assertEquals(13, PlanBuilder.units(Dose.mg(25), Dose.mg(2)))
        assertEquals(12, PlanBuilder.units(Dose(24_999), Dose.mg(2)))
        assertEquals(Dose.mg(22), guides(25.0, 2.0).first())
        // Under half a unit is none at all.
        assertEquals(0, PlanBuilder.units(Dose.mg(1.5), Dose.mg(4)))
        // An inhalator session is an eighth of a 15 mg cartridge.
        assertEquals(8, PlanBuilder.units(Dose.mg(15), Dose(1875)))
    }

    @Test
    fun `the daily guide is the weekly allowance divided by 7, in whole micrograms`() {
        assertEquals(Dose(14_285), WeekTarget(Dose.mg(100)).dailyGuide)
        assertEquals(Dose.mg(98), WeekTarget.daily(Dose.mg(14)).weeklyAllowance)
        // An inhalator taper in sessions of 1875 µg: 8 sessions over 7 steps.
        val sessions = PlanBuilder.targets(Dose.mg(15), Dose(1875), ProgrammeLength.WEEKS_8)
        assertEquals(listOf(11_250L, 9375, 7500, 5625, 3750, 1875, 0, 0).map(::Dose), sessions.map { it.dailyGuide })
        assertEquals(listOf(78_750L, 65_625, 52_500, 39_375, 26_250, 13_125, 0, 0).map(::Dose), sessions.map { it.weeklyAllowance })
    }

    @Test
    fun `allowances never go up, the first week is under the baseline, and the taper keeps some nicotine`() {
        val units = listOf(Dose.mg(1), Dose.mg(1.5), Dose(1875), Dose.mg(2), Dose.mg(4), Dose.mg(7), Dose.mg(14), Dose.mg(21), Dose.mg(25))
        val baselines = (1..80).flatMap { mg -> listOf(Dose.mg(mg), Dose(mg * 1000L + 333)) }
        for (length in ProgrammeLength.entries) {
            for (unit in units) {
                for (baseline in baselines) {
                    if (!PlanBuilder.canStepDown(baseline, unit)) {
                        assertThrows<IllegalArgumentException>("$baseline of $unit") { PlanBuilder.targets(baseline, unit, length) }
                        continue
                    }
                    val targets = PlanBuilder.targets(baseline, unit, length)
                    val allowances = targets.map { it.weeklyAllowance }
                    val case = "$baseline of $unit over ${length.weeks} weeks: $allowances"
                    assertEquals(length.weeks, targets.size, case)
                    assertTrue(allowances.zipWithNext().all { (week, next) -> next <= week }, case)
                    assertTrue(allowances.first() < baseline * WeekTarget.DAYS, case)
                    assertTrue(targets.all { it.dailyGuide.micrograms % unit.micrograms == 0L }, case)
                    assertTrue(targets.all { it.dailyGuide * WeekTarget.DAYS == it.weeklyAllowance }, case)
                    assertEquals(listOf(Dose.ZERO, Dose.ZERO), allowances.takeLast(2), case)
                    assertTrue(allowances[length.taperWeeks - 1] > Dose.ZERO, case)
                }
            }
        }
    }

    @Test
    fun `weeks run from the start day picked, tomorrow by default`() {
        val friday = date("2026-10-23")
        assertEquals(START, Programme.defaultStart(friday))
        val programme = planExample()
        assertEquals(listOf(START, date("2026-10-31"), date("2026-11-07")), programme.weeks.take(3).map { it.startDate })
        assertEquals(date("2027-01-15"), programme.weeks.last().endDate)
        assertEquals(programme.week(2), programme.weekOn(date("2026-11-06")))
        assertEquals(null, programme.weekOn(date("2026-10-23")))
        assertEquals(120, programme.maxHp)
    }

    @Test
    fun `nothing to step down from is refused`() {
        assertThrows<IllegalArgumentException> { PlanBuilder.targets(Dose.ZERO, Dose.mg(2), ProgrammeLength.WEEKS_12) }
        assertThrows<IllegalArgumentException> { PlanBuilder.targets(Dose.mg(24), Dose.ZERO, ProgrammeLength.WEEKS_12) }
    }
}
