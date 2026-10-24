package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.average
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.guideOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The running plan's daily guide as Insights reads it (PLAN §10): the guide line on the daily
 * chart, the calendar shaded against it, and the days that stayed within it. The guide counts
 * every product, so these only show with all of them together.
 *
 * [guideOn] is the plan's guide on a day, null outside it. The calendar needs something for every
 * day, so a day before the plan [start] is shaded against the [usualDay] it steps down from, and
 * a day after it's complete against nothing, since the plan ends NRT-free.
 */
class PlanGuides(
    val start: LocalDate,
    val usualDay: Dose,
    private val guide: (LocalDate) -> Dose?,
) {
    fun guideOn(date: LocalDate): Dose? = guide(date)

    /** What the calendar shades [date] against: its guide, or else the usual day or nothing. */
    fun shadeAgainst(date: LocalDate): Dose = guide(date) ?: if (date < start) usualDay else Dose.ZERO

    /** Whether any of [dates] come before the plan, so the calendar says what they're shaded against. */
    fun hasDaysBefore(dates: List<LocalDate>): Boolean = dates.any { it < start }

    /** The guide under each of [intake]'s days, or a gap for a day outside the plan. */
    fun dailyLine(intake: DailyIntake): List<Dose?> = intake.days.map { guide(it.date) }

    /**
     * The guide under each of [intake]'s weeks: the average guide over the days its column
     * averages (its finished days, or today alone when it has none yet), or a gap when any of
     * them is outside the plan.
     */
    fun weeklyLine(intake: DailyIntake): List<Dose?> =
        intake.days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.map { (_, week) ->
            val counted = week.filter { it.date != intake.today }.ifEmpty { week }
            val guides = counted.map { guide(it.date) }
            if (guides.any { it == null }) null else guides.filterNotNull().average()
        }

    /**
     * How many of [intake]'s finished days stayed at or under their guide, out of those that had
     * one. Null when none has, as in a plan's first day.
     */
    fun daysWithin(intake: DailyIntake): DaysWithin? {
        val judged = intake.days.filter { it.date != intake.today }.mapNotNull { day -> guide(day.date)?.let { day.total <= it } }
        if (judged.isEmpty()) return null
        return DaysWithin(within = judged.count { it }, days = judged.size)
    }

    /** [day]'s step on the calendar against [shadeAgainst], or 0 for a day that isn't tracked. */
    fun step(day: CalendarHeat.Day): Int =
        if (day.state != CalendarHeat.State.TRACKED) 0 else GuideShade.step(day.total, shadeAgainst(day.date))

    companion object {
        fun of(programme: Programme): PlanGuides = PlanGuides(programme.startDate, programme.usualDay.total) { programme.guideOn(it) }
    }
}

/** [within] of [days] finished days stayed at or under their guide. */
data class DaysWithin(val within: Int, val days: Int) {
    init {
        require(within in 0..days) { "$within of $days days" }
    }
}

/**
 * Shades a day by its share of the mg it's measured against, on the same five-step ramp as the
 * heat maps: up to half, up to three quarters, up to the guide itself, up to a quarter over, and
 * beyond. So every step from 4 up is over the guide. Nothing logged is the empty step, and with a
 * guide of nothing, any use at all is the top step.
 */
object GuideShade {
    /** Where steps 2 to 5 start, as percentages of the guide, for the legend. */
    val STARTS_PERCENT: List<Int> = listOf(50, 75, 100, 125)

    fun step(total: Dose, against: Dose): Int {
        if (total <= Dose.ZERO) return 0
        if (against <= Dose.ZERO) return HeatScale.STEPS
        // Steps of a quarter of the guide, in whole micrograms: total × 4 against guide × k.
        val quarters = total.micrograms * 4
        val guide = against.micrograms
        return when {
            quarters <= guide * 2 -> 1
            quarters <= guide * 3 -> 2
            quarters <= guide * 4 -> 3
            quarters <= guide * 5 -> 4
            else -> 5
        }
    }
}
