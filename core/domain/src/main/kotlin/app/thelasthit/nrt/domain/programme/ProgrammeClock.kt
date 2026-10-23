package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The moment the app looks at a programme: the time now, the phone's zone and the day start.
 * Weeks and results are worked out from this and the logs alone, so they come out the same
 * whenever the app looks.
 */
class ProgrammeClock(
    val now: Instant,
    val zone: ZoneId,
    val boundary: DayBoundary = DayBoundary(),
) {
    /** Today's logical day. */
    val today: LocalDate get() = boundary.logicalDate(now, zone)

    /** When [date]'s logical day ends: the next day start. */
    fun endOf(date: LocalDate): Instant = boundary.window(date, zone).endExclusive

    fun endOf(week: ProgrammeWeek): Instant = endOf(week.endDate)

    fun hasEnded(week: ProgrammeWeek): Boolean = now >= endOf(week)

    /** A week's result comes in [RESULT_GRACE] after it ends, so late logs still count. */
    fun resultDue(week: ProgrammeWeek): Instant = endOf(week) + RESULT_GRACE

    companion object {
        val RESULT_GRACE: Duration = Duration.ofHours(24)
    }
}
