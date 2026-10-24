package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Seven logical days with each day's total, for the week columns on Today: Monday to Sunday, or
 * once a plan runs, its own programme week ([from]).
 */
data class WeekSummary(
    val today: LocalDate,
    val days: List<DayTotal>,
) {
    val total: Dose get() = days.map { it.total }.sum()

    /**
     * Whether [day] is over and stayed at or under [guide], for its tick. Today isn't over yet,
     * and a day with no data has nothing to tick.
     */
    fun isWithin(day: DayTotal, guide: Dose): Boolean = day.tracked && day.date < today && day.total <= guide

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
        fun of(today: LocalDate, entries: List<Entry>, firstDay: LocalDate?): WeekSummary =
            from(mondayOf(today), today, entries, firstDay)

        /**
         * The seven days from [start], counted as [of] counts them, for a programme week, which
         * starts on the day the plan did rather than a Monday.
         */
        fun from(start: LocalDate, today: LocalDate, entries: List<Entry>, firstDay: LocalDate?): WeekSummary {
            val byDay = entries.groupBy { it.logicalDate }
            val days = (0L until 7L).map { offset ->
                val date = start.plusDays(offset)
                val tracked = date == today || (firstDay != null && date in firstDay..today)
                DayTotal(date, byDay[date].orEmpty().map { it.total }.sum(), tracked)
            }
            return WeekSummary(today, days)
        }
    }
}
