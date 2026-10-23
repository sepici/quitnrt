package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.average
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import java.time.LocalDate

/** Where a baseline came from: measured from the logs, or typed in as a usual amount. */
enum class BaselineSource { MEASURED, MANUAL }

/** The usual amount a day that a programme steps down from. */
data class Baseline(val daily: Dose, val source: BaselineSource) {
    init {
        require(daily > Dose.ZERO) { "A baseline needs some nicotine: $daily" }
    }

    val weekly: Dose get() = daily * WeekTarget.DAYS

    companion object {
        /** The skip option: a usual daily amount typed in, to start straight away. */
        fun typed(daily: Dose): Baseline = Baseline(daily, BaselineSource.MANUAL)
    }
}

/**
 * Seven logical days measured for a baseline. A day counts once it's over, and a baseline needs
 * all seven over with at least [MIN_DAYS_WITH_DOSES] of them logged. The baseline is the average
 * on the days with doses, since a day with none is usually a day that wasn't logged.
 */
data class BaselineDays(
    val days: List<Day>,
    /** The product with the most mg on the days counted so far, or null when nothing's counted. */
    val mainProductId: String?,
) {
    init {
        require(days.size == DAYS) { "A baseline is $DAYS days: ${days.size}" }
    }

    /** [doses] counts entries. A day [isOver] once a later logical day has begun. */
    data class Day(val date: LocalDate, val total: Dose, val doses: Int, val isOver: Boolean) {
        val counts: Boolean get() = isOver && doses > 0
    }

    val start: LocalDate get() = days.first().date
    val end: LocalDate get() = days.last().date

    val daysWithDoses: Int get() = days.count { it.counts }

    val isComplete: Boolean get() = days.all { it.isOver }

    val isReady: Boolean get() = isComplete && daysWithDoses >= MIN_DAYS_WITH_DOSES

    /** The average a day on the days counted so far, for showing the baseline as it builds. */
    val averageSoFar: Dose? get() = days.filter { it.counts }.map { it.total }.average()

    /** The measured baseline, to confirm or edit, once [isReady]. */
    val baseline: Baseline? get() = averageSoFar?.takeIf { isReady }?.let { Baseline(it, BaselineSource.MEASURED) }

    companion object {
        const val DAYS = 7
        const val MIN_DAYS_WITH_DOSES = 5

        /**
         * The days to measure on [today]. The last [DAYS] full days come first: if enough of them
         * are logged, they're the baseline at once, even from before a baseline was started on
         * [startedOn]. Otherwise a baseline started less than a week ago builds over its own
         * seven days. Once those are over, the window moves on a day at a time until enough of
         * it is logged.
         */
        fun measure(today: LocalDate, entries: List<Entry>, startedOn: LocalDate? = null): BaselineDays {
            val recent = over(today.minusDays(DAYS.toLong()), today, entries)
            if (startedOn == null || recent.isReady || startedOn <= recent.start) return recent
            return over(startedOn, today, entries)
        }

        private fun over(start: LocalDate, today: LocalDate, entries: List<Entry>): BaselineDays {
            val end = start.plusDays(DAYS - 1L)
            val inWindow = entries.filter { it.logicalDate in start..end }
            val byDay = inWindow.groupBy { it.logicalDate }
            val days = (0 until DAYS).map { offset ->
                val date = start.plusDays(offset.toLong())
                val onDay = byDay[date].orEmpty()
                Day(date, onDay.map { it.total }.sum(), onDay.size, isOver = date < today)
            }
            val main = inWindow.filter { it.logicalDate < today }
                .groupBy { it.productId }
                .mapValues { (_, list) -> list.map { it.total }.sum() }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Dose>> { it.value }.thenBy { it.key })
                .firstOrNull()?.key
            return BaselineDays(days, main)
        }
    }
}
