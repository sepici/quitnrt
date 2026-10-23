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

    /**
     * A day's total. [tracked] days run from the first day anything was logged up to today, and
     * today always counts. Days before that, and days still to come, have no data rather than a
     * zero: a week that starts mid-week mustn't look nicotine-free before the first dose.
     */
    data class DayTotal(val date: LocalDate, val total: Dose, val tracked: Boolean)

    companion object {
        fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /**
         * [entries] can include days outside the week; they're left out. [firstDay] is the first
         * logical day with a dose, or null if nothing has been logged yet.
         */
        fun of(today: LocalDate, entries: List<Entry>, firstDay: LocalDate?): WeekSummary {
            val monday = mondayOf(today)
            val byDay = entries.groupBy { it.logicalDate }
            val days = (0L until 7L).map { offset ->
                val date = monday.plusDays(offset)
                val tracked = date == today || (firstDay != null && date in firstDay..today)
                DayTotal(date, byDay[date].orEmpty().map { it.total }.sum(), tracked)
            }
            return WeekSummary(today, days)
        }
    }
}
