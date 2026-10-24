package app.thelasthit.nrt.domain.agent

import app.thelasthit.nrt.domain.log.LogTime
import app.thelasthit.nrt.domain.matcher.LogLinkParser
import app.thelasthit.nrt.domain.matcher.ProductMatch
import app.thelasthit.nrt.domain.matcher.ProductMatcher
import app.thelasthit.nrt.domain.matcher.TagMatch
import app.thelasthit.nrt.domain.matcher.TagMatcher
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.UserProduct
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What an agent such as Gemini asked to log, as `logNrtUsage` received it (PLAN §12): "Log a 2 mg
 * nicotine gum", or "Log an inhalator session at 3 pm, stressed".
 */
data class AgentLogAsk(
    /** A product in plain words, such as "two 4 mg gums" or "QuickMist", or a product's ID. */
    val productDescription: String,
    /** How many of what the description names. Null means the description's own number, or one. */
    val quantity: Int? = null,
    /** When, on the phone's clock. Null means now. */
    val time: LocalDateTime? = null,
    /** Tag keys or names, such as "stressed" or "After a meal". */
    val tags: List<String> = emptyList(),
    /** How strong the craving was, 1 to 5. */
    val cravingStrength: Int? = null,
)

/** A dose an agent's request logs once it's read. [at] is null for now. */
data class AgentLogRequest(
    val product: UserProduct,
    /** In the product's own unit: a cartridge is 8 on an inhalator logged by session. */
    val quantity: Int,
    val at: Instant?,
    val tagKeys: Set<String>,
    val cravingStrength: Int?,
)

sealed interface AgentLogReading {
    data class Ready(val request: AgentLogRequest) : AgentLogReading

    /**
     * Several of the user's products fit [text] equally well, and the rest of the request is
     * fine. Each choice is what would be logged if that product is the one meant, most likely
     * first, so the agent can ask.
     */
    data class Ambiguous(val text: String, val choices: List<AgentLogRequest>) : AgentLogReading

    data class Refused(val reason: AgentRefusal) : AgentLogReading
}

/** Why an agent's call was refused. The app turns each into its own words for the agent. */
sealed interface AgentRefusal {
    /** Set-up isn't finished, so there are no products to log or advise on yet. */
    data object NotSetUp : AgentRefusal

    /** The description is blank. */
    data object MissingProduct : AgentRefusal

    data class UnknownProduct(val text: String) : AgentRefusal

    /** The description is the exact ID of one of the user's products that's hidden. */
    data class HiddenProduct(val product: UserProduct) : AgentRefusal

    /** The quantity isn't from 1 to [AgentLogReader.MAX_QUANTITY]. */
    data class BadQuantity(val quantity: Int) : AgentRefusal

    /** The description gives a number of its own ("two gums") that isn't the quantity. */
    data class ConflictingQuantity(val inProduct: Int, val quantity: Int) : AgentRefusal

    /** More than [AgentLogReader.MAX_QUANTITY] of the product's units, such as 13 cartridges logged by session. */
    data class TooMany(val quantity: Int, val unit: DoseUnit) : AgentRefusal

    /** The time, on the phone's clock, hasn't come yet. */
    data class TimeInFuture(val at: LocalDateTime) : AgentRefusal

    /** Tags that name no tag, as the agent gave them. */
    data class UnknownTags(val tags: List<String>) : AgentRefusal

    /** Tags given by a name more than one tag goes by, each with the keys it could mean. */
    data class AmbiguousTags(val tags: Map<String, List<String>>) : AgentRefusal

    /** Craving strength isn't 1 to 5. */
    data class BadCravingStrength(val strength: Int) : AgentRefusal

    /** Nothing was logged recently enough to undo (see [AgentUndo]). */
    data object NothingToUndo : AgentRefusal
}

/**
 * Reads what an agent asked `logNrtUsage` to log, with the same rules as a quick-log link
 * ([LogLinkParser]): the description goes through the [ProductMatcher], tags through the
 * [TagMatcher], and a time in the future is refused with [LogTime]'s few minutes of leeway.
 *
 * Problems are reported one at a time, in the order a person would fix them: the product, then
 * the quantity, the time, the tags (names that match nothing before names several tags share)
 * and the craving strength. A product that could be several is only a question once everything
 * else is fine, so the agent never asks which product a request it can't log meant.
 *
 * The result says what to log; logging it, with the duplicate check, is the normal logging
 * path's job.
 */
class AgentLogReader(private val products: ProductMatcher, private val tags: TagMatcher) {

    fun read(ask: AgentLogAsk, now: Instant, zone: ZoneId): AgentLogReading {
        val text = ask.productDescription.trim()
        if (text.isEmpty()) return refused(AgentRefusal.MissingProduct)
        val match = products.match(text)
        val candidates = when (match) {
            is ProductMatch.Found -> listOf(match.candidate)
            is ProductMatch.Ambiguous -> match.candidates
            ProductMatch.Unknown -> return refused(
                products.hidden(text)?.let { AgentRefusal.HiddenProduct(it) } ?: AgentRefusal.UnknownProduct(text),
            )
        }

        val asked = ask.quantity
        if (asked != null && asked !in 1..MAX_QUANTITY) return refused(AgentRefusal.BadQuantity(asked))
        val inProduct = match.count
        if (asked != null && inProduct != null && asked != inProduct) {
            return refused(AgentRefusal.ConflictingQuantity(inProduct = inProduct, quantity = asked))
        }
        val count = asked ?: inProduct ?: 1
        // Each candidate in its own unit. A cartridge is 8 on an inhalator logged by session.
        val quantities = candidates.map { it.quantityFor(count) }
        candidates.zip(quantities).firstOrNull { (_, quantity) -> quantity > MAX_QUANTITY }?.let { (candidate, quantity) ->
            return refused(AgentRefusal.TooMany(quantity, candidate.product.unit))
        }

        val at = ask.time?.let { local ->
            val instant = local.atZone(zone).toInstant()
            if (!LogTime.isAcceptable(instant, now)) return refused(AgentRefusal.TimeInFuture(local))
            instant
        }

        val tagKeys = LinkedHashSet<String>()
        val unknown = mutableListOf<String>()
        val ambiguous = LinkedHashMap<String, List<String>>()
        for (tag in ask.tags.map { it.trim() }.filter { it.isNotEmpty() }) {
            when (val found = tags.match(tag)) {
                is TagMatch.Found -> tagKeys += found.key
                is TagMatch.Ambiguous -> ambiguous[tag] = found.keys
                TagMatch.Unknown -> unknown += tag
            }
        }
        if (unknown.isNotEmpty()) return refused(AgentRefusal.UnknownTags(unknown))
        if (ambiguous.isNotEmpty()) return refused(AgentRefusal.AmbiguousTags(ambiguous))

        val strength = ask.cravingStrength
        if (strength != null && strength !in CRAVING_STRENGTHS) return refused(AgentRefusal.BadCravingStrength(strength))

        val requests = candidates.zip(quantities) { candidate, quantity ->
            AgentLogRequest(candidate.product, quantity, at, tagKeys, strength)
        }
        return requests.singleOrNull()?.let { AgentLogReading.Ready(it) } ?: AgentLogReading.Ambiguous(text, requests)
    }

    private fun refused(reason: AgentRefusal) = AgentLogReading.Refused(reason)

    companion object {
        /** The most one call logs, in the product's own unit, as for a link and the log sheet's stepper. */
        const val MAX_QUANTITY = LogLinkParser.MAX_QUANTITY

        /** Craving strength, as the log sheet offers it. */
        val CRAVING_STRENGTHS = 1..5
    }
}
