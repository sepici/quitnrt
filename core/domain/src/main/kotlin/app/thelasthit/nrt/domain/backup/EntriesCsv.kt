package app.thelasthit.nrt.domain.backup

import app.thelasthit.nrt.domain.log.Entry
import java.time.format.DateTimeFormatter

/** Entries as a spreadsheet-friendly CSV, oldest first, in each entry's own local time. */
object EntriesCsv {

    private val header = listOf(
        "date", "local_time", "zone", "day_counted", "product", "quantity", "unit", "mg",
        "kind", "craving_strength", "tags", "note", "source",
    )

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun write(entries: List<Entry>): String = buildString {
        appendLine(header.joinToString(","))
        for (entry in entries.sortedBy { it.occurredAt }) {
            val local = entry.occurredAt.atZone(entry.zone)
            val row = listOf(
                local.toLocalDate().toString(),
                local.toLocalTime().format(timeFormat),
                entry.zone.id,
                entry.logicalDate.toString(),
                entry.productName,
                entry.quantity.toString(),
                entry.unit.name.lowercase(),
                entry.total.milligrams.toString(),
                entry.kind.name.lowercase(),
                entry.cravingStrength?.toString().orEmpty(),
                entry.tagKeys.sorted().joinToString(";"),
                entry.note.orEmpty(),
                entry.source.name.lowercase(),
            )
            appendLine(row.joinToString(",") { escape(it) })
        }
    }

    /** RFC 4180: quote fields with commas, quotes or line breaks, and double any quotes inside. */
    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
