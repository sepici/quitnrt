package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.log.Entry
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Day-to-day patterns, with patches left out: when the first dose comes, and the gaps between
 * doses. Gaps only count within a logical day, so a night's sleep is never one. Each change is
 * in minutes against the previous period, when there's one to compare with.
 */
data class Habits(
    /** The average clock time of each day's first dose. */
    val firstDose: LocalTime?,
    /** Positive when the first dose now comes later. */
    val firstDoseChange: Int?,
    val averageGap: Duration?,
    /** Positive when the gaps are now longer. */
    val averageGapChange: Int?,
    val longestGap: Gap?,
    val longestGapChange: Int?,
) {
    data class Gap(val length: Duration, val date: LocalDate)

    companion object {
        /** [previous] is the period just before, or null when it isn't all logged. */
        fun of(entries: List<Entry>, previous: List<Entry>?, dayStart: LocalTime): Habits {
            val now = Measures.of(entries, dayStart)
            val before = previous?.let { Measures.of(it, dayStart) }
            fun change(current: Long?, earlier: Long?) = if (current != null && earlier != null) (current - earlier).toInt() else null
            return Habits(
                firstDose = now.firstDoseMinutes?.let { dayStart.plusMinutes(it) },
                firstDoseChange = change(now.firstDoseMinutes, before?.firstDoseMinutes),
                averageGap = now.averageGapMinutes?.let(Duration::ofMinutes),
                averageGapChange = change(now.averageGapMinutes, before?.averageGapMinutes),
                longestGap = now.longest,
                longestGapChange = change(now.longest?.length?.toMinutes(), before?.longest?.length?.toMinutes()),
            )
        }
    }

    /**
     * One period's measures in whole minutes. The first dose is timed from the day start, so
     * days that start late in the evening average correctly across midnight.
     */
    private class Measures(val firstDoseMinutes: Long?, val averageGapMinutes: Long?, val longest: Gap?) {
        companion object {
            fun of(entries: List<Entry>, dayStart: LocalTime): Measures {
                val days = entries.filterNot { it.isBackground }.groupBy { it.logicalDate }
                    .mapValues { (_, list) -> list.sortedBy { it.occurredAt } }
                val gaps = days.flatMap { (date, list) ->
                    list.zipWithNext { a, b -> Gap(Duration.between(a.occurredAt, b.occurredAt), date) }
                }
                return Measures(
                    firstDoseMinutes = days.values.map { minutesAfter(it.first(), dayStart) }.roundedMean(),
                    averageGapMinutes = gaps.map { it.length.seconds }.roundedMean()?.let { (it + 30) / 60 },
                    // The latest of equally long gaps, since it's the most recent.
                    longest = gaps.maxWithOrNull(compareBy<Gap> { it.length }.thenBy { it.date }),
                )
            }

            private fun minutesAfter(entry: Entry, dayStart: LocalTime): Long {
                val clock = entry.occurredAt.atZone(entry.zone).toLocalTime()
                return Math.floorMod(clock.toSecondOfDay() / 60 - dayStart.toSecondOfDay() / 60, 24 * 60).toLong()
            }
        }
    }
}

/** The mean, rounded to the nearest whole number. */
private fun List<Long>.roundedMean(): Long? = if (isEmpty()) null else (sum() * 2 + size) / (2L * size)
