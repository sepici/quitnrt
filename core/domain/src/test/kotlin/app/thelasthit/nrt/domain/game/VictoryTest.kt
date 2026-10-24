package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class VictoryTest {

    /** Every week of [run] met, then every hit spent, the last at [lastHit]. */
    private fun defeated(run: WeekRun, usualDay: List<MixUnit> = EXAMPLE_2_USUAL_DAY, measuredFrom: LocalDate? = BASELINE_START, lastHit: Instant? = null): GameProgramme {
        while (run.next != null) run.result(met = true)
        var programme = programmeOf(run.weeks, usualDay, measuredFrom).withEarnedHits()
        var ledger = programme.ledger
        var at = resultDue(run.weeks.last()).plus(Duration.ofHours(1))
        while (ledger.canHit) {
            if (ledger.banked == 1 && lastHit != null) at = lastHit
            ledger = ledger.hit(at)
            at = at.plusSeconds(60)
        }
        programme = programme.copy(ledger = ledger)
        return programme
    }

    @Test
    fun `no summary while the demon has HP`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        run.result(met = true)
        val programme = programmeOf(run.weeks).withEarnedHits()
        assertNull(Victory.of(programme, emptyList(), BOUNDARY, ZONE))
    }

    @Test
    fun `the summary has the weeks, the usual day's mg, the hits and the day of the last one`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        // 02:30 on a Sunday counts toward the Saturday before, as every day in the app does.
        val lastHit = java.time.LocalDateTime.parse("2027-02-07T02:30").atZone(ZONE).toInstant()
        val victory = requireNotNull(Victory.of(defeated(run, lastHit = lastHit), emptyList(), BOUNDARY, ZONE))
        assertEquals(12, victory.weeks)
        assertEquals(0, victory.overtime)
        assertEquals(Dose.mg(32), victory.usualDaily)
        assertEquals(12, victory.hits)
        assertEquals(LocalDate.of(2027, 2, 6), victory.lastHitOn)
        assertEquals(emptyList<String>(), victory.triggers)
    }

    @Test
    fun `a repeat counts as a week of the plan, and overtime is counted on its own`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        run.result(met = false)
        run.repeatLastMissed()
        repeat(11) { run.result(met = true) }
        run.result(met = false)
        run.result(met = true)
        val victory = requireNotNull(Victory.of(defeated(run), emptyList(), BOUNDARY, ZONE))
        assertEquals(13, victory.weeks)
        assertEquals(1, victory.overtime)
        assertEquals(13, victory.hits)
    }

    @Test
    fun `the triggers are the baseline's most tagged, with patches and rare tags left out`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        val doses = buildList {
            for (day in 2..8) {
                add(dose("2026-11-%02dT08:00".format(day), tags = setOf("coffee_tea", "just_woke_up")))
                if (day <= 5) add(dose("2026-11-%02dT13:00".format(day), tags = setOf("after_meal")))
                if (day <= 3) add(dose("2026-11-%02dT20:00".format(day), tags = setOf("alcohol")))
                // A patch every morning, tagged: it never counts as a trigger.
                add(dose("2026-11-%02dT07:00".format(day), unit = patch(14), tags = setOf("routine")))
            }
            // After the baseline: not what the run started with.
            repeat(10) { add(dose("2026-11-20T09:00", tags = setOf("stress"))) }
        }
        val victory = requireNotNull(Victory.of(defeated(run), doses, BOUNDARY, ZONE))
        // 7 each for coffee and waking, ties by key; after a meal (4) is third; alcohol (2) too rare.
        assertEquals(listOf("coffee_tea", "just_woke_up"), victory.triggers)
    }

    @Test
    fun `a typed baseline takes its triggers from the plan's first week`() {
        val run = WeekRun(EXAMPLE_2_PLAN)
        val doses = (9..13).map { day -> dose("2026-11-%02dT10:00".format(day), unit = gum4(1), tags = setOf("work_break")) } +
            (2..8).map { day -> dose("2026-11-%02dT10:00".format(day), tags = setOf("bored")) }
        val victory = requireNotNull(Victory.of(defeated(run, measuredFrom = null), doses, BOUNDARY, ZONE))
        assertEquals(listOf("work_break"), victory.triggers)
    }
}
