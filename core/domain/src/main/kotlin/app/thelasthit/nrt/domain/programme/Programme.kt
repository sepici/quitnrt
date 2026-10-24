package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Where a programme is. A [Programme] with weeks is [ACTIVE], [OVERTIME] or [COMPLETE]. Before
 * that it's [BASELINE], and [ABANDONED] is the user's call, so neither comes from the weeks.
 */
enum class ProgrammeStatus { BASELINE, ACTIVE, OVERTIME, COMPLETE, ABANDONED }

enum class WeekKind {
    /** One of the weeks the plan was built with. */
    PLANNED,

    /** Added by repeating a missed week: its target again, a week longer and 10 HP more. */
    REPEAT,

    /** Added after the final week is missed. NRT-free, and it puts no HP on the demon. */
    OVERTIME,
}

enum class WeekResult {
    /** Still running, or in the 24 hours after it ends. */
    PENDING,
    MET,
    MISSED,

    /** Missed, then paid back by meeting a later week. */
    CAUGHT_UP,
}

/**
 * A week's extra goals (PLAN §6.3), each off while it's null or empty. They show in stats and
 * advice, and never change whether a week is met.
 */
data class WeekGoals(
    /**
     * The most units a day of each product, by product ID, such as 5 pieces of gum. A cap for
     * each product, since one cap across them would count a 1 mg spray the same as a 4 mg piece.
     * Empty while the goal is off.
     */
    val maxUnitsPerDay: Map<String, Int> = emptyMap(),
    /** The shortest gap between doses, in minutes. */
    val minGapMinutes: Int? = null,
    /** The time on the wall clock the day's first dose waits for. */
    val firstDoseNotBefore: LocalTime? = null,
) {
    /** How many of the three goals are on. */
    val onCount: Int get() = listOf(maxUnitsPerDay.isNotEmpty(), minGapMinutes != null, firstDoseNotBefore != null).count { it }

    init {
        require(maxUnitsPerDay.values.all { it >= 1 }) { "At least 1 unit a day: $maxUnitsPerDay" }
        require(minGapMinutes == null || minGapMinutes in 1..MINUTES_A_DAY) { "A gap is 1 to $MINUTES_A_DAY minutes: $minGapMinutes" }
        require(firstDoseNotBefore == null || firstDoseNotBefore.second == 0 && firstDoseNotBefore.nano == 0) {
            "The first dose waits for a whole minute: $firstDoseNotBefore"
        }
    }

    companion object {
        /** Every goal off, as they start. */
        val NONE = WeekGoals()

        private const val MINUTES_A_DAY = 24 * 60
    }
}

/**
 * One week of a programme: seven logical days from [startDate]. [number] is its place in the
 * run, counting repeats and overtime. A result, once in, has the time it came in and the total it
 * was judged on. [goals] are its extra goals, which a repeat takes from the week it repeats.
 */
data class ProgrammeWeek(
    val number: Int,
    val startDate: LocalDate,
    val target: WeekTarget,
    val kind: WeekKind = WeekKind.PLANNED,
    val result: WeekResult = WeekResult.PENDING,
    val resultAt: Instant? = null,
    val goals: WeekGoals = WeekGoals.NONE,
    /**
     * What was logged on its days when its result came in: the total the result was judged on.
     * It's kept once, with the result, so a dose backdated into the week later never changes it.
     * Null while the result is to come, and for results that came in before totals were kept.
     */
    val resultTotal: Dose? = null,
) {
    init {
        require(number >= 1) { "Weeks count from 1: $number" }
        require((result == WeekResult.PENDING) == (resultAt == null)) { "Week $number is $result at $resultAt" }
        require(result != WeekResult.PENDING || resultTotal == null) { "Week $number has no result to be judged on $resultTotal" }
        require(kind != WeekKind.OVERTIME || target.isNrtFree) { "Overtime week $number has $target" }
    }

    /** The last weeks of every programme, and overtime: a target of zero. */
    val isNrtFree: Boolean get() = target.isNrtFree

    val endDate: LocalDate get() = startDate.plusDays(WeekTarget.DAYS - 1L)

    operator fun contains(date: LocalDate): Boolean = date in startDate..endDate

    /** Repeats and overtime are both extensions of the plan as built. */
    val isExtension: Boolean get() = kind != WeekKind.PLANNED

    /** Overtime weeks put no HP on the demon, so they have no hit of their own to earn. */
    val earnsHit: Boolean get() = kind != WeekKind.OVERTIME

    /** Everything logged on the week's logical days. */
    fun totalOf(entries: List<Entry>): Dose = entries.filter { it.logicalDate in this }.map { it.total }.sum()

    /**
     * The week's total as it's shown: the one its result was judged on, once that's in and kept,
     * or else what [entries] log on its days now.
     */
    fun shownTotal(entries: List<Entry>): Dose = resultTotal ?: totalOf(entries)

    /** Met at or under the allowance, which for an NRT-free week means nothing at all. */
    fun isMetBy(total: Dose): Boolean = total <= target.weeklyAllowance
}

