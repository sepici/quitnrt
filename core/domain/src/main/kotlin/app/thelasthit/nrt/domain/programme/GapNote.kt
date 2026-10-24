package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.log.Entry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The spacing note on the log sheet (PLAN §6.3). With a gap goal on in the dose's week, a dose
 * closer than the goal to another one gets a calm note saying how far apart they are. It never
 * stops the dose being logged.
 *
 * It counts gaps as the goal's stats do ([GoalProgress]): only between doses on the same logical
 * day, so a night's sleep is never one, never for a patch, and to the minute, as the times show.
 */
data class GapNote(
    val goalMinutes: Int,
    /** The nearest dose to this one that day. */
    val other: Entry,
    /** How far this dose is from [other], under the goal. */
    val apart: Duration,
    /** Whether [other] came first, as the last dose before this one. */
    val otherCameFirst: Boolean,
) {
    companion object {
        /** The gap goal of [programme]'s week on [date], or null when it's off or there's no week. */
        fun goalOn(programme: Programme?, date: LocalDate): Int? = programme?.weekOn(date)?.goals?.minGapMinutes

        /**
         * The note for a dose at [at] on logical day [date], against the doses in [entries] on
         * that day, or null when the gap goal is off, the dose is a patch, or it's at least the
         * goal from the doses either side. [editing] is the entry being changed, which isn't
         * compared with itself.
         */
        fun of(
            goalMinutes: Int?,
            at: Instant,
            date: LocalDate,
            isPatch: Boolean,
            entries: List<Entry>,
            editing: String? = null,
        ): GapNote? {
            if (goalMinutes == null || isPatch) return null
            val time = at.toMinute()
            val nearest = entries
                .filter { it.logicalDate == date && !it.isBackground && it.id != editing }
                .minWithOrNull(compareBy<Entry> { Duration.between(it.occurredAt.toMinute(), time).abs() }.thenBy { it.occurredAt.toMinute() > time })
                ?: return null
            val apart = Duration.between(nearest.occurredAt.toMinute(), time).abs()
            if (apart >= Duration.ofMinutes(goalMinutes.toLong())) return null
            return GapNote(goalMinutes, nearest, apart, otherCameFirst = nearest.occurredAt.toMinute() <= time)
        }
    }
}
