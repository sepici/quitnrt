package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.average
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Mg per logical day across a window, with a 7-day rolling average. Today's total is still
 * partial, so today gets no average and stays out of [averagePerDay] and [weeks].
 */
data class DailyIntake(val days: List<Day>, val today: LocalDate) {

    /**
     * [doses] counts entries, so two pieces at once are one dose. [average] is the mean of the 7
     * days up to and including this one: null until there are 7 logged days to average, and for today.
     */
    data class Day(val date: LocalDate, val total: Dose, val doses: Int, val average: Dose?)

    /**
     * One Monday-to-Sunday week of the window, for ranges too long for a column a day.
     * [averagePerDay] is across its finished days in the window, [days] of them; null when the
     * week has none yet, as on a Monday.
     */
    data class Week(val monday: LocalDate, val averagePerDay: Dose?, val days: Int)

    /** The mean of the window's finished days, or null when today is the only day so far. */
    val averagePerDay: Dose? get() = finished.map { it.total }.average()

    /** The highest day, for its label on the chart. */
    val highest: Day? get() = days.filter { it.total > Dose.ZERO }.maxByOrNull { it.total }

    val weeks: List<Week> get() = days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (monday, week) ->
            val done = week.filter { it.date != today }
            Week(monday, done.map { it.total }.average(), done.size)
        }

    private val finished: List<Day> get() = days.filter { it.date != today }

    companion object {
        const val ROLLING_DAYS = 7

        /** Above this many days, the chart shows [weeks] rather than a column a day. */
        const val MAX_DAILY_COLUMNS = 30

        /**
         * [entries] should start [ROLLING_DAYS] − 1 days before [window], so its first days can
         * have an average. An average never reaches back before [firstDay].
         */
        fun of(window: InsightWindow, today: LocalDate, firstDay: LocalDate, entries: List<Entry>): DailyIntake {
            val byDay = entries.groupBy { it.logicalDate }
            val totals = byDay.mapValues { (_, list) -> list.map { it.total }.sum() }
            fun total(date: LocalDate) = totals[date] ?: Dose.ZERO
            val days = window.dates.map { date ->
                val from = date.minusDays(ROLLING_DAYS - 1L)
                val average = if (date >= today || from < firstDay) {
                    null
                } else {
                    (0 until ROLLING_DAYS).map { total(from.plusDays(it.toLong())) }.average()
                }
                Day(date, total(date), byDay[date]?.size ?: 0, average)
            }
            return DailyIntake(days, today)
        }
    }
}
