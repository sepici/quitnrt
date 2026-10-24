package app.thelasthit.nrt.domain.advice

import java.time.Duration
import java.time.Instant

/** One part of a breath. */
enum class BreathStep {
    IN,

    /** Holding with the lungs full. */
    HOLD_IN,

    OUT,

    /** Holding with the lungs empty, as box breathing does before the next breath in. */
    HOLD_OUT,
}

data class BreathPhase(val step: BreathStep, val seconds: Int)

/** A round of the pattern, in order: box breathing is 4 each, 4-7-8 is in 4, hold 7, out 8. */
val BreathingPattern.phases: List<BreathPhase>
    get() = when (this) {
        BreathingPattern.BOX -> listOf(
            BreathPhase(BreathStep.IN, 4),
            BreathPhase(BreathStep.HOLD_IN, 4),
            BreathPhase(BreathStep.OUT, 4),
            BreathPhase(BreathStep.HOLD_OUT, 4),
        )
        BreathingPattern.FOUR_SEVEN_EIGHT -> listOf(
            BreathPhase(BreathStep.IN, 4),
            BreathPhase(BreathStep.HOLD_IN, 7),
            BreathPhase(BreathStep.OUT, 8),
        )
    }

/** How long one round of the pattern takes: 16 seconds for box breathing, 19 for 4-7-8. */
val BreathingPattern.roundSeconds: Int get() = phases.sumOf { it.seconds }

/**
 * A breathing session of about [minutes], in whole rounds: the number of rounds nearest to that
 * time, and at least one. Box breathing's 16-second round gives 4 rounds for 1 minute, 8 for 2
 * and 11 for 3; 4-7-8's 19 seconds give 3, 6 and 9. A session counts towards the badges when it
 * runs to the end of its last round.
 */
data class BreathingPlan(val pattern: BreathingPattern, val minutes: Int) {
    init {
        require(minutes in MINUTES) { "A breathing session runs for 1 to 3 minutes, not $minutes" }
    }

    val rounds: Int = maxOf(1, (minutes * 60 * 2 + pattern.roundSeconds) / (2 * pattern.roundSeconds))

    val length: Duration = Duration.ofSeconds(rounds.toLong() * pattern.roundSeconds)

    /** Where the session is after [elapsed] of breathing, with pauses left out. */
    fun at(elapsed: Duration): BreathingMoment {
        val phases = pattern.phases
        val roundMillis = pattern.roundSeconds * 1000L
        val totalMillis = length.toMillis()
        val millis = elapsed.toMillis().coerceIn(0, totalMillis)
        if (millis >= totalMillis) {
            val last = phases.last()
            return BreathingMoment(
                round = rounds,
                step = last.step,
                count = last.seconds,
                stepProgress = 1.0,
                roundProgress = 1.0,
                phaseNumber = rounds * phases.size - 1,
                remaining = Duration.ZERO,
                roundsDone = rounds,
                finished = true,
            )
        }
        val roundIndex = (millis / roundMillis).toInt()
        val inRound = millis % roundMillis
        var start = 0L
        var phaseIndex = 0
        while (inRound >= start + phases[phaseIndex].seconds * 1000L) {
            start += phases[phaseIndex].seconds * 1000L
            phaseIndex++
        }
        val phase = phases[phaseIndex]
        val inPhase = inRound - start
        val left = totalMillis - millis
        return BreathingMoment(
            round = roundIndex + 1,
            step = phase.step,
            count = (inPhase / 1000).toInt() + 1,
            stepProgress = inPhase.toDouble() / (phase.seconds * 1000L),
            roundProgress = inRound.toDouble() / roundMillis,
            phaseNumber = roundIndex * phases.size + phaseIndex,
            remaining = Duration.ofSeconds((left + 999) / 1000),
            roundsDone = roundIndex,
            finished = false,
        )
    }

    companion object {
        val MINUTES: IntRange = 1..3
    }
}

