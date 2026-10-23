package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal val ZONE: ZoneId = ZoneId.of("Europe/London")
internal val BOUNDARY = DayBoundary()

/** A Monday. The baselines in these tests are measured from here, and plans start a week later. */
internal val BASELINE_START: LocalDate = LocalDate.of(2026, 11, 2)
internal val PLAN_START: LocalDate = BASELINE_START.plusDays(7)

internal fun gum4(units: Int) = MixUnit("gum-4mg", ProductForm.GUM, Dose.mg(4), units)
internal fun gum2(units: Int) = MixUnit("gum-2mg", ProductForm.GUM, Dose.mg(2), units)
internal fun patch(mg: Int) = MixUnit("patch-${mg}mg", ProductForm.PATCH_24H, Dose.mg(mg), 1)

/** PLAN 6.2's example 2: a usual day of 8 pieces of 4 mg gum, 32 mg. */
internal val EXAMPLE_2_USUAL_DAY = listOf(gum4(8))

/** Example 2's 12 weeks: two steps a week at first, the switch to 2 mg, then 2 NRT-free weeks. */
internal val EXAMPLE_2_PLAN: List<List<MixUnit>> =
    listOf(listOf(gum4(6)), listOf(gum4(4))) + (8 downTo 1).map { listOf(gum2(it)) } + listOf(emptyList(), emptyList())

/** Example 2's ladder: 4 mg pieces one at a time down to 4, the switch to 8 of 2 mg, then down to 1. */
private val EXAMPLE_2_LADDER: List<MixUnit> = (7 downTo 4).map { gum4(it) } + (8 downTo 1).map { gum2(it) }

/**
 * Example 2 over any of PLAN 6.2's lengths, by its rule for spreading the steps over the taper
 * weeks, then 2 NRT-free weeks. A fixture for the game's tests, written out here so they don't
 * depend on how the programme builds its plans.
 */
internal fun example2Plan(weeks: Int): List<List<MixUnit>> {
    val taper = weeks - 2
    val k = EXAMPLE_2_LADDER.size
    val steps = (1..taper).map { w -> if (k >= taper) w * (k / taper) + minOf(w, k % taper) else (k * w + taper - 1) / taper }
    return steps.map { listOf(EXAMPLE_2_LADDER[it - 1]) } + listOf(emptyList(), emptyList())
}

/** The start of logical day [date]. */
internal fun startOf(date: LocalDate): Instant = BOUNDARY.window(date, ZONE).start

/** A week's result comes in 24 hours after it ends: at the start of the second day after it. */
internal fun resultDue(week: GameWeek): Instant = startOf(week.endDate.plusDays(2))

/** A dose of [units] pieces at [at] ("2026-11-09T08:00") on the clock in London. */
internal fun dose(at: String, unit: MixUnit = gum4(1), units: Int = 1, tags: Set<String> = emptySet()): Entry =
    entry(at = at, productId = unit.productId, form = unit.form, perUnit = unit.perUnit, quantity = units, tags = tags, zone = ZONE)

internal fun programmeOf(
    weeks: List<GameWeek>,
    usualDay: List<MixUnit> = EXAMPLE_2_USUAL_DAY,
    measuredFrom: LocalDate? = BASELINE_START,
    ledger: HitLedger? = null,
): GameProgramme {
    val baseline = GameBaseline(usualDay, measuredFrom)
    return if (ledger == null) GameProgramme(baseline, weeks) else GameProgramme(baseline, weeks, ledger)
}

/**
 * A programme's weeks as they stand, driven one result at a time as the programme would: a week
 * met pays back every missed week since the last one met, a missed final week brings an overtime
 * week, and a missed week can be repeated. This is a test driver; the programme's own rules live
 * in `domain/programme`.
 */
internal class WeekRun(plan: List<List<MixUnit>>, start: LocalDate = PLAN_START) {
    val weeks: MutableList<GameWeek> = plan.mapIndexed { index, mix ->
        GameWeek(index + 1, start.plusWeeks(index.toLong()), mix)
    }.toMutableList()

    val next: GameWeek? get() = weeks.firstOrNull { !it.hasResult }

    /** The next week's result, when it's due. Returns when it came in. */
    fun result(met: Boolean): Instant {
        val index = weeks.indexOfFirst { !it.hasResult }
        val week = weeks[index]
        val at = resultDue(week)
        if (met) {
            for (back in 0 until index) {
                if (weeks[back].result == GameWeek.Result.MISSED) {
                    weeks[back] = weeks[back].copy(result = GameWeek.Result.CAUGHT_UP)
                }
            }
            weeks[index] = week.copy(result = GameWeek.Result.MET, resultAt = at)
        } else {
            weeks[index] = week.copy(result = GameWeek.Result.MISSED, resultAt = at)
            if (index == weeks.lastIndex) {
                weeks += GameWeek(week.number + 1, week.startDate.plusWeeks(1), emptyList(), GameWeek.Kind.OVERTIME)
            }
        }
        return at
    }

    /** Repeats the missed week just in: the week after takes its mix, and later weeks move on a week. */
    fun repeatLastMissed() {
        val index = weeks.indexOfLast { it.result == GameWeek.Result.MISSED }
        val missed = weeks[index]
        val again = GameWeek(missed.number + 1, missed.startDate.plusWeeks(1), missed.mix, GameWeek.Kind.REPEAT)
        val later = weeks.drop(index + 1).map { it.copy(number = it.number + 1, startDate = it.startDate.plusWeeks(1)) }
        weeks.subList(index + 1, weeks.size).clear()
        weeks += again
        weeks += later
    }
}
