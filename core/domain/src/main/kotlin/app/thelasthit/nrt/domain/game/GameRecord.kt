package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything the game's rules look at, as it stands on [today]: the logs, each programme with its
 * weeks and hit ledger, and the breathing sessions. These are the game's own plain types, so the
 * rules don't depend on how the programme is built or stored; the data layer maps onto them.
 */
data class GameRecord(
    /** The logical day it is now, from [DayBoundary]. */
    val today: LocalDate,
    val entries: List<Entry> = emptyList(),
    /**
     * When each entry was written, by entry ID. An entry missing here, such as one restored from
     * a backup that doesn't say, is never taken as backdated.
     */
    val loggedAt: Map<String, Instant> = emptyMap(),
    /** Oldest first. Badges carry over from one programme to the next. */
    val programmes: List<GameProgramme> = emptyList(),
    val breathing: List<BreathingSession> = emptyList(),
    /** For timing first doses from the day start, as Insights does. */
    val dayStart: LocalTime = DayBoundary.DEFAULT_DAY_START,
)

/**
 * One product's units a day, in a usual day or a week's daily mix (PLAN 6.2): "8 pieces of 4 mg
 * gum". [perUnit] is the unit's labelled strength.
 */
data class MixUnit(val productId: String, val form: ProductForm, val perUnit: Dose, val unitsPerDay: Int) {
    init {
        require(perUnit > Dose.ZERO) { "A unit needs some nicotine: $productId" }
        require(unitsPerDay >= 1) { "$productId needs at least one unit a day: $unitsPerDay" }
    }

    val daily: Dose get() = perUnit * unitsPerDay
}

/** The mg of a usual day or a daily mix. */
fun List<MixUnit>.daily(): Dose = map { it.daily }.sum()

/**
 * The baseline a programme steps down from: the [usualDay], and the seven logical days it was
 * measured over from [measuredFrom], or null when the usual day was typed in to start at once.
 */
data class GameBaseline(val usualDay: List<MixUnit>, val measuredFrom: LocalDate? = null) {
    init {
        require(usualDay.isNotEmpty()) { "A usual day needs at least one product" }
    }

    val daily: Dose get() = usualDay.daily()

    /** The days measured, or null for a typed baseline. */
    val measuredDays: List<LocalDate>?
        get() = measuredFrom?.let { from -> List(DAYS) { from.plusDays(it.toLong()) } }

    companion object {
        /** A baseline is 7 days of logging as normal, with at least 5 that have doses (PLAN 6.1). */
        const val DAYS = 7
        const val MIN_DAYS_WITH_DOSES = 5
    }
}

/**
 * One programme week: seven logical days from [startDate], its daily [mix], and its result once
 * that's in. [number] is its place in the run, counting repeats and overtime.
 */
data class GameWeek(
    val number: Int,
    val startDate: LocalDate,
    /** The week's daily mix. Empty for an NRT-free week. */
    val mix: List<MixUnit>,
    val kind: Kind = Kind.PLANNED,
    val result: Result = Result.PENDING,
    /** When the result came in. A caught-up week keeps the time it was missed. */
    val resultAt: Instant? = null,
) {
    enum class Kind {
        /** One of the weeks the plan was built with. */
        PLANNED,

        /** A missed week repeated: a week longer, and 10 HP more on the demon. */
        REPEAT,

        /** NRT-free weeks after a missed final week. They put no HP on the demon. */
        OVERTIME,
    }

    enum class Result {
        /** Planned, with no result yet: still running, or in the 24 hours after it ends. */
        PENDING,
        MET,
        MISSED,

        /** Missed, then paid back by meeting a later week. */
        CAUGHT_UP,
    }

    init {
        require(number >= 1) { "Weeks count from 1: $number" }
        require((result == Result.PENDING) == (resultAt == null)) { "Week $number is $result at $resultAt" }
        require(kind != Kind.OVERTIME || mix.isEmpty()) { "Overtime week $number has to be NRT-free" }
    }

    val endDate: LocalDate get() = startDate.plusDays(DAYS - 1L)

    val days: List<LocalDate> get() = List(DAYS) { startDate.plusDays(it.toLong()) }

    operator fun contains(date: LocalDate): Boolean = date >= startDate && date <= endDate

    val isNrtFree: Boolean get() = mix.isEmpty()

    /** The mix's mg, for pacing a day. */
    val dailyGuide: Dose get() = mix.daily()

    /** The target: 7 times the daily guide. */
    val weeklyAllowance: Dose get() = dailyGuide * DAYS

    /** Overtime puts no HP on the demon, so it has no hit of its own to earn. */
    val earnsHit: Boolean get() = kind != Kind.OVERTIME

    /** Met, or missed and paid back since. */
    val isSquared: Boolean get() = result == Result.MET || result == Result.CAUGHT_UP

    val hasResult: Boolean get() = result != Result.PENDING

    /** Everything logged on the week's days. */
    fun totalOf(entries: List<Entry>): Dose = entries.filter { it.logicalDate in this }.map { it.total }.sum()

    companion object {
        const val DAYS = 7
    }
}

