package app.thelasthit.nrt.domain.matcher

import app.thelasthit.nrt.domain.log.BuiltInTags

/**
 * A tag the [TagMatcher] can find: its key, and the names it shows under. The names live in the
 * app (a built-in tag's text from the strings, and the user's own name for it), so the caller
 * passes them in.
 */
data class TagName(val key: String, val names: List<String> = emptyList())

/**
 * Finds tags by key or by name, ignoring case, spaces, hyphens, underscores and apostrophes:
 * "after-meal", "after_meal" and "After a meal" all find `after_meal`, and "can't focus" finds
 * `cant_focus`. A key wins over another tag's name. Nothing fuzzier than that, so a tag that's
 * only nearly right is unknown rather than guessed.
 */
class TagMatcher(tags: List<TagName>) {

    private val byKey: Map<String, String> = tags.associate { Words.plain(it.key) to it.key }

    private val byName: Map<String, List<String>> = tags
        .flatMap { tag -> tag.names.map { Words.plain(it) to tag.key } }
        .filter { (name, _) -> name.isNotEmpty() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, keys) -> keys.distinct() }

    fun match(text: String): TagMatch {
        val plain = Words.plain(text)
        if (plain.isEmpty()) return TagMatch.Unknown
        byKey[plain]?.let { return TagMatch.Found(it) }
        val keys = byName[plain].orEmpty()
        return when (keys.size) {
            0 -> TagMatch.Unknown
            1 -> TagMatch.Found(keys.single())
            else -> TagMatch.Ambiguous(keys)
        }
    }

    companion object {
        /** The built-in tags by key alone, for when no names are to hand. */
        fun builtIns(): TagMatcher = TagMatcher(BuiltInTags.all.map { TagName(it.key) })
    }
}

sealed interface TagMatch {
    data class Found(val key: String) : TagMatch

    /** Two or more tags go by this name. */
    data class Ambiguous(val keys: List<String>) : TagMatch

    data object Unknown : TagMatch
}
