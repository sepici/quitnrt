package app.thelasthit.nrt.domain.reminders

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The reminders in PLAN §13. Each switches on and off on its own. A reminder about a moment (a
 * nudge, the check-in, "Forgot to log") is dropped in quiet hours, since it means nothing by the
 * morning. News that keeps (a week's result, a badge) waits for quiet hours to end.
 */
enum class Reminder(val onByDefault: Boolean, val waitsOutQuietHours: Boolean) {
    /** A log takes the week past 80% or 100% of its allowance, or today past the daily guide. */
    ALLOWANCE(onByDefault = true, waitsOutQuietHours = false),

    /** Daily at the check-in time: today's total against the guide, with Log and All good. */
    CHECK_IN(onByDefault = true, waitsOutQuietHours = false),

    /** A week's result as it comes in, with the hits it earned. */
    WEEKLY_RESULT(onByDefault = true, waitsOutQuietHours = true),

    /** A badge or gear unlocking. */
    ACHIEVEMENTS(onByDefault = true, waitsOutQuietHours = true),

    /** Nothing logged by about 2 hours after the usual first dose. */
    FORGOT_TO_LOG(onByDefault = false, waitsOutQuietHours = false),
}

/** The Reminders page: which reminders are on, the check-in's time, and quiet hours. */
data class ReminderSettings(
    val on: Set<Reminder> = DEFAULT_ON,
    val checkInTime: LocalTime = DEFAULT_CHECK_IN,
    val quietHours: QuietHours = QuietHours(),
) {
    init {
        require(checkInTime.second == 0 && checkInTime.nano == 0) { "The check-in is at a whole minute: $checkInTime" }
    }

    fun isOn(reminder: Reminder): Boolean = reminder in on

    fun with(reminder: Reminder, on: Boolean): ReminderSettings = copy(on = if (on) this.on + reminder else this.on - reminder)

    /** Whether [reminder] can go out at [at]: it's on, and quiet hours allow it now or later. */
    fun delivery(reminder: Reminder, at: Instant, zone: ZoneId): Delivery = when {
        !isOn(reminder) -> Delivery.Off
        !quietHours.isQuiet(at, zone) -> Delivery.Now
        reminder.waitsOutQuietHours -> Delivery.At(quietHours.endAfter(at, zone))
        else -> Delivery.Dropped
    }

    /** Whether the check-in time falls in quiet hours, so it never shows. */
    val checkInIsQuiet: Boolean get() = quietHours.enabled && checkInTime in quietHours

    companion object {
        val DEFAULT_ON: Set<Reminder> = Reminder.entries.filter { it.onByDefault }.toSet()

        /** PLAN §13: daily at 21:30, changeable. */
        val DEFAULT_CHECK_IN: LocalTime = LocalTime.of(21, 30)
    }
}

/** What becomes of a reminder at a given moment. */
sealed interface Delivery {
    /** Switched off. */
    data object Off : Delivery

    data object Now : Delivery

    /** In quiet hours, held until they end. */
    data class At(val instant: Instant) : Delivery

    /** In quiet hours, and not worth holding. */
    data object Dropped : Delivery
}

/**
 * Hours when no reminder shows, read off the phone's clock: from [start] up to [end], overnight
 * when [start] is later in the day than [end]. The same time for both is an empty window.
 */
data class QuietHours(
    val enabled: Boolean = true,
    val start: LocalTime = DEFAULT_START,
    val end: LocalTime = DEFAULT_END,
) {
    init {
        require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0) { "Quiet hours run in whole minutes: $start to $end" }
    }

    /** Whether the clock time [time] is inside the window, whether or not quiet hours are on. */
    operator fun contains(time: LocalTime): Boolean = when {
        start == end -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end
    }

    fun isQuiet(at: Instant, zone: ZoneId): Boolean = enabled && at.atZone(zone).toLocalTime() in this

    /** When quiet hours next end after [at], or [at] itself when it isn't quiet. */
    fun endAfter(at: Instant, zone: ZoneId): Instant = if (isQuiet(at, zone)) ReminderTimes.nextAt(end, at, zone) else at

    companion object {
        val DEFAULT_START: LocalTime = LocalTime.of(22, 0)
        val DEFAULT_END: LocalTime = LocalTime.of(7, 0)
    }
}

object ReminderTimes {

    /**
     * The next moment after [after] when the clock in [zone] reads [time]. On the day the clocks
     * go forward past it, that's when they jump; on the day they go back, the first time round.
     */
    fun nextAt(time: LocalTime, after: Instant, zone: ZoneId): Instant {
        val date = after.atZone(zone).toLocalDate()
        val today = ZonedDateTime.of(date, time, zone).withEarlierOffsetAtOverlap().toInstant()
        return if (today > after) today else ZonedDateTime.of(date.plusDays(1), time, zone).withEarlierOffsetAtOverlap().toInstant()
    }
}
