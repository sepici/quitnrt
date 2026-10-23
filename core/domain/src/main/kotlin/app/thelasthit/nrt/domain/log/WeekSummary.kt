package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Monday to Sunday of logical days, with each day's total, for the week columns on Today. Once a
 * programme starts, its own weeks take over from calendar weeks.
 */
data class WeekSummary(
    val today: LocalDate,
    val days: List<DayTotal>,
) {
    val total: Dose get() = days.map { it.total }.sum()

    data class DayTotal(val date: LocalDate, val total: Dose)

    companion object {
        fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** [entries] can include days outside the week; they're left out. Days after [today] are zero. */
        fun of(today: LocalDate, entries: List<Entry>): WeekSummary {
            val monday = mondayOf(today)
            val byDay = entries.groupBy { it.logicalDate }
            val days = (0L until 7L).map { offset ->
                val date = monday.plusDays(offset)
                DayTotal(date, byDay[date].orEmpty().map { it.total }.sum())
            }
            return WeekSummary(today, days)
        }
    }
}
