package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.log.TagGroup
import kotlinx.serialization.Serializable

/** The shape of `advice/strategies.json`. */
@Serializable
internal data class AdviceFile(
    val version: Int,
    val strategies: List<StrategyEntry>,
    val quitlines: List<QuitlineEntry>,
) {
    @Serializable
    data class StrategyEntry(
        val key: String,
        val title: String,
        val body: String,
        val steps: List<String>,
        val tags: List<String>,
        val groups: List<TagGroup>,
        val interactive: Interactive? = null,
        val sources: List<SourceEntry>,
    )

    @Serializable
    data class QuitlineEntry(
        val country: String,
        val region: String?,
        val name: String,
        val phone: String?,
        val web: String?,
        val source: SourceEntry,
    )

    /** [read] is the ISO date the page was read, such as "2026-10-23". */
    @Serializable
    data class SourceEntry(val publisher: String, val title: String, val url: String, val read: String)
}
