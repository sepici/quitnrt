package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.insights.CalendarHeat.State
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HeatMapsTest {

    private val dayStart = LocalTime.of(4, 0)

    @Test
    fun `rows start at the day start and wrap round to the hour before it`() {
        val map = HourHeatMap.of(emptyList(), dayStart)
        assertEquals(4, map.hourAt(0))
        assertEquals(23, map.hourAt(19))
        assertEquals(3, map.hourAt(23))
    }

    @Test
    fun `a late dose sits in its logical day's column at its clock hour`() {
        // Saturday 01:30 counts toward Friday.
        val map = HourHeatMap.of(listOf(entry("2026-10-24T01:30")), dayStart)
        assertEquals(1, map.cell(DayOfWeek.FRIDAY, 1).doses)
        assertEquals(0, map.cell(DayOfWeek.SATURDAY, 1).doses)
    }

    @Test
    fun `hours are read off the clock where the dose was taken`() {
        val map = HourHeatMap.of(listOf(entry("2026-10-21T08:15", zone = ZoneId.of("America/New_York"))), dayStart)
        assertEquals(1, map.cell(DayOfWeek.WEDNESDAY, 8).doses)
    }

    @Test
    fun `patches are left out, and cells count doses and mg`() {
        val map = HourHeatMap.of(
            listOf(
                entry("2026-10-19T07:30", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14)),
                entry("2026-10-19T08:10", perUnit = Dose.mg(4), quantity = 2),
                entry("2026-10-19T08:40"),
            ),
            dayStart,
        )
        val cell = map.cell(DayOfWeek.MONDAY, 8)
        assertEquals(2, cell.doses)
        assertEquals(Dose.mg(10), cell.total)
        assertEquals(0, map.cell(DayOfWeek.MONDAY, 7).doses)
        assertEquals(cell, map.busiest)
        assertEquals(2, map.maxDoses)
    }

    @Test
    fun `the ramp has an empty step and five shades, the top one for the largest`() {
        assertEquals(0, HeatScale.step(0, 10))
        assertEquals(1, HeatScale.step(1, 10))
        assertEquals(3, HeatScale.step(5, 10))
        assertEquals(5, HeatScale.step(10, 10))
        assertEquals(0, HeatScale.step(3, 0))
        val steps = (0L..20L).map { HeatScale.step(it, 20) }
        assertEquals(steps.sorted(), steps)
    }

    @Test
    fun `the calendar pads whole weeks and shows days before the first dose as no data`() {
        val today = LocalDate.parse("2026-10-23")
        val calendar = CalendarHeat.of(
            InsightRange.DAYS_7,
            today,
            firstDay = LocalDate.parse("2026-10-21"),
            entries = listOf(entry("2026-10-21T09:00"), entry("2026-10-22T09:00", perUnit = Dose.mg(4))),
        )
        // The range runs from Saturday the 17th to Friday the 23rd: the weeks of the 12th and 19th.
        assertEquals(2, calendar.weeks.size)
        val days = calendar.weeks.flatten().associateBy { it.date.dayOfMonth }
        assertEquals(State.OUTSIDE, days.getValue(16).state)
        assertEquals(State.NO_DATA, days.getValue(17).state)
        assertEquals(State.NO_DATA, days.getValue(20).state)
        assertEquals(State.TRACKED, days.getValue(21).state)
        assertEquals(Dose.mg(4), days.getValue(22).total)
        assertEquals(State.TRACKED, days.getValue(23).state)
        assertEquals(State.OUTSIDE, days.getValue(24).state)
    }

    @Test
    fun `the calendar shades from the lightest finished day with doses to the heaviest, and today doesn't set the scale`() {
        val today = LocalDate.parse("2026-10-23")
        val calendar = CalendarHeat.of(
            InsightRange.DAYS_7,
            today,
            firstDay = LocalDate.parse("2026-10-17"),
            entries = listOf(
                entry("2026-10-21T09:00", perUnit = Dose.mg(12)),
                entry("2026-10-22T09:00", perUnit = Dose.mg(18)),
                entry("2026-10-23T09:00", perUnit = Dose.mg(30)),
            ),
        )
        assertEquals(Dose.mg(12), calendar.lowTotal)
        assertEquals(Dose.mg(18), calendar.highTotal)
        val days = calendar.weeks.flatten().associateBy { it.date.dayOfMonth }
        assertEquals(0, calendar.step(days.getValue(20)))
        assertEquals(1, calendar.step(days.getValue(21)))
        assertEquals(5, calendar.step(days.getValue(22)))
        assertEquals(5, calendar.step(days.getValue(23)))
        assertEquals(0, calendar.step(days.getValue(16)))
    }

    @Test
    fun `the legend names the largest value on each step, and skips steps nothing reaches`() {
        assertEquals(listOf<Long?>(1, 2, 3, 4, 5), HeatScale.stepTops(5))
        assertEquals(listOf<Long?>(2, 4, 6, 8, 10), HeatScale.stepTops(10))
        assertEquals(listOf<Long?>(null, 1, null, 2, 3), HeatScale.stepTops(3))
        assertEquals(listOf<Long?>(null, null, null, null, 1), HeatScale.stepTops(1))
        assertEquals(listOf<Long?>(null, null, null, null, null), HeatScale.stepTops(0))
        for (max in 1L..40L) {
            HeatScale.stepTops(max).forEachIndexed { index, top ->
                if (top != null) {
                    assertEquals(index + 1, HeatScale.step(top, max))
                    assertTrue(top == max || HeatScale.step(top + 1, max) > index + 1)
                }
            }
        }
    }

    @Test
    fun `the calendar's legend marks where each step starts`() {
        // 10 to 20 mg in micrograms: steps start at 12, 14, 16 and 18 mg.
        assertEquals(listOf(12_000L, 14_000L, 16_000L, 18_000L), HeatScale.stepStarts(10_000, 20_000))
        assertEquals(emptyList<Long>(), HeatScale.stepStarts(12_000, 12_000))
        for ((low, high) in listOf(10_000L to 18_000L, 1_875L to 13_125L, 12_000L to 12_500L)) {
            HeatScale.stepStarts(low, high).forEachIndexed { index, start ->
                assertEquals(index + 2, HeatScale.stepBetween(start, low, high))
                assertEquals(index + 1, HeatScale.stepBetween(start - 1, low, high).coerceAtLeast(1))
            }
        }
    }

    @Test
    fun `steps between two values run in equal parts, with nothing still empty`() {
        assertEquals(0, HeatScale.stepBetween(0, 10, 20))
        assertEquals(1, HeatScale.stepBetween(10, 10, 20))
        assertEquals(3, HeatScale.stepBetween(15, 10, 20))
        assertEquals(5, HeatScale.stepBetween(20, 10, 20))
        assertEquals(3, HeatScale.stepBetween(12, 12, 12))
    }
}
