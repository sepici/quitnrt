package app.thelasthit.nrt.domain.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProductFormTest {

    @Test
    fun `only patches are background doses and only they have rated hours`() {
        for (form in ProductForm.entries) {
            val isPatch = form == ProductForm.PATCH_16H || form == ProductForm.PATCH_24H
            assertEquals(isPatch, form.isBackground, "$form")
            if (isPatch) assertNotNull(form.ratedHours, "$form") else assertNull(form.ratedHours, "$form")
        }
    }

    @Test
    fun `the inhalator logs sessions by default`() {
        assertEquals(DoseUnit.SESSION, ProductForm.INHALATOR.defaultUnit)
    }
}
