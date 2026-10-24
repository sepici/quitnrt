package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.AchievementRules
import app.thelasthit.nrt.domain.game.GameRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import app.thelasthit.nrt.domain.game.BreathingSession as GameSession

class BreathingTimerTest {

    private val start: Instant = Instant.parse("2026-10-23T14:04:26Z")

    private fun seconds(value: Double): Duration = Duration.ofMillis((value * 1000).toLong())

    @Test
    fun `a session is the whole rounds nearest to its minutes`() {
        fun rounds(pattern: BreathingPattern) = BreathingPlan.MINUTES.map { BreathingPlan(pattern, it).rounds }

        assertEquals(16, BreathingPattern.BOX.roundSeconds)
        assertEquals(19, BreathingPattern.FOUR_SEVEN_EIGHT.roundSeconds)
        // "4 rounds, about 1 minute", as the mockup says.
        assertEquals(listOf(4, 8, 11), rounds(BreathingPattern.BOX))
        assertEquals(listOf(3, 6, 9), rounds(BreathingPattern.FOUR_SEVEN_EIGHT))
        assertEquals(Duration.ofSeconds(64), BreathingPlan(BreathingPattern.BOX, 1).length)
        assertEquals(Duration.ofSeconds(171), BreathingPlan(BreathingPattern.FOUR_SEVEN_EIGHT, 3).length)
    }

    @Test
    fun `a session runs for 1 to 3 minutes`() {
        assertThrows<IllegalArgumentException> { BreathingPlan(BreathingPattern.BOX, 0) }
        assertThrows<IllegalArgumentException> { BreathingPlan(BreathingPattern.BOX, 4) }
    }

    @Test
    fun `the mockup's moment, 2 rounds and 2 seconds in, is breathe in 3 of round 3 with 30 seconds left`() {
        val moment = BreathingPlan(BreathingPattern.BOX, 1).at(seconds(34.2))

        assertEquals(3, moment.round)
        assertEquals(BreathStep.IN, moment.step)
        assertEquals(3, moment.count)
        assertEquals(Duration.ofSeconds(30), moment.remaining)
        assertEquals(2, moment.roundsDone)
        assertFalse(moment.finished)
        assertEquals(0.55, moment.stepProgress, 1e-9)
        assertEquals(2.2 / 16, moment.roundProgress, 1e-9)
        assertEquals(8, moment.phaseNumber)
    }

