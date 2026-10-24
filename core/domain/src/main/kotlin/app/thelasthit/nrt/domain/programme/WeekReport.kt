package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import java.time.LocalDate

/**
 * A programme week as Insights' weekly view shows it (PLAN §10): what's logged on its days
 * against its allowance, and how it went: met, missed, or missed and caught up later.
 *
 * [total] is what's logged on its days now. A result stays as it came in (PLAN §6.4), so a dose
 * backdated into a met week can take its total over the allowance, and a delete can take a missed
 * week's under it. [changedSince] says so, so the week can say its result is the one that came in.
 */
data class WeekReport(
    val week: ProgrammeWeek,
    val total: Dose,
    val state: State,
    /** For a week caught up, the met week that paid it back. */
    val caughtUpBy: Int? = null,
    /** For a met week, the missed weeks it paid back a hit for, oldest first. */
    val paidBack: List<Int> = emptyList(),
    /** For a missed week, the week still to come in that pays it back if it's met. */
    val backIfMet: Int? = null,
    /** Which overtime week this is, from 1, or null for a week of the plan (see [overtimeNumber]). */
    val overtime: Int? = null,
) {
    enum class State {
        /** Today is one of its days. */
        RUNNING,

        /** It's over, and its result comes in 24 hours after it ends. */
        WAITING,
        MET,
        MISSED,
        CAUGHT_UP,
    }

    val allowance: Dose get() = week.target.weeklyAllowance

    val against: AgainstGuide get() = AgainstGuide(total, allowance)

    /**
     * Whether the total is now on the other side of the allowance from the result that came in:
     * a met week now over it, or a missed one (caught up or not) now within it.
     */
    val changedSince: Boolean
        get() = when (state) {
            State.MET -> !against.isWithin
            State.MISSED, State.CAUGHT_UP -> against.isWithin
            State.RUNNING, State.WAITING -> false
        }

    companion object {
        /**
         * Every week of [programme] that has started by [today], oldest first, each with what
         * [entries] logged on its days. Weeks still to come are left out.
         */
        fun of(programme: Programme, entries: List<Entry>, today: LocalDate): List<WeekReport> {
            val weeks = programme.weeks.filter { it.startDate <= today }
            return weeks.map { week ->
                val state = when (week.result) {
                    WeekResult.MET -> State.MET
                    WeekResult.MISSED -> State.MISSED
                    WeekResult.CAUGHT_UP -> State.CAUGHT_UP
                    WeekResult.PENDING -> if (today in week) State.RUNNING else State.WAITING
                }
                val later = programme.weeks.filter { it.number > week.number }
                val caughtUpBy = if (state == State.CAUGHT_UP) later.firstOrNull { it.result == WeekResult.MET }?.number else null
                // Meeting the next week still to come pays back every missed week since the last met one.
                val backIfMet = if (state == State.MISSED) later.firstOrNull { it.result == WeekResult.PENDING }?.number else null
                val paidBack = if (state == State.MET) {
                    val since = programme.weeks.lastOrNull { it.number < week.number && it.result == WeekResult.MET }?.number ?: 0
                    // Overtime puts no HP on the demon, so there's no hit to pay back for it.
                    programme.weeks
                        .filter { it.number in (since + 1) until week.number && it.result == WeekResult.CAUGHT_UP && it.earnsHit }
                        .map { it.number }
                } else {
                    emptyList()
                }
                WeekReport(week, week.totalOf(entries), state, caughtUpBy, paidBack, backIfMet, programme.overtimeNumber(week))
            }
        }
    }
}