/** A moment in a breathing session, for the animation, the count and the screen reader. */
data class BreathingMoment(
    /** The round under way, from 1, or the last when the session is over. */
    val round: Int,
    val step: BreathStep,
    /** The second of the step, counting up from 1: "Breathe in, 1, 2, 3, 4". */
    val count: Int,
    /** How far through the step, from 0 to 1. */
    val stepProgress: Double,
    /** How far through the round, from 0 to 1: the dot's way round the square. */
    val roundProgress: Double,
    /** Steps begun since the start, from 0, so each change of step can buzz once. */
    val phaseNumber: Int,
    /** Breathing left, in whole seconds rounded up: "0:30 left". */
    val remaining: Duration,
    /** Whole rounds breathed. */
    val roundsDone: Int,
    val finished: Boolean,
) {
    /** How full the lungs are, from 0 to 1: the square grows on the breath in and shrinks on the way out. */
    val fullness: Double
        get() = when (step) {
            BreathStep.IN -> stepProgress
            BreathStep.HOLD_IN -> 1.0
            BreathStep.OUT -> 1.0 - stepProgress
            BreathStep.HOLD_OUT -> 0.0
        }
}

/**
 * A breathing session as it runs, by the clock: when it started, the breathing done before the
 * latest pause, and when it last started or resumed, or null while it's paused. Time paused
 * doesn't count, so pausing on the way is fine, and nothing counts past the plan's length.
 */
data class BreathingRun(
    val plan: BreathingPlan,
    val startedAt: Instant,
    val before: Duration = Duration.ZERO,
    val resumedAt: Instant? = startedAt,
) {
    val isPaused: Boolean get() = resumedAt == null

    /** The breathing done by [now], pauses left out. A clock that goes back adds nothing. */
    fun elapsed(now: Instant): Duration {
        val since = resumedAt?.let { Duration.between(it, now) }?.takeUnless { it.isNegative } ?: Duration.ZERO
        return minOf(before + since, plan.length)
    }

    fun moment(now: Instant): BreathingMoment = plan.at(elapsed(now))

    fun isFinished(now: Instant): Boolean = elapsed(now) >= plan.length

    fun pause(now: Instant): BreathingRun = if (isPaused) this else copy(before = elapsed(now), resumedAt = null)

    fun resume(now: Instant): BreathingRun = if (isPaused) copy(resumedAt = now) else this

    /** What to keep of the session when it ends or is stopped at [now], for the breathing badges. */
    fun outcome(now: Instant): BreathingOutcome {
        val moment = moment(now)
        return BreathingOutcome(
            pattern = plan.pattern,
            startedAt = startedAt,
            duration = Duration.ofSeconds(elapsed(now).seconds),
            rounds = moment.roundsDone,
            ranToEnd = moment.finished,
        )
    }
}

/** A session's record: it counts towards the badges when it [ranToEnd], in whole rounds. */
data class BreathingOutcome(
    val pattern: BreathingPattern,
    val startedAt: Instant,
    val duration: Duration,
    val rounds: Int,
    val ranToEnd: Boolean,
)

/**
 * How far someone is towards the next breathing badge, as a row of pips from [from] to [next].
 * [next] is null once every badge is earned.
 */
data class BreathingProgress(val sessions: Int, val from: Int, val next: Int?) {
    val pips: Int get() = next?.minus(from) ?: 0

    val filled: Int get() = (sessions - from).coerceIn(0, pips)
}

/** The breathing badges of PLAN §8.1, for 1, 10 and 25 sessions that ran to the end. */
object BreathingBadges {
    val AT: List<Int> = listOf(1, 10, 25)

    /** The most pips in a row. A longer way to the next badge starts from the badge before. */
    const val MAX_PIPS = 15

    /** Whether the session that brought the count to [sessions] earned a badge. */
    fun earnedAt(sessions: Int): Boolean = sessions in AT

    /**
     * The way to the next badge after [sessions], or, when [ending] and [sessions] is a badge's
     * count, the way that ends there, so the session that earns a badge shows its row full. The
     * row starts at 0 while that fits in [MAX_PIPS], and at the badge before when it doesn't:
     * 4 sessions show 4 of 10 pips, and 12 show 2 of 15 on the way from 10 to 25.
     */
    fun progress(sessions: Int, ending: Boolean = false): BreathingProgress {
        val next = AT.firstOrNull { if (ending) it >= sessions else it > sessions }
            ?: return BreathingProgress(sessions, AT.last(), null)
        val before = AT.lastOrNull { it < next } ?: 0
        val from = if (next <= MAX_PIPS) 0 else before
        return BreathingProgress(sessions, from, next)
    }
}
