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
        )
        assertEquals(
            listOf(6, 0, 0, 0, 4, 0, 0).map(Dose::mg),
            week.days.map { it.total },
        )
        assertEquals(LocalDate.parse("2026-10-19"), week.days.first().date)
        assertEquals(LocalDate.parse("2026-10-25"), week.days.last().date)
        assertEquals(Dose.mg(10), week.total)
    }
}