    @Test
    fun `4-7-8 holds for 7 and breathes out for 8, counting up each time`() {
        val plan = BreathingPlan(BreathingPattern.FOUR_SEVEN_EIGHT, 1)
        val steps = listOf(0.0, 3.9, 4.0, 10.9, 11.0, 18.9, 19.0).map { plan.at(seconds(it)) }

        assertEquals(
            listOf(BreathStep.IN, BreathStep.IN, BreathStep.HOLD_IN, BreathStep.HOLD_IN, BreathStep.OUT, BreathStep.OUT, BreathStep.IN),
            steps.map { it.step },
        )
        assertEquals(listOf(1, 4, 1, 7, 1, 8, 1), steps.map { it.count })
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3), steps.map { it.phaseNumber })
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 2), steps.map { it.round })
    }

    @Test
    fun `the square fills on the breath in, holds, then empties`() {
        val plan = BreathingPlan(BreathingPattern.BOX, 1)
        assertEquals(listOf(0.0, 0.5, 1.0, 0.5, 0.0), listOf(0.0, 2.0, 6.0, 10.0, 14.0).map { plan.at(seconds(it)).fullness })
    }

    @Test
    fun `at the end every round is done and nothing is left`() {
        val plan = BreathingPlan(BreathingPattern.BOX, 1)
        val end = plan.at(Duration.ofSeconds(64))
        val past = plan.at(Duration.ofSeconds(90))

        assertTrue(end.finished)
        assertEquals(4, end.roundsDone)
        assertEquals(Duration.ZERO, end.remaining)
        assertEquals(end, past)
        assertFalse(plan.at(seconds(63.99)).finished)
        assertEquals(3, plan.at(seconds(63.99)).roundsDone)
        assertEquals(Duration.ofSeconds(1), plan.at(seconds(63.99)).remaining)
    }

    @Test
    fun `pausing stops the clock, and resuming carries on where it left off`() {
        val plan = BreathingPlan(BreathingPattern.BOX, 1)
        val run = BreathingRun(plan, start)
        val paused = run.pause(start.plusSeconds(10))

        assertTrue(paused.isPaused)
        assertEquals(Duration.ofSeconds(10), paused.elapsed(start.plusSeconds(500)))
        assertFalse(paused.isFinished(start.plusSeconds(500)))
        // Pausing twice changes nothing.
        assertEquals(paused, paused.pause(start.plusSeconds(20)))

        val resumed = paused.resume(start.plusSeconds(100))
        assertEquals(Duration.ofSeconds(30), resumed.elapsed(start.plusSeconds(120)))
        assertEquals(resumed, resumed.resume(start.plusSeconds(130)))
        assertFalse(resumed.isFinished(start.plusSeconds(153)))
        assertTrue(resumed.isFinished(start.plusSeconds(154)))
        // Nothing counts past the end.
        assertEquals(plan.length, resumed.elapsed(start.plusSeconds(1000)))
    }

    @Test
    fun `a clock that goes back adds nothing`() {
        val run = BreathingRun(BreathingPlan(BreathingPattern.BOX, 1), start)
        assertEquals(Duration.ZERO, run.elapsed(start.minusSeconds(30)))
    }

    @Test
    fun `a session run to the end counts its rounds, and one stopped early keeps the whole rounds done`() {
        val run = BreathingRun(BreathingPlan(BreathingPattern.BOX, 1), start)

        assertEquals(
            BreathingOutcome(BreathingPattern.BOX, start, Duration.ofSeconds(64), rounds = 4, ranToEnd = true),
            run.outcome(start.plusSeconds(70)),
        )
        assertEquals(
            BreathingOutcome(BreathingPattern.BOX, start, Duration.ofSeconds(40), rounds = 2, ranToEnd = false),
            run.outcome(start.plusMillis(40_700)),
        )
        // The repository's session agrees on what counts.
        val stopped = run.outcome(start.plusSeconds(40))
        assertFalse(BreathingSession("s", stopped.pattern, stopped.startedAt, stopped.duration, stopped.rounds, stopped.ranToEnd).counts)
    }

    @Test
    fun `the pips run from 0 to the next badge while it fits, then from the badge before`() {
        assertEquals(BreathingProgress(0, 0, 1), BreathingBadges.progress(0))
        assertEquals(BreathingProgress(4, 0, 10), BreathingBadges.progress(4))
        assertEquals(10, BreathingBadges.progress(4).pips)
        assertEquals(4, BreathingBadges.progress(4).filled)
        assertEquals(BreathingProgress(10, 10, 25), BreathingBadges.progress(10))
        assertEquals(15, BreathingBadges.progress(12).pips)
        assertEquals(2, BreathingBadges.progress(12).filled)
        assertNull(BreathingBadges.progress(25).next)
        assertEquals(0, BreathingBadges.progress(31).pips)
    }

    @Test
    fun `the session that earns a badge shows the row it filled`() {
        assertTrue(BreathingBadges.earnedAt(10))
        assertFalse(BreathingBadges.earnedAt(11))
        val ten = BreathingBadges.progress(10, ending = true)
        assertEquals(BreathingProgress(10, 0, 10), ten)
        assertEquals(10, ten.filled)
        assertEquals(BreathingProgress(25, 10, 25), BreathingBadges.progress(25, ending = true))
        // A session that earns nothing shows the way on, as it would before.
        assertEquals(BreathingBadges.progress(5), BreathingBadges.progress(5, ending = true))
    }

    @Test
    fun `the badge counts are the game's own`() {
        fun record(sessions: Int) = GameRecord(
            today = TODAY,
            breathing = List(sessions) { GameSession(GameSession.Type.BOX, start, Duration.ofSeconds(64), rounds = 4, completed = true) },
        )
        val badges = listOf(Achievement.BREATHING_1, Achievement.BREATHING_10, Achievement.BREATHING_25)
        badges.zip(BreathingBadges.AT).forEach { (badge, count) ->
            assertTrue(AchievementRules.holds(badge, record(count)), "$badge at $count")
            assertFalse(AchievementRules.holds(badge, record(count - 1)), "$badge at ${count - 1}")
        }
    }
}
