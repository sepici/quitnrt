package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.log.Entry
import java.time.LocalDate
import java.time.LocalTime

/** Which way a timing tip moves a dose. */
enum class Shift { EARLIER, LATER }

/** The meal an after-meal dose could come before, by the clock. */
enum class Meal {
    BREAKFAST,
    LUNCH,
    DINNER;

    companion object {
        /**
         * Breakfast from 05:00, lunch from 11:00 to 15:00, and dinner from 17:00 to 22:00. Other
         * times don't name a meal, as it isn't clear which one it is.
         */
        fun at(time: LocalTime): Meal? = when {
            time < LocalTime.of(5, 0) -> null
            time < LocalTime.of(11, 0) -> BREAKFAST
            time < LocalTime.of(15, 0) -> LUNCH
            time < LocalTime.of(17, 0) -> null
            time < LocalTime.of(22, 0) -> DINNER
            else -> null
        }
    }
}

/** A time of day when doses with a tag usually come, and where a tip moves that dose to. */
data class TimingSlot(
    /** The usual time, rounded: "around 13:00". */
    val usual: LocalTime,
    /** The time to try instead: "12:30". It replaces the usual dose and never adds one. */
    val suggested: LocalTime,
    /** The doses that make up the usual time, and the days they came on. */
    val doses: Int,
    val days: Int,
    /** For an after-meal tip, the meal [suggested] comes before, or null when it isn't clear. */
    val meal: Meal?,
)

/**
 * A tip from someone's own logs that moves a dose they already have, never one to add: "Try
 * having your dose at 12:30, before lunch" or "try 09:15, 15 minutes later".
 */
data class TimingTip(
    val tag: String,
    /** The key of the strategy the tip goes with, whose steps say how. */
    val strategy: String,
    val shift: Shift,
    /** Doses with [tag] in the advice window. */
    val doses: Int,
    /** In order through the logical day. One for each usual time, never more. */
    val slots: List<TimingSlot>,
    /** Doses with [tag] in each half hour of the logical day, from the day start: 48 counts. */
    val halfHours: List<Int>,
) {
    /** The slot with the most doses, the earliest of equals. */
    val lead: TimingSlot get() = slots.maxBy { it.doses }
}

/**
 * Timing tips from the advice window's doses, with patches left out.
 *
 * - **After a meal:** when at least a quarter of doses are tagged After a meal, each usual time
 *   is rounded to the half hour, and the tip moves the dose half an hour before it, before the
 *   meal. A usual time where most doses were the day's first is left alone, since a tip never
 *   brings the first dose of the day earlier.
 * - **Just woke up:** the usual time of those doses, rounded to the quarter hour, and the tip
 *   moves the first dose 15 minutes later.
 *
 * A usual time is the busiest 90 minutes of the day for the tag, with doses on at least
 * [MIN_DAYS] different days; its time is their median, by the clock where each was taken.
 */
object TimingTips {
    /** How wide a usual time can be. */
    const val SPAN_MINUTES = 90

    /** Doses on this many different days make a usual time. */
    const val MIN_DAYS = 4

    private const val DAY_MINUTES = 24 * 60
    private const val HALF_HOUR = 30

    private class Rule(
        val tag: String,
        val strategy: String,
        val shift: Shift,
        /** Minutes to round the usual time to, and to move it by. */
        val step: Int,
        /** Whether the tag needs at least a quarter of all doses. */
        val needsQuarter: Boolean,
        val maxSlots: Int,
        val meals: Boolean,
    )

    private val RULES = listOf(
        Rule("after_meal", "dose_before_meal", Shift.EARLIER, step = 30, needsQuarter = true, maxSlots = 3, meals = true),
        // One wake-up a day, so one usual time.
        Rule("woke_up", "morning_routine", Shift.LATER, step = 15, needsQuarter = false, maxSlots = 1, meals = false),
    )

    /** The tags that can have a timing tip. */
    val tags: Set<String> = RULES.map { it.tag }.toSet()

    /**
     * The tips from [entries] over the advice window ending before [today], in the order of
     * [tags]. Entries outside the window are ignored. [dayStart] is when each logical day begins.
     */
    fun of(entries: List<Entry>, today: LocalDate, dayStart: LocalTime): List<TimingTip> {
        val window = AdviceWindow.of(today)
        val doses = entries.filter { !it.isBackground && it.logicalDate in window }
        val firsts = doses.groupBy { it.logicalDate }.values.map { day -> day.minBy { it.occurredAt }.id }.toSet()
        return RULES.mapNotNull { tip(it, doses, firsts, dayStart) }
    }

