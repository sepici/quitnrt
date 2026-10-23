package app.thelasthit.nrt.domain.backup

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EntriesCsvTest {

    private fun entry(id: String, at: String, note: String? = null, tags: Set<String> = emptySet()) = Entry(
        id = id, productId = "gum", productName = "Nicorette gum 2 mg", form = ProductForm.GUM, unit = DoseUnit.PIECE,
        perUnit = Dose.mg(2), quantity = 1, total = Dose.mg(2), occurredAt = Instant.parse(at),
        zone = ZoneId.of("Europe/London"), logicalDate = LocalDate.parse("2026-10-23"), kind = EntryKind.CRAVING,
        cravingStrength = 4, tagKeys = tags, note = note,
    )

    @Test
    fun `writes a header and one row per entry, oldest first, in local time`() {
        val csv = EntriesCsv.write(
            listOf(
                entry("b", "2026-10-23T12:40:00Z", tags = setOf("work_break", "after_meal")),
                entry("a", "2026-10-23T07:10:00Z"),
            ),
        )
        val lines = csv.trimEnd().lines()
        assertEquals("date,local_time,zone,day_counted,product,quantity,unit,mg,kind,craving_strength,tags,note,source", lines[0])
        assertEquals("2026-10-23,08:10,Europe/London,2026-10-23,Nicorette gum 2 mg,1,piece,2.0,craving,4,,,app", lines[1])
        assertEquals("2026-10-23,13:40,Europe/London,2026-10-23,Nicorette gum 2 mg,1,piece,2.0,craving,4,after_meal;work_break,,app", lines[2])
    }

    @Test
    fun `notes with commas, quotes and line breaks are quoted`() {
        val csv = EntriesCsv.write(listOf(entry("a", "2026-10-23T07:10:00Z", note = "Rough day, \"really\"\nrough")))
        assertEquals(true, csv.contains("\"Rough day, \"\"really\"\"\nrough\""))
    }
}
