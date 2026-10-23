package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import java.time.Instant
import java.time.LocalDate

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
 * One week of a programme: seven logical days from [startDate]. [number] is its place in the
 * run, counting repeats and overtime. A result, once in, has the time it came in.
 */
data class ProgrammeWeek(
    val number: Int,
    val startDate: LocalDate,
    val target: WeekTarget,
    val kind: WeekKind = WeekKind.PLANNED,
    val isNrtFree: Boolean = false,
    val result: WeekResult = WeekResult.PENDING,
    val resultAt: Instant? = null,
) {
    init {
        require(number >= 1) { "Weeks count from 1: $number" }
        require((result == WeekResult.PENDING) == (resultAt == null)) { "Week $number is $result at $resultAt" }
        require(!isNrtFree || target == WeekTarget.NRT_FREE) { "Week $number is NRT-free with $target" }
    }

    val endDate: LocalDate get() = startDate.plusDays(WeekTarget.DAYS - 1L)

    operator fun contains(date: LocalDate): Boolean = date in startDate..endDate

    /** Repeats and overtime are both extensions of the plan as built. */
    val isExtension: Boolean get() = kind != WeekKind.PLANNED

    /** Overtime weeks put no HP on the demon, so they have no hit of their own to earn. */
    val earnsHit: Boolean get() = kind != WeekKind.OVERTIME

    /** Everything logged on the week's logical days. */
    fun totalOf(entries: List<Entry>): Dose = entries.filter { it.logicalDate in this }.map { it.total }.sum()

    /** Met at or under the allowance, which for an NRT-free week means nothing at all. */
    fun isMetBy(total: Dose): Boolean = total <= target.weeklyAllowance
}

/** What [Programme.settle] found: the programme with its new results, and the hits they earned. */
data class Settlement(val programme: Programme, val newHits: List<EarnedHit>)

/**
 * A programme from its first week: the weeks back to back from the start day, each with its
 * target and result. The demon's HP and the hits come from the weeks alone, so they can always
 * be worked out again and checked against the hit ledger.
 */
data class Programme(
    val baseline: Baseline,
    val length: ProgrammeLength,
    val mainProductId: String?,
    val weeks: List<ProgrammeWeek>,
) {
    init {
        require(weeks.isNotEmpty()) { "A programme needs weeks" }
        weeks.forEachIndexed { index, week ->
            require(week.number == index + 1) { "Week ${week.number} is in place ${index + 1}" }
            require(week.startDate == weeks.first().startDate.plusWeeks(index.toLong())) {
                "Week ${week.number} doesn't follow on from the week before"
            }
        }
    }

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
     * overtime: NRT-free weeks, one after another, until one is met.
     */
    fun settle(entries: List<Entry>, clock: ProgrammeClock): Settlement {
        val settled = weeks.toMutableList()
        var index = 0
        while (index < settled.size) {
            val week = settled[index]
            if (week.result == WeekResult.PENDING) {
                val due = clock.resultDue(week)
                if (clock.now < due) break
                if (week.isMetBy(week.totalOf(entries))) {
                    for (back in 0 until index) {
                        if (settled[back].result == WeekResult.MISSED) {
                            settled[back] = settled[back].copy(result = WeekResult.CAUGHT_UP)
                        }
                    }
                    settled[index] = week.copy(result = WeekResult.MET, resultAt = due)
                } else {
                    settled[index] = week.copy(result = WeekResult.MISSED, resultAt = due)
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
        isNrtFree = true,
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
     * Repeats missed week [number]: the week after it, already running, takes its target, and
     * every later week moves on a week. That adds a week and 10 HP, and meeting the repeat pays
     * back the missed week too.
     */
    fun repeat(number: Int, clock: ProgrammeClock): Programme {
        require(canRepeat(number, clock)) { "Week $number can't be repeated now" }
        val missed = week(number)
        val again = ProgrammeWeek(
            number = number + 1,
            startDate = missed.endDate.plusDays(1),
            target = missed.target,
            kind = WeekKind.REPEAT,
            isNrtFree = missed.isNrtFree,
        )
        val later = weeks.drop(number).map { it.copy(number = it.number + 1, startDate = it.startDate.plusWeeks(1)) }
        return copy(weeks = weeks.take(number) + again + later)
    }

    // Editing.

    /**
     * The weekly allowances week [number] can be set to on [today], or null when it can't be
     * edited: it has started, or it's NRT-free. Allowances never go up, so it stays between the
     * week after and the week before, or the baseline for the first week.
     */
    fun editableRange(number: Int, today: LocalDate): ClosedRange<Dose>? {
        val week = weeks.getOrNull(number - 1) ?: return null
        if (week.isNrtFree || week.kind == WeekKind.OVERTIME || week.startDate <= today) return null
        val most = weeks.getOrNull(number - 2)?.target?.weeklyAllowance ?: baseline.weekly
        val least = weeks.getOrNull(number)?.target?.weeklyAllowance ?: Dose.ZERO
        return least..most
    }

    fun edit(number: Int, weeklyAllowance: Dose, today: LocalDate): Programme {
        val range = requireNotNull(editableRange(number, today)) { "Week $number can't be edited on $today" }
        require(weeklyAllowance in range) { "Week $number has to stay within $range: $weeklyAllowance" }
        return copy(weeks = weeks.map { if (it.number == number) it.copy(target = WeekTarget(weeklyAllowance)) else it })
    }

    companion object {
        /** Programme weeks run from the start day picked, tomorrow unless the user says otherwise. */
        fun defaultStart(today: LocalDate): LocalDate = today.plusDays(1)

        /**
         * A new programme, built from [baseline] in whole units of the main product, [mainUnit]
         * each. It needs a baseline [PlanBuilder.canStepDown] through with that unit.
         */
        fun create(
            baseline: Baseline,
            length: ProgrammeLength,
            mainUnit: Dose,
            startDate: LocalDate,
            mainProductId: String? = null,
        ): Programme {
            val weeks = PlanBuilder.targets(baseline.daily, mainUnit, length).mapIndexed { index, target ->
                ProgrammeWeek(
                    number = index + 1,
                    startDate = startDate.plusWeeks(index.toLong()),
                    target = target,
                    isNrtFree = index >= length.taperWeeks,
                )
            }
            return Programme(baseline, length, mainProductId, weeks)
        }
    }
}
