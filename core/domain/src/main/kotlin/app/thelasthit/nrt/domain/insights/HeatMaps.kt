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

    /**
     * For the legend under [step]: the largest whole value on each step from 1 to [STEPS], or
     * null for a step no whole value reaches, as with 3 doses at most, which use steps 2, 4 and 5.
     */
    fun stepTops(max: Long): List<Long?> = (1..STEPS).map { k ->
        (max * k / STEPS).takeIf { it >= 1 && step(it, max) == k }
    }

    /**
     * For the legend under [stepBetween]: where each step from 2 up starts, as the smallest whole
     * value on it. Empty when [high] isn't above [low], since every value then takes one step.
     */
    fun stepStarts(low: Long, high: Long): List<Long> =
        if (high <= low) emptyList() else (1 until STEPS).map { k -> low + ceilDiv(k * (high - low), STEPS.toLong()) }

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
}

/**
 * When doses happen, as 24 hour-long rows by 7 day columns, Monday first. Rows run an hour each
 * from the day start and columns are the logical day, so with a 04:00 day start a dose at 01:30
 * that counts toward Friday sits at the bottom of Friday's column. A day start on the half hour
 * moves the rows with it (04:30 to 05:30 and so on), so a dose just before it still sits at the
 * bottom of the day it counts toward. Times are read off the clock where the dose was taken.
 * Patches are left out, since they release steadily rather than answering a craving.
 *
 * A weekday the window hasn't reached yet, such as Saturday in a first week that began on a
 * Monday, has no days rather than empty cells, so it never reads as a run of zeros ([daysOf]).
 */
class HourHeatMap private constructor(
    val dayStart: LocalTime,
    private val grid: Map<Pair<DayOfWeek, Int>, Cell>,
    private val days: Map<DayOfWeek, List<LocalDate>>,
) {

    /** [row] counts from 0 at the day start, and [from] is the clock time it starts at. */
    data class Cell(val day: DayOfWeek, val row: Int, val from: LocalTime, val doses: Int, val total: Dose) {
        /** When the row ends, an hour after [from]. */
        val to: LocalTime get() = from.plusHours(1)
    }

    /** The clock time [row] starts at, counting from 0 at the top. */
    fun startOf(row: Int): LocalTime = dayStart.plusHours(row.toLong())

    fun cell(day: DayOfWeek, row: Int): Cell = grid[day to row] ?: Cell(day, row, startOf(row), 0, Dose.ZERO)

    /** Only the cells with doses in them. */
    val cells: Collection<Cell> get() = grid.values

    val maxDoses: Int get() = grid.values.maxOfOrNull { it.doses } ?: 0

    val maxTotal: Dose get() = grid.values.maxOfOrNull { it.total } ?: Dose.ZERO

    /** The cell with the most doses, then the most mg, for the chart's summary. */
    val busiest: Cell? get() = grid.values.maxWithOrNull(compareBy<Cell> { it.doses }.thenBy { it.total })

    /** The window's days that fall on [day], oldest first: none for a weekday it hasn't reached. */
    fun daysOf(day: DayOfWeek): List<LocalDate> = days[day].orEmpty()

    companion object {
        const val ROWS = 24

        /**
         * [dates] are the window's logical days, all of them tracked, since a window never starts
         * before the first dose. They only feed [daysOf]: the cells count every entry given.
         */
        fun of(entries: List<Entry>, dayStart: LocalTime, dates: List<LocalDate> = emptyList()): HourHeatMap {
            val start = dayStart.toSecondOfDay() / 60
            val grid = entries.filterNot { it.isBackground }
                .groupBy { entry ->
                    val clock = entry.occurredAt.atZone(entry.zone).toLocalTime().toSecondOfDay() / 60
                    entry.logicalDate.dayOfWeek to Math.floorMod(clock - start, ROWS * 60) / 60
                }
                .mapValues { (key, list) ->
                    val (day, row) = key
                    Cell(day, row, dayStart.plusHours(row.toLong()), list.size, list.map { it.total }.sum())
                }
            return HourHeatMap(dayStart, grid, dates.distinct().sorted().groupBy { it.dayOfWeek })
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
