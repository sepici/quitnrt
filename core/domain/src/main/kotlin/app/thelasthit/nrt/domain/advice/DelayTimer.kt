package app.thelasthit.nrt.domain.advice

import java.time.Duration
import java.time.Instant

/**
 * The delay timer (PLAN §9): [minutes] from [startedAt], by the clock, so it keeps counting
 * while the app is closed and is right whenever it's next looked at.
 */
data class DelayRun(val startedAt: Instant, val minutes: Int) {
    init {
        require(minutes in MINUTES) { "The delay timer runs for 5 to 10 minutes, not $minutes" }
    }

    val length: Duration = Duration.ofMinutes(minutes.toLong())

    val endsAt: Instant = startedAt + length

    /** The time left at [now], in whole seconds rounded up, so it reads 0:00 only at the end. */
    fun remaining(now: Instant): Duration {
        val millis = Duration.between(now, endsAt).toMillis().coerceIn(0, length.toMillis())
        return Duration.ofSeconds((millis + 999) / 1000)
    }

    /** How much of the time has gone, from 0 to 1. */
    fun progress(now: Instant): Double =
        (Duration.between(startedAt, now).toMillis().toDouble() / length.toMillis()).coerceIn(0.0, 1.0)

    fun isDone(now: Instant): Boolean = !now.isBefore(endsAt)

    /**
     * Whether to buzz on finding the timer over at [now]. Only close to the end: if the app comes
     * back long after, the screen says it's done without a buzz out of nowhere.
     */
    fun buzzesAt(now: Instant): Boolean = isDone(now) && now.isBefore(endsAt + BUZZ_WINDOW)

    companion object {
        val MINUTES: IntRange = 5..10

        const val DEFAULT_MINUTES = 5

        val BUZZ_WINDOW: Duration = Duration.ofMinutes(1)
    }
}
