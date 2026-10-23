package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.EarnedHit.Kind.CATCH_UP
import app.thelasthit.nrt.domain.programme.EarnedHit.Kind.WEEK_MET
import app.thelasthit.nrt.domain.programme.WeekResult.CAUGHT_UP
import app.thelasthit.nrt.domain.programme.WeekResult.MET
import app.thelasthit.nrt.domain.programme.WeekResult.MISSED
import app.thelasthit.nrt.domain.programme.WeekResult.PENDING
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MissedWeeksTest {

    private val programme = planExample()

    private val Programme.results: List<WeekResult> get() = weeks.map { it.result }

    /** Each hit as its kind, the week it pays for and the week that earned it. */
    private fun List<EarnedHit>.summary() = map { Triple(it.kind, it.week, it.earnedBy) }

    @Test
    fun `carrying on, meeting the next week pays back the missed one`() {
        val played = play(programme, "mmxm")
        assertEquals(listOf(MET, MET, CAUGHT_UP, MET) + List(8) { PENDING }, played.programme.results)
        assertEquals(
            listOf(Triple(WEEK_MET, 1, 1), Triple(WEEK_MET, 2, 2), Triple(WEEK_MET, 4, 4), Triple(CATCH_UP, 3, 4)),
            played.hits.summary(),
        )
        // The catch-up arrives with the result of the week that earned it.
        val week4 = played.programme.week(4)
        assertEquals(listOf(week4.resultAt, week4.resultAt), played.hits.takeLast(2).map { it.at })
    }

    @Test
    fun `carrying on keeps the plan's lower targets`() {
        val played = play(programme, "mmx").programme
        assertEquals(programme.allowances, played.allowances)
        assertEquals(12, played.weeks.size)
        assertEquals(120, played.maxHp)
    }

    @Test
    fun `meeting a week pays back every missed week since the last met one`() {
        val beforeCatchUp = play(programme, "mxxx").programme
        assertEquals(listOf(MET, MISSED, MISSED, MISSED), beforeCatchUp.results.take(4))
        val played = play(programme, "mxxxm")
        assertEquals(listOf(MET, CAUGHT_UP, CAUGHT_UP, CAUGHT_UP, MET), played.programme.results.take(5))
        assertEquals(
            listOf(Triple(WEEK_MET, 5, 5), Triple(CATCH_UP, 2, 5), Triple(CATCH_UP, 3, 5), Triple(CATCH_UP, 4, 5)),
            played.hits.drop(1).summary(),
        )
        assertEquals(setOf(played.programme.week(5).resultAt), played.hits.drop(1).map { it.at }.toSet())
    }

    @Test
    fun `each catch-up is paid once`() {
        val played = play(programme, "xmxxm")
        assertEquals(
            listOf(
                Triple(WEEK_MET, 2, 2), Triple(CATCH_UP, 1, 2),
                Triple(WEEK_MET, 5, 5), Triple(CATCH_UP, 3, 5), Triple(CATCH_UP, 4, 5),
            ),
            played.hits.summary(),
        )
        assertEquals(played.hits, played.programme.earnedHits)
    }

    @Test
    fun `repeating a missed week keeps its target a week longer, for 10 HP more`() {
        val missed = play(programme, "mmx")
        val clock = dueFor(missed.programme.week(3))
        val repeated = missed.programme.repeat(3, clock)
        assertEquals(13, repeated.weeks.size)
        assertEquals(130, repeated.maxHp)
        val again = repeated.week(4)
        assertEquals(WeekKind.REPEAT, again.kind)
        assertTrue(again.isExtension)
        assertEquals(programme.week(4).startDate, again.startDate)
        assertEquals(Dose.mg(112), again.target.weeklyAllowance)
        // The rest of the plan moves on a week, unchanged.
        assertEquals(programme.weeks.drop(3).map { it.target }, repeated.weeks.drop(4).map { it.target })
        assertEquals(programme.weeks.drop(3).map { it.startDate.plusWeeks(1) }, repeated.weeks.drop(4).map { it.startDate })
        assertEquals(date("2027-01-22"), repeated.weeks.last().endDate)
        assertTrue(repeated.allowances.zipWithNext().all { (week, next) -> next <= week })
    }

    @Test
    fun `meeting the repeat pays back the missed week too`() {
        val played = play(programme, "mmrm")
        assertEquals(listOf(MET, MET, CAUGHT_UP, MET), played.programme.results.take(4))
        assertEquals(listOf(Triple(WEEK_MET, 4, 4), Triple(CATCH_UP, 3, 4)), played.hits.drop(2).summary())
    }

    @Test
    fun `a missed repeat can be repeated again`() {
        val played = play(programme, "mmrrm").programme
        assertEquals(14, played.weeks.size)
        assertEquals(140, played.maxHp)
        assertEquals(listOf(WeekKind.REPEAT, WeekKind.REPEAT, WeekKind.PLANNED), played.weeks.drop(3).take(3).map { it.kind })
        assertEquals(List(3) { Dose.mg(112) }, played.allowances.drop(2).take(3))
        assertEquals(listOf(MET, MET, CAUGHT_UP, CAUGHT_UP, MET), played.results.take(5))
    }

    @Test
    fun `the choice to repeat is open while the next week runs`() {
        val missed = play(programme, "mmx").programme
        // Week 3's result comes in at 04:00 on Sunday 15 November. Week 4 ends at 04:00 on the 21st.
        assertTrue(missed.canRepeat(3, clockAt("2026-11-15T04:00")))
        assertTrue(missed.canRepeat(3, clockAt("2026-11-21T03:59")))
        assertEquals(missed.week(3), missed.openChoice(clockAt("2026-11-20T12:00")))
        assertFalse(missed.canRepeat(3, clockAt("2026-11-21T04:00")))
        assertNull(missed.openChoice(clockAt("2026-11-21T04:00")))
        assertThrows<IllegalArgumentException> { missed.repeat(3, clockAt("2026-11-21T04:00")) }
    }

    @Test
    fun `only a missed week can be repeated, and only once`() {
        val missed = play(programme, "mmx").programme
        val clock = clockAt("2026-11-16T12:00")
        assertFalse(missed.canRepeat(2, clock))
        assertFalse(missed.canRepeat(4, clock))
        val repeated = missed.repeat(3, clock)
        assertFalse(repeated.canRepeat(3, clock))
        assertNull(repeated.openChoice(clock))
    }

    @Test
    fun `a missed final week goes to overtime rather than a repeat`() {
        val played = play(programme, "mmmmmmmmmmmx").programme
        assertEquals(ProgrammeStatus.OVERTIME, played.status)
        assertEquals(13, played.weeks.size)
        val overtime = played.week(13)
        assertEquals(WeekKind.OVERTIME, overtime.kind)
        assertTrue(overtime.isNrtFree)
        assertEquals(played.week(12).endDate.plusDays(1), overtime.startDate)
        assertFalse(played.canRepeat(12, dueFor(played.week(12))))
        // Overtime puts no HP on the demon.
        assertEquals(120, played.maxHp)
        assertEquals(1, played.hitsStillOnOffer)
    }

    @Test
    fun `overtime runs until a week is met, which pays back everything`() {
        // Weeks 10 to 12 missed, then one overtime week missed and the next met.
        val played = play(programme, "mmmmmmmmmxxxxm")
        val after = played.programme
        assertEquals(ProgrammeStatus.COMPLETE, after.status)
        assertEquals(14, after.weeks.size)
        assertEquals(listOf(WeekKind.OVERTIME, WeekKind.OVERTIME), after.weeks.takeLast(2).map { it.kind })
        assertEquals(List(9) { MET } + List(4) { CAUGHT_UP } + MET, after.results)
        // The overtime week earns the hits for weeks 10 to 12, and none of its own.
        assertEquals(
            listOf(Triple(CATCH_UP, 10, 14), Triple(CATCH_UP, 11, 14), Triple(CATCH_UP, 12, 14)),
            played.hits.drop(9).summary(),
        )
        assertEquals(after.maxHp, HP_PER_HIT * after.earnedHits.size)
        assertEquals(0, after.hp(after.earnedHits.size))
    }

    @Test
    fun `meeting the final week completes the programme`() {
        val played = play(programme, "m".repeat(12)).programme
        assertEquals(ProgrammeStatus.COMPLETE, played.status)
        assertEquals(12, played.weeks.size)
        // Nothing more comes in, however long the app waits.
        val later = played.settle(emptyList(), clockAt("2027-06-01T12:00"))
        assertEquals(played, later.programme)
        assertEquals(emptyList<EarnedHit>(), later.newHits)
    }

    @Test
    fun `a programme is active until overtime or the end`() {
        assertEquals(ProgrammeStatus.ACTIVE, programme.status)
        assertEquals(ProgrammeStatus.ACTIVE, play(programme, "mxr").programme.status)
    }
}
