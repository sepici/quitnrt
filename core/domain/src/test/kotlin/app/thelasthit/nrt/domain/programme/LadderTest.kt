package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LadderTest {

    private fun mg(vararg values: Double) = values.map(Dose::mg)

    private val Ladder.totals: List<Dose> get() = steps.map { it.total }

    private val Ladder.switches: List<Int> get() = (1..size).filter(::isSwitch)

    /** A product added by hand in Settings: no catalogue line, so no brand to look in. */
    private fun byHand(id: String, form: ProductForm, mg: Int) =
        UserProduct(id, null, "My $id", "My $id", form, form.defaultUnit, Dose.mg(mg))

    /** A generic product the ladder falls back to, with the strictest daily limit of the packs it stands for. */
    private fun standIn(id: String, limit: Int) = product(id).copy(label = LabelLimit(maxUnitsPerDay = limit))

    // In the UK, the strictest 2 mg gum pack allows 15 a day, and both 1 mg lozenge packs 24.
    private val GENERIC_GUM_2 = standIn("generic-gum-2mg", 15)
    private val GENERIC_LOZENGE_1 = standIn("generic-lozenge-1mg", 24)
    private val GENERIC_PATCH_14 = product("generic-patch-24h-14mg")
    private val GENERIC_PATCH_7 = product("generic-patch-24h-7mg")

    @Test
    fun `a pack whose brand has no lower strength falls back to the generic line in its market`() {
        // Equate's patch in the US is 21 mg only, and Equate has no other patch: the generic
        // 14 mg and 7 mg follow, so the plan doesn't drop from 21 mg straight to nothing.
        val equate = product("equate-patch-us-21mg")
        val ladder = ladder(usual(equate to 1), market = "US")
        assertEquals(listOf(mix(GENERIC_PATCH_14 to 1), mix(GENERIC_PATCH_7 to 1)), ladder.steps)
        assertEquals(listOf(GENERIC_PATCH_14, GENERIC_PATCH_7), ladder.newProducts)
        // With gum, the patch steps down a strength at a time too.
        assertEquals(mix(GENERIC_PATCH_14 to 1, GUM_2 to 4), ladder(usual(equate to 1, GUM_2 to 4), market = "US").steps.first())
        // up&up in the US and Pharmacy Care in Australia are 21 mg only as well.
        assertEquals(ladder.steps, ladder(usual(product("up-and-up-patch-us-21mg") to 1), market = "US").steps)
        assertEquals(ladder.steps, ladder(usual(product("pharmacy-care-patch-au-21mg") to 1), market = "AU").steps)
        // Life Brand's Canadian patch is 14 mg only.
        assertEquals(listOf(mix(GENERIC_PATCH_7 to 1)), ladder(usual(product("life-brand-patch-ca-14mg") to 1), market = "CA").steps)
        // The CVS and Walgreens minis are 4 mg only. The generic mini lozenge comes in 1.5 mg
        // too, but no mini lozenge in the US is, so 2 mg is the bottom.
        val genericMini2 = standIn("generic-mini-lozenge-2mg", 20)
        for (mini in listOf("cvs-mini-lozenge-us-4mg", "walgreens-mini-lozenge-us-4mg")) {
            val minis = ladder(usual(product(mini) to 4), market = "US")
            assertEquals(listOf(mix(product(mini) to 3), mix(product(mini) to 2), mix(genericMini2 to 4)) + (3 downTo 1).map { mix(genericMini2 to it) }, minis.steps)
            assertEquals(listOf(genericMini2), minis.newProducts)
        }
    }

    @Test
    fun `a generic stand-in keeps to the strictest daily limit of the packs it stands for`() {
        // Every US 2 mg mini lozenge says 20 a day, so the generic one standing in for the CVS
        // 4 mg mini does too. 24 a day comes down to 20 at step 1, and the switch waits until 10
        // pieces of 4 mg become 20 of 2 mg, as Nicorette's own minis do.
        val cvs4 = product("cvs-mini-lozenge-us-4mg")
        val genericMini2 = standIn("generic-mini-lozenge-2mg", 20)
        val cvs = ladder(usual(cvs4 to 24), market = "US")
        assertEquals((20 downTo 10).map { mix(cvs4 to it) } + (20 downTo 1).map { mix(genericMini2 to it) }, cvs.steps)
        assertEquals(listOf(genericMini2), cvs.newProducts)
        val nicorette4 = product("nicorette-mini-lozenge-us-4mg")
        val nicorette2 = product("nicorette-mini-lozenge-us-2mg")
        val nicorette = ladder(usual(nicorette4 to 24), market = "US")
        assertEquals((20 downTo 10).map { mix(nicorette4 to it) } + (20 downTo 1).map { mix(nicorette2 to it) }, nicorette.steps)
        // NiQuitin's discontinued 4 mg gum: UK 2 mg gum packs allow 15 a day at the strictest, so
        // 20 a day switches at 7 pieces to 14, as Nicorette's gum does.
        val oldStock = product("niquitin-gum-uk-4mg")
        assertEquals((15 downTo 7).map { mix(oldStock to it) } + (14 downTo 1).map { mix(GENERIC_GUM_2 to it) }, ladder(usual(oldStock to 20)).steps)
        // The stand-in joins the user's list from the catalogue, with no limit of its own. A
        // plan built later still keeps it to 20.
        val joined = ladder(usual(cvs4 to 24), own = listOf(cvs4, product("generic-mini-lozenge-2mg")), market = "US")
        assertEquals(cvs.steps, joined.steps)
        assertEquals(emptyList<Any>(), joined.newProducts)
        // A limit the user's own generic product has stays: 8 a day here.
        val own8 = product("generic-mini-lozenge-2mg").copy(label = LabelLimit(maxUnitsPerDay = 8))
        val small = ladder(usual(cvs4 to 24), own = listOf(cvs4, own8), market = "US")
        assertEquals((20 downTo 4).map { mix(cvs4 to it) } + (8 downTo 1).map { mix(own8 to it) }, small.steps)
        // Somewhere else no pack is sold, so the generic line has no limit to keep to.
        val own4 = byHand("gum-4", ProductForm.GUM, 4)
        assertTrue(mix(product("generic-gum-2mg") to 24) in ladder(usual(own4 to 24), market = "ZZ").steps)
    }

    @Test
    fun `a product added by hand falls back to the generic line of its form`() {
        // 4 mg gum added by hand steps down as PLAN's second example does, through generic 2 mg.
        val own4 = byHand("gum-4", ProductForm.GUM, 4)
        val gum = ladder(usual(own4 to 8))
        assertEquals((7 downTo 4).map { mix(own4 to it) } + (8 downTo 1).map { mix(GENERIC_GUM_2 to it) }, gum.steps)
        assertEquals(listOf(GENERIC_GUM_2), gum.newProducts)
        // The user's own 2 mg gum still comes first.
        val own2 = byHand("gum-2", ProductForm.GUM, 2)
        assertEquals(mix(own2 to 8), ladder(usual(own4 to 8), own = listOf(own4, own2)).steps[4])
        // A 21 mg patch added by hand somewhere else: no pack is sold there, so every strength of
        // the generic line counts.
        val ownPatch = byHand("patch-21", ProductForm.PATCH_24H, 21).copy(label = LabelLimit(maxUnitsPerDay = 1))
        assertEquals(listOf(mix(GENERIC_PATCH_14 to 1), mix(GENERIC_PATCH_7 to 1)), ladder(usual(ownPatch to 1), market = "ZZ").steps)
        // The catalogue has no generic pouch, so pouches come off one at a time.
        val pouch = byHand("pouch-6", ProductForm.POUCH, 6)
        assertEquals((2 downTo 1).map { mix(pouch to it) }, ladder(usual(pouch to 3)).steps)
    }

    @Test
    fun `the generic line offers only strengths a pack of its form is sold at in the market`() {
        // The generic lozenge comes in 4, 2, 1.5 and 1 mg. In the UK no lozenge is 1.5 mg, so a
        // generic 4 mg lozenge goes to 2 mg, then 1 mg.
        val generic4 = product("generic-lozenge-4mg")
        val generic2 = standIn("generic-lozenge-2mg", 15)
        val uk = ladder(usual(generic4 to 4))
        val ukExpected = listOf(mix(generic4 to 3), mix(generic4 to 2)) +
            (4 downTo 2).map { mix(generic2 to it) } +
            (4 downTo 1).map { mix(GENERIC_LOZENGE_1 to it) }
        assertEquals(ukExpected, uk.steps)
        // Somewhere else, nothing is sold, so 1.5 mg comes between them, and there are no limits.
        val elsewhere = ladder(usual(generic4 to 4), market = "ZZ")
        val elsewhereExpected = listOf(mix(generic4 to 3), mix(generic4 to 2)) +
            (4 downTo 3).map { mix(product("generic-lozenge-2mg") to it) } +
            (4 downTo 2).map { mix(product("generic-lozenge-1-5mg") to it) } +
            (3 downTo 1).map { mix(product("generic-lozenge-1mg") to it) }
        assertEquals(elsewhereExpected, elsewhere.steps)
        assertEquals(listOf(3, 5, 8), elsewhere.switches)
        // US lozenges are 2 and 4 mg, so a US 2 mg lozenge has nothing lower, generic or not.
        val us2 = product("nicorette-lozenge-us-2mg")
        assertEquals(listOf(mix(us2 to 1)), ladder(usual(us2 to 2), market = "US").steps)
        // A 15 mg inhalator in the UK keeps its sessions: no 10 mg cartridge is sold there.
        val inhalator = product("nicorette-inhalator-uk-15mg")
        assertTrue(ladder(usual(inhalator to 3)).steps.all { it.products == listOf(inhalator) })
    }

    @Test
    fun `a switch shows in the mixes, so a ladder read back has it too`() {
        // PLAN's second and third examples: the switch to 8 pieces of 2 mg is step 5.
        assertEquals(listOf(5), ladder(usual(GUM_4 to 8)).switches)
        assertEquals(listOf(5), ladder(usual(GUM_4 to 8, SPRAY to 3)).switches)
        // The 2 mg pieces already in the mix count up when the 4 mg ones switch.
        assertEquals(listOf(3), ladder(usual(GUM_4 to 4, GUM_2 to 4)).switches)
        // One piece of 4 mg moving to one of 2 mg is a switch too.
        assertEquals(listOf(1), ladder(usual(GUM_4 to 1)).switches)
        // Dropping the last 4 mg piece from a mix with 2 mg gum is one fewer, not a switch.
        val lastStrong = ladder(usual(GUM_4 to 1, GUM_2 to 4))
        assertEquals(mix(GUM_2 to 4), lastStrong.steps.first())
        assertEquals(emptyList<Int>(), lastStrong.switches)
        // Patches step down a strength at a time, and none of those steps is a switch.
        assertEquals(emptyList<Int>(), ladder(usual(PATCH_21 to 1)).switches)
        assertEquals(emptyList<Int>(), ladder(usual(PATCH_21 to 1, GUM_2 to 6)).switches)
        assertFalse(ladder(usual(GUM_4 to 8)).isSwitch(0))
        // Read back from storage, with no new products.
        val built = ladder(usual(GUM_4 to 8, SPRAY to 3))
        assertEquals(listOf(5), Ladder(built.usual, built.steps).switches)
        assertThrows<IllegalArgumentException> { built.isSwitch(built.size + 1) }
    }

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
    fun `a 4 mg lozenge switches to the catalogue's 2 mg in the same line, then to the generic 1 mg`() {
        // NiQuitin has no 1 mg lozenge, but 1 mg lozenges are sold in the UK, so the generic
        // line's 1 mg follows once 3 pieces of 2 mg come to no more than 6 pieces of 1 mg.
        val ladder = ladder(usual(LOZENGE_4 to 6))
        val expected = (5 downTo 3).map { mix(LOZENGE_4 to it) } +
            (6 downTo 3).map { mix(LOZENGE_2 to it) } +
            (6 downTo 1).map { mix(GENERIC_LOZENGE_1 to it) }
        assertEquals(expected, ladder.steps)
        assertEquals(listOf(LOZENGE_2, GENERIC_LOZENGE_1), ladder.newProducts)
        assertEquals(listOf(4, 8), (1..ladder.size).filter(ladder::isSwitch))
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
        // NiQuitin gum is discontinued, so its 2 mg can't be bought, and the generic 2 mg stands
        // in for it rather than the old stock dropping from 4 mg straight to nothing.
        val oldStock = product("niquitin-gum-uk-4mg")
        assertEquals(listOf(mix(GENERIC_GUM_2 to 1)), ladder(usual(oldStock to 1)).steps)
        // The US Nicorette lozenge steps to its US 2 mg, for a UK user too. 1 mg lozenges are sold
        // in the UK, though not in the US, so only the UK user goes on to the generic 1 mg.
        val us4 = product("nicorette-lozenge-us-4mg")
        val us2 = product("nicorette-lozenge-us-2mg")
        assertEquals(listOf(mix(us2 to 1)), ladder(usual(us4 to 1), market = "US").steps)
        assertEquals(listOf(mix(us2 to 1), mix(GENERIC_LOZENGE_1 to 1)), ladder(usual(us4 to 1), market = "GB").steps)
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
