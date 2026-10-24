package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A [total] against a [guide], a day's or a week's: at or under it with what's left, or over it
 * by how much. At the guide exactly is within it, as a week at its allowance is met.
 */
data class AgainstGuide(val total: Dose, val guide: Dose) {
    val isWithin: Boolean get() = total <= guide

    /** What's left before the guide, or nothing once it's reached. */
    val left: Dose get() = Dose((guide.micrograms - total.micrograms).coerceAtLeast(0))

    /** How far over the guide, or nothing while it's within. */
    val over: Dose get() = Dose((total.micrograms - guide.micrograms).coerceAtLeast(0))
}

/**
 * The week [date] is paced against, for its daily guide (PLAN §6.2), or null for a day outside
 * the plan: before its first week, or once it's complete.
 *
 * After the final week the plan runs on NRT-free until that week's result is in, since missing it
 * starts overtime from the next day. The days in between get the overtime week that would follow,
 * which isn't one of [Programme.weeks] yet, so the guide never lapses for a day and comes back.
 */
fun Programme.guideWeekOn(date: LocalDate): ProgrammeWeek? {
    weekOn(date)?.let { return it }
    val last = weeks.last()
    if (date <= last.endDate || last.result != WeekResult.PENDING) return null
    val next = ProgrammeWeek(
        number = last.number + 1,
        startDate = last.endDate.plusDays(1),
        target = WeekTarget.NRT_FREE,
        kind = WeekKind.OVERTIME,
    )
    return next.takeIf { date in it }
}

/** The daily guide on [date], or null for a day outside the plan (see [guideWeekOn]). */
fun Programme.guideOn(date: LocalDate): Dose? = guideWeekOn(date)?.target?.dailyGuide

/**
 * Whether [date], with [total] logged, stayed at or under its guide, for its tick in History and
 * on Today's week. Only a day that's over counts, so today never has a tick, and nor does a day
 * outside the plan.
 */
fun Programme.isWithinGuide(date: LocalDate, total: Dose, today: LocalDate): Boolean {
    if (date >= today) return false
    val guide = guideOn(date) ?: return false
    return total <= guide
}

/**
 * Where today sits in the running plan, for Today: the programme [week] it falls in and which
 * [day] of it, 1 to 7. Programme weeks run from the start day picked, so Today's week follows
 * them rather than Mondays (ROADMAP P3-2).
 */
data class PlanToday(
    val week: ProgrammeWeek,
    val day: Int,
    /** The plan's weeks as built, with repeats and without overtime: the 12 of "Week 4 of 12". */
    val plannedWeeks: Int,
    /**
     * The final week whose result is still to come, while [week] is the overtime it would start
     * if it's missed. Null the rest of the time.
     */
    val awaitingResultOf: Int?,
) {
    init {
        require(day in 1..WeekTarget.DAYS) { "A week has days 1 to ${WeekTarget.DAYS}: $day" }
    }

    val guide: Dose get() = week.target.dailyGuide

    val allowance: Dose get() = week.target.weeklyAllowance

    val isNrtFree: Boolean get() = week.isNrtFree

    val isOvertime: Boolean get() = week.kind == WeekKind.OVERTIME

    /** Today's [total] against the daily guide. */
    fun today(total: Dose): AgainstGuide = AgainstGuide(total, guide)

    /** The week's [total] so far against its allowance. */
    fun week(total: Dose): AgainstGuide = AgainstGuide(total, allowance)

    companion object {
        /** Today in [programme], or null when today has no guide: before the plan starts, or after it's done. */
        fun of(programme: Programme, today: LocalDate): PlanToday? {
            val week = programme.guideWeekOn(today) ?: return null
            val day = ChronoUnit.DAYS.between(week.startDate, today).toInt() + 1
            val awaiting = if (week.number > programme.weeks.size) programme.weeks.last().number else null
            return PlanToday(week, day, programme.weeks.count { it.kind != WeekKind.OVERTIME }, awaiting)
        }
    }
}
