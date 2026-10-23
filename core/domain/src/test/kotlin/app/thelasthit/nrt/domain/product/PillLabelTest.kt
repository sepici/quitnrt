package app.thelasthit.nrt.domain.product

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.PillLabel.ByForm
import app.thelasthit.nrt.domain.product.PillLabel.ByName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PillLabelTest {

    private fun product(id: String, shortName: String, form: ProductForm, mg: Double, cartridgeMg: Double? = null) = UserProduct(
        id = id,
        catalogueId = id,
        name = shortName,
        shortName = shortName,
        form = form,
        unit = form.defaultUnit,
        perUnit = Dose.mg(mg),
        cartridge = cartridgeMg?.let(Dose::mg),
    )

    @Test
    fun `names the form when every pill reads differently`() {
        val labels = PillLabel.of(
            listOf(
                product("gum-2", "Nicorette gum", ProductForm.GUM, 2.0),
                product("gum-4", "Nicorette gum", ProductForm.GUM, 4.0),
                product("mist", "QuickMist", ProductForm.MOUTH_SPRAY, 1.0),
                product("patch", "NiQuitin patch", ProductForm.PATCH_24H, 14.0),
            ),
        )
        assertEquals(ByForm(FormWord.GUM, Dose.mg(2)), labels["gum-2"])
        assertEquals(ByForm(FormWord.GUM, Dose.mg(4)), labels["gum-4"])
        assertEquals(ByForm(FormWord.MOUTH_SPRAY, Dose.mg(1)), labels["mist"])
        assertEquals(ByForm(FormWord.PATCH, Dose.mg(14)), labels["patch"])
    }

    @Test
    fun `uses the product name for pills that would read the same`() {
        val labels = PillLabel.of(
            listOf(
                product("nicorette", "Nicorette gum", ProductForm.GUM, 2.0),
                product("nicassist", "NicAssist gum", ProductForm.GUM, 2.0),
                product("lozenge", "NiQuitin lozenge", ProductForm.LOZENGE, 2.0),
            ),
        )
        assertEquals(ByName("Nicorette gum", Dose.mg(2)), labels["nicorette"])
        assertEquals(ByName("NicAssist gum", Dose.mg(2)), labels["nicassist"])
        assertEquals(ByForm(FormWord.LOZENGE, Dose.mg(2)), labels["lozenge"])
    }

    @Test
    fun `16 and 24 hour patches of the same strength clash`() {
        val labels = PillLabel.of(
            listOf(
                product("invisi", "Nicorette Invisipatch", ProductForm.PATCH_16H, 15.0),
                product("nicassist", "NicAssist 24 h patch", ProductForm.PATCH_24H, 15.0),
            ),
        )
        assertEquals(ByName("Nicorette Invisipatch", Dose.mg(15)), labels["invisi"])
        assertEquals(ByName("NicAssist 24 h patch", Dose.mg(15)), labels["nicassist"])
    }

    @Test
    fun `the inhalator shows its cartridge strength`() {
        val labels = PillLabel.of(listOf(product("inhalator", "Nicorette Inhalator", ProductForm.INHALATOR, 1.875, cartridgeMg = 15.0)))
        assertEquals(ByForm(FormWord.INHALATOR, Dose.mg(15)), labels["inhalator"])
    }

    @Test
    fun `a product with no form word uses its name`() {
        val labels = PillLabel.of(listOf(product("custom", "Travel pack", ProductForm.OTHER, 3.0)))
        assertEquals(ByName("Travel pack", Dose.mg(3)), labels["custom"])
    }
}
