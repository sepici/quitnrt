package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.insights.InsightWindow
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
    /** In order through the logical day. At most one for each meal, and never two for the same dose. */
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
 * A usual time is the busiest [SPAN_MINUTES] of the day for a tag, with doses on at least
 * [MIN_DAYS] different days and on at least half the days with any dose ([MIN_DAY_SHARE]), so it's
 * a daily habit. Its time is their median, by the clock where each was taken.
 *
 * - **After a meal:** when at least a quarter of doses are tagged After a meal, each usual time
 *   is rounded to the half hour, and the tip moves the dose half an hour before it, before the
 *   meal. Each meal gets one slot at most, and the doses either side of a slot belong to it, so
 *   one dose a day at a time that varies is never split in two. A slot is left out when, on any
 *   of its days, the new time would come before that day's first dose, since a tip never brings
 *   the first dose of the day earlier.
 * - **Just woke up:** from the doses tagged Just woke up that were the day's first, over the
 *   window's last [RECENT_DAYS] days, so the tip moves on each week once it's followed. Their
 *   usual time is rounded to the quarter hour, and the tip moves the first dose 15 minutes later.
 *   It's left out when that's still earlier than the window's median first dose.
 */
object TimingTips {
    /** How wide a usual time can be. */
    const val SPAN_MINUTES = 90

    /** Doses on this many different days make a usual time. */
    const val MIN_DAYS = 4

    /** A usual time also needs doses on this share of the days with any dose. */
    const val MIN_DAY_SHARE = 0.5

    /** The wake-up tip reads the usual time off this many of the window's latest days. */
    const val RECENT_DAYS = 7

    /** The most after-meal slots: breakfast, lunch and dinner. */
    const val MAX_SLOTS = 3

    const val AFTER_MEAL = "after_meal"
    const val WOKE_UP = "woke_up"

    private const val DAY_MINUTES = 24 * 60
    private const val HALF_HOUR = 30
    private const val QUARTER_HOUR = 15

    /** The tags that can have a timing tip. */
    val tags: Set<String> = setOf(AFTER_MEAL, WOKE_UP)

    /**
     * The tips from [entries] over the advice window ending before [today], in the order of
     * [tags]. Entries outside the window are ignored. [dayStart] is when each logical day begins.
     */
    fun of(entries: List<Entry>, today: LocalDate, dayStart: LocalTime): List<TimingTip> {
        val window = AdviceWindow.of(today)
        val doses = entries.filter { !it.isBackground && it.logicalDate in window }.map { Timed(minutesAfter(it, dayStart), it) }
        val firsts = doses.groupBy { it.entry.logicalDate }.mapValues { (_, day) -> day.minBy { it.entry.occurredAt } }
        return listOfNotNull(afterMeal(doses, firsts, dayStart), wokeUp(doses, firsts, window, dayStart))
    }

    /** A dose and when it was taken, in minutes from the day start. */
    private class Timed(val minutes: Int, val entry: Entry)

    private fun afterMeal(doses: List<Timed>, firsts: Map<LocalDate, Timed>, dayStart: LocalTime): TimingTip? {
        val tagged = doses.filter { AFTER_MEAL in it.entry.tagKeys }
        if (tagged.isEmpty() || tagged.size * 4 < doses.size) return null
        val doseDays = doses.days()
        var left = tagged.sortedBy { it.minutes }
        val slots = mutableListOf<Pair<Int, TimingSlot>>()
        while (slots.size < MAX_SLOTS) {
            val members = busiest(left)?.takeIf { isUsual(it, doseDays) } ?: break
            // The doses either side are the same dose, on days it came a little earlier or later.
            val from = members.first().minutes - SPAN_MINUTES
            val to = members.last().minutes + SPAN_MINUTES
            left = left.filter { it.minutes < from || it.minutes > to }

            val usual = round(median(members), HALF_HOUR, tiesEarlier = true)
            val suggested = usual - HALF_HOUR
            // A move across the day start would put the dose on another day.
            if (suggested < 0) continue
            if (members.any { firsts.getValue(it.entry.logicalDate).minutes > suggested }) continue
            val suggestedTime = dayStart.plusMinutes(suggested.toLong())
            val meal = Meal.at(suggestedTime)
            if (meal != null && slots.any { it.second.meal == meal }) continue
            slots += suggested to TimingSlot(dayStart.plusMinutes(usual.toLong()), suggestedTime, members.size, members.days(), meal)
        }
        return tipOf(AFTER_MEAL, "dose_before_meal", Shift.EARLIER, tagged, slots)
    }

    private fun wokeUp(doses: List<Timed>, firsts: Map<LocalDate, Timed>, window: InsightWindow, dayStart: LocalTime): TimingTip? {
        val tagged = doses.filter { WOKE_UP in it.entry.tagKeys }
        if (tagged.isEmpty()) return null
        val since = window.end.minusDays(RECENT_DAYS - 1L)
        val recentDays = doses.filter { it.entry.logicalDate >= since }.days()
        val mornings = firsts.values.filter { it.entry.logicalDate >= since && WOKE_UP in it.entry.tagKeys }.sortedBy { it.minutes }
        val members = busiest(mornings)?.takeIf { isUsual(it, recentDays) } ?: return null

        val usual = round(median(members), QUARTER_HOUR, tiesEarlier = false)
        val suggested = usual + QUARTER_HOUR
        if (suggested >= DAY_MINUTES || suggested < median(firsts.values.toList())) return null
        val slot = TimingSlot(
            usual = dayStart.plusMinutes(usual.toLong()),
            suggested = dayStart.plusMinutes(suggested.toLong()),
            doses = members.size,
            days = members.days(),
            meal = null,
        )
        return tipOf(WOKE_UP, "morning_routine", Shift.LATER, tagged, listOf(suggested to slot))
    }

    /** The tip for [tag] with [slots], each with its place in the logical day, or null with none. */
    private fun tipOf(tag: String, strategy: String, shift: Shift, tagged: List<Timed>, slots: List<Pair<Int, TimingSlot>>): TimingTip? {
        if (slots.isEmpty()) return null
        val halfHours = IntArray(DAY_MINUTES / HALF_HOUR)
        tagged.forEach { halfHours[it.minutes / HALF_HOUR]++ }
        return TimingTip(
            tag = tag,
            strategy = strategy,
            shift = shift,
            doses = tagged.size,
            slots = slots.sortedBy { it.first }.map { it.second },
            halfHours = halfHours.toList(),
        )
    }

    /** Whether [members] come often enough to be a usual time, against the [doseDays] with any dose. */
    private fun isUsual(members: List<Timed>, doseDays: Int): Boolean {
        val days = members.days()
        return days >= MIN_DAYS && days >= doseDays * MIN_DAY_SHARE
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

    /** The median minute of [doses], halfway between the middle two when there's an even number. */
    private fun median(doses: List<Timed>): Int {
        val minutes = doses.map { it.minutes }.sorted()
        return (minutes[(minutes.size - 1) / 2] + minutes[minutes.size / 2]) / 2
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
