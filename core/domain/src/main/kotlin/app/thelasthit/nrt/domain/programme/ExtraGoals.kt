package app.thelasthit.nrt.domain.programme

import java.time.LocalDate
import java.time.LocalTime

/**
 * Setting the extra goals (PLAN §6.3), which start off. They're set for the week running now, or
 * week 1 before the plan starts, and carry into every week after it:
 * - **Most units a day:** a cap for each product in the week's mix. A later week keeps the cap
 *   of each product it shares, and a product new to it, such as a lower strength, starts at its
 *   own mix's units.
 * - **The gap between doses** stays the same.
 * - **First dose not before** moves [STEP_MINUTES] later each week, and stops at [LATEST_FIRST_DOSE]
 *   so it never wraps past midnight.
 *
 * NRT-free weeks and overtime get none, since there's nothing to pace. Weeks before the one set
 * keep theirs, so past weeks' stats still read against the goals they had.
 */
object ExtraGoals {

    const val STEP_MINUTES = 15L

    val LATEST_FIRST_DOSE: LocalTime = LocalTime.of(23, 45)

    /**
     * The week the goals are set for on [today]: the one running, or week 1 before the plan
     * starts. Null when that week is NRT-free, or the plan's weeks are over.
     */
    fun weekToSet(programme: Programme, today: LocalDate): ProgrammeWeek? {
        val week = if (today < programme.startDate) programme.week(1) else programme.weekOn(today) ?: return null
        return week.takeUnless { it.isNrtFree || it.kind == WeekKind.OVERTIME }
    }

    /** The goals each product starts with when the cap goal is switched on: its units in [week]'s mix. */
    fun startingCaps(week: ProgrammeWeek): Map<String, Int> = week.target.mix.items.associate { it.product.id to it.units }

    /** [time] moved on [weeks] weeks, as a later week's first dose is. */
    fun later(time: LocalTime, weeks: Int): LocalTime {
        val minutes = time.hour * 60L + time.minute + STEP_MINUTES * weeks
        val latest = LATEST_FIRST_DOSE.hour * 60L + LATEST_FIRST_DOSE.minute
        return LocalTime.ofSecondOfDay(minOf(minutes, latest) * 60)
    }

    /** [programme] with [goals] for week [from], and carried into the weeks after it. */
    fun apply(programme: Programme, from: Int, goals: WeekGoals): Programme {
        require(from in 1..programme.weeks.size) { "There's no week $from" }
        val first = programme.week(from)
        require(!first.isNrtFree && first.kind != WeekKind.OVERTIME) { "Week $from has nothing to pace" }
        return programme.copy(
            weeks = programme.weeks.map { week ->
                when {
                    week.number < from -> week
                    week.number == from -> week.copy(goals = goals)
                    week.isNrtFree || week.kind == WeekKind.OVERTIME -> week.copy(goals = WeekGoals.NONE)
                    else -> week.copy(goals = carried(goals, week, week.number - from))
                }
            },
        )
    }

    private fun carried(goals: WeekGoals, week: ProgrammeWeek, weeksOn: Int): WeekGoals = WeekGoals(
        maxUnitsPerDay = if (goals.maxUnitsPerDay.isEmpty()) {
            emptyMap()
        } else {
            week.target.mix.items.associate { item -> item.product.id to (goals.maxUnitsPerDay[item.product.id] ?: item.units) }
        },
        minGapMinutes = goals.minGapMinutes,
        firstDoseNotBefore = goals.firstDoseNotBefore?.let { later(it, weeksOn) },
    )
}
