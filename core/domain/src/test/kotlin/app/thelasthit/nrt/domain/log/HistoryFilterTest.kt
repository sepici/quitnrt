package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HistoryFilterTest {

    private val spray = "nicorette-quickmist-uk-1mg"

    @Test
    fun `a product filter matches that product only`() {
        val filter = HistoryFilter.Product(spray)
        assertTrue(filter.matches(entry("2026-10-22T21:05", productId = spray, form = ProductForm.MOUTH_SPRAY, perUnit = Dose.mg(1))))
        assertFalse(filter.matches(entry("2026-10-22T21:05")))
    }

    @Test
    fun `a tag filter matches doses carrying it, among other tags`() {
        val filter = HistoryFilter.Tag("after_meal")
        assertTrue(filter.matches(entry("2026-10-23T13:40", tags = setOf("after_meal"))))
        assertTrue(filter.matches(entry("2026-10-23T13:40", tags = setOf("stressed", "after_meal"))))
        assertFalse(filter.matches(entry("2026-10-23T10:55", tags = setOf("work_break"))))
        assertFalse(filter.matches(entry("2026-10-23T08:10")))
    }

    @Test
    fun `days with a match keep every dose in their count and only matches in their total`() {
        val all = listOf(
            entry("2026-10-23T13:40", tags = setOf("after_meal")),
            entry("2026-10-23T10:55", tags = setOf("work_break")),
            entry("2026-10-23T08:10", perUnit = Dose.mg(4), tags = setOf("coffee_tea")),
            entry("2026-10-22T12:50", tags = setOf("after_meal")),
            entry("2026-10-22T08:15", perUnit = Dose.mg(4), tags = setOf("woke_up")),
            entry("2026-10-21T20:40", perUnit = Dose.mg(4), tags = setOf("stressed")),
        )
        val filter = HistoryFilter.Tag("after_meal")
        val dosesOn = all.groupingBy { it.logicalDate }.eachCount()

        val days = HistoryFilter.days(all.filter(filter::matches), dosesOn)

        assertEquals(listOf("2026-10-23", "2026-10-22").map(LocalDate::parse), days.map { it.date })
        assertEquals(listOf(1, 1), days.map { it.entries.size })
        assertEquals(listOf(3, 2), days.map { it.doses })
        assertEquals(listOf(Dose.mg(2), Dose.mg(2)), days.map { it.total })
    }

    @Test
    fun `a day's count never falls below its matches`() {
        val matching = listOf(entry("2026-10-23T13:40"), entry("2026-10-23T10:55"))
        assertEquals(2, HistoryFilter.days(matching, emptyMap()).single().doses)
    }

    @Test
    fun `without a filter a day counts its own entries`() {
        val day = LoggedDay.groupByDay(listOf(entry("2026-10-23T13:40"), entry("2026-10-23T10:55"))).single()
        assertEquals(2, day.doses)
    }

    @Test
    fun `choices leave out what was never used and put the most used first`() {
        val tags = listOf("stressed", "bored", "after_meal", "woke_up", "happy")
        val uses = mapOf("bored" to 2, "after_meal" to 48, "woke_up" to 12, "stressed" to 12, "happy" to 0)
        assertEquals(listOf("after_meal", "stressed", "woke_up", "bored"), HistoryFilter.choices(tags, uses) { it })
    }

    @Test
    fun `with nothing used there are no choices`() {
        assertEquals(emptyList<String>(), HistoryFilter.choices(listOf("stressed", "bored"), emptyMap()) { it })
    }
}
