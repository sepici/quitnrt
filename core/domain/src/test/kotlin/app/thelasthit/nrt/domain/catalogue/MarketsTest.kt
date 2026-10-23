package app.thelasthit.nrt.domain.catalogue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketsTest {

    private val catalogue = Catalogue.bundled()

    @Test
    fun `the phone's region picks the market, or somewhere else`() {
        assertEquals("GB", Markets.forRegion("gb"))
        assertEquals("NZ", Markets.forRegion("NZ"))
        assertEquals(Markets.ELSEWHERE, Markets.forRegion("DE"))
        assertEquals(Markets.ELSEWHERE, Markets.forRegion(null))
    }

    @Test
    fun `somewhere else gets only the generic products`() {
        val products = catalogue.browse(Markets.ELSEWHERE)
        assertTrue(products.isNotEmpty())
        assertTrue(products.all { it.kind == LineKind.GENERIC })
    }

    @Test
    fun `other countries come after the local ones and generic products stay last`() {
        val products = catalogue.browse("GB", otherCountries = true)
        val firstForeign = products.indexOfFirst { it.markets.isNotEmpty() && "GB" !in it.markets }
        val lastLocal = products.indexOfLast { "GB" in it.markets }
        assertTrue(lastLocal < firstForeign, "UK products first")
        assertTrue(products.takeLastWhile { it.kind == LineKind.GENERIC }.isNotEmpty(), "generic at the end")
        assertTrue(products.none { it.status == ProductStatus.DISCONTINUED })
    }

    @Test
    fun `lines group strengths from weakest up, in catalogue order`() {
        val lines = CatalogueLine.group(catalogue.forMarket("GB").filter { it.brand == "NiQuitin" })
        val patch = lines.first { it.lineId == "niquitin-clear-patch-uk" }
        assertEquals(listOf(7.0, 14.0, 21.0), patch.products.map { it.strength.milligrams })
        assertEquals(lines.map { it.lineId }.distinct(), lines.map { it.lineId })
    }
}
