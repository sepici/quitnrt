package app.thelasthit.nrt.domain.reminders

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The evening check-in (PLAN §13): "Today 14 mg, guide 16 mg. Anything missing?" It goes out once
 * a logical day, at the check-in time, unless that's in quiet hours.
 */
data class CheckIn(val today: Dose, val doses: Int, val guide: Dose?) {
    companion object {
        /** [entries] are the day's, [guide] the day's guide if a plan is running. */
        fun of(entries: List<Entry>, guide: Dose?): CheckIn = CheckIn(entries.map { it.total }.sum(), entries.size, guide)

        /**
         * When the next check-in is due after [now]. One already sent on today's logical day
         * ([sentOn]) moves it to the next day's.
         */
        fun nextAt(time: LocalTime, now: Instant, zone: ZoneId, boundary: DayBoundary, sentOn: LocalDate?): Instant {
            var next = ReminderTimes.nextAt(time, now, zone)
            while (sentOn != null && boundary.logicalDate(next, zone) <= sentOn) next = ReminderTimes.nextAt(time, next, zone)
            return next
        }

        /**
         * Whether a check-in that wakes at [now] is the one due at [time], rather than one woken
         * hours early or late, as after the phone changes zone. WorkManager's timing is rough, so
         * it allows [LEEWAY] either side.
         */
        fun isDue(time: LocalTime, now: Instant, zone: ZoneId): Boolean {
            val next = ReminderTimes.nextAt(time, now.minus(LEEWAY), zone)
            return Duration.between(next, now).abs() <= LEEWAY
        }

        val LEEWAY: Duration = Duration.ofHours(1)
    }
}

/**
 * "Forgot to log" (PLAN §13, off by default): when nothing is logged by about 2 hours after the
 * usual first log, "Nothing logged yet today. Forgot one?" Patches count, since this is about
 * logging, not cravings: a patch put on each morning is that day's first log.
 */
object ForgotToLog {

    /** The usual first log comes from this many full days before today. */
    const val LOOK_BACK_DAYS = 14

    /** Fewer logged days than this in the look-back, and there's no usual time to go by. */
    const val MIN_DAYS = 3

    val AFTER_USUAL: Duration = Duration.ofHours(2)

    /**
     * The average clock time of the first log on each logged day of the [LOOK_BACK_DAYS] before
     * [today], or null with fewer than [MIN_DAYS] such days. It's averaged as time since the day
     * start, so a first log just after midnight and one just before average sensibly.
     */
    fun usualFirstLog(entries: List<Entry>, today: LocalDate, dayStart: LocalTime): LocalTime? =
        usualMinutes(entries, today, dayStart)?.let { dayStart.plusMinutes(it) }

    /**
     * When [date]'s reminder is due on the clock in [zone]: [AFTER_USUAL] after the usual first
     * log. Null when there's no usual time, or it would come after the day has ended.
     */
    fun dueOn(date: LocalDate, entries: List<Entry>, boundary: DayBoundary, zone: ZoneId): Instant? {
        val minutes = usualMinutes(entries, date, boundary.dayStart) ?: return null
        val due = date.atTime(boundary.dayStart).plusMinutes(minutes + AFTER_USUAL.toMinutes()).atZone(zone).toInstant()
        return due.takeIf { it < boundary.window(date, zone).endExclusive }
    }

    /**
     * When to look next after [now]: today's due time if it's still to come and today hasn't had
     * one ([remindedOn]), else tomorrow's. Null when there's no usual time yet.
     */
    fun nextCheck(now: Instant, entries: List<Entry>, boundary: DayBoundary, zone: ZoneId, remindedOn: LocalDate?): Instant? {
        val today = boundary.logicalDate(now, zone)
        val due = dueOn(today, entries, boundary, zone)
        if (due != null && due > now && remindedOn != today) return due
        return dueOn(today.plusDays(1), entries, boundary, zone)
    }

    /**
     * Whether to remind at [now]: it's past today's due time, today has nothing logged, and
     * today hasn't had one yet.
     */
    fun shouldRemind(now: Instant, entries: List<Entry>, boundary: DayBoundary, zone: ZoneId, remindedOn: LocalDate?): Boolean {
        val today = boundary.logicalDate(now, zone)
        if (remindedOn == today) return false
        val due = dueOn(today, entries, boundary, zone) ?: return false
        return now >= due && entries.none { it.logicalDate == today }
    }

    private fun usualMinutes(entries: List<Entry>, today: LocalDate, dayStart: LocalTime): Long? {
        val from = today.minusDays(LOOK_BACK_DAYS.toLong())
        val firsts = entries.filter { it.logicalDate >= from && it.logicalDate < today }
            .groupBy { it.logicalDate }
            .values
            .map { day -> day.minBy { it.occurredAt } }
        if (firsts.size < MIN_DAYS) return null
        val minutes = firsts.map { entry ->
            val clock = entry.occurredAt.atZone(entry.zone).toLocalTime()
            Math.floorMod(clock.toSecondOfDay() / 60 - dayStart.toSecondOfDay() / 60, MINUTES_A_DAY).toLong()
        }
        return (minutes.sum() * 2 + minutes.size) / (2L * minutes.size)
    }

    private const val MINUTES_A_DAY = 24 * 60
}
