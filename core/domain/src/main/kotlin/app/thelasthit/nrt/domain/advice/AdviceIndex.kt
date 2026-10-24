package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import java.time.LocalTime

/** A part of the full list: the strategies for one kind of trigger, or for any moment when [group] is null. */
data class StrategySection(val group: TagGroup?, val strategies: List<Strategy>)

/**
 * The full advice list (PLAN §9 and §15): every strategy once, under the kind of trigger it
 * suits best, after someone's own top triggers and the 4 Ds.
 */
object AdviceIndex {
    /** How many of its tags a strategy's row names. */
    const val ROW_TAGS = 3

    /** A group a strategy names counts as this many of its tags when placing it. */
    const val NAMED_GROUP_WEIGHT = 2

    /**
     * The section [strategy] sits in: null, for any moment, when it names every group. Otherwise
     * the group most of its tags are in, where a group it names counts as [NAMED_GROUP_WEIGHT]
     * tags, and the earlier group of equals. So "Make each dose a decision", which names Habit
     * and tags two routines, sits under Habit.
     */
    fun sectionOf(strategy: Strategy): TagGroup? {
        if (strategy.suitsAnyTime) return null
        val score = TagGroup.entries.associateWith { group ->
            strategy.tags.count { BuiltInTags.find(it)?.group == group } + if (group in strategy.groups) NAMED_GROUP_WEIGHT else 0
        }
        return TagGroup.entries.maxWith(compareBy<TagGroup> { score.getValue(it) }.thenByDescending { it.ordinal })
    }

    /**
     * Every strategy for someone using [forms], by section: any time first, then the groups in
     * their order, each in the library's order. A strategy about some forms, such as gum, only
     * shows to people who use one of them. With no products yet, every strategy shows.
     */
    fun sections(library: AdviceLibrary, forms: Set<ProductForm>): List<StrategySection> {
        val shown = library.strategies.filter { strategy -> forms.isEmpty() || strategy.forms.isEmpty() || forms.any(strategy::appliesTo) }
        val bySection = shown.groupBy(::sectionOf)
        return (listOf<TagGroup?>(null) + TagGroup.entries).mapNotNull { group ->
            bySection[group]?.let { StrategySection(group, it) }
        }
    }

    /**
     * The tags a strategy's row names, up to [ROW_TAGS]: the ones in its section's group first,
     * so "Ride out the urge" under Body reads "Strong craving, Withdrawal, Stressed", then the
     * rest, each in the library's order.
     */
    fun rowTags(strategy: Strategy): List<String> {
        val section = sectionOf(strategy) ?: return strategy.tags.take(ROW_TAGS)
        val (inSection, others) = strategy.tags.partition { BuiltInTags.find(it)?.group == section }
        return (inSection + others).take(ROW_TAGS)
    }
}

/**
 * What a strategy's page shows from someone's own logs: the top trigger it's listed under, a
 * timing tip that goes with it, and other strategies for the same tag.
 */
data class StrategyContext(
    /**
     * The tag the page is about: the top trigger the strategy is listed under, else the tag of
     * its timing tip, else its own first tag. Null for a strategy that names no tag.
     */
    val tag: String?,
    /** The top trigger the strategy is listed under, or null when it isn't one of them. */
    val trigger: TopTrigger?,
    val tip: TimingTip?,
    /** Doses in the advice window, patches left out: "19 of your 70 doses". */
    val doses: Int,
    /** Other strategies for [tag], best first. */
    val also: List<Strategy>,
) {
    /** Whether the page has anything from someone's own logs to show. */
    val fromLogs: Boolean get() = trigger != null || tip != null

    /** Doses with [tag] in the advice window. */
    val tagged: Int get() = tip?.doses ?: trigger?.doses ?: 0

    companion object {
        /** How many other strategies the page lists. */
        const val ALSO = 2

        /**
         * The context for [strategy] from [personal]. The other strategies are the rest of its
         * trigger's, then the ones that name its tag, without repeats. [groupOf] gives a tag's
         * group, built-in or added.
         */
        fun of(
            strategy: Strategy,
            personal: PersonalAdvice,
            matcher: AdviceMatcher,
            forms: Set<ProductForm>,
            groupOf: (String) -> TagGroup?,
        ): StrategyContext {
            val listed = personal.forTriggers.firstOrNull { advice -> advice.strategies.any { it.strategy.key == strategy.key } }
            val tip = personal.tips.firstOrNull { it.strategy == strategy.key && (listed == null || it.tag == listed.trigger.key) }
            val tag = listed?.trigger?.key ?: tip?.tag ?: strategy.tags.firstOrNull()
            val siblings = listed?.strategies?.map { it.strategy }.orEmpty()
            val forTag = tag?.let { key ->
                val group = groupOf(key) ?: return@let emptyList()
                matcher.matches(key, group, forms).filter { it.level == MatchLevel.TAG }.map { it.strategy }
            }.orEmpty()
            val also = (siblings + forTag).filter { it.key != strategy.key }.distinctBy { it.key }.take(ALSO)
            return StrategyContext(tag, listed?.trigger, tip, personal.triggers.doses, also)
        }
    }
}

/** Doses with the tip's tag in each hour of the logical day, from the day start: 24 counts. */
val TimingTip.hourly: List<Int> get() = halfHours.chunked(2) { it.sum() }

/** Minutes from [dayStart] to [time] through the logical day, so 0 to 1439. */
fun minutesIntoDay(time: LocalTime, dayStart: LocalTime): Int =
    Math.floorMod(time.toSecondOfDay() / 60 - dayStart.toSecondOfDay() / 60, 24 * 60)
