package app.thelasthit.nrt.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Decides which day an entry counts toward. With the default 04:00 day start, a dose at 01:30
 * counts toward the evening before.
 *
 * Each logical day runs from its day start to the next day's start, in the entry's own zone.
 * On clock-change days that span is 23 or 25 hours. If the day start falls in a skipped hour,
 * the day begins when the clocks jump. If it falls in a repeated hour, the day begins at the
 * first pass, so logical days never go backwards.
 */
class DayBoundary(val dayStart: LocalTime = DEFAULT_DAY_START) {

    fun logicalDate(instant: Instant, zone: ZoneId): LocalDate {
        val calendarDate = instant.atZone(zone).toLocalDate()
        return if (instant < start(calendarDate, zone)) calendarDate.minusDays(1) else calendarDate
    }

    fun window(date: LocalDate, zone: ZoneId): DayWindow =
        DayWindow(start = start(date, zone), endExclusive = start(date.plusDays(1), zone))

    private fun start(date: LocalDate, zone: ZoneId): Instant {
        val wallClock = date.atTime(dayStart)
        val transition = zone.rules.getTransition(wallClock)
        return if (transition != null && transition.isGap) {
            transition.instant
        } else {
            wallClock.atZone(zone).withEarlierOffsetAtOverlap().toInstant()
        }
    }

    companion object {
        val DEFAULT_DAY_START: LocalTime = LocalTime.of(4, 0)
    }
}

data class DayWindow(val start: Instant, val endExclusive: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant < endExclusive
}
