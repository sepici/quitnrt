package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.insights.InsightWindow
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import java.time.LocalDate
import java.time.LocalTime

/** How closely a strategy fits a tag, closest first. */
enum class MatchLevel {
    /** The strategy names the tag itself. */
    TAG,

    /** The strategy names the tag's group, but not every group. */
    GROUP,

    /** The strategy names every group, so it suits any moment. */
    ANY_TIME,
}

data class StrategyMatch(val strategy: Strategy, val level: MatchLevel)

/** One of someone's top triggers and the strategies picked for it. */
data class TriggerAdvice(val trigger: TopTrigger, val strategies: List<StrategyMatch>)

/**
 * A moment that brings up the advice for after a slip. The caller decides when one applies:
 * after a missed week's result, or when a day went over the daily guide.
 */
enum class Slip { MISSED_WEEK, OVER_GUIDE }

/** The advice on Today's card, with what its reason line needs. */
data class TodayAdvice(val strategy: Strategy, val reason: Reason, val timing: TimingTip?) {
    sealed interface Reason {
        /** The strategy is for someone's top trigger over [window]: the tag, its share and the period. */
        data class Trigger(val trigger: TopTrigger, val window: InsightWindow) : Reason

        data class AfterSlip(val slip: Slip) : Reason
    }
}

/** The tip straight after logging: a strategy for one of the dose's tags, or for a slip. */
data class LoggedTip(
    val strategy: Strategy,
    /** The dose's tag the tip answers, or null when it's for a slip. */
    val tag: String?,
    val slip: Slip?,
)

/**
 * Picks strategies from [library] for someone's triggers.
 *
 * For a tag, a strategy that names the tag comes first, then one that names its group, then the
 * ones for any time. Within each, a strategy written for fewer moments comes first, as it's more
 * about this one, and then the library's own order. Strategies about one kind of product, such as
 * gum, are kept to people who use it. After a slip is left out unless the caller passes a [Slip].
 */
class AdviceMatcher(private val library: AdviceLibrary) {

    private val order: Map<String, Int> = library.strategies.withIndex().associate { (index, strategy) -> strategy.key to index }

    /** How many built-in tags each strategy suits. */
    private val breadth: Map<String, Int> = library.strategies.associate { strategy ->
        strategy.key to BuiltInTags.all.count { strategy.suits(it.key, it.group) }
    }

    private val ranking: Comparator<StrategyMatch> = compareBy<StrategyMatch> { it.level }
        .thenBy { breadth.getValue(it.strategy.key) }
        .thenBy { order.getValue(it.strategy.key) }

    /**
     * Every strategy that suits the tag [key] in [group] for someone using [forms], best first.
     * A strategy limited to some forms needs one of them in [forms].
     */
    fun matches(key: String, group: TagGroup, forms: Set<ProductForm>, slip: Slip? = null): List<StrategyMatch> =
        library.strategies
            .filter { (slip != null || it.key != AFTER_A_SLIP) && it.appliesToAny(forms) }
            .mapNotNull { strategy -> levelOf(strategy, key, group)?.let { StrategyMatch(strategy, it) } }
            .sortedWith(ranking)

    /**
     * Up to [each] strategies for each of [triggers], in their order. A strategy that an earlier
     * trigger already has is skipped, unless the trigger would be left with none: then it keeps
     * its best match.
     */
    fun forTriggers(
        triggers: List<TopTrigger>,
        forms: Set<ProductForm>,
        slip: Slip? = null,
        each: Int = PER_TRIGGER,
    ): List<TriggerAdvice> {
        val used = mutableSetOf<String>()
        return triggers.map { trigger ->
            val all = matches(trigger.key, trigger.group, forms, slip)
            val picked = all.filterNot { it.strategy.key in used }.take(each).ifEmpty { all.take(1) }
            used += picked.map { it.strategy.key }
            TriggerAdvice(trigger, picked)
        }
    }

