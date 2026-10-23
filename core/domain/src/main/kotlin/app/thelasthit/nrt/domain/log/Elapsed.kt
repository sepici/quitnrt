package app.thelasthit.nrt.domain.log

import java.time.Duration

/**
 * Time since the last dose, rounded down, in the parts Today shows: "25 min", "1 h 25 min",
 * "2 days 3 h". Minutes drop off once it's a day or more.
 */
data class Elapsed(val days: Long, val hours: Int, val minutes: Int) {

    /** Under a minute reads as "just now". */
    val isJustNow: Boolean get() = days == 0L && hours == 0 && minutes == 0

    companion object {
        /** A negative duration, from a dose logged a little in the future, counts as just now. */
        fun of(duration: Duration): Elapsed {
            val d = if (duration.isNegative) Duration.ZERO else duration
            // Not toHoursPart(): that needs API 31 on Android.
            val days = d.toDays()
            return Elapsed(
                days = days,
                hours = (d.toHours() % 24).toInt(),
                minutes = if (days > 0) 0 else (d.toMinutes() % 60).toInt(),
            )
        }
    }
}
