package app.thelasthit.nrt.domain.log

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The quick choices on the log sheet. Anything else goes through the date and time picker. */
enum class QuickTime {
    NOW,
    MINUTES_15_AGO,
    MINUTES_30_AGO,
    HOUR_AGO,
    TWO_HOURS_AGO,
    YESTERDAY;

    /** "Yesterday" keeps the time of day on the wall clock, so 15:05 today becomes 15:05 yesterday. */
    fun resolve(now: Instant, zone: ZoneId): Instant = when (this) {
        NOW -> now
        MINUTES_15_AGO -> now.minus(Duration.ofMinutes(15))
        MINUTES_30_AGO -> now.minus(Duration.ofMinutes(30))
        HOUR_AGO -> now.minus(Duration.ofHours(1))
        TWO_HOURS_AGO -> now.minus(Duration.ofHours(2))
        YESTERDAY -> now.atZone(zone).minusDays(1).toInstant()
    }
}

object LogTime {
    /** Phone clocks and watches drift a little, so a few minutes into the future is let through. */
    val FUTURE_LEEWAY: Duration = Duration.ofMinutes(5)

    fun isAcceptable(at: Instant, now: Instant): Boolean = !at.isAfter(now.plus(FUTURE_LEEWAY))
}
