package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Puts a value on a heat map's ramp: 0 is empty, then 1 up to [STEPS], the top step for the largest value. */
object HeatScale {
    const val STEPS = 5

    /** From nothing up to [max], as the hour map counts doses. */
    fun step(value: Long, max: Long): Int =
        if (value <= 0 || max <= 0) 0 else ((value * STEPS + max - 1) / max).toInt().coerceIn(1, STEPS)

    /**
     * In equal steps from [low] up to [high], so even a steady month shows its heavier days.
     * Nothing is still empty, and when every value is the same it takes the middle step.
     */
    fun stepBetween(value: Long, low: Long, high: Long): Int = when {
        value <= 0 -> 0
        high <= low -> (STEPS + 1) / 2
        else -> (1 + (value - low) * STEPS / (high - low)).toInt().coerceIn(1, STEPS)
    }
}

/**
 * When doses happen, as 24 hour rows by 7 day columns, Monday first. Rows start at the day
 * start's hour and columns are the logical day, so a dose at 01:30 that counts toward Friday sits
 * at the bottom of Friday's column. Hours are read off the clock where the dose was taken.
 * Patches are left out, since they release steadily rather than answering a craving.
 */
class HourHeatMap private constructor(val firstHour: Int, private val grid: Map<Pair<DayOfWeek, Int>, Cell>) {

    data class Cell(val day: DayOfWeek, val hour: Int, val doses: Int, val total: Dose)

    /** The clock hour on [row], counting from 0 at the top. */
    fun hourAt(row: Int): Int = (firstHour + row) % 24

    fun cell(day: DayOfWeek, hour: Int): Cell = grid[day to hour] ?: Cell(day, hour, 0, Dose.ZERO)

    /** Only the cells with doses in them. */
    val cells: Collection<Cell> get() = grid.values

    val maxDoses: Int get() = grid.values.maxOfOrNull { it.doses } ?: 0

    val maxTotal: Dose get() = grid.values.maxOfOrNull { it.total } ?: Dose.ZERO

    /** The cell with the most doses, then the most mg, for the chart's summary. */
    val busiest: Cell? get() = grid.values.maxWithOrNull(compareBy<Cell> { it.doses }.thenBy { it.total })

    companion object {
        fun of(entries: List<Entry>, dayStart: LocalTime): HourHeatMap {
            val grid = entries.filterNot { it.isBackground }
                .groupBy { it.logicalDate.dayOfWeek to it.occurredAt.atZone(it.zone).hour }
                .mapValues { (key, list) -> Cell(key.first, key.second, list.size, list.map { it.total }.sum()) }
            return HourHeatMap(dayStart.hour, grid)
        }
    }
}

/**
 * A range's days as a calendar, one square a day in Monday-to-Sunday weeks, shaded by mg.
 * Days in the range from the first dose on are tracked. Earlier days in the range have no data
 * rather than a zero, and the rest of the first and last weeks are outside the range.
 *
 * Shading runs from the lightest finished day with doses to the heaviest ([lowTotal] to
 * [highTotal]). A day with none logged stays empty and doesn't flatten the scale, and today is
 * still partial, so it's shaded on the scale but doesn't set it.
 */
data class CalendarHeat(val weeks: List<List<Day>>, val today: LocalDate) {

    enum class State { TRACKED, NO_DATA, OUTSIDE }

    data class Day(val date: LocalDate, val total: Dose, val state: State)

    private val finished: List<Day> get() = weeks.flatten().filter { it.state == State.TRACKED && it.date != today }

    val lowTotal: Dose get() = finished.filter { it.total > Dose.ZERO }.minOfOrNull { it.total } ?: Dose.ZERO

    val highTotal: Dose get() = finished.maxOfOrNull { it.total } ?: Dose.ZERO

    /** The day's step on [HeatScale], or 0 for a day that isn't tracked. */
    fun step(day: Day): Int =
        if (day.state != State.TRACKED) 0 else HeatScale.stepBetween(day.total.micrograms, lowTotal.micrograms, highTotal.micrograms)

    companion object {
        fun of(range: InsightRange, today: LocalDate, firstDay: LocalDate?, entries: List<Entry>): CalendarHeat {
            val start = today.minusDays(range.days - 1L)
            val totals = entries.groupBy { it.logicalDate }.mapValues { (_, list) -> list.map { it.total }.sum() }
            var date = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val last = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val days = buildList {
                while (date <= last) {
                    val state = when {
                        date < start || date > today -> State.OUTSIDE
                        date == today || (firstDay != null && date >= firstDay) -> State.TRACKED
                        else -> State.NO_DATA
                    }
                    add(Day(date, if (state == State.TRACKED) totals[date] ?: Dose.ZERO else Dose.ZERO, state))
                    date = date.plusDays(1)
                }
            }
            return CalendarHeat(days.chunked(7), today)
        }
    }
}
