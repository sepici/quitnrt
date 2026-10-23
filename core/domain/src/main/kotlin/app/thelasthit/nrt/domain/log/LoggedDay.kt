package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import java.time.LocalDate

/** One logical day in History: its entries, newest first, and their total. */
data class LoggedDay(val date: LocalDate, val entries: List<Entry>) {
    val total: Dose get() = entries.map { it.total }.sum()

    companion object {
        /** Groups entries by logical day, newest day first. Days with nothing logged are left out. */
        fun groupByDay(entries: List<Entry>): List<LoggedDay> =
            entries.groupBy { it.logicalDate }
                .map { (date, onDay) -> LoggedDay(date, onDay.sortedByDescending { it.occurredAt }) }
                .sortedByDescending { it.date }
    }
}
