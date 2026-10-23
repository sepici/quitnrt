package app.thelasthit.nrt.domain.matcher

import app.thelasthit.nrt.domain.log.LogTime
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
 *   or encoded spaces between the words. It has to name exactly one of the user's products.
 * - `qty`: a whole number from 1 to [MAX_QUANTITY], one by default. It counts what `product`
 *   names, so `product=inhalator-cartridge&qty=2` is 16 sessions on an inhalator logged by session.
 * - `at`: a local date and time, such as `2026-10-23T15:00`, read in the phone's zone. Now when
 *   left out. A time in the future is refused, with [LogTime]'s few minutes of leeway.
 * - `tags`: tag keys or names separated by commas. Every one has to name a tag.
 *
 * A parameter the link doesn't know, or one given twice, is refused rather than ignored, since a
 * typo like `qnty=2` would otherwise log the wrong amount. When a link has several problems, the
 * first one found is reported. The result says what to log; logging it, with the duplicate check,
 * is the normal logging path's job.
 */
class LogLinkParser(private val products: ProductMatcher, private val tags: TagMatcher) {

    fun parse(link: String, now: Instant, zone: ZoneId): LogLinkResult {
        val query = queryOf(link) ?: return invalid(LogLinkError.NotALogLink)
        val params = LinkedHashMap<String, String>()
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val name = decode(part.substringBefore('='))?.trim() ?: return invalid(LogLinkError.BadEncoding)
            val value = decode(part.substringAfter('=', "")) ?: return invalid(LogLinkError.BadEncoding)
            val key = name.lowercase(Locale.ROOT)
            if (key !in PARAMETERS) return invalid(LogLinkError.UnknownParameter(name))
            if (params.put(key, value) != null) return invalid(LogLinkError.RepeatedParameter(key))
        }

        val productText = params[PRODUCT]?.trim().orEmpty()
        if (productText.isEmpty()) return invalid(LogLinkError.MissingProduct)
        val found = when (val match = products.match(productText)) {
            is ProductMatch.Found -> match
            is ProductMatch.Ambiguous -> {
                return invalid(LogLinkError.AmbiguousProduct(productText, match.candidates.map { it.product }))
            }
            ProductMatch.Unknown -> return invalid(LogLinkError.UnknownProduct(productText))
        }

        val qtyText = params[QTY]?.trim()
        val qty = qtyText?.let { text ->
            text.takeIf { QTY_DIGITS.matches(it) }?.toInt()?.takeIf { it in 1..MAX_QUANTITY }
                ?: return invalid(LogLinkError.BadQuantity(text))
        }
        val inProduct = found.count
        if (qty != null && inProduct != null && qty != inProduct) {
            return invalid(LogLinkError.ConflictingQuantity(inProduct = inProduct, qty = qty))
        }
        val quantity = found.candidate.quantityFor(qty ?: inProduct ?: 1)
        if (quantity > MAX_QUANTITY) return invalid(LogLinkError.TooMany(quantity))

        val at = params[AT]?.trim()?.let { text ->
            val local = localTime(text) ?: return invalid(LogLinkError.BadTime(text))
            val instant = local.atZone(zone).toInstant()
            if (!LogTime.isAcceptable(instant, now)) return invalid(LogLinkError.TimeInFuture(local))
            instant
        }

        val tagKeys = LinkedHashSet<String>()
        val unknownTags = mutableListOf<String>()
        params[TAGS].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { text ->
            when (val tag = tags.match(text)) {
                is TagMatch.Found -> tagKeys += tag.key
                is TagMatch.Ambiguous, TagMatch.Unknown -> unknownTags += text
            }
        }
        if (unknownTags.isNotEmpty()) return invalid(LogLinkError.UnknownTags(unknownTags))

        return LogLinkResult.Valid(LogLinkRequest(found.product, quantity, at, tagKeys))
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

    private fun decode(text: String): String? = try {
        URLDecoder.decode(text, Charsets.UTF_8)
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

        /** The most one link logs, in the product's own unit. The log sheet's stepper stops at 99 too. */
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

    /** Several of the user's products fit, most likely first. */
    data class AmbiguousProduct(val text: String, val candidates: List<UserProduct>) : LogLinkError

    /** `qty` isn't a whole number from 1 to [LogLinkParser.MAX_QUANTITY]. */
    data class BadQuantity(val text: String) : LogLinkError

    /** `product` gives a number of its own ("two gums") that isn't `qty`. */
    data class ConflictingQuantity(val inProduct: Int, val qty: Int) : LogLinkError

    /** More than [LogLinkParser.MAX_QUANTITY] of the product's units, such as 13 cartridges logged by session. */
    data class TooMany(val quantity: Int) : LogLinkError

    /** `at` isn't a date and time like 2026-10-23T15:00. */
    data class BadTime(val text: String) : LogLinkError

    data class TimeInFuture(val at: LocalDateTime) : LogLinkError

    /** Tags that name no tag, or more than one. */
    data class UnknownTags(val tags: List<String>) : LogLinkError
}
