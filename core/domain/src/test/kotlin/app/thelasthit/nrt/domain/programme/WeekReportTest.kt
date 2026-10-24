package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        // Meeting week 3 would pay it back.
        assertEquals(listOf(null, 3, null), reports.map { it.backIfMet })
    }

    @Test
    fun `every missed week since the last met one comes back if the week running is met`() {
        val played = play(programme, "xx")
        val reports = WeekReport.of(played.programme, played.entries, played.programme.week(3).startDate)
        assertEquals(listOf(3, 3, null), reports.map { it.backIfMet })
    }

    @Test
    fun `a met week stays met when a dose is backdated into it, and says its total has changed`() {
        // Week 1 met at exactly its 140 mg, then a forgotten 20 mg dose logged on its last day.
        val played = play(programme, "m")
        val week = played.programme.week(1)
        val backdated = played.entries + dose(week.endDate.atTime(21, 0), Dose.mg(20))
        val report = WeekReport.of(played.programme, backdated, week.endDate.plusDays(3)).first()
        assertEquals(WeekReport.State.MET, report.state)
        assertEquals(Dose.mg(160), report.total)
        assertTrue(report.changedSince)

        assertFalse(WeekReport.of(played.programme, played.entries, week.endDate.plusDays(3)).first().changedSince)
    }

    @Test
    fun `a missed week stays missed when its doses are deleted, and says its total has changed`() {
        val played = play(programme, "mx")
        val missed = played.programme.week(2)
        val left = played.entries.filterNot { it.logicalDate in missed }
        val reports = WeekReport.of(played.programme, left, played.programme.week(3).startDate)
        assertEquals(WeekReport.State.MISSED, reports[1].state)
        assertEquals(Dose.ZERO, reports[1].total)
        assertTrue(reports[1].changedSince)
        assertEquals(listOf(false, true, false), reports.map { it.changedSince })
    }

    @Test
    fun `overtime weeks count among themselves, and pay back only the weeks with hits`() {
        // Week 12 missed, the first overtime missed, then the second met.
        val played = play(programme, "mmmmmmmmmmmxxm")
        val reports = WeekReport.of(played.programme, played.entries, played.programme.week(14).endDate.plusDays(2))
        assertEquals(14, reports.size)
        assertEquals(List(12) { null } + listOf(1, 2), reports.map { it.overtime })
        assertEquals(listOf(WeekReport.State.CAUGHT_UP, WeekReport.State.CAUGHT_UP, WeekReport.State.MET), reports.takeLast(3).map { it.state })
        assertEquals(listOf(14, 14, null), reports.takeLast(3).map { it.caughtUpBy })
        // The first overtime put no HP on Gary, so meeting the second pays back week 12 alone.
        assertEquals(listOf(12), reports.last().paidBack)
    }
}
