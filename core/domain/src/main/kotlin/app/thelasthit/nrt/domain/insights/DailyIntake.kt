package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.average
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import java.time.LocalDate

/**
 * Mg per logical day across a window, with a 7-day rolling average. Today's total is still
 * partial, so today gets no average and stays out of [averagePerDay].
 */
data class DailyIntake(val days: List<Day>, val today: LocalDate) {

    /** [average] is the rolling average of the days up to and including this one; null for today. */
    data class Day(val date: LocalDate, val total: Dose, val average: Dose?)

    /** The mean of the window's finished days, or null when today is the only day so far. */
    val averagePerDay: Dose? get() = days.filter { it.date != today }.map { it.total }.average()

    /** The highest day, for its label on the chart. */
    val highest: Day? get() = days.filter { it.total > Dose.ZERO }.maxByOrNull { it.total }

    companion object {
        const val ROLLING_DAYS = 7

        /**
         * [entries] should start [ROLLING_DAYS] − 1 days before [window], so its first days get a
         * full average. Days before [firstDay] never count toward an average.
         */
        fun of(window: InsightWindow, today: LocalDate, firstDay: LocalDate, entries: List<Entry>): DailyIntake {
            val totals = entries.groupBy { it.logicalDate }.mapValues { (_, list) -> list.map { it.total }.sum() }
            fun total(date: LocalDate) = totals[date] ?: Dose.ZERO
            val days = window.dates.map { date ->
                val average = if (date >= today) {
                    null
                } else {
                    (0 until ROLLING_DAYS).map { date.minusDays(it.toLong()) }.filter { it >= firstDay }.map(::total).average()
                }
                Day(date, total(date), average)
            }
            return DailyIntake(days, today)
        }
    }
}
