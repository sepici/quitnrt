package app.thelasthit.nrt.domain.reminders

import app.thelasthit.nrt.domain.programme.EarnedHit
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.WeekResult
import java.time.Duration
import java.time.Instant

/**
 * A week's result for the weekly-result reminder: met or missed, with the hits it earned. A met
 * week that paid back missed ones lists them in [paidBack], and its [hits] count them too.
 */
data class ResultNotice(val week: ProgrammeWeek, val hits: Int, val paidBack: List<Int>) {
    val met: Boolean get() = week.result == WeekResult.MET
}

object ResultNotices {

    /**
     * A result older than this isn't news any more, as after restoring a backup. It's a little
     * over a week, so a phone that was off when one result came in still hears of it with the next.
     */
    val TOO_OLD: Duration = Duration.ofDays(10)

    /**
     * The results that came in after [notifiedUpTo], oldest first, leaving out any older than
     * [TOO_OLD]. Results are worked out whenever the app looks, so this only depends on when
     * each came in, not on who brought it in. A week missed and paid back since shows as the
     * met week's catch-up rather than on its own.
     */
    fun since(plan: Programme, notifiedUpTo: Instant?, now: Instant): List<ResultNotice> {
        val cutoff = now.minus(TOO_OLD).let { old -> if (notifiedUpTo != null && notifiedUpTo > old) notifiedUpTo else old }
        val hits = plan.earnedHits
        return plan.weeks
            .filter { week ->
                val at = week.resultAt
                at != null && at > cutoff && (week.result == WeekResult.MET || week.result == WeekResult.MISSED)
            }
            .sortedBy { it.resultAt }
            .map { week ->
                val earned = hits.filter { it.earnedBy == week.number }
                ResultNotice(week, earned.size, earned.filter { it.kind == EarnedHit.Kind.CATCH_UP }.map { it.week })
            }
    }

    /** What to keep as notified once [notices] are out: the newest result among them. */
    fun notifiedUpTo(notices: List<ResultNotice>, before: Instant?): Instant? =
        (notices.mapNotNull { it.week.resultAt } + listOfNotNull(before)).maxOrNull()
}
