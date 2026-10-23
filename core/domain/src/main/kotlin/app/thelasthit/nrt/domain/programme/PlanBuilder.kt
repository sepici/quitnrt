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
 * What a week aims for: its daily [mix], at [step] on the plan's ladder, or none for an NRT-free
 * week. The daily guide is the mix's mg exactly, for pacing on Today. The weekly allowance is
 * 7 times the guide, and it's the target: a week is judged on mg alone.
 */
data class WeekTarget(val step: Int?, val mix: Mix) {
    init {
        require((step == null) == mix.isEmpty) { "Only an NRT-free week has no step: $step, $mix" }
        require(step == null || step >= 0) { "Steps count from 0, the usual day: $step" }
    }

    val isNrtFree: Boolean get() = step == null

    val dailyGuide: Dose get() = mix.total

    val weeklyAllowance: Dose get() = dailyGuide * DAYS

    companion object {
        const val DAYS = 7
        val NRT_FREE = WeekTarget(null, Mix.NONE)

        /** The week at [step] on [ladder]. */
        fun at(ladder: Ladder, step: Int): WeekTarget = WeekTarget(step, ladder.mixAt(step))
    }
}

/**
 * Spreads a ladder's steps over a programme's taper weeks (PLAN §6.2). With k steps and T taper
 * weeks:
 * - With k ≥ T, every week takes k div T steps, and the first k mod T weeks take one more, while
 *   the extra step is a smaller share of the dose.
 * - With k < T, week w is at step ⌈k × w ÷ T⌉, so a step lasts more than a week.
 * - With no steps, every taper week holds at the usual day.
 *
 * So the last taper week is always at the bottom of the ladder, the first is below the usual day
 * whenever the ladder has steps, and allowances never go up. The NRT-free weeks follow.
 */
object PlanBuilder {

    /** The step taper week [week] of [taperWeeks] is at, on a ladder of [steps] steps. */
    fun stepFor(week: Int, steps: Int, taperWeeks: Int): Int {
        require(taperWeeks >= 1) { "A taper needs weeks: $taperWeeks" }
        require(week in 1..taperWeeks) { "Week $week isn't one of the $taperWeeks taper weeks" }
        require(steps >= 0) { "A ladder can't have $steps steps" }
        return if (steps >= taperWeeks) {
            week * (steps / taperWeeks) + minOf(week, steps % taperWeeks)
        } else {
            ceilDiv(steps * week, taperWeeks)
        }
    }

    /** Every week's target: the taper weeks down [ladder], then the NRT-free weeks. */
    fun targets(ladder: Ladder, length: ProgrammeLength): List<WeekTarget> {
        val taper = (1..length.taperWeeks).map { week ->
            WeekTarget.at(ladder, stepFor(week, ladder.size, length.taperWeeks))
        }
        return taper + List(ProgrammeLength.NRT_FREE_WEEKS) { WeekTarget.NRT_FREE }
    }

    private fun ceilDiv(numerator: Int, denominator: Int): Int = (numerator + denominator - 1) / denominator
}
