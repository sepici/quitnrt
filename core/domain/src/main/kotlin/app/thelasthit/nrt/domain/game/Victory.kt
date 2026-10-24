package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the victory screen sums up once the demon falls (PLAN 7.3): the weeks it took, the usual
 * day that came down to none, the hits it took, and the triggers that came with the doses at
 * the start. A programme only ends at 0 HP once its last weeks are met with no NRT, so every one
 * of those triggers was got on top of.
 */
data class Victory(
    /** The plan's weeks, repeats included. */
    val weeks: Int,
    /** NRT-free weeks run on after a missed final week. */
    val overtime: Int,
    /**
     * The usual day the plan stepped down from, product by product, as the Plan tab's summary
     * puts it: "8 pieces of 4 mg gum and 3 sprays" (PLAN 6.1).
     */
    val usualDay: List<MixUnit>,
    /** Every hit spent on the demon. */
    val hits: Int,
    /** The logical day the final hit landed. */
    val lastHitOn: LocalDate,
    /** The start's most common tags, most doses first: up to [TRIGGERS] keys. */
    val triggers: List<String>,
) {
    /** The usual day's mg. */
    val usualDaily: Dose get() = usualDay.daily()

    companion object {
        /** How many triggers the summary names. */
        const val TRIGGERS = 2

        /** A tag needs this many doses at the start to count as a trigger, as advice has it. */
        const val MIN_TRIGGER_DOSES = 3

        /**
         * The summary for [programme] once its demon is defeated, or null while it has HP. The
         * triggers come from [entries] on the baseline's seven days, or, for a typed baseline, on
         * the plan's first week. Patches are left out, as from every trigger.
         */
        fun of(programme: GameProgramme, entries: List<Entry>, boundary: DayBoundary, zone: ZoneId): Victory? {
            val ledger = programme.ledger
            val defeatedAt = ledger.defeatedAt ?: return null
            val startDays = (programme.baseline.measuredDays ?: programme.weeks.firstOrNull()?.days.orEmpty()).toSet()
            val atStart = entries.filter { !it.isBackground && it.logicalDate in startDays }
            val triggers = atStart.flatMap { it.tagKeys }.groupingBy { it }.eachCount()
                .filterValues { it >= MIN_TRIGGER_DOSES }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(TRIGGERS)
                .map { it.key }
            return Victory(
                weeks = programme.weeks.count { it.kind != GameWeek.Kind.OVERTIME },
                overtime = programme.weeks.count { it.kind == GameWeek.Kind.OVERTIME },
                usualDay = programme.baseline.usualDay,
                hits = ledger.spent,
                lastHitOn = boundary.logicalDate(defeatedAt, zone),
                triggers = triggers,
            )
        }
    }
}
