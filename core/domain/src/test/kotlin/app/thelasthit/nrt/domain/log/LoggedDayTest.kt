package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LoggedDayTest {

    @Test
    fun `groups by logical day, newest first, with totals`() {
        val days = LoggedDay.groupByDay(
            listOf(
                entry("2026-10-21T08:00", perUnit = Dose.mg(4)),
                entry("2026-10-23T08:10"),
                entry("2026-10-23T01:20"), // counts toward the 22nd with a 04:00 day start
                entry("2026-10-23T13:40"),
            ),
        )
        assertEquals(listOf("2026-10-23", "2026-10-22", "2026-10-21").map(LocalDate::parse), days.map { it.date })
        assertEquals(listOf(Dose.mg(4), Dose.mg(2), Dose.mg(4)), days.map { it.total })
        assertEquals(listOf("13:40", "08:10"), days.first().entries.map { it.occurredAt.atZone(LONDON).toLocalTime().toString() })
    }

    @Test
    fun `nothing logged is no days`() {
        assertEquals(emptyList<LoggedDay>(), LoggedDay.groupByDay(emptyList()))
    }

    @Test
    fun `a dose after midnight counts toward the evening before`() {
        val late = entry("2026-10-23T01:20")
        assertEquals(LocalDate.parse("2026-10-23"), late.calendarDate)
        assertEquals(LocalDate.parse("2026-10-22"), late.logicalDate)
        assertTrue(late.countsTowardEveningBefore)
        assertFalse(entry("2026-10-23T08:10").countsTowardEveningBefore)
    }
}
