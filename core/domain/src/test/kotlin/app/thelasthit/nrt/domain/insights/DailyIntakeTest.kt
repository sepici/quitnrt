package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.average
import app.thelasthit.nrt.domain.log.entry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyIntakeTest {

    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `totals each logical day, with a dose after midnight on the evening before`() {
        val first = LocalDate.parse("2026-10-21")
        val intake = DailyIntake.of(
            InsightWindow(first, today),
            today,
            first,
            listOf(
                entry("2026-10-21T09:00"),
                entry("2026-10-22T01:30"),
                entry("2026-10-22T09:00", perUnit = Dose.mg(4)),
                entry("2026-10-23T08:00"),
            ),
        )
        assertEquals(listOf(4, 4, 2).map(Dose::mg), intake.days.map { it.total })
    }

    @Test
    fun `the rolling average covers up to 7 days, never before the first, and not today`() {
        val first = LocalDate.parse("2026-10-13")
        // 3 mg on the 13th, then a milligram more each day up to 12 mg on the 22nd.
        val entries = (13..22).map { day -> entry("2026-10-${day}T09:00", perUnit = Dose.mg(day - 10)) }
        val averages = DailyIntake.of(InsightWindow(first, today), today, first, entries).days
            .associate { it.date.dayOfMonth to it.average }
        assertEquals(Dose.mg(3), averages[13])
        assertEquals(Dose.mg(4), averages[15])
        assertEquals(Dose.mg(9), averages[22])
        assertNull(averages[23])
    }

    @Test
    fun `the average a day leaves out today, since it's still partial`() {
        val first = LocalDate.parse("2026-10-21")
        val intake = DailyIntake.of(
            InsightWindow(first, today),
            today,
            first,
            listOf(
                entry("2026-10-21T09:00", perUnit = Dose.mg(10)),
                entry("2026-10-22T09:00", perUnit = Dose.mg(13)),
                entry("2026-10-23T09:00", perUnit = Dose.mg(1)),
            ),
        )
        assertEquals(Dose.mg(11.5), intake.averagePerDay)
        assertEquals(LocalDate.parse("2026-10-22"), intake.highest?.date)
    }

    @Test
    fun `with only today logged there's no average a day yet`() {
        val intake = DailyIntake.of(InsightWindow(today, today), today, today, listOf(entry("2026-10-23T09:00")))
        assertNull(intake.averagePerDay)
    }

    @Test
    fun `an average rounds to the nearest microgram`() {
        assertEquals(Dose(2L), listOf(Dose(1L), Dose(2L), Dose(2L)).average())
        assertEquals(Dose(2L), listOf(Dose(1L), Dose(2L)).average())
        assertNull(emptyList<Dose>().average())
    }
}
