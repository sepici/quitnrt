package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.domain.programme.guideOn
import java.time.LocalDate

/**
 * When the advice for after a slip comes up (PLAN §9, with §6.4's "a missed week is
 * information"): only when the running programme says something went over. Without a plan there's
 * no guide to go over, so there's never a slip.
 */
object Slips {

    /** How long after a missed week Today's advice answers it: the week that follows. */
    const val MISSED_WEEK_DAYS = 7L

    /**
     * The slip Today's advice answers on [today]: the last week whose result is in was missed,
     * during the week after it, or else yesterday went over its daily guide. It's settled by the
     * date, as the rest of Today's advice is, so a dose logged today doesn't change it. [entries]
     * need only hold yesterday's doses, and others are ignored. A week caught up since is no
     * longer a slip.
     */
    fun onToday(plan: Programme?, today: LocalDate, entries: List<Entry>): Slip? {
        if (plan == null || plan.status == ProgrammeStatus.COMPLETE || plan.startDate >= today) return null
        val last = plan.weeks.lastOrNull { it.endDate < today && it.result != WeekResult.PENDING }
        if (last != null && last.result == WeekResult.MISSED && today <= last.endDate.plusDays(MISSED_WEEK_DAYS)) {
            return Slip.MISSED_WEEK
        }
        val yesterday = today.minusDays(1)
        val guide = plan.guideOn(yesterday) ?: return null
        val total = entries.filter { it.logicalDate == yesterday }.map { it.total }.sum()
        return if (total > guide) Slip.OVER_GUIDE else null
    }

    /**
     * The slip [dose] brings straight after it's logged: it's today's, and today is now over its
     * daily guide. [day] is today's doses, with [dose] among them. Patches count towards the
     * guide like any dose, so a patch can bring it too.
     */
    fun afterLog(plan: Programme?, dose: Entry, day: List<Entry>, today: LocalDate): Slip? {
        if (plan == null || plan.status == ProgrammeStatus.COMPLETE || dose.logicalDate != today) return null
        val guide = plan.guideOn(today) ?: return null
        val doses = if (day.any { it.id == dose.id }) day else day + dose
        val total = doses.filter { it.logicalDate == today }.map { it.total }.sum()
        return if (total > guide) Slip.OVER_GUIDE else null
    }
}
