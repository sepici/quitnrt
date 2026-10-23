package app.thelasthit.nrt.data

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.ProductForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductRepositoryTest {

    private lateinit var data: TestData

    @Before
    fun setUp() {
        data = TestData()
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun theInhalatorIsLoggedBySessionWithItsLimitConverted() = runTest {
        val inhalator = data.products.addFromCatalogue("nicorette-inhalator-uk-15mg")
        assertEquals(DoseUnit.SESSION, inhalator.unit)
        assertEquals(Dose(1875), inhalator.perUnit)
        assertEquals(Dose.mg(15), inhalator.cartridge)
        assertEquals(48, inhalator.label.maxUnitsPerDay)
    }

    @Test
    fun theQuickLogKeepsTheUsersOrder() = runTest {
        data.products.addFromCatalogue("nicorette-gum-uk-2mg")
        data.products.addFromCatalogue("nicorette-gum-uk-4mg")
        data.products.addFromCatalogue("nicorette-quickmist-uk-1mg")
        assertEquals(
            listOf("nicorette-gum-uk-2mg", "nicorette-gum-uk-4mg", "nicorette-quickmist-uk-1mg"),
            data.products.quickLog.first().map { it.id },
        )

        data.products.setQuickLog(listOf("nicorette-quickmist-uk-1mg", "nicorette-gum-uk-2mg"))
        assertEquals(listOf("nicorette-quickmist-uk-1mg", "nicorette-gum-uk-2mg"), data.products.quickLog.first().map { it.id })

        data.products.removeFromQuickLog("nicorette-quickmist-uk-1mg")
        data.products.addFromCatalogue("nicorette-gum-uk-4mg")
        assertEquals(listOf("nicorette-gum-uk-2mg", "nicorette-gum-uk-4mg"), data.products.quickLog.first().map { it.id })
        assertEquals(3, data.products.all.first().size)
    }

    @Test
    fun customProductsCoverPouchesAndAnythingElse() = runTest {
        val pouch = data.products.addCustom("Nicotine pouch", ProductForm.POUCH, Dose.mg(6))
        assertTrue(pouch.id.startsWith("custom-"))
        assertEquals(DoseUnit.POUCH, pouch.unit)
        assertEquals(pouch, data.products.get(pouch.id))
    }

    @Test
    fun shortNamesCoverEveryProduct() = runTest {
        data.products.addFromCatalogue("nicorette-gum-uk-2mg")
        data.products.addFromCatalogue("nicorette-quickmist-uk-1mg", addToQuickLog = false)
        assertEquals(
            mapOf("nicorette-gum-uk-2mg" to "Nicorette gum", "nicorette-quickmist-uk-1mg" to "QuickMist"),
            data.products.shortNames.first(),
        )
    }

    @Test
    fun hiddenProductsLeaveTheQuickLogAndComeBack() = runTest {
        data.products.addFromCatalogue("nicorette-gum-uk-2mg")
        data.products.addFromCatalogue("nicorette-gum-uk-4mg")
        data.products.setHidden("nicorette-gum-uk-2mg", true)
        assertEquals(listOf("nicorette-gum-uk-4mg"), data.products.quickLog.first().map { it.id })
        assertEquals(2, data.products.everything.first().size)

        data.products.setHidden("nicorette-gum-uk-2mg", false)
        assertEquals(listOf("nicorette-gum-uk-2mg", "nicorette-gum-uk-4mg"), data.products.quickLog.first().map { it.id })
    }

    @Test
    fun anUpdateChangesTheNameAndLimitButNotTheDose() = runTest {
        val custom = data.products.addCustom("Holiday gum", ProductForm.GUM, Dose.mg(2))
        data.products.update(custom.id, "Duty free gum", LabelLimit(maxUnitsPerDay = 15))
        val updated = requireNotNull(data.products.get(custom.id))
        assertEquals("Duty free gum", updated.name)
        assertEquals("Duty free gum", updated.shortName)
        assertEquals(15, updated.label.maxUnitsPerDay)
        assertEquals(Dose.mg(2), updated.perUnit)
    }
}
