package app.thelasthit.nrt.domain.reminders

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import java.time.LocalDate

/**
 * The nudge a log can bring (PLAN §13): the week going past 80% or 100% of its allowance, or
 * today going past the daily guide. Each fires on the log that crosses its line, so it comes
 * once, and only for the week that's running. "Past" means over: a week at exactly its
 * allowance is met, and a day at exactly the guide is within it.
 */
sealed interface AllowanceNudge {

    /** The week's total is over its allowance, so it won't be met. In an NRT-free week, any dose. */
    data class WeekOver(val week: ProgrammeWeek, val total: Dose) : AllowanceNudge

    /** The week's total is over [NEAR_PERCENT]% of its allowance, with [left] still to go. */
    data class WeekNear(val week: ProgrammeWeek, val total: Dose, val left: Dose) : AllowanceNudge

    /** Today's total is over the daily guide, and the week still has [weekLeft] of its allowance. */
    data class DayOver(val today: Dose, val guide: Dose, val weekLeft: Dose) : AllowanceNudge

    companion object {
        const val NEAR_PERCENT = 80

        /**
         * The nudge [entry], just logged, brings, if any. [logged] holds the running week's
         * entries with [entry] among them, and [today] is today's logical day. A log on an earlier
         * day only counts towards the week. Once the week is past its allowance, nothing more
         * comes that week: the one nudge says it.
         */
        fun forLog(entry: Entry, plan: Programme?, status: ProgrammeStatus, logged: List<Entry>, today: LocalDate): AllowanceNudge? {
            if (plan == null || status !in TodayGuide.RUNNING) return null
            val week = plan.weekOn(today) ?: return null
            if (entry.logicalDate !in week) return null
            val allowance = week.target.weeklyAllowance
            val after = week.totalOf(logged)
            val before = after.less(entry.total)

            if (crosses(before, after, allowance)) return WeekOver(week, after)
            if (after > allowance) return null
            val near = Dose(allowance.micrograms * NEAR_PERCENT / 100)
            if (!week.isNrtFree && crosses(before, after, near)) return WeekNear(week, after, allowance.less(after))
            if (entry.logicalDate != today || week.isNrtFree) return null
            val guide = week.target.dailyGuide
            val dayAfter = logged.filter { it.logicalDate == today }.map { it.total }.sum()
            return if (crosses(dayAfter.less(entry.total), dayAfter, guide)) DayOver(dayAfter, guide, allowance.less(after)) else null
        }

        /** From at or under [line] to over it. */
        private fun crosses(before: Dose, after: Dose, line: Dose): Boolean = before <= line && after > line

        /** What's left of this once [other] is taken off, never below nothing. */
        private fun Dose.less(other: Dose): Dose = Dose((micrograms - other.micrograms).coerceAtLeast(0))
    }
}

/** The daily guide the app measures today against. */
object TodayGuide {

    /** A plan's weeks run while it's active or in overtime. */
    val RUNNING: Set<ProgrammeStatus> = setOf(ProgrammeStatus.ACTIVE, ProgrammeStatus.OVERTIME)

    /**
     * The guide on [date]: the week's mix in mg, which is nothing in an NRT-free week. Null with
     * no plan running, or outside its weeks, such as the day before it starts.
     */
    fun on(plan: Programme?, status: ProgrammeStatus?, date: LocalDate): Dose? =
        if (plan == null || status !in RUNNING) null else plan.weekOn(date)?.target?.dailyGuide
}
