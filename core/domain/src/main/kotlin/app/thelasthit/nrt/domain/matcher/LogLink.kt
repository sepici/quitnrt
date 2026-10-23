package app.thelasthit.nrt.domain.matcher

import app.thelasthit.nrt.domain.log.LogTime
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.UserProduct
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Reads a quick-log link, the kind Routines, Tasker or an NFC sticker on a gum pack can open:
 *
 * ```
 * lasthit://log?product=nicorette-gum-2mg&qty=1&at=2026-10-23T15:00&tags=stressed,after-meal
 * ```
 *
 * - `product` (required): a product ID, or a description the [ProductMatcher] reads, with hyphens
 *   or encoded spaces between the words. A hyphen between digits before "mg" is a decimal point,
 *   as in the catalogue's IDs: `nicorette-nasal-spray-0-5mg` is the 0.5 mg spray. It has to name
 *   one of the user's products that isn't hidden. The exact ID of a hidden one, such as a pinned
 *   shortcut's, is [LogLinkError.HiddenProduct]. When it fits several equally well and the rest
 *   of the link is fine, the result is [LogLinkResult.Ambiguous], so the app can ask which one.
 * - `qty`: a whole number from 1 to [MAX_QUANTITY], one by default. It counts what `product`
 *   names, so `product=inhalator-cartridge&qty=2` is 16 sessions on an inhalator logged by session.
 * - `at`: a local date and time, such as `2026-10-23T15:00`, read in the phone's zone. Now when
 *   left out. A time in the future is refused, with [LogTime]'s few minutes of leeway.
 * - `tags`: tag keys or names separated by commas. Every one has to name exactly one tag. A comma
 *   inside a tag's name is sent as `%2C`.
 *
 * A parameter the link doesn't know, or one given twice, is refused rather than ignored, since a
 * typo like `qnty=2` would otherwise log the wrong amount. When a link has several problems, the
 * first one found is reported, and tags that name nothing come before names that several tags
 * share. A product that could be several is only a question once everything else is known to be
 * fine, so the app never asks which product a broken link meant.
 *
 * `lasthit://log` with nothing after it is [LogLinkResult.Blank], which opens the log sheet.
 *
 * The result says what to log; logging it, with the duplicate check, is the normal logging path's
 * job.
 */
class LogLinkParser(private val products: ProductMatcher, private val tags: TagMatcher) {

    fun parse(link: String, now: Instant, zone: ZoneId): LogLinkResult {
        val query = queryOf(link) ?: return invalid(LogLinkError.NotALogLink)
        val params = LinkedHashMap<String, String>()
        var rawTags = ""
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val name = decode(part.substringBefore('='))?.trim() ?: return invalid(LogLinkError.BadEncoding)
            val raw = part.substringAfter('=', "")
            val value = decode(raw) ?: return invalid(LogLinkError.BadEncoding)
            val key = name.lowercase(Locale.ROOT)
            if (key !in PARAMETERS) return invalid(LogLinkError.UnknownParameter(name))
            if (params.put(key, value) != null) return invalid(LogLinkError.RepeatedParameter(key))
            if (key == TAGS) rawTags = raw
        }
        if (params.isEmpty()) return LogLinkResult.Blank

        val productText = params[PRODUCT]?.trim().orEmpty()
        if (productText.isEmpty()) return invalid(LogLinkError.MissingProduct)
        val match = products.match(productText)
        val candidates = when (match) {
            is ProductMatch.Found -> listOf(match.candidate)
            is ProductMatch.Ambiguous -> match.candidates
            ProductMatch.Unknown -> return invalid(
                products.hidden(productText)?.let { LogLinkError.HiddenProduct(it) } ?: LogLinkError.UnknownProduct(productText),
            )
        }

        val qtyText = params[QTY]?.trim()
        val qty = qtyText?.let { text ->
            text.takeIf { QTY_DIGITS.matches(it) }?.toInt()?.takeIf { it in 1..MAX_QUANTITY }
                ?: return invalid(LogLinkError.BadQuantity(text))
        }
        val inProduct = match.count
        if (qty != null && inProduct != null && qty != inProduct) {
            return invalid(LogLinkError.ConflictingQuantity(inProduct = inProduct, qty = qty))
        }
        // Each candidate in its own unit. A cartridge is 8 on an inhalator logged by session.
        val quantities = candidates.map { it.quantityFor(qty ?: inProduct ?: 1) }
        candidates.zip(quantities).firstOrNull { (_, quantity) -> quantity > MAX_QUANTITY }?.let { (candidate, quantity) ->
            return invalid(LogLinkError.TooMany(quantity, candidate.product.unit))
        }

        val at = params[AT]?.trim()?.let { text ->
            val local = localTime(text) ?: return invalid(LogLinkError.BadTime(text))
            val instant = local.atZone(zone).toInstant()
            if (!LogTime.isAcceptable(instant, now)) return invalid(LogLinkError.TimeInFuture(local))
            instant
        }

