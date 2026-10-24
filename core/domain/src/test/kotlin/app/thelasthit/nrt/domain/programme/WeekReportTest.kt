package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeekReportTest {

    private val programme = planExample()

    @Test
    fun `only the weeks that have started, each with its total against its allowance`() {
        val entries = listOf(dose("2026-10-26T09:00", Dose.mg(100)), dose("2026-11-02T09:00", Dose.mg(30)))
        val reports = WeekReport.of(programme, entries, today = date("2026-11-02"))
        assertEquals(listOf(1, 2), reports.map { it.week.number })
        assertEquals(listOf(Dose.mg(100), Dose.mg(30)), reports.map { it.total })
        assertEquals(listOf(Dose.mg(140), Dose.mg(126)), reports.map { it.allowance })
        // Week 1 is over, and its result isn't in yet: this programme hasn't been settled.
        assertEquals(listOf(WeekReport.State.WAITING, WeekReport.State.RUNNING), reports.map { it.state })
        assertEquals(Dose.mg(40), reports[0].against.left)
    }

    @Test
    fun `before the plan starts there's nothing to report`() {
        assertEquals(emptyList<WeekReport>(), WeekReport.of(programme, emptyList(), today = date("2026-10-23")))
    }

    @Test
    fun `a met week says which missed weeks it paid back, and they say which week did`() {
        // Missed, missed, met, then met.
        val played = play(programme, "xxmm")
        val today = played.programme.week(5).startDate
        val reports = WeekReport.of(played.programme, played.entries, today)
        assertEquals(
            listOf(
                WeekReport.State.CAUGHT_UP,
                WeekReport.State.CAUGHT_UP,
                WeekReport.State.MET,
                WeekReport.State.MET,
                WeekReport.State.RUNNING,
            ),
            reports.map { it.state },
        )
        assertEquals(listOf(3, 3, null, null, null), reports.map { it.caughtUpBy })
        assertEquals(listOf(1, 2), reports[2].paidBack)
        assertEquals(emptyList<Int>(), reports[3].paidBack)
        assertEquals(Dose.mg(141), reports[0].total)
        assertEquals(Dose.mg(1), reports[0].against.over)
    }

    @Test
    fun `a missed week stays missed until a later one is met`() {
        val played = play(programme, "mx")
        val reports = WeekReport.of(played.programme, played.entries, played.programme.week(3).startDate)
        assertEquals(listOf(WeekReport.State.MET, WeekReport.State.MISSED, WeekReport.State.RUNNING), reports.map { it.state })
        assertEquals(listOf(null, null, null), reports.map { it.caughtUpBy })
    }
}
