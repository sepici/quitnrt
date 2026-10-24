package app.thelasthit.nrt.domain.agent

import app.thelasthit.nrt.domain.advice.AdviceLibrary
import app.thelasthit.nrt.domain.advice.AdviceMatcher
import app.thelasthit.nrt.domain.advice.Quitline
import app.thelasthit.nrt.domain.advice.Strategy
import app.thelasthit.nrt.domain.advice.TopTrigger
import app.thelasthit.nrt.domain.advice.TopTriggers
import app.thelasthit.nrt.domain.insights.InsightWindow
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.matcher.TagMatch
import app.thelasthit.nrt.domain.matcher.TagMatcher
import app.thelasthit.nrt.domain.product.ProductForm
import java.time.LocalDate

/**
 * The strategy `getNrtAdvice` gives an agent, and why it's the one. [quitlines] are the free lines
 * for the user's country, since the advice points to them for help beyond it (PLAN §9, Limits).
 */
data class AgentAdviceAnswer(val strategy: Strategy, val reason: Reason, val quitlines: List<Quitline> = emptyList()) {
    sealed interface Reason {
        /** The agent asked about the tag [key]. */
        data class AskedTag(val key: String) : Reason

        /** No tag was asked about, so it's for someone's top trigger over [window]. */
        data class ForTopTrigger(val trigger: TopTrigger, val window: InsightWindow) : Reason

        /** No tag was asked about, and too little is logged to know someone's triggers. */
        data object AnyTime : Reason
    }
}

sealed interface AgentAdviceResult {
    data class Answer(val answer: AgentAdviceAnswer) : AgentAdviceResult

    /** The tag asked about names no tag, or more than one. */
    data class Refused(val reason: AgentRefusal) : AgentAdviceResult
}

/**
 * Picks the advice for `getNrtAdvice(tag?)` (PLAN §12): the best strategy for the tag asked about,
 * or else for someone's current top trigger, by the same rules as Today's card and Insights
 * ([AdviceMatcher], over the last 14 full days). With too little logged to know the top trigger,
 * it's the 4 Ds, the advice for any time.
 *
 * The advice never suggests taking more: it only comes from the bundled library, whose text is
 * held to that, and the advice for after a slip is left out, as it is everywhere unless the app
 * passes a slip.
 */
class AgentAdvice(private val library: AdviceLibrary, private val matcher: AdviceMatcher = AdviceMatcher(library)) {

    /**
     * [tag] is a tag key or name, or null for the top trigger. [tags] finds it, and [groupOf]
     * gives a tag's group. [entries] cover at least the advice window before [today], and
     * [forms] are the forms of the products someone uses, so advice about gum goes to people who
     * use gum. [country] is the user's, an ISO code such as "GB", for the quitlines.
     */
    fun pick(
        tag: String?,
        tags: TagMatcher,
        groupOf: (String) -> TagGroup?,
        entries: List<Entry>,
        today: LocalDate,
        forms: Set<ProductForm>,
        country: String? = null,
    ): AgentAdviceResult = when (val result = choose(tag, tags, groupOf, entries, today, forms)) {
        is AgentAdviceResult.Answer -> AgentAdviceResult.Answer(result.answer.copy(quitlines = quitlinesFor(country)))
        is AgentAdviceResult.Refused -> result
    }

    /** The quitlines for [country], or none when it isn't known yet. */
    fun quitlinesFor(country: String?): List<Quitline> = country?.let(library::quitlinesFor).orEmpty()

    private fun choose(
        tag: String?,
        tags: TagMatcher,
        groupOf: (String) -> TagGroup?,
        entries: List<Entry>,
        today: LocalDate,
        forms: Set<ProductForm>,
    ): AgentAdviceResult {
        val asked = tag?.trim()?.takeIf { it.isNotEmpty() }
        if (asked != null) {
            val key = when (val found = tags.match(asked)) {
                is TagMatch.Found -> found.key
                is TagMatch.Ambiguous -> return AgentAdviceResult.Refused(AgentRefusal.AmbiguousTags(mapOf(asked to found.keys)))
                TagMatch.Unknown -> return AgentAdviceResult.Refused(AgentRefusal.UnknownTags(listOf(asked)))
            }
            val group = groupOf(key) ?: return AgentAdviceResult.Refused(AgentRefusal.UnknownTags(listOf(asked)))
            val best = matcher.matches(key, group, forms).firstOrNull()?.strategy ?: return anyTime()
            return answer(best, AgentAdviceAnswer.Reason.AskedTag(key))
        }

        val triggers = TopTriggers.of(entries, today, groupOf)
        val top: TopTrigger = triggers.top.firstOrNull() ?: return anyTime()
        val best = matcher.forTriggers(listOf(top), forms).single().strategies.firstOrNull()?.strategy ?: return anyTime()
        return answer(best, AgentAdviceAnswer.Reason.ForTopTrigger(top, triggers.window))
    }

    private fun anyTime(): AgentAdviceResult {
        val strategy = library.find(ANY_TIME) ?: library.strategies.first { it.suitsAnyTime && it.key != AdviceMatcher.AFTER_A_SLIP }
        return answer(strategy, AgentAdviceAnswer.Reason.AnyTime)
    }

    private fun answer(strategy: Strategy, reason: AgentAdviceAnswer.Reason) =
        AgentAdviceResult.Answer(AgentAdviceAnswer(strategy, reason))

    companion object {
        /** The 4 Ds: delay, deep breaths, drink water, do something else. PLAN §9's advice for any time. */
        const val ANY_TIME = "four_ds"
    }
}