/** What [Programme.settle] found: the programme with its new results, and the hits they earned. */
data class Settlement(val programme: Programme, val newHits: List<EarnedHit>)

/**
 * A programme from its first week: the weeks back to back from the start day, each with its
 * target and result, and the [ladder] their mixes come from. The demon's HP and the hits come
 * from the weeks alone, so they can always be worked out again and checked against the hit
 * ledger.
 */
data class Programme(
    val ladder: Ladder,
    val length: ProgrammeLength,
    val weeks: List<ProgrammeWeek>,
) {
    init {
        require(weeks.isNotEmpty()) { "A programme needs weeks" }
        weeks.forEachIndexed { index, week ->
            require(week.number == index + 1) { "Week ${week.number} is in place ${index + 1}" }
            require(week.startDate == weeks.first().startDate.plusWeeks(index.toLong())) {
                "Week ${week.number} doesn't follow on from the week before"
            }
            require(week.target.step == null || week.target.step in 0..ladder.size) {
                "Week ${week.number} is at step ${week.target.step} of a ladder of ${ladder.size}"
            }
        }
    }

    /** The usual day the programme steps down from. */
    val usualDay: UsualDay get() = ladder.usual

    val startDate: LocalDate get() = weeks.first().startDate

    val status: ProgrammeStatus
        get() = when {
            weeks.last().result == WeekResult.MET -> ProgrammeStatus.COMPLETE
            weeks.any { it.kind == WeekKind.OVERTIME } -> ProgrammeStatus.OVERTIME
            else -> ProgrammeStatus.ACTIVE
        }

    fun week(number: Int): ProgrammeWeek = weeks[number - 1]

    /** The week [date] falls in, or null before the start and after the last week so far. */
    fun weekOn(date: LocalDate): ProgrammeWeek? = weeks.firstOrNull { date in it }

    // The demon's HP and the hits.

    /** One hit for each week that puts HP on the demon, so meeting every week takes it to 0. */
    val hitsOnOffer: Int get() = weeks.count { it.earnsHit }

    /** 10 for each week of the plan, and for each repeat. */
    val maxHp: Int get() = HP_PER_HIT * hitsOnOffer

    /**
     * Every hit the results have earned, in the order they came in. A met week earns its own,
     * then one for each week it caught up.
     */
    val earnedHits: List<EarnedHit>
        get() = buildList {
            var paidUpTo = 0
            weeks.forEachIndexed { index, week ->
                if (week.result != WeekResult.MET) return@forEachIndexed
                val at = checkNotNull(week.resultAt)
                if (week.earnsHit) add(EarnedHit(EarnedHit.Kind.WEEK_MET, week.number, week.number, at))
                for (back in weeks.subList(paidUpTo, index)) {
                    if (back.result == WeekResult.CAUGHT_UP && back.earnsHit) {
                        add(EarnedHit(EarnedHit.Kind.CATCH_UP, back.number, week.number, at))
                    }
                }
                paidUpTo = index + 1
            }
        }

    /** Hits still to earn: from weeks to come, and missed weeks a met one can still pay back. */
    val hitsStillOnOffer: Int
        get() = weeks.count { it.earnsHit && (it.result == WeekResult.PENDING || it.result == WeekResult.MISSED) }

    /** The demon's HP once [hitsSpent] of the earned hits have been spent on it. */
    fun hp(hitsSpent: Int): Int {
        require(hitsSpent in 0..earnedHits.size) { "Only ${earnedHits.size} hits earned, not $hitsSpent" }
        return maxHp - HP_PER_HIT * hitsSpent
    }

    // Results.

    /**
     * Brings in every result that's due by [clock]: each week 24 hours after it ends. Results
     * already in stay as they are, so editing old entries never changes them.
     *
     * A result counts every one of [entries] on its week's days. An entry doesn't say when it was
     * written, so [entries] have to be the logs as they stood when each result came due. The
     * caller makes sure of that by settling before it writes any entry (a log, an edit, a delete,
     * an import or a sync), so nothing written after a result is due can reach it.
     *
     * A met week pays back every missed week since the last met one. A missed final week starts
     * overtime: NRT-free weeks, one after another, until one is met. Each result keeps the total
     * it was judged on ([ProgrammeWeek.resultTotal]).
     */
    fun settle(entries: List<Entry>, clock: ProgrammeClock): Settlement {
        val settled = weeks.toMutableList()
        var index = 0
        while (index < settled.size) {
            val week = settled[index]
            if (week.result == WeekResult.PENDING) {
                val due = clock.resultDue(week)
                if (clock.now < due) break
                val total = week.totalOf(entries)
                if (week.isMetBy(total)) {
                    for (back in 0 until index) {
                        if (settled[back].result == WeekResult.MISSED) {
                            settled[back] = settled[back].copy(result = WeekResult.CAUGHT_UP)
                        }
                    }
                    settled[index] = week.copy(result = WeekResult.MET, resultAt = due, resultTotal = total)
                } else {
                    settled[index] = week.copy(result = WeekResult.MISSED, resultAt = due, resultTotal = total)
                    if (index == settled.lastIndex) settled += overtimeAfter(week)
                }
            }
            index++
        }
        val programme = copy(weeks = settled)
        // Results only come in after the ones already in, so their hits follow on.
        return Settlement(programme, programme.earnedHits.drop(earnedHits.size))
    }

    private fun overtimeAfter(week: ProgrammeWeek) = ProgrammeWeek(
        number = week.number + 1,
        startDate = week.endDate.plusDays(1),
        target = WeekTarget.NRT_FREE,
        kind = WeekKind.OVERTIME,
    )

    // After a missed week.

    /**
     * Whether missed week [number] can still be repeated rather than carried on from. The choice
     * is open while the week after it runs, and carrying on stands once that week ends. A missed
     * final week goes to overtime instead, and a week is only repeated once.
     */
    fun canRepeat(number: Int, clock: ProgrammeClock): Boolean {
        val missed = weeks.getOrNull(number - 1) ?: return false
        val next = weeks.getOrNull(number) ?: return false
        return missed.result == WeekResult.MISSED &&
            missed.kind != WeekKind.OVERTIME &&
            next.kind == WeekKind.PLANNED &&
            !clock.hasEnded(next)
    }

    /** The missed week whose choice is open, if there is one. */
    fun openChoice(clock: ProgrammeClock): ProgrammeWeek? = weeks.lastOrNull { canRepeat(it.number, clock) }

    /**
     * Repeats missed week [number]: the week after it, already running, takes its target, mix
     * and goals, and every later week moves on a week with its own. That adds a week and 10 HP,
     * and meeting the repeat pays back the missed week too.
     */
    fun repeat(number: Int, clock: ProgrammeClock): Programme {
        require(canRepeat(number, clock)) { "Week $number can't be repeated now" }
        val missed = week(number)
        val again = ProgrammeWeek(
            number = number + 1,
            startDate = missed.endDate.plusDays(1),
            target = missed.target,
            kind = WeekKind.REPEAT,
            goals = missed.goals,
        )
        val later = weeks.drop(number).map { it.copy(number = it.number + 1, startDate = it.startDate.plusWeeks(1)) }
        return copy(weeks = weeks.take(number) + again + later)
    }

    // Editing.

    /**
     * The ladder steps week [number] can be moved to on [today], or null when it can't be edited:
     * it has started, or it's NRT-free. It can't go above the week before (for week 1, the usual
     * day, or the first step when the usual day is over a label limit), and it can go down to
     * the bottom of the ladder, one unit of the weakest product.
     */
    fun editableSteps(number: Int, today: LocalDate): IntRange? {
        val week = weeks.getOrNull(number - 1) ?: return null
        if (week.isNrtFree || week.startDate <= today) return null
        val highest = when (val before = weeks.getOrNull(number - 2)) {
            null -> ladder.top
            else -> before.target.step ?: return null
        }
        return highest..ladder.size
    }

    /**
     * Moves week [number] to [step] on the ladder. Later weeks that would then be above it move
     * down to it, so allowances never go up. A week that changes mix has its caps on units a day
     * redone for the new one ([ExtraGoals.forMix]).
     */
    fun edit(number: Int, step: Int, today: LocalDate): Programme {
        val range = requireNotNull(editableSteps(number, today)) { "Week $number can't be edited on $today" }
        require(step in range) { "Week $number has to stay within steps $range: $step" }
        val target = WeekTarget.at(ladder, step)
        return copy(
            weeks = weeks.map { week ->
                val above = week.number > number && !week.isNrtFree && checkNotNull(week.target.step) < step
                when {
                    week.number != number && !above -> week
                    week.target == target -> week
                    else -> week.copy(target = target, goals = ExtraGoals.forMix(week.goals, target.mix))
                }
            },
        )
    }

    companion object {
        /** Programme weeks run from the start day picked, tomorrow unless the user says otherwise. */
        fun defaultStart(today: LocalDate): LocalDate = today.plusDays(1)

        /** A new programme from [startDate], its taper weeks spread down [ladder] (PLAN §6.2). */
        fun create(ladder: Ladder, length: ProgrammeLength, startDate: LocalDate): Programme {
            val weeks = PlanBuilder.targets(ladder, length).mapIndexed { index, target ->
                ProgrammeWeek(number = index + 1, startDate = startDate.plusWeeks(index.toLong()), target = target)
            }
            return Programme(ladder, length, weeks)
        }
    }
}
