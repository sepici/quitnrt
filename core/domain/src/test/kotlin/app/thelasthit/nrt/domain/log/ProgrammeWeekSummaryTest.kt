package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ProgrammeWeekSummaryTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `a programme week runs from its own start day`() {
        // From Saturday 17 October, with today Friday 23 October, the week's last day.
        val week = WeekSummary.from(
            start = date("2026-10-17"),
            today = date("2026-10-23"),
            entries = listOf(
                entry("2026-10-16T20:00"), // the Friday before
                entry("2026-10-17T09:00", perUnit = Dose.mg(4)),
                entry("2026-10-23T08:00"),
                entry("2026-10-24T09:00"), // the next week
            ),
            firstDay = date("2026-10-17"),
        )
        assertEquals((17..23).map { date("2026-10-$it") }, week.days.map { it.date })
        assertEquals(listOf(4, 0, 0, 0, 0, 0, 2).map(Dose::mg), week.days.map { it.total })
        assertEquals(List(7) { true }, week.days.map { it.tracked })
        assertEquals(Dose.mg(6), week.total)
    }

    @Test
    fun `a tick only for a finished day at or under the guide`() {
        val week = WeekSummary.from(
            start = date("2026-10-19"),
            today = date("2026-10-23"),
            entries = listOf(
                entry("2026-10-19T09:00", perUnit = Dose.mg(14)),
                entry("2026-10-20T09:00", perUnit = Dose.mg(15)),
                entry("2026-10-23T09:00", perUnit = Dose.mg(2)),
            ),
            firstDay = date("2026-10-19"),
        )
        val guide = Dose.mg(14)
        assertEquals(
            // Monday at it, Tuesday over, Wednesday and Thursday with nothing, today not over, and the days to come.
            listOf(true, false, true, true, false, false, false),
            week.days.map { week.isWithin(it, guide) },
        )
    }

    @Test
    fun `a calendar week is still Monday to Sunday`() {
        val today = date("2026-10-23")
        assertEquals(WeekSummary.from(date("2026-10-19"), today, emptyList(), null), WeekSummary.of(today, emptyList(), null))
    }
}