    private class Timed(val minutes: Int, val entry: Entry)

    private fun tip(rule: Rule, doses: List<Entry>, firsts: Set<String>, dayStart: LocalTime): TimingTip? {
        val tagged = doses.filter { rule.tag in it.tagKeys }
        if (tagged.isEmpty() || (rule.needsQuarter && tagged.size * 4 < doses.size)) return null
        var left = tagged.map { Timed(minutesAfter(it, dayStart), it) }.sortedBy { it.minutes }
        val slots = mutableListOf<Pair<Int, TimingSlot>>()
        while (slots.size < rule.maxSlots) {
            val busiest = busiest(left) ?: break
            if (busiest.days() < MIN_DAYS) break
            left = left - busiest.toSet()
            slot(rule, busiest, firsts, dayStart)?.let { slots += it }
        }
        if (slots.isEmpty()) return null
        val halfHours = IntArray(DAY_MINUTES / HALF_HOUR)
        tagged.forEach { halfHours[minutesAfter(it, dayStart) / HALF_HOUR]++ }
        return TimingTip(
            tag = rule.tag,
            strategy = rule.strategy,
            shift = rule.shift,
            doses = tagged.size,
            slots = slots.sortedBy { it.first }.map { it.second },
            halfHours = halfHours.toList(),
        )
    }

    /**
     * The doses in the busiest [SPAN_MINUTES] of [sorted]: the most days first, then the most
     * doses, then the earliest.
     */
    private fun busiest(sorted: List<Timed>): List<Timed>? = sorted.indices
        .map { start -> sorted.subList(start, sorted.size).takeWhile { it.minutes < sorted[start].minutes + SPAN_MINUTES } }
        .map { span -> Triple(span, span.days(), span.size) }
        .maxWithOrNull(compareBy<Triple<List<Timed>, Int, Int>> { it.second }.thenBy { it.third }.thenByDescending { it.first.first().minutes })
        ?.first

    /** The slot for [members], with its place in the logical day, or null when the tip can't apply. */
    private fun slot(rule: Rule, members: List<Timed>, firsts: Set<String>, dayStart: LocalTime): Pair<Int, TimingSlot>? {
        if (rule.shift == Shift.EARLIER && members.count { it.entry.id in firsts } * 2 > members.size) return null
        val minutes = members.map { it.minutes }.sorted()
        val median = (minutes[(minutes.size - 1) / 2] + minutes[minutes.size / 2]) / 2
        val usual = round(median, rule.step, tiesEarlier = rule.shift == Shift.EARLIER)
        val suggested = if (rule.shift == Shift.EARLIER) usual - rule.step else usual + rule.step
        // A move across the day start would put the dose on another day.
        if (suggested < 0 || suggested >= DAY_MINUTES) return null
        val suggestedTime = dayStart.plusMinutes(suggested.toLong())
        return suggested to TimingSlot(
            usual = dayStart.plusMinutes(usual.toLong()),
            suggested = suggestedTime,
            doses = members.size,
            days = members.days(),
            meal = if (rule.meals) Meal.at(suggestedTime) else null,
        )
    }

    /** [minutes] to the nearest [step]. Halfway goes earlier for a tip that moves a dose earlier. */
    private fun round(minutes: Int, step: Int, tiesEarlier: Boolean): Int {
        val below = minutes / step * step
        val over = minutes - below
        return when {
            over * 2 < step -> below
            over * 2 > step -> below + step
            tiesEarlier -> below
            else -> below + step
        }
    }

    private fun List<Timed>.days(): Int = map { it.entry.logicalDate }.distinct().size

    /** Minutes from the day start to when [entry] was taken, by its own clock, so 0 to 1439. */
    private fun minutesAfter(entry: Entry, dayStart: LocalTime): Int {
        val clock = entry.occurredAt.atZone(entry.zone).toLocalTime()
        return Math.floorMod(clock.toSecondOfDay() / 60 - dayStart.toSecondOfDay() / 60, DAY_MINUTES)
    }
}
