package app.thelasthit.nrt.domain.absorbed

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.DoseMath
import app.thelasthit.nrt.domain.dose.InhalatorUnit
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AbsorbedEstimateTest {

    private fun estimate(labelled: Dose, form: ProductForm, own: AbsorptionFactor? = null) =
        requireNotNull(AbsorbedEstimate.of(labelled, form, own)) { "no estimate for $form" }

    private var nextId = 0

    private fun entry(form: ProductForm, total: Dose, productId: String = "p") = Entry(
        id = "e${nextId++}",
        productId = productId,
        productName = "Test",
        form = form,
        unit = form.defaultUnit,
        perUnit = total,
        quantity = 1,
        total = total,
        occurredAt = Instant.parse("2026-10-23T12:00:00Z"),
        zone = ZoneId.of("Europe/London"),
        logicalDate = LocalDate.of(2026, 10, 23),
    )

    @Test
    fun `the labelled mg times the form's factor, in whole micrograms`() {
        assertEquals(Dose.mg(2), estimate(Dose.mg(4), ProductForm.GUM))
        assertEquals(Dose.mg(1), estimate(Dose.mg(2), ProductForm.GUM))
        assertEquals(Dose(2400), estimate(Dose.mg(4), ProductForm.LOZENGE))
        assertEquals(Dose(600), estimate(Dose.mg(1), ProductForm.MOUTH_SPRAY))
        assertEquals(Dose(1000), estimate(Dose.mg(2), ProductForm.MICROTAB))
        assertEquals(Dose(16_800), estimate(Dose.mg(21), ProductForm.PATCH_24H))
        assertEquals(Dose(20_000), estimate(Dose.mg(25), ProductForm.PATCH_16H))
    }

    @Test
    fun `odd amounts round to the nearest microgram, halves up`() {
        // Half a mg of nasal spray at 53% is 265 µg exactly; one spray of 0.5 mg.
        assertEquals(Dose(265), estimate(Dose(500), ProductForm.NASAL_SPRAY))
        // 1 µg of gum is half a microgram absorbed, which rounds up.
        assertEquals(Dose(1), estimate(Dose(1), ProductForm.GUM))
        // 3 µg at 53% is 1.59 µg.
        assertEquals(Dose(2), estimate(Dose(3), ProductForm.NASAL_SPRAY))
        assertEquals(Dose.ZERO, estimate(Dose.ZERO, ProductForm.GUM))
    }

    @Test
    fun `an inhalator session is an eighth of the cartridge, then the factor`() {
        val session = DoseMath.inhalatorPerUnit(Dose.mg(15), InhalatorUnit.SESSION)
        assertEquals(Dose(1875), session)
        assertEquals(Dose(375), estimate(session, ProductForm.INHALATOR))
    }

    @Test
    fun `a product's own factor wins over its form's`() {
        assertEquals(Dose(1800), estimate(Dose.mg(2), ProductForm.GUM, own = AbsorptionFactor(900)))
        assertEquals(AbsorptionFactor(900), AbsorbedEstimate.factorFor(ProductForm.GUM, AbsorptionFactor(900)))
        assertEquals(AbsorptionFactor(500), AbsorbedEstimate.factorFor(ProductForm.GUM))
        // Even for a form with no factor of its own.
        assertEquals(Dose(700), estimate(Dose.mg(1), ProductForm.OTHER, own = AbsorptionFactor(700)))
    }

    @Test
    fun `other has no factor, so no estimate`() {
        assertNull(AbsorbedEstimate.factorFor(ProductForm.OTHER))
        assertNull(AbsorbedEstimate.of(Dose.mg(3), ProductForm.OTHER))
    }

    @Test
    fun `a total adds up each dose's own estimate`() {
        val doses = listOf(
            entry(ProductForm.GUM, Dose.mg(4)),
            entry(ProductForm.NASAL_SPRAY, Dose(500)),
            entry(ProductForm.NASAL_SPRAY, Dose(500)),
            entry(ProductForm.PATCH_24H, Dose.mg(14)),
        )
        // 2000 + 265 + 265 + 11 200, where 53% of the 1 mg together would be 530.
        assertEquals(Dose(13_730), AbsorbedEstimate.total(doses))
        assertEquals(doses.sumOf { estimate(it.total, it.form).micrograms }, AbsorbedEstimate.total(doses)?.micrograms)
    }

    @Test
    fun `a total uses each product's own factor`() {
        val doses = listOf(entry(ProductForm.GUM, Dose.mg(2), productId = "own"), entry(ProductForm.GUM, Dose.mg(2)))
        val total = AbsorbedEstimate.total(doses) { if (it.productId == "own") AbsorptionFactor(1000) else null }
        assertEquals(Dose(3000), total)
    }

    @Test
    fun `no doses, or one that can't be estimated, gives no total`() {
        assertNull(AbsorbedEstimate.total(emptyList()))
        assertNull(AbsorbedEstimate.total(listOf(entry(ProductForm.GUM, Dose.mg(2)), entry(ProductForm.OTHER, Dose.mg(1)))))
    }

    @Test
    fun `the estimate leaves the logged amount as it was`() {
        val dose = entry(ProductForm.GUM, Dose.mg(4))
        AbsorbedEstimate.of(dose)
        AbsorbedEstimate.total(listOf(dose))
        assertEquals(Dose.mg(4), dose.total)
    }

    @Test
    fun `a factor is between 1 and 1000 thousandths`() {
        assertThrows<IllegalArgumentException> { AbsorptionFactor(0) }
        assertThrows<IllegalArgumentException> { AbsorptionFactor(1001) }
        assertEquals(53, AbsorptionFactor(530).percent)
        assertEquals(20, AbsorptionFactor(200).percent)
    }

    @Test
    fun `a catalogue line can carry its own factor`() {
        val text = """
            {"version": 1, "lines": [{
              "id": "test-gum", "brand": "Test", "line": "Gum", "shortName": "Test gum", "kind": "BRAND",
              "form": "GUM", "unit": "PIECE", "markets": ["GB"], "status": "CURRENT", "flavours": [],
              "strengths": [{"mg": 2}], "absorptionPerMille": 650
            }]}
        """.trimIndent()
        val product = Catalogue.parse(text).products.single()
        assertEquals(AbsorptionFactor(650), product.absorption)
        assertEquals(Dose(1300), estimate(product.strength, product.form, product.absorption))
    }

    @Test
    fun `a line without one leaves it to the form`() {
        val gum = requireNotNull(Catalogue.bundled().find("nicorette-gum-uk-2mg"))
        assertNull(gum.absorption)
        assertEquals(Dose(1000), estimate(gum.strength, gum.form, gum.absorption))
    }
}
