package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.insights.Habits
import app.thelasthit.nrt.domain.log.Entry
import java.time.Duration
import java.time.LocalDate

/**
 * The rule behind each achievement of PLAN 8.1. Each one reads a [GameRecord] and nothing else,
 * and says only whether it holds: none can touch the hit ledger, so none deals damage.
 *
 * Most are cumulative, so a bad week never takes one away, and an unlocked badge is stored for
 * good. A day counts once it's over and a week's result once it's in, so nothing unlocks on a
 * day or a week that could still change.
 */
object AchievementRules {

    /** Every achievement that holds for [record], in PLAN's order. */
    fun unlocked(record: GameRecord): Set<Achievement> {
        val facts = Facts(record)
        return Achievement.entries.filterTo(LinkedHashSet()) { facts.holds(it) }
    }

    fun holds(achievement: Achievement, record: GameRecord): Boolean = Facts(record).holds(achievement)

    /** An entry written at least this long after the dose was taken was backdated: it had been forgotten. */
    val BACKDATED_BY: Duration = Duration.ofHours(1)

    /** How far the first dose or the average gap has to move against the baseline, in minutes. */
    const val HABIT_MINUTES = 30

    /**
     * A week's habits are compared with the baseline's when it has doses on at least this many
     * days, as a baseline needs (PLAN 6.1).
     */
    const val HABIT_WEEK_DAYS = GameBaseline.MIN_DAYS_WITH_DOSES

    /** The baseline's top trigger needs at least this many doses to be worth comparing with. */
    const val TOP_TRIGGER_MIN_DOSES = 5

    /** How the rules see a record: worked out once, and only as far as a rule needs. */
    private class Facts(val record: GameRecord) {
        val today: LocalDate = record.today

        val dayTotals: Map<LocalDate, Dose> by lazy {
            record.entries.groupBy { it.logicalDate }.mapValues { (_, list) -> list.map { it.total }.sum() }
        }

        fun dayTotal(date: LocalDate): Dose = dayTotals[date] ?: Dose.ZERO

        fun isOver(date: LocalDate): Boolean = date < today

        val loggedDays: Int get() = dayTotals.size

        val longestStreak: Int by lazy {
            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            for (date in dayTotals.keys.sorted()) {
                run = if (previous != null && date == previous.plusDays(1)) run + 1 else 1
                longest = maxOf(longest, run)
                previous = date
            }
            longest
        }

        val tagged: Int by lazy { record.entries.count { it.tagKeys.isNotEmpty() } }

        /** Every week of every programme, with its programme. */
        val weeks: List<Pair<GameProgramme, GameWeek>> by lazy {
            record.programmes.flatMap { programme -> programme.weeks.map { programme to it } }
        }

        /** Weeks met, and missed weeks paid back since: the weeks squared. */
        val weeksSquared: Int by lazy { weeks.count { (_, week) -> week.isSquared } }

        fun total(week: GameWeek): Dose = week.days.map(::dayTotal).sum()

        val breaths: Int by lazy { record.breathing.count { it.counts } }

        /** Doses that answer a craving: patches stay out of habits, as they do in Insights. */
        val doses: List<Entry> by lazy { record.entries.filterNot { it.isBackground } }

        fun holds(achievement: Achievement): Boolean = when (achievement) {
            Achievement.FIRST_ENTRY -> loggedDays >= 1
            Achievement.LOGGED_3_DAYS -> loggedDays >= 3
            Achievement.LOGGED_7_DAYS -> loggedDays >= 7
            Achievement.LOGGED_14_DAYS -> loggedDays >= 14
            Achievement.LOGGED_30_DAYS -> loggedDays >= 30
            Achievement.LOGGED_60_DAYS -> loggedDays >= 60
            Achievement.LOGGED_90_DAYS -> loggedDays >= 90
            Achievement.STREAK_10_DAYS -> longestStreak >= 10
            Achievement.STREAK_21_DAYS -> longestStreak >= 21
            Achievement.STREAK_40_DAYS -> longestStreak >= 40
            Achievement.BASELINE_DONE -> record.programmes.any(::baselineDone)
            Achievement.HONEST_LEDGER -> record.entries.any(::wasBackdated)
            Achievement.TAGGED_25 -> tagged >= 25
            Achievement.TAGGED_100 -> tagged >= 100

            Achievement.FIRST_DAY_IN_GUIDE -> weeks.any { (_, week) ->
                week.days.any { isOver(it) && dayTotal(it) <= week.dailyGuide }
            }
            Achievement.FIRST_WEEK_MET -> weeks.any { (_, week) -> week.result == GameWeek.Result.MET }
            Achievement.WEEKS_MET_3 -> weeksSquared >= 3
            Achievement.WEEKS_MET_6 -> weeksSquared >= 6
            Achievement.WEEKS_MET_10 -> weeksSquared >= 10
            Achievement.BACK_IN_STEP -> weeks.any { (_, week) -> week.result == GameWeek.Result.CAUGHT_UP }
            Achievement.HALFWAY -> weeks.any { (programme, week) ->
                week.result == GameWeek.Result.MET && week.dailyGuide * 2 <= programme.baseline.daily
            }
            Achievement.DOWN_A_STRENGTH -> weeks.any { (programme, week) ->
                week.result == GameWeek.Result.MET && isDownAStrength(programme.baseline.usualDay, week.mix)
            }
            Achievement.FINAL_TAPER_WEEK -> record.programmes.any { programme ->
                programme.finalTaperWeek?.let { it.startDate <= today } == true
            }

            Achievement.WEEK_10_PERCENT_UNDER -> weeks.any { (_, week) ->
                week.result == GameWeek.Result.MET && week.weeklyAllowance > Dose.ZERO &&
                    total(week).micrograms * 10 <= week.weeklyAllowance.micrograms * 9
            }
            Achievement.FIVE_DAYS_UNDER -> weeks.any { (_, week) ->
                week.dailyGuide > Dose.ZERO && week.days.count { isOver(it) && dayTotal(it) < week.dailyGuide } >= 5
            }
            Achievement.THREE_WEEKS_UNDER -> record.programmes.any { programme ->
                programme.weeks.longestRun(::isUnder) >= 3
            }

            Achievement.LATER_FIRST_DOSE -> habitWeeks.any { (it.habits.firstDoseChange ?: 0) >= HABIT_MINUTES }
            Achievement.LONGER_GAPS -> habitWeeks.any { (it.habits.averageGapChange ?: 0) >= HABIT_MINUTES }
            Achievement.TOP_TRIGGER_DOWN -> habitWeeks.any { it.topTriggerDown }
            Achievement.BREATHING_1 -> breaths >= 1
            Achievement.BREATHING_10 -> breaths >= 10
            Achievement.BREATHING_25 -> breaths >= 25

            Achievement.DEMON_DEFEATED -> record.programmes.any { it.ledger.isDefeated }
        }

