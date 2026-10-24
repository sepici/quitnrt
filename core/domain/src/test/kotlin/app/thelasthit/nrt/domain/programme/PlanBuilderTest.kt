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
    fun `PLAN's third example, 8 pieces of 4 mg gum and 3 sprays over 12 weeks`() {
        // 15 steps for 10 weeks. Week 2 ends at 4 pieces of 4 mg, and week 3's switch stops it
        // there, so weeks 4 to 6 take two steps each.
        val programme = plan(usual(GUM_4 to 8, SPRAY to 3))
        assertEquals(15, programme.ladder.size)
        val gum = listOf(GUM_4 to 6, GUM_4 to 4, GUM_2 to 8, GUM_2 to 6, GUM_2 to 4, GUM_2 to 2, GUM_2 to 1, GUM_2 to 1)
        val sprays = listOf(3, 3, 3, 3, 3, 3, 3, 2, 2, 1)
        val expected = sprays.indices.map { week -> mix(*listOfNotNull(gum.getOrNull(week), SPRAY to sprays[week]).toTypedArray()) }
        assertEquals(expected + listOf(Mix.NONE, Mix.NONE), programme.mixes)
        assertEquals(mg(27, 19, 19, 15, 11, 7, 5, 4, 2, 1, 0, 0), programme.guides)
        assertEquals(mg(189, 133, 133, 105, 77, 49, 35, 28, 14, 7, 0, 0), programme.allowances)
        assertEquals(listOf(2, 4, 5, 7, 9, 11, 12, 13, 14, 15, null, null), programme.steps)
    }

    @Test
    fun `a switch that falls as the first step of a double week gets the week to itself`() {
        // PLAN's second example over 8 weeks: 12 steps for 6 weeks, two a week. Week 3 would take
        // the switch to 8 pieces of 2 mg and then 7 pieces, so it stops at the switch, and the 7
        // steps left spread over weeks 4 to 6: three, then two, then two.
        val programme = plan(usual(GUM_4 to 8), ProgrammeLength.WEEKS_8)
        assertEquals(listOf(2, 4, 5, 8, 10, 12, null, null), programme.steps)
        val expected = listOf(mix(GUM_4 to 6), mix(GUM_4 to 4), mix(GUM_2 to 8), mix(GUM_2 to 5), mix(GUM_2 to 3), mix(GUM_2 to 1))
        assertEquals(expected + listOf(Mix.NONE, Mix.NONE), programme.mixes)
        assertEquals(mg(24, 16, 16, 10, 6, 2, 0, 0), programme.guides)
        assertEquals(listOf(2, 4, 6, 8, 10, 12), (1..6).map { PlanBuilder.stepFor(it, 12, 6) })
    }

    @Test
    fun `a switch that ends a double week needs no stop`() {
        // 10 pieces of 4 mg switch at 5 pieces, step 6 of 15. Week 3 takes steps 5 and 6, so it
        // ends on the switch, and week 4 counts down from it.
        val programme = plan(usual(GUM_4 to 10))
        assertEquals(listOf(2, 4, 6, 8, 10, 11, 12, 13, 14, 15, null, null), programme.steps)
        assertEquals(mix(GUM_4 to 6), programme.week(2).target.mix)
        assertEquals(mix(GUM_2 to 10), programme.week(3).target.mix)
        assertEquals(mix(GUM_2 to 8), programme.week(4).target.mix)
    }

    @Test
    fun `two switches in one ladder each get a week of their own`() {
        // NiQuitin 4 mg lozenges switch to its 2 mg at step 4, then to the generic 1 mg at step 8.
        // Over 8 weeks that's 13 steps for 6 weeks: week 2 stops at the first switch and week 4
        // at the second, and each time the steps left spread over the weeks left.
        val generic1 = product("generic-lozenge-1mg")
        val programme = plan(usual(LOZENGE_4 to 6), ProgrammeLength.WEEKS_8)
        assertEquals(listOf(4, 8), (1..programme.ladder.size).filter(programme.ladder::isSwitch))
        assertEquals(listOf(3, 4, 7, 8, 11, 13, null, null), programme.steps)
        val expected = listOf(mix(LOZENGE_4 to 3), mix(LOZENGE_2 to 6), mix(LOZENGE_2 to 3), mix(generic1 to 6), mix(generic1 to 3), mix(generic1 to 1))
        assertEquals(expected + listOf(Mix.NONE, Mix.NONE), programme.mixes)
        assertEquals(mg(12, 12, 6, 6, 3, 1, 0, 0), programme.guides)
        // Over 12 weeks each week takes one or two steps and both switches end a week anyway.
        assertEquals(listOf(2, 4, 6, 7, 8, 9, 10, 11, 12, 13, null, null), plan(usual(LOZENGE_4 to 6)).steps)
    }

    @Test
    fun `the last taper week takes the rest of the ladder, even past a switch`() {
        // 6 pieces of 2 mg gum and 3 of 4 mg lozenges over 8 weeks: week 4 stops at the switch to
        // 2 mg lozenges (step 7), and the last week would stop at the switch to 1 mg (step 11).
        // There's no week after it to spread the last step over, so it goes on to the bottom.
        val programme = plan(usual(GUM_2 to 6, LOZENGE_4 to 3), ProgrammeLength.WEEKS_8)
        assertEquals(listOf(7, 11), (1..programme.ladder.size).filter(programme.ladder::isSwitch))
        assertEquals(listOf(2, 4, 6, 7, 10, 12, null, null), programme.steps)
        assertEquals(mix(LOZENGE_2 to 1), programme.week(5).target.mix)
        assertEquals(mix(product("generic-lozenge-1mg") to 1), programme.week(6).target.mix)
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
        // switch, and the 13 steps left spread over weeks 5 to 10.
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
        // Packs whose brand has no lower strength, and one added by hand: the generic line follows.
        val equate = product("equate-patch-us-21mg")
        val cvsMini = product("cvs-mini-lozenge-us-4mg")
        val byHand = UserProduct("own-gum", null, "Gum 4 mg", "Gum", ProductForm.GUM, ProductForm.GUM.defaultUnit, Dose.mg(4))
        val pool = listOf(GUM_2, GUM_4, SPRAY, PATCH_21, PATCH_14, LOZENGE_4, inhalator, mint, nasal, microtab, invisi, pouch, preQuit, nicotinell4, thrive2, equate, cvsMini, byHand)
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
        for (usual in usualDays) played = checkPlans(ladder(usual), played)
        // The shop packs with nothing lower in their brand, in their own markets.
        val shopPacks = listOf(
            "US" to "equate-patch-us-21mg",
            "US" to "up-and-up-patch-us-21mg",
            "US" to "walgreens-mini-lozenge-us-4mg",
            "AU" to "pharmacy-care-patch-au-21mg",
            "CA" to "life-brand-patch-ca-14mg",
            "ZZ" to "generic-lozenge-4mg",
        )
        for ((market, id) in shopPacks) {
            val pack = product(id)
            val most = if (pack.form.defaultUnit == PATCH_21.form.defaultUnit) 2 else 24
            for (units in 1..most) {
                played = checkPlans(ladder(usual(pack to units), market = market), played)
                for (other in listOf(GUM_2, GUM_4, SPRAY, LOZENGE_4)) {
                    for (extra in listOf(1, 3, 8)) played = checkPlans(ladder(usual(pack to units, other to extra), market = market), played)
                }
            }
        }
    }

    /**
     * Checks every length of plan down [ladder] keeps PLAN §6.2's rules, and plays one in every 97
     * through to the end. Returns how many plans have been checked, [played] included.
     */
    private fun checkPlans(ladder: Ladder, played: Int): Int {
        var count = played
        val usual = ladder.usual
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
            // A switch gets a week of its own: no taper week but the last passes through one.
            val steps = taper.map { checkNotNull(it.target.step) }
            assertTrue(steps.zipWithNext().all { (week, next) -> next >= week }, case)
            for (week in 0 until steps.lastIndex) {
                val from = if (week == 0) 0 else steps[week - 1]
                assertTrue((from + 1 until steps[week]).none(ladder::isSwitch), "$case, week ${week + 1}")
            }
            assertEquals(listOf(Dose.ZERO, Dose.ZERO), allowances.takeLast(2), case)
            assertEquals(length.weeks, programme.hitsOnOffer, case)
            assertEquals(HP_PER_HIT * length.weeks, programme.maxHp, case)
            if (count++ % 97 == 0) {
                val after = play(programme, "m".repeat(length.weeks)).programme
                assertEquals(after.hitsOnOffer, after.earnedHits.size, case)
                assertEquals(0, after.hp(after.earnedHits.size), case)
            }
        }
        return count
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
