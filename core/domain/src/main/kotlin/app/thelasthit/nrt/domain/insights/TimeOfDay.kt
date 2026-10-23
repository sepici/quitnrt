package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.log.Entry
import java.time.LocalTime

/**
 * A part of the day, by the clock where a dose was taken: morning from 05:00, afternoon from
 * 12:00, evening from 17:00 and night from 22:00 until morning.
 */
enum class TimeOfDay(val from: LocalTime) {
    MORNING(LocalTime.of(5, 0)),
    AFTERNOON(LocalTime.of(12, 0)),
    EVENING(LocalTime.of(17, 0)),
    NIGHT(LocalTime.of(22, 0));

    companion object {
        fun of(time: LocalTime): TimeOfDay = when {
            time < MORNING.from -> NIGHT
            time < AFTERNOON.from -> MORNING
            time < EVENING.from -> AFTERNOON
            time < NIGHT.from -> EVENING
            else -> NIGHT
        }

        fun of(entry: Entry): TimeOfDay = of(entry.occurredAt.atZone(entry.zone).toLocalTime())
    }
}