        /** A measured baseline whose seven days are over, at least five of them with doses. */
        fun baselineDone(programme: GameProgramme): Boolean {
            val days = programme.baseline.measuredDays ?: return false
            return isOver(days.last()) && days.count { it in dayTotals } >= GameBaseline.MIN_DAYS_WITH_DOSES
        }

        fun wasBackdated(entry: Entry): Boolean {
            val written = record.loggedAt[entry.id] ?: return false
            return Duration.between(entry.occurredAt, written) >= BACKDATED_BY
        }

        /** Met, and under the allowance rather than at it. An NRT-free week can't be under zero. */
        fun isUnder(week: GameWeek): Boolean =
            week.result == GameWeek.Result.MET && week.weeklyAllowance > Dose.ZERO && total(week) < week.weeklyAllowance

        /**
         * Each finished week with doses on enough days, against its programme's measured
         * baseline. A typed baseline has no days to compare with.
         */
        val habitWeeks: List<HabitWeek> by lazy {
            record.programmes.flatMap { programme ->
                val baselineDays = programme.baseline.measuredDays?.toSet() ?: return@flatMap emptyList()
                val baseline = doses.filter { it.logicalDate in baselineDays }
                if (baseline.isEmpty()) return@flatMap emptyList()
                val top = TopTrigger.of(baseline)
                programme.weeks.filter { isOver(it.endDate) }.mapNotNull { week ->
                    val inWeek = doses.filter { it.logicalDate in week }
                    if (inWeek.map { it.logicalDate }.toSet().size < HABIT_WEEK_DAYS) return@mapNotNull null
                    HabitWeek(Habits.of(inWeek, baseline, record.dayStart), top?.isDownBy(inWeek) == true)
                }
            }
        }
    }

    /** A finished week's habits against the baseline's, and whether the baseline's top trigger fell. */
    private class HabitWeek(val habits: Habits, val topTriggerDown: Boolean)

    /** The baseline's most used tag, by doses, with ties going to the first key as in Insights. */
    private class TopTrigger(val key: String, val doses: Int, val outOf: Int) {

        /** Whether its share of [week]'s doses is down by a third or more, in whole numbers. */
        fun isDownBy(week: List<Entry>): Boolean {
            val inWeek = week.count { key in it.tagKeys }.toLong()
            return week.isNotEmpty() && 3 * inWeek * outOf <= 2L * doses * week.size
        }

        companion object {
            fun of(baseline: List<Entry>): TopTrigger? = baseline.flatMap { it.tagKeys }.groupingBy { it }.eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.takeIf { it.value >= TOP_TRIGGER_MIN_DOSES }
                ?.let { TopTrigger(it.key, it.value, baseline.size) }
        }
    }

    /**
     * Whether a week's mix has a lower strength of a kind of product than the usual day had, such
     * as 2 mg gum after 4 mg, or a 14 mg patch after a 21 mg one.
     */
    internal fun isDownAStrength(usualDay: List<MixUnit>, mix: List<MixUnit>): Boolean {
        val strongest = usualDay.groupBy { it.form }.mapValues { (_, units) -> units.maxOf { it.perUnit } }
        return mix.groupBy { it.form }.any { (form, units) ->
            val before = strongest[form] ?: return@any false
            units.maxOf { it.perUnit } < before
        }
    }

    /** The most consecutive items that match. */
    private fun <T> List<T>.longestRun(matches: (T) -> Boolean): Int {
        var longest = 0
        var run = 0
        for (item in this) {
            run = if (matches(item)) run + 1 else 0
            longest = maxOf(longest, run)
        }
        return longest
    }
}
