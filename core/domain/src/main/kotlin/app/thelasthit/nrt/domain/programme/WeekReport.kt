package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import java.time.LocalDate

/**
 * A programme week as Insights' weekly view shows it (PLAN §10): what was logged on its days
 * against its allowance, and how it went: met, missed, or missed and caught up later.
 */
data class WeekReport(
    val week: ProgrammeWeek,
    val total: Dose,
    val state: State,
    /** For a week caught up, the met week that paid it back. */
    val caughtUpBy: Int? = null,
    /** For a met week, the missed weeks it paid back, oldest first. */
    val paidBack: List<Int> = emptyList(),
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
                val caughtUpBy = if (state == State.CAUGHT_UP) {
                    programme.weeks.firstOrNull { it.number > week.number && it.result == WeekResult.MET }?.number
                } else {
                    null
                }
                val paidBack = if (state == State.MET) {
                    val since = programme.weeks.lastOrNull { it.number < week.number && it.result == WeekResult.MET }?.number ?: 0
                    programme.weeks.filter { it.number in (since + 1) until week.number && it.result == WeekResult.CAUGHT_UP }.map { it.number }
                } else {
                    emptyList()
                }
                WeekReport(week, week.totalOf(entries), state, caughtUpBy, paidBack)
            }
        }
    }
}
