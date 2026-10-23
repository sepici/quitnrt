package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose

/** How long a programme runs. The last [NRT_FREE_WEEKS] of every length are at zero. */
enum class ProgrammeLength(val weeks: Int) {
    WEEKS_8(8),
    WEEKS_12(12),
    WEEKS_16(16),
    WEEKS_24(24);

    /** The weeks that step down before the NRT-free ones: 10 of a 12-week programme. */
    val taperWeeks: Int get() = weeks - NRT_FREE_WEEKS

    companion object {
        const val NRT_FREE_WEEKS = 2
        val DEFAULT = WEEKS_12
    }
}

/**
 * What a week aims for. The weekly allowance is the target. The daily guide paces it on Today:
 * the allowance divided by 7, rounded down to a whole microgram, so a week kept to the guide
 * every day is always met.
 */
data class WeekTarget(val weeklyAllowance: Dose) {
    val dailyGuide: Dose get() = Dose(weeklyAllowance.micrograms / DAYS)

    companion object {
        const val DAYS = 7
        val NRT_FREE = WeekTarget(Dose.ZERO)

        /** The week that keeps to [guide] a day. */
        fun daily(guide: Dose): WeekTarget = WeekTarget(guide * DAYS)
    }
}

/**
 * Builds the weekly targets of a programme from a baseline. The taper steps down in whole units
 * of the main product (pieces of 2 mg gum, say) to zero in the first NRT-free week:
 * - The baseline becomes whole units, to the nearest one and at least one.
 * - Each week drops the same number of units where it can. When the units don't divide evenly,
 *   the weeks that drop one more come first, while it's a smaller share of the dose.
 * - With fewer units than drops to make, the one-unit drops are spread evenly from the first
 *   week to the first NRT-free one, a little sooner rather than later, so the taper still uses
 *   the weeks chosen.
 *
 * So allowances never go up and the first week is always below the baseline. Every taper week
 * keeps some nicotine, unless the baseline is a single unit: then there's nothing to step down
 * through, and a smaller product would make a better main one.
 */
object PlanBuilder {

    fun targets(baselineDaily: Dose, unit: Dose, length: ProgrammeLength): List<WeekTarget> {
        require(baselineDaily > Dose.ZERO) { "A baseline needs some nicotine: $baselineDaily" }
        require(unit > Dose.ZERO) { "A unit needs some nicotine: $unit" }
        val units = units(baselineDaily, unit)
        // The steps run from the baseline to the first NRT-free week, one more than the taper weeks.
        val steps = length.taperWeeks + 1
        val taper = (1..length.taperWeeks).map { week ->
            WeekTarget.daily(Dose(unit.micrograms * unitsLeft(units, steps, week)))
        }
        return taper + List(ProgrammeLength.NRT_FREE_WEEKS) { WeekTarget.NRT_FREE }
    }

    /** [baselineDaily] in whole units of [unit], to the nearest one (halves up) and at least one. */
    fun units(baselineDaily: Dose, unit: Dose): Long =
        maxOf(1L, (baselineDaily.micrograms * 2 + unit.micrograms) / (unit.micrograms * 2))

    /** The units a day left after [step] of [steps], going from [units] down to zero. */
    private fun unitsLeft(units: Long, steps: Int, step: Int): Long {
        val dropped = when {
            units >= steps -> {
                val each = units / steps
                val extra = units % steps
                each * step + minOf(step.toLong(), extra)
            }
            units == 1L -> 1L
            // Drops at the first step and the last, and evenly between, each as soon as it can be.
            else -> ceilDiv(step * (units - 1), steps - 1L)
        }
        return units - dropped
    }

    private fun ceilDiv(numerator: Long, denominator: Long): Long = (numerator + denominator - 1) / denominator
}
