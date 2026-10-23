package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BaselineTest {

    private val today = date("2026-10-23")
    private val products = listOf(GUM_2, GUM_4, SPRAY, PATCH_21)

    /** [mg] of 2 mg gum on each day of October listed, at 10:00. */
    private fun daily(vararg days: Pair<Int, Int>) = days.map { (day, mg) -> use("2026-10-%02dT10:00".format(day), GUM_2, mg / 2) }

    @Test
    fun `the last 7 full days are a baseline at once when 5 of them have doses`() {
        val entries = daily(16 to 20, 17 to 24, 19 to 28, 21 to 22, 22 to 26, 23 to 40)
        val days = BaselineDays.measure(today, entries)
        assertEquals(date("2026-10-16"), days.start)
        assertEquals(date("2026-10-22"), days.end)
        assertEquals(5, days.daysWithDoses)
        assertTrue(days.isReady)
        // The days with nothing logged are left out of the average, and so is today.
        assertEquals(UsualDay(mix(GUM_2 to 12), BaselineSource.MEASURED, measured = Dose.mg(24)), days.usualDay(products))
    }

    @Test
    fun `4 days with doses aren't enough`() {
        val days = BaselineDays.measure(today, daily(16 to 20, 17 to 24, 19 to 28, 21 to 22))
        assertTrue(days.isComplete)
        assertFalse(days.isReady)
        assertNull(days.usualDay(products))
        assertEquals(Dose.mg(23.5), days.averageSoFar)
    }

    @Test
    fun `a baseline started this week builds over its own 7 days`() {
        val days = BaselineDays.measure(today, daily(19 to 20, 20 to 22, 21 to 24, 22 to 26, 23 to 10), startedOn = date("2026-10-19"))
        assertEquals(date("2026-10-19"), days.start)
        assertEquals(date("2026-10-25"), days.end)
        assertEquals(listOf(true, true, true, true, false, false, false), days.days.map { it.isOver })
        assertEquals(4, days.daysWithDoses)
        assertFalse(days.isComplete)
        assertNull(days.usualDay(products))
        assertEquals(Dose.mg(23), days.averageSoFar)
    }

    @Test
    fun `a started baseline is ready once its seventh day is over`() {
        val entries = daily(19 to 20, 20 to 22, 22 to 26, 24 to 30, 25 to 22)
        val started = date("2026-10-19")
        val building = BaselineDays.measure(date("2026-10-25"), entries, started)
        assertEquals(started, building.start)
        assertFalse(building.isReady)
        val days = BaselineDays.measure(date("2026-10-26"), entries, started)
        assertEquals(started, days.start)
        assertEquals(Dose.mg(24), days.usualDay(products)?.total)
    }

    @Test
    fun `days logged before a baseline was started count when there are enough of them`() {
        val entries = daily(16 to 20, 17 to 24, 18 to 24, 19 to 28, 20 to 24, 21 to 24)
        val days = BaselineDays.measure(today, entries, startedOn = today)
        assertEquals(date("2026-10-16"), days.start)
        assertTrue(days.isReady)
    }

    @Test
    fun `when its 7 days end with too few logged, the window moves on a day at a time`() {
        val entries = daily(12 to 20, 14 to 20, 16 to 20, 18 to 20, 19 to 30, 20 to 30)
        val started = date("2026-10-12")
        val first = BaselineDays.measure(date("2026-10-19"), entries, started)
        assertEquals(started, first.start)
        assertFalse(first.isReady)
        val next = BaselineDays.measure(date("2026-10-20"), entries, started)
        assertEquals(date("2026-10-13"), next.start)
        assertFalse(next.isReady)
        val ready = BaselineDays.measure(date("2026-10-21"), entries, started)
        assertEquals(date("2026-10-14"), ready.start)
        assertEquals(mix(GUM_2 to 12), ready.usualDay(products)?.mix)
    }

    @Test
    fun `a dose after midnight counts toward the evening before`() {
        val entries = daily(16 to 20, 17 to 20, 18 to 20, 19 to 20) + use("2026-10-23T01:30", GUM_2, 2)
        val days = BaselineDays.measure(today, entries)
        assertEquals(Dose.mg(4), days.days.last().total)
        assertTrue(days.isReady)
    }

    @Test
    fun `each product is its average units a day on the days with doses, rounded half up`() {
        // Six days with doses and a Thursday with none. 39 pieces of 4 mg gum is 6.5 a day, which
        // rounds up to 7. 14 sprays is 2.3 a day, so 2. Two pieces of 2 mg gum round to none.
        val gum4 = listOf(7, 6, 7, 6, 7, 6)
        val sprays = listOf(2, 3, 2, 3, 2, 2)
        val entries = (16..21).flatMap { day ->
            listOf(
                use("2026-10-${day}T09:00", GUM_4, gum4[day - 16]),
                use("2026-10-${day}T13:00", SPRAY, sprays[day - 16]),
            )
        } + use("2026-10-17T20:00", GUM_2) + use("2026-10-20T20:00", GUM_2)
        val days = BaselineDays.measure(today, entries)
        assertEquals(6, days.daysWithDoses)
        val usual = requireNotNull(days.usualDay(products))
        assertEquals(mix(GUM_4 to 7, SPRAY to 2), usual.mix)
        assertEquals(Dose.mg(30), usual.total)
        // The measured average stays for the confirm screen: 174 mg over 6 days.
        assertEquals(Dose.mg(29), usual.measured)
        assertEquals(BaselineSource.MEASURED, usual.source)
    }

    @Test
    fun `the product with the most mg keeps at least one unit`() {
        // A 21 mg patch on 2 of 5 days is 0.4 a day, but it's the most mg, so it stays at one.
        val entries = (16..20).map { day -> use("2026-10-${day}T13:00", SPRAY) } +
            use("2026-10-16T07:00", PATCH_21) + use("2026-10-18T07:00", PATCH_21)
        val usual = BaselineDays.measure(today, entries).usualDay(products)
        assertEquals(mix(PATCH_21 to 1, SPRAY to 1), usual?.mix)
        // Everything rounding to none still leaves one unit of the product with the most mg.
        val scattered = listOf(
            use("2026-10-16T09:00", GUM_4), use("2026-10-17T09:00", GUM_4),
            use("2026-10-18T09:00", GUM_2), use("2026-10-19T09:00", GUM_2),
            use("2026-10-20T09:00", SPRAY),
        )
        assertEquals(mix(GUM_4 to 1), BaselineDays.measure(today, scattered).usualDay(products)?.mix)
    }

    @Test
    fun `today's doses don't count towards the units`() {
        val entries = daily(16 to 20, 17 to 20, 18 to 20, 19 to 20, 20 to 20) + use("2026-10-23T09:00", GUM_4, 10)
        val usual = BaselineDays.measure(today, entries).usualDay(products)
        assertEquals(mix(GUM_2 to 10), usual?.mix)
    }

    @Test
    fun `a product no longer in the list stands in from its latest entry`() {
        val pouch = UserProduct("pouch-6", null, "Pouch 6 mg", "Pouch", ProductForm.POUCH, ProductForm.POUCH.defaultUnit, Dose.mg(6))
        val entries = (16..20).map { day -> use("2026-10-${day}T10:00", pouch, 3) }
        val usual = requireNotNull(BaselineDays.measure(today, entries).usualDay(products))
        val stoodIn = usual.mix.items.single().product
        assertEquals(listOf("pouch-6", "Pouch 6 mg"), listOf(stoodIn.id, stoodIn.name))
        assertEquals(ProductForm.POUCH, stoodIn.form)
        assertEquals(Dose.mg(18), usual.total)
    }

    @Test
    fun `a typed usual day is whole units of each product, straight away`() {
        val typed = UsualDay.typed(GUM_4 to 8, SPRAY to 3, GUM_2 to 0)
        assertEquals(BaselineSource.MANUAL, typed.source)
        assertEquals(mix(GUM_4 to 8, SPRAY to 3), typed.mix)
        assertEquals(Dose.mg(35), typed.total)
        assertEquals(Dose.mg(245), typed.weekly)
        assertNull(typed.measured)
        assertThrows<IllegalArgumentException> { UsualDay.typed(GUM_2 to 0) }
        assertThrows<IllegalArgumentException> { UsualDay.typed() }
    }

    @Test
    fun `a mix keeps one item per product, in a fixed order`() {
        assertEquals(mix(SPRAY to 3, GUM_4 to 8), mix(GUM_4 to 5, SPRAY to 3, GUM_4 to 3))
        assertEquals(listOf(GUM_4, GUM_2, SPRAY), mix(SPRAY to 1, GUM_2 to 1, GUM_4 to 1).products)
        assertEquals(4, mix(GUM_4 to 1, SPRAY to 3).units)
        assertEquals(3, mix(GUM_4 to 1, SPRAY to 3).unitsOf(SPRAY.id))
        assertEquals(Dose.ZERO, Mix.NONE.total)
    }
}
