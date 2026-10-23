package app.thelasthit.nrt.domain.programme

import java.time.Instant

/** What the user picks after a missed week (PLAN §6.4). */
enum class MissedWeekChoice {
    /** The default: the next week keeps its planned, lower target. */
    CARRY_ON,

    /** The next week takes the missed week's target: a week longer and 10 HP more. */
    REPEAT,
}

/** The choice made after missed week [week], and when it was made. */
data class WeekChoice(val week: Int, val choice: MissedWeekChoice, val at: Instant)

/**
 * The choice after a missed week, as the Plan tab asks it. A sheet comes up when the result comes
 * in, with Carry on picked, and closing it counts as Carry on. Carry on can still be changed to
 * Repeat until the end of the day it was picked, for as long as [Programme.canRepeat] allows. A
 * repeat can't be taken back.
 *
 * [choices] are the choices recorded so far, by the missed week's number.
 */
object MissedWeekChoices {

    /** The missed week still waiting for an answer: one whose choice is open, with none recorded. */
    fun awaiting(programme: Programme, choices: Map<Int, WeekChoice>, clock: ProgrammeClock): ProgrammeWeek? =
        programme.openChoice(clock)?.takeIf { it.number !in choices }

    /** Whether [choice] can be recorded for missed week [number] now. */
    fun canChoose(
        programme: Programme,
        number: Int,
        choice: MissedWeekChoice,
        choices: Map<Int, WeekChoice>,
        clock: ProgrammeClock,
    ): Boolean {
        if (!programme.canRepeat(number, clock)) return false
        val made = choices[number] ?: return true
        return when (choice) {
            MissedWeekChoice.CARRY_ON -> made.choice == MissedWeekChoice.CARRY_ON
            MissedWeekChoice.REPEAT ->
                made.choice == MissedWeekChoice.CARRY_ON && clock.boundary.logicalDate(made.at, clock.zone) == clock.today
        }
    }
}