    /**
     * The advice for Today's card on the logical day [date]. After a slip, it's the advice for
     * that. Otherwise it's for the top trigger, turning daily through its first few strategies,
     * so the same date always gives the same one. Null when there's too little data.
     *
     * [tips] are the timing tips from the same window. The one for the trigger comes with the
     * advice on the days its strategy is the one shown.
     */
    fun today(
        triggers: TopTriggers,
        tips: List<TimingTip>,
        forms: Set<ProductForm>,
        date: LocalDate,
        slip: Slip? = null,
    ): TodayAdvice? {
        if (slip != null) {
            library.find(AFTER_A_SLIP)?.let { return TodayAdvice(it, TodayAdvice.Reason.AfterSlip(slip), timing = null) }
        }
        val trigger = triggers.top.firstOrNull() ?: return null
        val choices = forTriggers(listOf(trigger), forms).single().strategies
        if (choices.isEmpty()) return null
        val strategy = choices[Math.floorMod(date.toEpochDay(), choices.size.toLong()).toInt()].strategy
        val timing = tips.firstOrNull { it.tag == trigger.key && it.strategy == strategy.key }
        return TodayAdvice(strategy, TodayAdvice.Reason.Trigger(trigger, triggers.window), timing)
    }

    /**
     * The tip straight after logging [dose]: the best match across its tags, with an interactive
     * strategy first when it names the tag, such as breathing for Stressed. After a slip, it's the
     * advice for that. Null for a patch, or a dose with no tag that has a group.
     */
    fun afterLogging(dose: Entry, groupOf: (String) -> TagGroup?, slip: Slip? = null): LoggedTip? {
        if (dose.isBackground) return null
        if (slip != null) {
            library.find(AFTER_A_SLIP)?.let { return LoggedTip(it, tag = null, slip = slip) }
        }
        val forms = setOf(dose.form)
        val candidates = dose.tagKeys.sorted().flatMap { key ->
            val group = groupOf(key) ?: return@flatMap emptyList()
            matches(key, group, forms).map { key to it }
        }
        val interactiveFirst = compareBy<Pair<String, StrategyMatch>> { (_, match) -> match.level }
            .thenBy { (_, match) -> if (match.level == MatchLevel.TAG && match.strategy.interactive != null) 0 else 1 }
            .thenBy(ranking) { it.second }
            .thenBy { it.first }
        return candidates.minWithOrNull(interactiveFirst)?.let { (key, match) -> LoggedTip(match.strategy, key, slip = null) }
    }

    /**
     * Everything advice needs from [entries]: the top triggers over the advice window ending
     * before [today], their strategies, the timing tips and Today's advice. [forms] are the forms
     * of the products someone uses. With too little data, only a slip's advice comes through.
     */
    fun personal(
        entries: List<Entry>,
        today: LocalDate,
        dayStart: LocalTime,
        groupOf: (String) -> TagGroup?,
        forms: Set<ProductForm>,
        slip: Slip? = null,
    ): PersonalAdvice {
        val triggers = TopTriggers.of(entries, today, groupOf)
        val tips = if (triggers.enoughData) TimingTips.of(entries, today, dayStart) else emptyList()
        return PersonalAdvice(
            triggers = triggers,
            forTriggers = forTriggers(triggers.top, forms, slip),
            tips = tips,
            today = today(triggers, tips, forms, today, slip),
        )
    }

    private fun levelOf(strategy: Strategy, key: String, group: TagGroup): MatchLevel? = when {
        key in strategy.tags -> MatchLevel.TAG
        strategy.suitsAnyTime -> MatchLevel.ANY_TIME
        group in strategy.groups -> MatchLevel.GROUP
        else -> null
    }

    private fun Strategy.appliesToAny(forms: Set<ProductForm>): Boolean = this.forms.isEmpty() || forms.any(::appliesTo)

    companion object {
        /** How many strategies each top trigger gets, and how many Today's card turns through. */
        const val PER_TRIGGER = 3

        /** The strategy for after a slip, which only comes up when the caller says there was one. */
        const val AFTER_A_SLIP = "after_a_slip"
    }
}

/** What [AdviceMatcher.personal] works out, for Today, Insights and the advice list. */
data class PersonalAdvice(
    val triggers: TopTriggers,
    /** Each top trigger with its strategies, none repeated. Empty when there's too little data. */
    val forTriggers: List<TriggerAdvice>,
    val tips: List<TimingTip>,
    val today: TodayAdvice?,
)
