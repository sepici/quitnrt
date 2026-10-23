package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.LabelLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LadderTest {

    private fun mg(vararg values: Double) = values.map(Dose::mg)

    private val Ladder.totals: List<Dose> get() = steps.map { it.total }

    @Test
    fun `whole pieces come off one at a time, with no lower strength to switch to`() {
        val ladder = ladder(usual(GUM_2 to 12))
        assertEquals((11 downTo 1).map { mix(GUM_2 to it) }, ladder.steps)
        assertEquals(0, ladder.top)
        assertEquals(emptyList<Any>(), ladder.newProducts)
    }

    @Test
    fun `4 mg gum comes down to half the pieces, then switches to twice as many 2 mg pieces`() {
        val ladder = ladder(usual(GUM_4 to 8))
        val expected = (7 downTo 4).map { mix(GUM_4 to it) } + (8 downTo 1).map { mix(GUM_2 to it) }
        assertEquals(expected, ladder.steps)
        // The switch is a step of its own, though the mg stays at 16.
        assertEquals(mg(28.0, 24.0, 20.0, 16.0, 16.0, 14.0, 12.0, 10.0, 8.0, 6.0, 4.0, 2.0), ladder.totals)
        // The 2 mg gum isn't in the user's list, so it comes back to be added.
        assertEquals(listOf(GUM_2), ladder.newProducts)
        assertTrue(ladder.isNew(GUM_2))
        assertFalse(ladder.isNew(GUM_4))
        // With the 2 mg gum already in the list, nothing is new.
        assertEquals(expected, ladder(usual(GUM_4 to 8), own = listOf(GUM_4, GUM_2)).steps)
        assertEquals(emptyList<Any>(), ladder(usual(GUM_4 to 8), own = listOf(GUM_4, GUM_2)).newProducts)
    }

    @Test
    fun `a 21 mg patch alone steps down a strength at a time`() {
        val ladder = ladder(usual(PATCH_21 to 1))
        assertEquals(listOf(mix(PATCH_14 to 1), mix(PATCH_7 to 1)), ladder.steps)
        assertEquals(listOf(PATCH_14, PATCH_7), ladder.newProducts)
    }

    @Test
    fun `a patch with gum steps down the patch while it's the most mg, and it comes off at its lowest`() {
        val ladder = ladder(usual(PATCH_21 to 1, GUM_2 to 6))
        val expected = listOf(
            mix(PATCH_14 to 1, GUM_2 to 6),
            mix(PATCH_7 to 1, GUM_2 to 6),
            mix(PATCH_7 to 1, GUM_2 to 5),
            mix(PATCH_7 to 1, GUM_2 to 4),
            // 7 mg of patch against 6 mg of gum: the patch comes off.
            mix(PATCH_7 to 1, GUM_2 to 3),
            mix(GUM_2 to 3),
            mix(GUM_2 to 2),
            mix(GUM_2 to 1),
        )
        assertEquals(expected, ladder.steps)
        assertEquals(mg(26.0, 19.0, 17.0, 15.0, 13.0, 6.0, 4.0, 2.0), ladder.totals)
    }

    @Test
    fun `gum with mouth spray takes from whichever has the most mg, and a tie goes to the stronger unit`() {
        val ladder = ladder(usual(GUM_2 to 4, SPRAY to 4))
        val expected = listOf(
            mix(GUM_2 to 3, SPRAY to 4),
            mix(GUM_2 to 2, SPRAY to 4),
            // 4 mg each: the gum's 2 mg pieces are stronger than 1 mg sprays.
            mix(GUM_2 to 1, SPRAY to 4),
            mix(GUM_2 to 1, SPRAY to 3),
            mix(GUM_2 to 1, SPRAY to 2),
            // 2 mg each: the last piece of gum goes, as there's no lower strength of it.
            mix(SPRAY to 2),
            mix(SPRAY to 1),
        )
        assertEquals(expected, ladder.steps)
    }

    @Test
    fun `mixed strengths of gum lose the strong pieces first, then switch`() {
        val ladder = ladder(usual(GUM_4 to 4, GUM_2 to 4))
        val expected = listOf(mix(GUM_4 to 3, GUM_2 to 4), mix(GUM_4 to 2, GUM_2 to 4)) + (8 downTo 1).map { mix(GUM_2 to it) }
        assertEquals(expected, ladder.steps)
        assertEquals(mg(20.0, 16.0, 16.0, 14.0, 12.0, 10.0, 8.0, 6.0, 4.0, 2.0), ladder.totals)
    }

    @Test
    fun `a 4 mg lozenge switches to the catalogue's 2 mg in the same line`() {
        val ladder = ladder(usual(LOZENGE_4 to 6))
        val expected = (5 downTo 3).map { mix(LOZENGE_4 to it) } + (6 downTo 1).map { mix(LOZENGE_2 to it) }
        assertEquals(expected, ladder.steps)
        assertEquals(listOf(LOZENGE_2), ladder.newProducts)
        // Nicotinell's mint lozenge goes from 2 mg to 1 mg.
        val mint2 = product("nicotinell-lozenge-uk-2mg")
        val mint1 = product("nicotinell-lozenge-uk-1mg")
        assertEquals(listOf(mix(mint2 to 2), mix(mint2 to 1), mix(mint1 to 2), mix(mint1 to 1)), ladder(usual(mint2 to 3)).steps)
    }

    @Test
    fun `one piece of 4 mg gum steps to one piece of 2 mg`() {
        val ladder = ladder(usual(GUM_4 to 1))
        assertEquals(listOf(mix(GUM_2 to 1)), ladder.steps)
        assertEquals(listOf(GUM_2), ladder.newProducts)
    }

    @Test
    fun `a last strong piece that the switch covers becomes two weaker ones`() {
        // From 3 pieces a day, 1 piece of 4 mg is under 3 pieces of 2 mg, so it switches whole:
        // 2 pieces of 2 mg, the same 4 mg, rather than dropping straight to 1 piece.
        assertEquals(listOf(mix(GUM_4 to 2), mix(GUM_4 to 1), mix(GUM_2 to 2), mix(GUM_2 to 1)), ladder(usual(GUM_4 to 3)).steps)
    }

    @Test
    fun `one spray a day is already the bottom of the ladder`() {
        val ladder = ladder(usual(SPRAY to 1))
        assertEquals(emptyList<Mix>(), ladder.steps)
        assertEquals(0, ladder.size)
        assertEquals(mix(SPRAY to 1), ladder.mixAt(0))
    }

    @Test
    fun `a usual day over a pack's daily limit comes down to it at step 1`() {
        // Nicorette gum's pack allows 15 pieces a day.
        val ladder = ladder(usual(GUM_2 to 20))
        assertEquals((15 downTo 1).map { mix(GUM_2 to it) }, ladder.steps)
        assertEquals(1, ladder.top)
        // NiQuitin's lozenge pack allows 15 too. Only the lozenges come down, so step 1 is 70 mg,
        // rather than the gum's 40 mg going first while the lozenges wait.
        val both = ladder(usual(LOZENGE_2 to 16, GUM_4 to 10))
        assertEquals(1, both.top)
        assertEquals(listOf(mix(GUM_4 to 10, LOZENGE_2 to 15), mix(GUM_4 to 9, LOZENGE_2 to 15)), both.steps.take(2))
        assertEquals(Dose.mg(70), both.steps.first().total)
    }

    @Test
    fun `a switch waits until the lower strength's pieces fit its pack's limit`() {
        // 10 pieces of 4 mg would switch to 20 of 2 mg, over the 15 a day the pack allows, so the
        // 4 mg pieces keep coming off until 7 of them switch to 14 of 2 mg.
        val strong = ladder(usual(GUM_4 to 20))
        assertEquals((15 downTo 7).map { mix(GUM_4 to it) } + (14 downTo 1).map { mix(GUM_2 to it) }, strong.steps)
        // A mix of strengths loses its 4 mg pieces one at a time, never 28 mg in one step.
        val mixed = ladder(usual(GUM_4 to 15, GUM_2 to 15))
        assertEquals((14 downTo 0).map { mix(GUM_4 to it, GUM_2 to 15) } + (14 downTo 1).map { mix(GUM_2 to it) }, mixed.steps)
        val tenAndEight = ladder(usual(GUM_4 to 10, GUM_2 to 8))
        assertEquals((9 downTo 3).map { mix(GUM_4 to it, GUM_2 to 8) } + (14 downTo 1).map { mix(GUM_2 to it) }, tenAndEight.steps)
        // The user's own 2 mg gum, with a pack limit of 5 a day: 8 pieces of 4 mg switch at 2 pieces.
        val small2 = GUM_2.copy(label = LabelLimit(maxUnitsPerDay = 5))
        val own = ladder(usual(GUM_4 to 8), own = listOf(GUM_4, small2))
        assertEquals((7 downTo 2).map { mix(GUM_4 to it) } + (4 downTo 1).map { mix(small2 to it) }, own.steps)
    }

    @Test
    fun `two patches step down a strength at a time, within each pack's limit of one`() {
        // The 21 mg can't become a second 14 mg, so the 14 mg steps down first, and when neither
        // can, the weaker one comes off.
        val pair = ladder(usual(PATCH_21 to 1, PATCH_14 to 1))
        val expected = listOf(
            mix(PATCH_21 to 1, PATCH_7 to 1),
            mix(PATCH_14 to 1, PATCH_7 to 1),
            mix(PATCH_14 to 1),
            mix(PATCH_7 to 1),
        )
        assertEquals(expected, pair.steps)
        // Two 21 mg patches are over the limit, so step 1 makes one of them a 14 mg.
        val two = ladder(usual(PATCH_21 to 2))
        assertEquals(1, two.top)
        assertEquals(listOf(mix(PATCH_21 to 1, PATCH_14 to 1)) + expected, two.steps)
    }

    @Test
    fun `a tie goes to the stronger unit, even in a form listed later`() {
        // 14 mg each: the patch's 14 mg is a stronger unit than a 2 mg piece of gum.
        assertEquals(mix(PATCH_7 to 1, GUM_2 to 7), ladder(usual(PATCH_14 to 1, GUM_2 to 7)).steps.first())
        // 8 mg each: a 4 mg lozenge is stronger than a 2 mg piece of gum.
        assertEquals(mix(LOZENGE_4 to 1, GUM_2 to 4), ladder(usual(LOZENGE_4 to 2, GUM_2 to 4)).steps.first())
    }

    @Test
    fun `the user's own product comes before the catalogue's, of the same strength`() {
        val tesco2 = product("tesco-gum-2mg")
        val ladder = ladder(usual(GUM_4 to 2), own = listOf(GUM_4, tesco2))
        assertEquals(listOf(mix(GUM_4 to 1), mix(tesco2 to 2), mix(tesco2 to 1)), ladder.steps)
        assertEquals(emptyList<Any>(), ladder.newProducts)
        // A hidden one still counts as the user's own, after any shown ones.
        val hidden = ladder(usual(GUM_4 to 2), own = listOf(GUM_4, tesco2.copy(isHidden = true)))
        assertEquals(tesco2.copy(isHidden = true), hidden.steps.last().products.single())
        val superdrug2 = product("superdrug-gum-2mg").copy(isHidden = true)
        val shown = ladder(usual(GUM_4 to 2), own = listOf(GUM_4, superdrug2, tesco2))
        assertEquals(tesco2, shown.steps.last().products.single())
    }

    @Test
    fun `a line with no lower strength steps down through the same brand's other lines`() {
        // NiQuitin Pre-Quit is 21 mg only. NiQuitin Clear has 14 and 7 mg.
        val preQuit = product("niquitin-pre-quit-patch-uk-21mg")
        val alone = ladder(usual(preQuit to 1))
        assertEquals(listOf(mix(PATCH_14 to 1), mix(PATCH_7 to 1)), alone.steps)
        assertEquals(listOf(PATCH_14, PATCH_7), alone.newProducts)
        // With gum, the patch steps down rather than coming off in one go.
        assertEquals(mix(PATCH_14 to 1, GUM_2 to 4), ladder(usual(preQuit to 1, GUM_2 to 4)).steps.first())
        // Nicabate Classic in Australia steps down through Nicabate Clear.
        val classic = product("nicabate-classic-patch-au-21mg")
        val clear14 = product("nicabate-clear-patch-au-14mg")
        val clear7 = product("nicabate-clear-patch-au-7mg")
        assertEquals(listOf(mix(clear14 to 1), mix(clear7 to 1)), ladder(usual(classic to 1), market = "AU").steps)
        // Another line of the brand counts only where it's sold: Habitrol's 1 mg lozenge is a New
        // Zealand pack, so the US 2 mg one has nothing lower in the US.
        val habitrolUs2 = product("habitrol-lozenge-us-2mg")
        val habitrolNz1 = product("habitrol-lozenge-nz-1mg")
        assertEquals(listOf(mix(habitrolUs2 to 1)), ladder(usual(habitrolUs2 to 2), market = "US").steps)
        assertEquals(
            listOf(mix(habitrolUs2 to 1), mix(habitrolNz1 to 2), mix(habitrolNz1 to 1)),
            ladder(usual(habitrolUs2 to 2), market = "NZ").steps,
        )
    }

    @Test
    fun `the lines in use count wherever they're sold, but never a discontinued pack`() {
        // NiQuitin gum is discontinued, so its 2 mg can't be bought, and one piece is the bottom.
        val oldStock = product("niquitin-gum-uk-4mg")
        assertEquals(emptyList<Mix>(), ladder(usual(oldStock to 1)).steps)
        // The US Nicorette lozenge steps to its US 2 mg, for a UK user too.
        val us4 = product("nicorette-lozenge-us-4mg")
        val us2 = product("nicorette-lozenge-us-2mg")
        assertEquals(listOf(mix(us2 to 1)), ladder(usual(us4 to 1), market = "US").steps)
        assertEquals(listOf(mix(us2 to 1)), ladder(usual(us4 to 1), market = "GB").steps)
        // Somewhere else: UK Nicorette gum still switches to its 2 mg, as PLAN's second example does.
        val elsewhere = ladder(usual(GUM_4 to 8), market = "ZZ")
        assertEquals(ladder(usual(GUM_4 to 8)).steps, elsewhere.steps)
        assertTrue(mix(GUM_2 to 8) in elsewhere.steps)
        // The 1.5 mg Minis are discontinued, so one 2 mg Mini is the bottom.
        val minis2 = product("niquitin-minis-uk-2mg")
        assertEquals(listOf(mix(minis2 to 1)), ladder(usual(minis2 to 2)).steps)
    }

    @Test
    fun `an inhalator steps down in sessions`() {
        val inhalator = product("nicorette-inhalator-uk-15mg")
        val ladder = ladder(usual(inhalator to 6))
        assertEquals((5 downTo 1).map { mix(inhalator to it) }, ladder.steps)
        assertEquals(Dose(1875 * 5L), ladder.steps.first().total)
    }

    @Test
    fun `a ladder read back has to keep to its rules`() {
        val usual = usual(GUM_2 to 4)
        assertThrows<IllegalArgumentException> { Ladder(usual, listOf(Mix.NONE)) }
        assertThrows<IllegalArgumentException> { Ladder(usual, listOf(mix(GUM_2 to 2), mix(GUM_2 to 3))) }
        assertThrows<IllegalArgumentException> { Ladder(usual, emptyList()).mixAt(1) }
    }

    @Test
    fun `a ladder read back still loads after a pack's limit is lowered`() {
        // Built for 12 pieces a day, then the pack's limit set to 10 in Settings.
        val built = ladder(usual(GUM_2 to 12))
        val lowered = GUM_2.copy(label = LabelLimit(maxUnitsPerDay = 10))
        val stored = Ladder(usual(lowered to 12), built.steps.map { mix(lowered to it.units) })
        assertEquals(built.size, stored.size)
        // A week can go no higher than the first step within the new limit.
        assertEquals(2, stored.top)
        assertEquals(mix(lowered to 10), stored.mixAt(stored.top))
    }
}
