package app.thelasthit.nrt.domain.matcher

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ProductMatcherTest {

    private val catalogue = Catalogue.bundled()

    private fun item(id: String, quickLog: Int? = null) = requireNotNull(catalogue.find(id)) { "missing $id" }.toUserProduct(quickLog)

    private fun custom(id: String, name: String, form: ProductForm, mg: Double, unit: DoseUnit = form.defaultUnit) = UserProduct(
        id = id,
        catalogueId = null,
        name = name,
        shortName = name,
        form = form,
        unit = unit,
        perUnit = Dose.mg(mg),
    )

    /** What a match comes to, in a form a table can state. */
    private sealed interface Expect {
        data class Found(val id: String, val quantity: Int) : Expect
        data class Ambiguous(val ids: List<String>) : Expect
        data object Unknown : Expect
    }

    private fun found(id: String, quantity: Int = 1) = Expect.Found(id, quantity)
    private fun ambiguous(vararg ids: String) = Expect.Ambiguous(ids.toList())
    private val unknown = Expect.Unknown

    private fun ProductMatch.expect(): Expect = when (this) {
        is ProductMatch.Found -> Expect.Found(product.id, quantity)
        is ProductMatch.Ambiguous -> Expect.Ambiguous(candidates.map { it.product.id })
        ProductMatch.Unknown -> Expect.Unknown
    }

    private fun table(matcher: ProductMatcher, vararg rows: Pair<String, Expect>): List<DynamicTest> =
        rows.map { (phrase, expected) ->
            dynamicTest("\"$phrase\" is $expected") { assertEquals(expected, matcher.match(phrase).expect(), phrase) }
        }

    private val gum2 = "nicorette-gum-uk-2mg"
    private val gum4 = "nicorette-gum-uk-4mg"
    private val mist = "nicorette-quickmist-uk-1mg"
    private val inhalator = "nicorette-inhalator-uk-15mg"
    private val patch = "niquitin-clear-patch-uk-21mg"
    private val minis = "niquitin-minis-uk-4mg"
    private val nasal = "nicorette-nasal-spray-uk-0-5mg"

    /** Someone in the UK with one of most forms. Every phrase from PLAN §12 names one product here. */
    private val mine = ProductMatcher(
        listOf(item(gum2, 0), item(gum4, 1), item(mist, 2), item(inhalator), item(patch), item(minis), item(nasal)),
        catalogue,
    )

    @TestFactory
    fun `the phrases from the plan`() = table(
        mine,
        "2mg nicotine gum" to found(gum2),
        "two 4 mg gums" to found(gum4, 2),
        "nicorette 4" to found(gum4),
        "21mg patch" to found(patch),
        "two sprays of QuickMist" to found(mist, 2),
        "inhalator session" to found(inhalator),
    )

    @TestFactory
    fun `numbers, strengths and counts`() = table(
        mine,
        "2 mg nicotine gum" to found(gum2),
        "2MG GUM" to found(gum2),
        "4 milligram gum" to found(gum4),
        "a 2 mg gum" to found(gum2),
        "four 2mg gums" to found(gum2, 4),
        "4 2mg gums" to found(gum2, 4),
        "2 4 gums" to found(gum4, 2),
        "2mg gum 3" to found(gum2, 3),
        "nicorette gum 2mg x3" to found(gum2, 3),
        "2x 4mg gum" to found(gum4, 2),
        "4mg gum × 2" to found(gum4, 2),
        "a couple of 2mg gums" to found(gum2, 2),
        "two pieces of nicorette gum 2mg" to found(gum2, 2),
        "0.5mg spray" to found(nasal),
        "0,5 mg nasal spray" to found(nasal),
        ".5mg nasal spray" to found(nasal),
        "15mg inhalator" to found(inhalator),
        "niquitin minis 4" to found(minis),
        "a 24 hour patch" to found(patch),
        "24h patch" to found(patch),
        "twenty one mg patch" to unknown,
    )

    @TestFactory
    fun `names, forms and their synonyms`() = table(
        mine,
        "Nicorette gum 2 mg" to found(gum2),
        "nicorette-gum-2mg" to found(gum2),
        "Quick Mist" to found(mist),
        "quickmist" to found(mist),
        "a puff of QuickMist" to found(mist),
        "3 puffs" to found(mist, 3),
        "mouth spray" to found(mist),
        "oral spray" to found(mist),
        "nicorette mouth spray" to found(mist),
        "nasal spray" to found(nasal),
        "nose spray" to found(nasal),
        "chewing gum 4mg" to found(gum4),
        "Nicorette Icy White gum 2mg" to found(gum2),
        "log two 4mg gums please" to found(gum4, 2),
        "patches" to found(patch),
        "4 mg lozenge" to found(minis),
        "mini" to found(minis),
        "inhaler" to found(inhalator),
    )

    @TestFactory
    fun `the inhalator logged by session`() = table(
        mine,
        "inhalator session" to found(inhalator),
        "an inhalator session" to found(inhalator),
        "3 sessions" to found(inhalator, 3),
        "an inhalator cartridge" to found(inhalator, 8),
        "2 cartridges" to found(inhalator, 16),
        "inhalator" to found(inhalator),
        "inhalator puff" to unknown,
        "half a cartridge" to unknown,
    )

    @TestFactory
    fun `ambiguous phrases come back ranked by the quick log`() = table(
        mine,
        "nicorette gum" to ambiguous(gum2, gum4),
        "gum" to ambiguous(gum2, gum4),
        "two gums" to ambiguous(gum2, gum4),
        "4mg" to ambiguous(gum4, minis),
        "spray" to ambiguous(mist, nasal),
        "nicorette spray" to ambiguous(mist, nasal),
        "nicorette" to ambiguous(gum2, gum4, mist, inhalator, nasal),
        "NiQuitin" to ambiguous(minis, patch),
        "fresh mint gum" to ambiguous(gum2, gum4),
    )

    @TestFactory
    fun `phrases that match nothing`() = table(
        mine,
        "" to unknown,
        "   " to unknown,
        "please" to unknown,
        "two" to unknown,
        "2" to unknown,
        "6mg gum" to unknown,
        "200mg gum" to unknown,
        "0 gums" to unknown,
        "zero gums" to unknown,
        "boots gum" to unknown,
        "cinnamon gum" to unknown,
        "2mg lozenge" to unknown,
        "16 hour patch" to unknown,
        "nicotine patch 7mg" to unknown,
        "1/2 gum" to unknown,
        "gum patch" to unknown,
        "2mg 4mg gum" to unknown,
        "vape" to unknown,
    )

    private val nicassist = "boots-nicassist-gum-2mg"
    private val nicotinellPatch = "nicotinell-patch-uk-21mg"
    private val cools = "nicorette-cools-uk-2mg"
    private val minis2 = "niquitin-minis-uk-2mg"

    /** Products that read alike, plus two of the user's own. */
    private val crowded = ProductMatcher(
        listOf(
            item(gum2, 1),
            item(nicassist, 0),
            item(patch),
            item(nicotinellPatch),
            item(cools),
            item(minis2),
            custom("zyn", "Zyn", ProductForm.POUCH, 6.0),
            custom("travel", "Travel pack", ProductForm.OTHER, 3.0),
        ),
        catalogue,
    )

    @TestFactory
    fun `products that read alike`() = table(
        crowded,
        "2mg gum" to ambiguous(nicassist, gum2),
        "21mg patch" to ambiguous(nicotinellPatch, patch),
        "2mg" to ambiguous(nicassist, gum2, cools, minis2),
        "nicorette 2" to ambiguous(gum2, cools),
        "boots gum" to found(nicassist),
        "nic assist 2" to found(nicassist),
        "nicotinell 21" to found(nicotinellPatch),
        "clear patch" to found(patch),
        "2mg lozenge" to found(cools),
        "mini lozenge" to found(minis2),
        "zyn" to found("zyn"),
        "two zyn pouches" to found("zyn", 2),
        "6mg pouch" to found("zyn"),
        "travel pack" to found("travel"),
        "the 3 mg travel pack" to found("travel"),
        "travel gum" to unknown,
    )

    @TestFactory
    fun `an inhalator logged in puffs or whole cartridges`(): List<DynamicTest> {
        val puffs = UserProduct(
            id = "puffs",
            catalogueId = null,
            name = "My inhalator",
            shortName = "My inhalator",
            form = ProductForm.INHALATOR,
            unit = DoseUnit.PUFF,
            perUnit = Dose(188),
            cartridge = Dose.mg(15),
            puffsPerCartridge = 80,
        )
        val cartridges = UserProduct(
            id = "cartridges",
            catalogueId = null,
            name = "Old inhalator",
            shortName = "Old inhalator",
            form = ProductForm.INHALATOR,
            unit = DoseUnit.CARTRIDGE,
            perUnit = Dose.mg(10),
            cartridge = Dose.mg(10),
        )
        return table(
            ProductMatcher(listOf(puffs)),
            "inhalator puff" to found("puffs"),
            "3 puffs" to found("puffs", 3),
            "an inhalator session" to found("puffs", 10),
            "a cartridge" to found("puffs", 80),
            "15mg inhalator" to found("puffs"),
        ) + table(
            ProductMatcher(listOf(cartridges)),
            "inhalator cartridge" to found("cartridges"),
            "2 cartridges" to found("cartridges", 2),
            "inhalator" to found("cartridges"),
            "inhalator session" to unknown,
            "a puff" to unknown,
        )
    }

    @Test
    fun `an exact id is that product, with no count of its own`() {
        val match = mine.match("nicorette-gum-uk-4mg")
        assertEquals(ProductMatch.Found(ProductCandidate(item(gum4, 1)), count = null), match)
        assertEquals(1, (match as ProductMatch.Found).quantity)
        assertEquals(found("zyn"), crowded.match("ZYN").expect())
    }

    @Test
    fun `the count is the phrase's own, apart from any unit conversion`() {
        assertEquals(null, mine.match("21mg patch").count)
        assertEquals(2, mine.match("two 4 mg gums").count)
        val cartridges = mine.match("2 cartridges") as ProductMatch.Found
        assertEquals(2, cartridges.count)
        assertEquals(8, cartridges.candidate.unitsPerCount)
        assertEquals(24, cartridges.candidate.quantityFor(3))
        assertEquals(2, mine.match("two gums").count)
    }

    @Test
    fun `hidden products are left out`() {
        val matcher = ProductMatcher(listOf(item(gum2), item(gum4).copy(isHidden = true)), catalogue)
        assertEquals(found(gum2), matcher.match("nicorette gum").expect())
        assertEquals(unknown, matcher.match("4mg gum").expect())
        assertEquals(unknown, matcher.match(gum4).expect())
    }

    @Test
    fun `the catalogue adds brands, lines and flavours`() {
        val products = listOf(item(gum2), item("nicotinell-spray-uk-1mg"))
        val withCatalogue = ProductMatcher(products, catalogue)
        val without = ProductMatcher(products)

        assertEquals(found("nicotinell-spray-uk-1mg"), withCatalogue.match("rapid relief spray").expect())
        assertEquals(unknown, without.match("rapid relief spray").expect())
        assertEquals(found(gum2), withCatalogue.match("icy white gum").expect())
        assertEquals(unknown, without.match("icy white gum").expect())
        // The catalogue ID's own words still work without it.
        assertEquals(found(gum2), without.match("nicorette gum uk 2mg").expect())
    }

    @Test
    fun `ties keep the quick log order, then go by name`() {
        val matcher = ProductMatcher(
            listOf(item("nicotinell-gum-uk-2mg"), item(gum2), item("tesco-gum-2mg", 1), item("asda-gum-2mg", 0)),
            catalogue,
        )
        assertEquals(
            ambiguous("asda-gum-2mg", "tesco-gum-2mg", gum2, "nicotinell-gum-uk-2mg"),
            matcher.match("2mg gum").expect(),
        )
    }
}
