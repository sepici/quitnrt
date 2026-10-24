package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlanBuilderTest {

    private fun mg(vararg values: Int) = values.map(Dose::mg)

    private fun plan(usual: UsualDay, length: ProgrammeLength = ProgrammeLength.WEEKS_12, own: List<UserProduct> = usual.mix.products) =
        Programme.create(ladder(usual, own), length, START)

    @Test
    fun `PLAN's first example, 12 pieces of 2 mg gum over 12 weeks`() {
        val programme = planExample()
        assertEquals((10 downTo 1).map { mix(GUM_2 to it) } + listOf(Mix.NONE, Mix.NONE), programme.mixes)
        assertEquals(mg(20, 18, 16, 14, 12, 10, 8, 6, 4, 2, 0, 0), programme.guides)
        assertEquals(mg(140, 126, 112, 98, 84, 70, 56, 42, 28, 14, 0, 0), programme.allowances)
    }

    @Test
    fun `PLAN's second example, 8 pieces of 4 mg gum over 12 weeks`() {
        // 12 steps for 10 taper weeks, so weeks 1 and 2 take two steps each.
        val programme = plan(usual(GUM_4 to 8))
        val expected = listOf(mix(GUM_4 to 6), mix(GUM_4 to 4)) + (8 downTo 1).map { mix(GUM_2 to it) } + listOf(Mix.NONE, Mix.NONE)
        assertEquals(expected, programme.mixes)
        assertEquals(mg(24, 16, 16, 14, 12, 10, 8, 6, 4, 2, 0, 0), programme.guides)
        assertEquals(mg(168, 112, 112, 98, 84, 70, 56, 42, 28, 14, 0, 0), programme.allowances)
        assertEquals(listOf(2, 4) + (5..12) + listOf(null, null), programme.steps)
    }

    @Test
    fun `PLAN's third example, gum and sprays, where the switch gets a week of its own`() {
        // 15 steps for 10 weeks. Week 3 would take the switch and one more piece off, so it stops
        // at the switch, and weeks 4 to 10 share the 10 steps left: weeks 4 to 6 take two each.
        val programme = plan(usual(GUM_4 to 8, SPRAY to 3))
        assertEquals(15, programme.ladder.size)
        val gum = listOf(GUM_4 to 6, GUM_4 to 4, GUM_2 to 8, GUM_2 to 6, GUM_2 to 4, GUM_2 to 2, GUM_2 to 1, GUM_2 to 1, null, null)
        val sprays = listOf(3, 3, 3, 3, 3, 3, 3, 2, 2, 1)
        val expected = gum.zip(sprays) { pieces, spray -> Mix.of(listOfNotNull(pieces, SPRAY to spray).map { MixItem(it.first, it.second) }) }
        assertEquals(expected + listOf(Mix.NONE, Mix.NONE), programme.mixes)
        assertEquals(mg(27, 19, 19, 15, 11, 7, 5, 4, 2, 1, 0, 0), programme.guides)
        assertEquals(setOf(5), PlanBuilder.switchSteps(programme.ladder))
    }

    @Test
    fun `a week stops at a switch, and the steps left spread over the weeks after it`() {
        // 15 steps over 10 weeks would be 2, 4, 6 and so on; a switch at 5 stops week 3 there.
        assertEquals(listOf(2, 4, 5, 7, 9, 11, 12, 13, 14, 15), PlanBuilder.taperSteps(15, 10, setOf(5)))
        // A week that lands on the switch takes it as usual.
        assertEquals((1..10).map { PlanBuilder.stepFor(it, 15, 10) }, PlanBuilder.taperSteps(15, 10, setOf(4)))
        // Two switches, each with a week of its own.
        assertEquals(listOf(2, 3, 5, 6, 8, 10, 12, 13, 14, 15), PlanBuilder.taperSteps(15, 10, setOf(3, 6)))
        // The last week always reaches the bottom, even past a switch.
        assertEquals(listOf(2, 4), PlanBuilder.taperSteps(4, 2, setOf(3)))
        // With no switches it's PLAN's formula.
        assertEquals((1..10).map { PlanBuilder.stepFor(it, 12, 10) }, PlanBuilder.taperSteps(12, 10))
    }

    @Test
    fun `the steps spread over the taper weeks by PLAN's formula`() {
        // k ≥ T: k div T steps a week, and one more in the first k mod T weeks.
        assertEquals(listOf(2, 4) + (5..12), (1..10).map { PlanBuilder.stepFor(it, 12, 10) })
        assertEquals(listOf(2) + (3..11), (1..10).map { PlanBuilder.stepFor(it, 11, 10) })
        assertEquals((1..10).map { it * 3 }, (1..10).map { PlanBuilder.stepFor(it, 30, 10) })
        assertEquals((1..10).toList(), (1..10).map { PlanBuilder.stepFor(it, 10, 10) })
        // k < T: week w is at step ⌈k × w ÷ T⌉.
        assertEquals(listOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 2), (1..10).map { PlanBuilder.stepFor(it, 2, 10) })
        assertEquals(List(10) { 1 }, (1..10).map { PlanBuilder.stepFor(it, 1, 10) })
        // No steps: every week holds at the usual day.
        assertEquals(List(10) { 0 }, (1..10).map { PlanBuilder.stepFor(it, 0, 10) })
        assertThrows<IllegalArgumentException> { PlanBuilder.stepFor(0, 5, 10) }
        assertThrows<IllegalArgumentException> { PlanBuilder.stepFor(11, 5, 10) }
    }

    @Test
    fun `with fewer steps than weeks, a step lasts more than a week`() {
        // 4 pieces of 2 mg gum over 24 weeks: 3 steps for 22 taper weeks.
        val programme = plan(usual(GUM_2 to 4), ProgrammeLength.WEEKS_24)
        val guides = List(7) { 6 } + List(7) { 4 } + List(8) { 2 } + listOf(0, 0)
        assertEquals(guides.map(Dose::mg), programme.guides)
        assertEquals(guides.map { Dose.mg(it * 7) }, programme.allowances)
    }

    @Test
    fun `a 21 mg patch alone, and a patch with gum`() {
        val patch = plan(usual(PATCH_21 to 1))
        assertEquals(List(5) { mix(PATCH_14 to 1) } + List(5) { mix(PATCH_7 to 1) } + listOf(Mix.NONE, Mix.NONE), patch.mixes)
        assertEquals(mg(98, 98, 98, 98, 98, 49, 49, 49, 49, 49, 0, 0), patch.allowances)
        // 8 steps for 10 weeks: weeks 5 and 10 hold.
        val withGum = plan(usual(PATCH_21 to 1, GUM_2 to 6))
        assertEquals(mg(26, 19, 17, 15, 15, 13, 6, 4, 2, 2, 0, 0), withGum.guides)
        assertEquals(mix(PATCH_7 to 1, GUM_2 to 3), withGum.week(6).target.mix)
        assertEquals(mix(GUM_2 to 3), withGum.week(7).target.mix)
    }

    @Test
    fun `gum with mouth spray, and mixed strengths of gum`() {
        val spray = plan(usual(GUM_2 to 4, SPRAY to 4))
        // 7 steps for 10 weeks: weeks 4, 7 and 10 hold.
        assertEquals(mg(10, 8, 6, 6, 5, 4, 4, 2, 1, 1, 0, 0), spray.guides)
        val mixed = plan(usual(GUM_4 to 4, GUM_2 to 4))
        assertEquals(mg(20, 16, 16, 14, 12, 10, 8, 6, 4, 2, 0, 0), mixed.guides)
        assertEquals(mix(GUM_4 to 2, GUM_2 to 4), mixed.week(2).target.mix)
        assertEquals(mix(GUM_2 to 8), mixed.week(3).target.mix)
    }

    @Test
    fun `one piece of 4 mg gum is one piece of 2 mg through the taper`() {
        val programme = plan(usual(GUM_4 to 1))
        assertEquals(List(10) { mix(GUM_2 to 1) } + listOf(Mix.NONE, Mix.NONE), programme.mixes)
        assertEquals(List(10) { Dose.mg(14) } + listOf(Dose.ZERO, Dose.ZERO), programme.allowances)
    }

    @Test
    fun `one spray a day holds until the NRT-free weeks`() {
        val programme = plan(usual(SPRAY to 1))
        assertEquals(List(10) { mix(SPRAY to 1) } + listOf(Mix.NONE, Mix.NONE), programme.mixes)
        assertEquals(List(10) { 0 } + listOf(null, null), programme.steps)
        assertEquals(List(10) { Dose.mg(7) } + listOf(Dose.ZERO, Dose.ZERO), programme.allowances)
        assertEquals(120, programme.maxHp)
    }

    @Test
    fun `a usual day over a label limit starts the plan at or under it`() {
        // 20 pieces a day of gum whose pack allows 15: steps start at 15, and there are 15 of them.
        val programme = plan(usual(GUM_2 to 20))
        assertEquals(mg(28, 24, 20, 16, 12, 10, 8, 6, 4, 2, 0, 0), programme.guides)
        assertTrue(programme.mixes.all { it.isWithinLabels })
        // 20 pieces of 4 mg: 15 of them at step 1, and the switch waits for 7, which make 14 of 2 mg.
        // 23 steps for 10 weeks, so weeks 1 to 3 take three steps each. Week 4 stops at the
        // switch, at the same mg, and weeks 5 to 10 take the 13 steps left.
        val strong = plan(usual(GUM_4 to 20))
        assertEquals(mg(52, 40, 28, 28, 22, 18, 14, 10, 6, 2, 0, 0), strong.guides)
        assertEquals(mix(GUM_4 to 7), strong.week(3).target.mix)
        assertEquals(mix(GUM_2 to 14), strong.week(4).target.mix)
        assertEquals(mix(GUM_2 to 11), strong.week(5).target.mix)
        assertTrue(strong.mixes.all { it.isWithinLabels })
    }

    @Test
    fun `every length ends with 2 NRT-free weeks`() {
        assertEquals(ProgrammeLength.WEEKS_12, ProgrammeLength.DEFAULT)
        assertEquals(listOf(8, 12, 16, 24), ProgrammeLength.entries.map { it.weeks })
        for (length in ProgrammeLength.entries) {
            val programme = plan(usual(GUM_2 to 12), length)
            assertEquals(length.weeks, programme.weeks.size)
            assertEquals(List(length.taperWeeks) { false } + listOf(true, true), programme.weeks.map { it.isNrtFree })
            assertEquals(listOf(Dose.ZERO, Dose.ZERO), programme.allowances.takeLast(2))
        }
    }

    @Test
    fun `the daily guide is the mix's mg exactly, and the allowance 7 times it`() {
        val inhalator = product("nicorette-inhalator-uk-15mg")
        val target = plan(usual(inhalator to 8, SPRAY to 3), ProgrammeLength.WEEKS_8).week(1).target
        assertEquals(target.mix.total, target.dailyGuide)
        assertEquals(target.dailyGuide * 7, target.weeklyAllowance)
        assertEquals(Dose.ZERO, WeekTarget.NRT_FREE.weeklyAllowance)
        assertThrows<IllegalArgumentException> { WeekTarget(null, mix(SPRAY to 1)) }
        assertThrows<IllegalArgumentException> { WeekTarget(3, Mix.NONE) }
    }

    @Test
    fun `allowances never go up, and the hits on offer match the HP, for many usual days`() {
        val inhalator = product("nicorette-inhalator-uk-15mg")
        val mint = product("nicotinell-lozenge-uk-2mg")
        val nasal = product("nicorette-nasal-spray-uk-0-5mg")
        val microtab = product("nicorette-microtab-uk-2mg")
        val invisi = product("nicorette-invisipatch-uk-25mg")
        val pouch = UserProduct("pouch", null, "Pouch 6 mg", "Pouch", ProductForm.POUCH, ProductForm.POUCH.defaultUnit, Dose.mg(6))
        // A patch whose lower strengths are in another line of its brand, gum whose 2 mg pack
        // allows more than its 4 mg one, and a lozenge whose 1 mg is sold in another country.
        val preQuit = product("niquitin-pre-quit-patch-uk-21mg")
        val nicotinell4 = product("nicotinell-gum-uk-4mg")
        val thrive2 = product("thrive-lozenge-ca-2mg")
        val pool = listOf(GUM_2, GUM_4, SPRAY, PATCH_21, PATCH_14, LOZENGE_4, inhalator, mint, nasal, microtab, invisi, pouch, preQuit, nicotinell4, thrive2)
        val usualDays = buildList {
            for (first in pool) {
                val most = if (first.form.defaultUnit == PATCH_21.form.defaultUnit) 2 else 24
                for (units in 1..most) {
                    add(usual(first to units))
                    for (second in pool.filter { it.form != first.form }) {
                        for (extra in listOf(1, 3, 8)) add(usual(first to units, second to extra))
                    }
                }
            }
            // Mixed strengths of one kind.
            for (strong in 1..12) for (weak in 1..12) add(usual(GUM_4 to strong, GUM_2 to weak))
            for (strong in 1..12) for (weak in 1..12) add(usual(GUM_4 to strong, GUM_2 to weak, SPRAY to weak))
            // Up to and over the packs' limit of 15 a day.
            for (strong in listOf(8, 15, 20)) for (weak in listOf(5, 15, 20)) add(usual(GUM_4 to strong, GUM_2 to weak))
            add(usual(PATCH_21 to 1, PATCH_14 to 1))
            add(usual(PATCH_21 to 1, PATCH_14 to 1, PATCH_7 to 1))
            add(usual(PATCH_21 to 2, PATCH_14 to 1))
            add(usual(PATCH_21 to 2, GUM_4 to 10))
        }
        var played = 0
        for (usual in usualDays) {
            val ladder = ladder(usual)
            assertEquals(1, ladder.mixAt(ladder.size).units, "$usual ends at one unit")
            assertEquals(if (usual.mix.isWithinLabels) 0 else 1, ladder.top, "$usual: ${ladder.steps}")
            // From the top, no step drops more than the strongest unit in the mix before it.
            for (step in ladder.top until ladder.size) {
                val before = ladder.mixAt(step)
                val drop = before.total.micrograms - ladder.mixAt(step + 1).total.micrograms
                assertTrue(drop <= before.items.maxOf { it.product.perUnit.micrograms }, "$usual, step ${step + 1}: ${ladder.steps}")
            }
            for (length in ProgrammeLength.entries) {
                val programme = Programme.create(ladder, length, START)
                val case = "$usual over ${length.weeks} weeks: ${programme.mixes}"
                val allowances = programme.allowances
                val taper = programme.weeks.take(length.taperWeeks)
                assertEquals(length.weeks, programme.weeks.size, case)
                assertTrue(allowances.zipWithNext().all { (week, next) -> next <= week }, case)
                // The first week is under the usual day whenever there's a step to take.
                if (ladder.size > 0) assertTrue(allowances.first() < usual.weekly, case) else assertEquals(usual.weekly, allowances.first(), case)
                assertTrue(programme.weeks.all { it.target.dailyGuide * 7 == it.target.weeklyAllowance }, case)
                assertTrue(programme.mixes.all { it.isWithinLabels }, case)
                assertTrue(taper.all { it.target.weeklyAllowance > Dose.ZERO }, case)
                assertEquals(ladder.size, taper.last().target.step, case)
                assertEquals(listOf(Dose.ZERO, Dose.ZERO), allowances.takeLast(2), case)
                assertEquals(length.weeks, programme.hitsOnOffer, case)
                assertEquals(HP_PER_HIT * length.weeks, programme.maxHp, case)
                if (played++ % 97 == 0) {
                    val after = play(programme, "m".repeat(length.weeks)).programme
                    assertEquals(after.hitsOnOffer, after.earnedHits.size, case)
                    assertEquals(0, after.hp(after.earnedHits.size), case)
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
        assertEquals(usual(GUM_2 to 12), programme.usualDay)
    }
}
