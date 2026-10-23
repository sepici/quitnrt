package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What the Today screen needs to know about one logical day. */
data class DaySummary(
    val date: LocalDate,
    val total: Dose,
    /** Newest first. */
    val entries: List<Entry>,
    /**
     * The latest dose that isn't a patch, since patches don't count for spacing. It can be from an
     * earlier day: first thing in the morning, the last dose was last night.
     */
    val lastDose: Entry?,
) {
    fun timeSinceLastDose(now: Instant): Duration? = lastDose?.let { Duration.between(it.occurredAt, now) }

    companion object {
        /** [entries] should cover [date] and, ideally, the evening before it. */
        fun of(date: LocalDate, entries: List<Entry>): DaySummary {
            val sameDay = entries.filter { it.logicalDate == date }.sortedByDescending { it.occurredAt }
            return DaySummary(
                date = date,
                total = sameDay.map { it.total }.sum(),
                entries = sameDay,
                lastDose = entries.filter { !it.isBackground && it.logicalDate <= date }.maxByOrNull { it.occurredAt },
            )
        }
    }
}