/**
 * A programme as the game sees it: the [baseline], its [weeks] back to back (none while the
 * baseline runs), and the demon's [ledger].
 *
 * An abandoned programme keeps every week it was planned with, so the demon keeps its max HP and
 * can't be defeated by the hits it had. [endedOn] says where it stopped.
 */
data class GameProgramme(
    val baseline: GameBaseline,
    val weeks: List<GameWeek> = emptyList(),
    val ledger: HitLedger = HitLedger(HitLedger.HP_PER_HIT * weeks.count { it.earnsHit }),
    /**
     * The logical day the user abandoned it, or null while it runs and once it's complete. That
     * day and the ones after it are outside the programme, so the rules don't hold later logs up
     * against its guides, and its weeks with no result by then never get one.
     */
    val endedOn: LocalDate? = null,
) {
    init {
        weeks.forEachIndexed { index, week ->
            require(week.number == index + 1) { "Week ${week.number} is in place ${index + 1}" }
            require(week.startDate == weeks.first().startDate.plusWeeks(index.toLong())) {
                "Week ${week.number} doesn't follow on from the week before"
            }
        }
        val firstPending = weeks.indexOfFirst { !it.hasResult }.takeIf { it >= 0 } ?: weeks.size
        require(weeks.drop(firstPending).none { it.hasResult }) { "Results come in week by week" }
        weeks.forEachIndexed { index, week ->
            val metLater = weeks.drop(index + 1).any { it.result == GameWeek.Result.MET }
            when (week.result) {
                GameWeek.Result.MISSED -> require(!metLater) { "Week ${week.number} would be caught up by a later week met" }
                GameWeek.Result.CAUGHT_UP -> require(metLater) { "Week ${week.number} is caught up with no later week met" }
                else -> Unit
            }
        }
        if (endedOn != null) {
            require(weeks.none { it.hasResult && it.startDate >= endedOn }) { "A week after the end on $endedOn has a result" }
        }
        require(ledger.maxHp == maxHp) { "The ledger's ${ledger.maxHp} HP isn't the weeks' $maxHp" }
        val earnable = earnedHits.map { it.kind to it.week }.toSet()
        require(ledger.rows.filter { it.kind.isEarned }.all { (it.kind to it.week) in earnable }) {
            "The ledger has hits the weeks haven't earned"
        }
    }

    val startDate: LocalDate? get() = weeks.firstOrNull()?.startDate

    /** Whether [date] comes before the programme was abandoned, if it was. */
    fun ranOn(date: LocalDate): Boolean = endedOn == null || date < endedOn

    /** One hit for each week that puts HP on the demon, so squaring every week takes it to 0. */
    val hitsOnOffer: Int get() = weeks.count { it.earnsHit }

    /** 10 HP for each week of the plan, and for each repeat (PLAN 7.2). */
    val maxHp: Int get() = HitLedger.HP_PER_HIT * hitsOnOffer

    /**
     * The hits the results have earned, in the order they came in. A met week earns its own, and
     * one for each missed week since the last met one, which it pays back (PLAN 6.4). Catch-up
     * hits arrive with the week that earns them.
     */
    val earnedHits: List<HitLedger.Row>
        get() = buildList {
            var paidUpTo = 0
            weeks.forEachIndexed { index, week ->
                if (week.result != GameWeek.Result.MET) return@forEachIndexed
                val at = checkNotNull(week.resultAt)
                if (week.earnsHit) add(HitLedger.Row(HitLedger.Kind.WEEK_MET, at, week.number))
                for (back in weeks.subList(paidUpTo, index)) {
                    if (back.result == GameWeek.Result.CAUGHT_UP && back.earnsHit) {
                        add(HitLedger.Row(HitLedger.Kind.CATCH_UP, at, back.number))
                    }
                }
                paidUpTo = index + 1
            }
        }

    /** Hits the results have earned that the ledger doesn't have yet, for the data layer to write. */
    val unrecordedHits: List<HitLedger.Row>
        get() {
            val recorded = ledger.rows.filter { it.kind.isEarned }.map { it.week }.toSet()
            return earnedHits.filter { it.week !in recorded }
        }

    /** The ledger with every earned hit in it. */
    fun withEarnedHits(): GameProgramme = copy(ledger = ledger.withEarned(earnedHits))

    /**
     * The last week of the taper as planned: the last planned week with some NRT. A repeat of it
     * comes after, but the final taper week is reached when the planned one starts.
     */
    val finalTaperWeek: GameWeek?
        get() = weeks.lastOrNull { it.kind == GameWeek.Kind.PLANNED && !it.isNrtFree }
}

/** A session with the breathing timer (PLAN 9). */
data class BreathingSession(
    val type: Type,
    val startedAt: Instant,
    val duration: Duration,
    /** Whole rounds of the pattern breathed. */
    val rounds: Int,
    /** Ran to the end, rather than being stopped. Pausing on the way is fine. */
    val completed: Boolean,
) {
    enum class Type { BOX, FOUR_SEVEN_EIGHT }

    init {
        require(!duration.isNegative) { "A session can't run backwards: $duration" }
        require(rounds >= 0) { "Rounds can't be negative: $rounds" }
    }

    /** It counts towards the badges when it ran to the end, in whole rounds. */
    val counts: Boolean get() = completed && rounds >= 1
}
