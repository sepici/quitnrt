package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * How a week's extra goals went (PLAN §6.3), for Insights and the Plan tab. Goals show in stats
 * and never change whether a week is met.
 *
 * Only the week's days that are over count, as with the days within the guide, so today counts
 * once it ends. Days are logical days, as each entry's [Entry.logicalDate] has them.
 * - **Most units a day:** a day keeps a product's cap when its units of that product, logged on
 *   that day, are at or under it. A day with none keeps it. Patches count here, since a patch can
 *   have a cap like any product in the mix.
 * - **The gap between doses:** each gap between two doses next to each other on the same logical
 *   day, as Habits counts them, so a night's sleep is never one. A gap at least as long as the goal
 *   keeps it.
 * - **First dose not before:** a day keeps it when its first dose comes at or after the time, on
 *   the dose's own clock, or it has no dose. Times are read from the day start, as Habits reads
 *   the first dose, so one taken after midnight counts as late in its day rather than early.
 *
 * Patches are left out of the gap and the first dose, as Habits leaves them out: a patch goes on
 * at the same time each day, so it would hide when the first craving dose comes.
 */
data class GoalProgress(
    val week: ProgrammeWeek,
    /** The week's days that are over, which the caps and the first dose count. */
    val days: Int,
    /** A line for each product with a cap, in the week's mix order. */
    val caps: List<Cap>,
    val gap: Gap?,
    val firstDose: FirstDose?,
) {
    /** One goal's count: [kept] of [of] days or gaps kept it. */
    sealed interface Count {
        val kept: Int
        val of: Int

        /** Kept every time it was counted, which is the only time the app shows it in green. */
        val isAllKept: Boolean get() = of > 0 && kept == of
    }

    /** At most [maxUnits] of [product] a day. */
    data class Cap(val product: UserProduct, val maxUnits: Int, override val kept: Int, override val of: Int) : Count

    /** At least [minutes] between doses, over [of] gaps. */
    data class Gap(val minutes: Int, override val kept: Int, override val of: Int) : Count

    /** The first dose of the day at [notBefore] or later. */
    data class FirstDose(val notBefore: LocalTime, override val kept: Int, override val of: Int) : Count

    /** Every count, caps first, in the order the goals page lists them. */
    val counts: List<Count> get() = caps + listOfNotNull(gap, firstDose)

    /** No goal was on that week. */
    val isEmpty: Boolean get() = counts.isEmpty()

    init {
        require(days in 0..WeekTarget.DAYS) { "A week has 0 to ${WeekTarget.DAYS} days over: $days" }
        counts.forEach { require(it.kept in 0..it.of) { "Kept ${it.kept} of ${it.of}" } }
    }

    companion object {
        /**
         * [week]'s goals against [entries] (any, from any days) on [today], with the day starting
         * at [dayStart].
         */
        fun of(week: ProgrammeWeek, entries: List<Entry>, today: LocalDate, dayStart: LocalTime): GoalProgress {
            val goals = week.goals
            val dates = (0 until WeekTarget.DAYS).map { week.startDate.plusDays(it.toLong()) }.filter { it < today }
            val byDay = entries.filter { it.logicalDate in dates }.groupBy { it.logicalDate }
            val caps = week.target.mix.items.mapNotNull { item ->
                val max = goals.maxUnitsPerDay[item.product.id] ?: return@mapNotNull null
                val kept = dates.count { date -> byDay[date].orEmpty().filter { it.productId == item.product.id }.sumOf { it.quantity } <= max }
                Cap(item.product, max, kept, dates.size)
            }
            // Doses that pace: every product but patches, in the order they were taken.
            val paced = dates.associateWith { date -> byDay[date].orEmpty().filterNot { it.isBackground }.sortedBy { it.occurredAt } }
            val gap = goals.minGapMinutes?.let { minutes ->
                val goal = Duration.ofMinutes(minutes.toLong())
                val gaps = paced.values.flatMap { day -> day.zipWithNext { a, b -> Duration.between(a.occurredAt, b.occurredAt) } }
                Gap(minutes, kept = gaps.count { it >= goal }, of = gaps.size)
            }
            val firstDose = goals.firstDoseNotBefore?.let { time ->
                val from = minutesAfter(time, dayStart)
                val kept = paced.values.count { day ->
                    val first = day.firstOrNull() ?: return@count true
                    minutesAfter(first.occurredAt.atZone(first.zone).toLocalTime(), dayStart) >= from
                }
                FirstDose(time, kept, dates.size)
            }
            return GoalProgress(week, dates.size, caps, gap, firstDose)
        }

        /** [time] as minutes after [dayStart], 0 to a day less a minute, so the day reads in order across midnight. */
        private fun minutesAfter(time: LocalTime, dayStart: LocalTime): Int =
            Math.floorMod(time.toSecondOfDay() / 60 - dayStart.toSecondOfDay() / 60, MINUTES_A_DAY)

        private const val MINUTES_A_DAY = 24 * 60
    }
}

/**
 * Insights' "Your goals": the extra goals of the programme week running on a day and of the week
 * before it, each goal with this week's count beside last week's. A goal on in only one of the
 * two has nothing for the other.
 */
data class GoalWeeks(
    /** The week running, when it has a goal on. */
    val thisWeek: GoalProgress?,
    /** The week before it, or the last week once every week is over, when it had a goal on. */
    val lastWeek: GoalProgress?,
) {
    /** One goal, as it went in each week. */
    data class Line<T : GoalProgress.Count>(val thisWeek: T?, val lastWeek: T?)

    /** A line for each product capped in either week: this week's in its mix order, then last week's that left it. */
    val caps: List<Line<GoalProgress.Cap>>
        get() {
            val now = thisWeek?.caps.orEmpty()
            val before = lastWeek?.caps.orEmpty()
            val ids = (now + before).map { it.product.id }.distinct()
            return ids.map { id -> Line(now.firstOrNull { it.product.id == id }, before.firstOrNull { it.product.id == id }) }
        }

    val gap: Line<GoalProgress.Gap>?
        get() = Line(thisWeek?.gap, lastWeek?.gap).takeIf { it.thisWeek != null || it.lastWeek != null }

    val firstDose: Line<GoalProgress.FirstDose>?
        get() = Line(thisWeek?.firstDose, lastWeek?.firstDose).takeIf { it.thisWeek != null || it.lastWeek != null }

    companion object {
        /**
         * The goals of [programme]'s week on [today] and the week before, or null when neither
         * has a goal on. Before the plan starts there's nothing to show, and once its weeks are
         * over the last one shows as last week.
         */
        fun of(programme: Programme, entries: List<Entry>, today: LocalDate, dayStart: LocalTime): GoalWeeks? {
            val current = programme.weekOn(today)
            val previous = if (current != null) {
                programme.weeks.getOrNull(current.number - 2)
            } else {
                programme.weeks.lastOrNull { it.endDate < today }
            }
            fun progress(week: ProgrammeWeek?) =
                week?.takeIf { it.goals.onCount > 0 }?.let { GoalProgress.of(it, entries, today, dayStart) }
            val weeks = GoalWeeks(progress(current), progress(previous))
            return weeks.takeIf { it.thisWeek != null || it.lastWeek != null }
        }
    }
}
