package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ByProductTest {

    @Test
    fun `shares are of mg, and units a day are across the whole period`() {
        val byProduct = ByProduct.of(
            listOf(
                entry("2026-10-20T07:30", productId = "patch", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14)),
                entry("2026-10-20T08:00", productId = "gum", quantity = 2),
                entry("2026-10-21T08:00", productId = "gum"),
            ),
            days = 2,
        )
        assertEquals(listOf("patch", "gum"), byProduct.products.map { it.productId })
        val gum = byProduct.products.last()
        assertEquals(Dose.mg(6), gum.total)
        assertEquals(0.3, gum.share, 1e-9)
        assertEquals(3, gum.units)
        assertEquals(1.5, gum.unitsPerDay, 1e-9)
        assertEquals(2, gum.days)
        assertEquals(ProductForm.GUM, gum.form)
        val patch = byProduct.products.first()
        assertEquals(1, patch.days)
        assertEquals(ProductForm.PATCH_24H, patch.form)
    }

    @Test
    fun `with nothing logged there's nothing to share`() {
        assertEquals(emptyList<ByProduct.Share>(), ByProduct.of(emptyList(), days = 7).products)
    }
}
