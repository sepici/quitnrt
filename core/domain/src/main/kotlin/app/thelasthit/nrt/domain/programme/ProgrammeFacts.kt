package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.product.UserProduct
import java.time.LocalDate

// What the Plan tab says about a programme, worked out from its weeks.

/**
 * A week where the plan moves a kind of product to a lower strength: [product] is the strength it
 * moves to, such as 2 mg gum in week 3. [isNew] when the product wasn't in the user's list when
 * the plan was built, so it's added when the plan starts.
 */
data class StrengthSwitch(val week: Int, val product: UserProduct, val isNew: Boolean)

/**
 * Every week that brings in a lower strength than the week before had of the same form, in week
 * order: the switches the plan's preview marks. Week 1 is compared with the usual day.
 */
fun Programme.switches(): List<StrengthSwitch> = buildList {
    var before = usualDay.mix
    for (week in weeks) {
        val mix = week.target.mix
        for (product in PlanBuilder.lowerStrengths(before, mix)) add(StrengthSwitch(week.number, product, ladder.isNew(product)))
        before = mix
    }
}

/**
 * How many ladder steps each taper week takes from the week before, by week number: week 1 from
 * the usual day. NRT-free weeks have none.
 */
fun Programme.stepsTaken(): Map<Int, Int> = buildMap {
    var before = 0
    for (week in weeks) {
        val step = week.target.step ?: continue
        put(week.number, step - before)
        before = step
    }
}

/** The missed weeks [week]'s result paid back, if it was met: every one since the last met week. */
fun Programme.paidBackBy(week: Int): List<Int> {
    val index = week - 1
    if (weeks.getOrNull(index)?.result != WeekResult.MET) return emptyList()
    val lastMet = weeks.subList(0, index).indexOfLast { it.result == WeekResult.MET }
    return weeks.subList(lastMet + 1, index).filter { it.result == WeekResult.CAUGHT_UP }.map { it.number }
}

/** The met week that paid back missed week [week], or null if it hasn't been. */
fun Programme.caughtUpIn(week: Int): Int? {
    if (weeks.getOrNull(week - 1)?.result != WeekResult.CAUGHT_UP) return null
    return weeks.drop(week).firstOrNull { it.result == WeekResult.MET }?.number
}

/** The missed weeks that meeting the next week would pay back, with a hit for each. */
val Programme.owed: List<Int> get() = weeks.filter { it.result == WeekResult.MISSED && it.earnsHit }.map { it.number }

/** Missed weeks still to pay back, hit or not: what meeting an overtime week makes good. */
val Programme.unpaid: List<Int> get() = weeks.filter { it.result == WeekResult.MISSED }.map { it.number }

/** The hits [week]'s result earned: its own, and one for each missed week it paid back. */
fun Programme.hitsEarnedBy(week: Int): Int = earnedHits.count { it.earnedBy == week }

/** A week that has ended but whose result is still to come, in the 24 hours after it ends. */
fun Programme.awaitingResult(clock: ProgrammeClock): ProgrammeWeek? =
    weeks.firstOrNull { it.result == WeekResult.PENDING && clock.hasEnded(it) }

/** The planned weeks and repeats: the run before any overtime. */
val Programme.plannedWeeks: Int get() = weeks.count { it.kind != WeekKind.OVERTIME }

/** The weeks of overtime so far. */
val Programme.overtimeWeeks: Int get() = weeks.count { it.kind == WeekKind.OVERTIME }

/** The day of its week [date] is, from 1 to 7. */
fun ProgrammeWeek.dayOf(date: LocalDate): Int = (date.toEpochDay() - startDate.toEpochDay()).toInt() + 1
