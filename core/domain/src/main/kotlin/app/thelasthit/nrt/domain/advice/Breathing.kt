package app.thelasthit.nrt.domain.advice

import java.time.Duration
import java.time.Instant

/** The breathing timer's two patterns (PLAN §9). */
enum class BreathingPattern {
    /** 4-4-4-4: in, hold, out, hold. */
    BOX,

    /** In for 4, hold for 7, out for 8. */
    FOUR_SEVEN_EIGHT,
}

/**
 * One run of the breathing timer, kept for the breathing badges. A session counts towards them
 * when it [ranToEnd] with at least one whole round done. Pausing on the way is fine.
 */
data class BreathingSession(
    val id: String,
    val pattern: BreathingPattern,
    val startedAt: Instant,
    val duration: Duration,
    val rounds: Int,
    val ranToEnd: Boolean,
) {
    init {
        require(!duration.isNegative) { "A session can't run for $duration" }
        require(rounds >= 0) { "Rounds can't be negative: $rounds" }
    }

    /** Whether the session counts towards the breathing badges. */
    val counts: Boolean get() = ranToEnd && rounds >= 1
}
