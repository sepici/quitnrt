package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WeekSummaryTest {

    @Test
    fun `the week runs Monday to Sunday`() {
        assertEquals(LocalDate.parse("2026-10-19"), WeekSummary.mondayOf(LocalDate.parse("2026-10-23")))
        assertEquals(LocalDate.parse("2026-10-19"), WeekSummary.mondayOf(LocalDate.parse("2026-10-19")))
        assertEquals(LocalDate.parse("2026-10-19"), WeekSummary.mondayOf(LocalDate.parse("2026-10-25")))
    }

    @Test
    fun `totals each logical day and leaves other weeks out`() {
        val week = WeekSummary.of(
            today = LocalDate.parse("2026-10-23"),
            entries = listOf(
                entry("2026-10-18T20:00"), // Sunday before
                entry("2026-10-19T09:00", perUnit = Dose.mg(4)),
                entry("2026-10-20T01:30"), // after midnight, so it counts toward Monday
                entry("2026-10-23T08:10"),
                entry("2026-10-23T13:40"),
            ),
            firstDay = LocalDate.parse("2026-10-12"),
        )
        assertEquals(
            listOf(6, 0, 0, 0, 4, 0, 0).map(Dose::mg),
            week.days.map { it.total },
        )
        assertEquals(LocalDate.parse("2026-10-19"), week.days.first().date)
        assertEquals(LocalDate.parse("2026-10-25"), week.days.last().date)
        assertEquals(Dose.mg(10), week.total)
        assertEquals(listOf(true, true, true, true, true, false, false), week.days.map { it.tracked })
    }

    @Test
    fun `days before the first dose have no data rather than a zero`() {
        val week = WeekSummary.of(
            today = LocalDate.parse("2026-10-23"),
            entries = listOf(entry("2026-10-21T09:00")),
            firstDay = LocalDate.parse("2026-10-21"),
        )
        assertEquals(listOf(false, false, true, true, true, false, false), week.days.map { it.tracked })
    }

    @Test
    fun `with nothing logged yet only today is tracked`() {
        val week = WeekSummary.of(today = LocalDate.parse("2026-10-23"), entries = emptyList(), firstDay = null)
        assertEquals(listOf(false, false, false, false, true, false, false), week.days.map { it.tracked })
    }
}
