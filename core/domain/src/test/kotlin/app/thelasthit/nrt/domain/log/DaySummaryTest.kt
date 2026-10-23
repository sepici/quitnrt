package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

class DaySummaryTest {

    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `totals the day newest first and skips patches for the last dose`() {
        val entries = listOf(
            entry("2026-10-23T08:10", perUnit = Dose.mg(4)),
            entry("2026-10-23T10:55"),
            entry("2026-10-23T13:40"),
            entry("2026-10-23T14:00", productId = "patch", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14)),
        )
        val summary = DaySummary.of(today, entries)
        assertEquals(Dose.mg(22), summary.total)
        assertEquals(listOf("14:00", "13:40", "10:55", "08:10"), summary.entries.map { it.occurredAt.atZone(LONDON).toLocalTime().toString() })
        assertEquals(entries[2], summary.lastDose)
        assertEquals(Duration.ofMinutes(85), summary.timeSinceLastDose(london("2026-10-23T15:05")))
    }

    @Test
    fun `the last dose can be from last night`() {
        val lastNight = entry("2026-10-23T01:30") // counts toward the 22nd with a 04:00 day start
        val summary = DaySummary.of(today, listOf(lastNight))
        assertEquals(Dose.ZERO, summary.total)
        assertEquals(lastNight, summary.lastDose)
    }

    @Test
    fun `an empty day has no last dose`() {
        val summary = DaySummary.of(today, emptyList())
        assertNull(summary.lastDose)
        assertNull(summary.timeSinceLastDose(london("2026-10-23T15:05")))
    }
}