        val tagKeys = LinkedHashSet<String>()
        val unknownTags = mutableListOf<String>()
        val ambiguousTags = LinkedHashMap<String, List<String>>()
        // Split before decoding, so a comma sent as %2C stays inside a tag's name.
        for (part in rawTags.split(',')) {
            val text = decode(part)?.trim() ?: return invalid(LogLinkError.BadEncoding)
            if (text.isEmpty()) continue
            when (val tag = tags.match(text)) {
                is TagMatch.Found -> tagKeys += tag.key
                is TagMatch.Ambiguous -> ambiguousTags[text] = tag.keys
                TagMatch.Unknown -> unknownTags += text
            }
        }
        if (unknownTags.isNotEmpty()) return invalid(LogLinkError.UnknownTags(unknownTags))
        if (ambiguousTags.isNotEmpty()) return invalid(LogLinkError.AmbiguousTags(ambiguousTags))

        val requests = candidates.zip(quantities) { candidate, quantity -> LogLinkRequest(candidate.product, quantity, at, tagKeys) }
        return requests.singleOrNull()?.let { LogLinkResult.Valid(it) } ?: LogLinkResult.Ambiguous(productText, requests)
    }

    private fun invalid(error: LogLinkError) = LogLinkResult.Invalid(error)

    /** The part after "?", empty when there's none, or null when this isn't a log link. */
    private fun queryOf(link: String): String? {
        val text = link.trim()
        val prefix = "$SCHEME://$HOST"
        if (!text.startsWith(prefix, ignoreCase = true)) return null
        val rest = text.substring(prefix.length).substringBefore('#').removePrefix("/")
        return when {
            rest.isEmpty() -> ""
            rest.startsWith('?') -> rest.substring(1)
            // Another path, or a longer host such as lasthit://logs.
            else -> null
        }
    }

    /**
     * Null for a %-escape that doesn't decode. This is the overload that names the charset as text:
     * the one that takes a Charset is only on Android from API 33, and the app runs from 26.
     */
    private fun decode(text: String): String? = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: IllegalArgumentException) {
        null
    }

    /** ISO local date and time, with a space allowed in place of the "T". Seconds are optional. */
    private fun localTime(text: String): LocalDateTime? = try {
        LocalDateTime.parse(text.replaceFirst(' ', 'T'))
    } catch (e: DateTimeParseException) {
        null
    }

    companion object {
        const val SCHEME = "lasthit"
        const val HOST = "log"

        /** The most one link logs, in the product's own unit. The log sheet's stepper stops here too. */
        const val MAX_QUANTITY = 99

        private const val PRODUCT = "product"
        private const val QTY = "qty"
        private const val AT = "at"
        private const val TAGS = "tags"
        private val PARAMETERS = setOf(PRODUCT, QTY, AT, TAGS)
        private val QTY_DIGITS = Regex("[0-9]{1,3}")
    }
}

/** What a valid link asks to log. [at] is null when the link gave no time, meaning now. */
data class LogLinkRequest(
    val product: UserProduct,
    val quantity: Int,
    val at: Instant?,
    val tagKeys: Set<String>,
)

sealed interface LogLinkResult {
    data class Valid(val request: LogLinkRequest) : LogLinkResult

    /** `lasthit://log` with nothing after it, which opens the log sheet. */
    data object Blank : LogLinkResult

    /**
     * Several of the user's products fit [text] equally well, and the rest of the link is fine.
     * Each choice is what the link logs if that product is the one meant, most likely first, so
     * the app can ask. Choices differ only in the product and, for an inhalator, the quantity.
     */
    data class Ambiguous(val text: String, val choices: List<LogLinkRequest>) : LogLinkResult

    data class Invalid(val error: LogLinkError) : LogLinkResult
}

/** Why a link was refused. The app turns each into its own words. */
sealed interface LogLinkError {
    /** Not a `lasthit://log` link. */
    data object NotALogLink : LogLinkError

    /** A %-escape that doesn't decode. */
    data object BadEncoding : LogLinkError

    data class UnknownParameter(val name: String) : LogLinkError

    data class RepeatedParameter(val name: String) : LogLinkError

    data object MissingProduct : LogLinkError

    data class UnknownProduct(val text: String) : LogLinkError

    /**
     * `product` is the exact ID of one of the user's products that's hidden, as a pinned shortcut
     * made before it was hidden is. Hidden products aren't logged from links, as they aren't from
     * the log sheet, so the app can say how to bring it back.
     */
    data class HiddenProduct(val product: UserProduct) : LogLinkError

    /** `qty` isn't a whole number from 1 to [LogLinkParser.MAX_QUANTITY]. */
    data class BadQuantity(val text: String) : LogLinkError

    /** `product` gives a number of its own ("two gums") that isn't `qty`. */
    data class ConflictingQuantity(val inProduct: Int, val qty: Int) : LogLinkError

    /**
     * More than [LogLinkParser.MAX_QUANTITY] of the product's units, such as 13 cartridges on an
     * inhalator logged by session: [quantity] is then 104 and [unit] is sessions.
     */
    data class TooMany(val quantity: Int, val unit: DoseUnit) : LogLinkError

    /** `at` isn't a date and time like 2026-10-23T15:00. */
    data class BadTime(val text: String) : LogLinkError

    data class TimeInFuture(val at: LocalDateTime) : LogLinkError

    /** Tags that name no tag, as the link gave them. */
    data class UnknownTags(val tags: List<String>) : LogLinkError

    /**
     * Tags given by a name that more than one tag goes by, such as two of the user's own tags both
     * called "Gym". Each comes with the keys it could mean, in the link's order.
     */
    data class AmbiguousTags(val tags: Map<String, List<String>>) : LogLinkError
}
