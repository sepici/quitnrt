package app.thelasthit.nrt.domain.catalogue

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.RollingLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class CatalogueTest {

    private val catalogue = Catalogue.bundled()
    private val markets = setOf("GB", "IE", "US", "CA", "AU", "NZ")

    private fun product(id: String) = requireNotNull(catalogue.find(id)) { "missing $id" }

    @Test
    fun `the bundled catalogue loads with tidy, unique ids`() {
        assertTrue(catalogue.products.size > 150, "only ${catalogue.products.size} products")
        assertEquals(catalogue.products.size, catalogue.products.map { it.id }.toSet().size)
        catalogue.products.forEach { assertTrue(Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(it.id), it.id) }
    }

    @Test
    fun `every product has a sensible shape`() {
        for (p in catalogue.products) {
            assertTrue(p.strength > Dose.ZERO, p.id)
            assertTrue(p.markets.all { it in markets }, "${p.id} has markets ${p.markets}")
            assertEquals(p.kind == LineKind.GENERIC, p.markets.isEmpty(), "${p.id} market rule")
            if (p.form.isBackground) assertEquals(1, p.label.maxUnitsPerDay, "${p.id} is a patch")
            if (p.form == ProductForm.INHALATOR) assertEquals(DoseUnit.CARTRIDGE, p.unit, p.id)
            if (p.form != ProductForm.INHALATOR) assertEquals(p.form.defaultUnit, p.unit, p.id)
        }
    }

    @Test
    fun `ids and display names read naturally`() {
        assertEquals("Nicorette gum 2 mg", product("nicorette-gum-uk-2mg").displayName)
        assertEquals("NiQuitin Minis 1.5 mg", product("niquitin-minis-1-5-uk-1-5mg").displayName)
        assertEquals("Nicorette nasal spray 0.5 mg", product("nicorette-nasal-spray-uk-0-5mg").displayName)
    }

    @Test
    fun `label limits come through per strength and per line`() {
        val nicotinell2 = product("nicotinell-gum-uk-2mg")
        val nicotinell4 = product("nicotinell-gum-uk-4mg")
        assertEquals(24, nicotinell2.label.maxUnitsPerDay)
        assertEquals(15, nicotinell4.label.maxUnitsPerDay)
        assertEquals(RollingLimit(1, Duration.ofHours(1)), nicotinell4.label.window)

        val quickMist = product("nicorette-quickmist-uk-1mg")
        assertEquals(64, quickMist.label.maxUnitsPerDay)
        assertEquals(RollingLimit(4, Duration.ofHours(1)), quickMist.label.window)

        val usLozenge = product("nicorette-lozenge-us-4mg")
        assertEquals(RollingLimit(5, Duration.ofHours(6)), usLozenge.label.window)

        assertNull(product("life-brand-gum-ca-2mg").label.maxUnitsPerDay, "unverified limits stay empty")
    }

    @Test
    fun `a market shows its own products and the generic ones, brands first`() {
        val uk = catalogue.forMarket("gb")
        val ids = uk.map { it.id }
        assertTrue("nicorette-gum-uk-2mg" in ids)
        assertTrue("generic-gum-2mg" in ids)
        assertFalse("nicorette-gum-us-2mg" in ids)
        assertFalse("niquitin-gum-uk-2mg" in ids, "discontinued is hidden by default")
        assertTrue("niquitin-gum-uk-2mg" in catalogue.forMarket("GB", includeDiscontinued = true).map { it.id })
        assertEquals(uk.map { it.kind }.sorted(), uk.map { it.kind })
    }

    @Test
    fun `search matches words and strengths`() {
        assertEquals(listOf("niquitin-minis-uk-4mg"), catalogue.search("niquitin minis 4").map { it.id })
        assertEquals("nicorette-quickmist-uk-1mg", catalogue.search("quickmist", country = "GB").first().id)
        assertEquals("nicorette-gum-uk-2mg", catalogue.search("Nicorette gum 2mg", country = "GB").first().id)
        assertTrue(catalogue.search("").isEmpty())
        assertTrue(catalogue.search("zyn").isEmpty())
    }

    @Test
    fun `broken catalogue files are rejected`() {
        assertThrows<IllegalArgumentException> { Catalogue.parse("""{"version": 2, "lines": []}""") }
        val line = """{"id":"x","brand":"B","line":"L","shortName":"S","kind":"BRAND","form":"GUM","unit":"PIECE",
            "markets":["GB"],"status":"CURRENT","flavours":[],"strengths":[{"mg":2},{"mg":2}]}"""
        assertThrows<IllegalArgumentException> { Catalogue.parse("""{"version": 1, "lines": [$line]}""") }
    }
}
