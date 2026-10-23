package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale

/**
 * The advice bundled with the app, as `advice/strategies.json`: strategies for the moments people
 * tag, each from public health guidance with its source, and the quitlines to point people to.
 *
 * It's general information, not medical advice, and nothing in it suggests taking more nicotine.
 * Which strategies come first for someone is a separate rule; this is only the content.
 */
class AdviceLibrary internal constructor(
    val strategies: List<Strategy>,
    val quitlines: List<Quitline>,
) {
    private val byKey = strategies.associateBy { it.key }

    fun find(key: String): Strategy? = byKey[key]

    /** The strategy that opens [interactive]. The library has one of each. */
    fun opening(interactive: Interactive): Strategy = strategies.single { it.interactive == interactive }

    /** Quitlines for [country], an ISO code such as "GB". Empty where the library has none. */
    fun quitlinesFor(country: String): List<Quitline> {
        val code = country.uppercase(Locale.ROOT)
        return quitlines.filter { it.country == code }
    }

    companion object {
        private const val RESOURCE = "/advice/strategies.json"

        private val json = Json { ignoreUnknownKeys = false }

        private val KEY = Regex("[a-z]+(_[a-z0-9]+)*")

        /** Loads the advice bundled with the app. */
        fun bundled(): AdviceLibrary {
            val text = AdviceLibrary::class.java.getResourceAsStream(RESOURCE)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Advice resource $RESOURCE is missing")
            return parse(text)
        }

        internal fun parse(text: String): AdviceLibrary {
            val file = json.decodeFromString(AdviceFile.serializer(), text)
            require(file.version == 1) { "Unsupported advice version ${file.version}" }
            val strategies = file.strategies.map(::strategy)
            val duplicates = strategies.groupBy { it.key }.filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) { "Duplicate strategy keys: $duplicates" }
            Interactive.entries.forEach { kind ->
                require(strategies.count { it.interactive == kind } <= 1) { "More than one strategy opens $kind" }
            }
            return AdviceLibrary(strategies, file.quitlines.map(::quitline))
        }

        private fun strategy(entry: AdviceFile.StrategyEntry): Strategy {
            val key = entry.key
            require(KEY.matches(key)) { "Strategy key \"$key\" isn't lower_snake_case" }
            require(entry.title.isNotBlank() && entry.body.isNotBlank()) { "$key needs a title and a body" }
            require(entry.steps.isNotEmpty() && entry.steps.none { it.isBlank() }) { "$key needs its steps" }
            require(entry.tags.isNotEmpty() || entry.groups.isNotEmpty()) { "$key suits no tag or group" }
            val unknown = entry.tags.filter { BuiltInTags.find(it) == null }
            require(unknown.isEmpty()) { "$key names tags that don't exist: $unknown" }
            require(entry.tags.toSet().size == entry.tags.size) { "$key lists a tag twice" }
            require(entry.groups.toSet().size == entry.groups.size) { "$key lists a group twice" }
            require(entry.sources.isNotEmpty()) { "$key has no source" }
            return Strategy(
                key = key,
                title = entry.title,
                body = entry.body,
                steps = entry.steps,
                tags = entry.tags.toSet(),
                groups = entry.groups.toSet(),
                interactive = entry.interactive,
                sources = entry.sources.map { source(it, key) },
            )
        }

        private fun quitline(entry: AdviceFile.QuitlineEntry): Quitline {
            val where = listOfNotNull(entry.country, entry.region).joinToString(" ")
            require(Regex("[A-Z]{2}").matches(entry.country)) { "Quitline country \"${entry.country}\" isn't an ISO code" }
            require(entry.name.isNotBlank()) { "The $where quitline needs a name" }
            require(!entry.phone.isNullOrBlank() || !entry.web.isNullOrBlank()) { "The $where quitline needs a number or a site" }
            return Quitline(
                country = entry.country,
                region = entry.region,
                name = entry.name,
                phone = entry.phone,
                web = entry.web,
                source = source(entry.source, "The $where quitline"),
            )
        }

        private fun source(entry: AdviceFile.SourceEntry, owner: String): AdviceSource {
            require(entry.publisher.isNotBlank() && entry.title.isNotBlank()) { "$owner has a source with no publisher or title" }
            require(entry.url.startsWith("https://")) { "$owner has a source that isn't an https address: ${entry.url}" }
            return AdviceSource(entry.publisher, entry.title, entry.url, LocalDate.parse(entry.read))
        }
    }
}

/**
 * One way through a moment, such as box breathing or swapping the morning coffee.
 *
 * A strategy suits the tags it names and every tag in the groups it names, which takes in the
 * tags people add themselves. One that names every group suits any time ([suitsAnyTime]).
 */
data class Strategy(
    /** Stable, in lower_snake_case. Anything stored about a strategy uses this. */
    val key: String,
    /** Sentence case, with no full stop. */
    val title: String,
    /** One to three sentences on why it helps. */
    val body: String,
    /** What to do, in order. */
    val steps: List<String>,
    /** Built-in tag keys, from [BuiltInTags]. */
    val tags: Set<String>,
    val groups: Set<TagGroup>,
    /** What the strategy opens besides its text, or null for text only. */
    val interactive: Interactive?,
    /** Where the advice comes from. Never empty. */
    val sources: List<AdviceSource>,
) {
    val suitsAnyTime: Boolean get() = groups.containsAll(TagGroup.entries)
}

/** The two strategies that are more than text. */
enum class Interactive {
    /** Box breathing (4-4-4-4) or 4-7-8 breathing, animated, for 1 to 3 minutes. */
    BREATHING_TIMER,

    /** A countdown of 5 to 10 minutes, since an urge peaks and passes. */
    DELAY_TIMER,
}

/** A page the advice comes from, and the day it was read. */
data class AdviceSource(val publisher: String, val title: String, val url: String, val read: LocalDate)

/**
 * A free line for help with stopping, for one country or one part of it. [phone] is written the
 * way the service writes it. Northern Ireland has no single number, only a site with local services.
 */
data class Quitline(
    val country: String,
    /** A nation within the country, such as "Scotland", or null for the whole country. */
    val region: String?,
    val name: String,
    val phone: String?,
    val web: String?,
    val source: AdviceSource,
)
