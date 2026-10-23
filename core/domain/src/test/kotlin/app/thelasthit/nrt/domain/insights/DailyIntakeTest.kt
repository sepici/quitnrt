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
    fun `the rolling average starts once there are 7 days to average, and leaves out today`() {
        val first = LocalDate.parse("2026-10-13")
        // 3 mg on the 13th, then a milligram more each day up to 12 mg on the 22nd.
        val entries = (13..22).map { day -> entry("2026-10-${day}T09:00", perUnit = Dose.mg(day - 10)) }
        val averages = DailyIntake.of(InsightWindow(first, today), today, first, entries).days
            .associate { it.date.dayOfMonth to it.average }
        assertNull(averages[13])
        assertNull(averages[18])
        assertEquals(Dose.mg(6), averages[19])
        assertEquals(Dose.mg(9), averages[22])
        assertNull(averages[23])
    }

    @Test
    fun `weeks average their finished days, Monday to Sunday`() {
        val first = LocalDate.parse("2026-10-13")
        val entries = (13..23).map { day -> entry("2026-10-${day}T09:00", perUnit = Dose.mg(day - 10)) }
        val weeks = DailyIntake.of(InsightWindow(first, today), today, first, entries).weeks
        assertEquals(listOf(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-19")), weeks.map { it.monday })
        // Tuesday to Sunday the first week, and Monday to Thursday so far this week.
        assertEquals(listOf(Dose.mg(5.5), Dose.mg(10.5)), weeks.map { it.averagePerDay })
        assertEquals(listOf(6, 4), weeks.map { it.days })
    }

    @Test
    fun `on a Monday this week has no average yet`() {
        val monday = LocalDate.parse("2026-10-26")
        val week = DailyIntake.of(InsightWindow(monday, monday), monday, monday, listOf(entry("2026-10-26T09:00"))).weeks.single()
        assertNull(week.averagePerDay)
        assertEquals(0, week.days)
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
