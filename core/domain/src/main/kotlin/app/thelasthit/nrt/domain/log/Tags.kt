package app.thelasthit.nrt.domain.log

/** Groups let trigger stats roll up and decide which advice to show. */
enum class TagGroup { FEELINGS, ROUTINE, PEOPLE, BODY, HABIT }

/**
 * A built-in tag. The key is stable: it's stored with entries, maps to advice, and names the
 * tag's text in the app's strings (`tag_<key>`). Users can rename, hide and reorder built-in
 * tags, and add their own, without the key changing.
 */
data class BuiltInTag(val key: String, val group: TagGroup)

object BuiltInTags {
    val all: List<BuiltInTag> = listOf(
        "stressed", "anxious", "angry", "low", "bored", "lonely", "happy", "excited", "celebrating", "relieved",
    ).map { BuiltInTag(it, TagGroup.FEELINGS) } + listOf(
        "after_meal", "coffee_tea", "drink", "woke_up", "before_bed", "commuting", "work_break",
        "after_exercise", "on_call", "screen_time",
    ).map { BuiltInTag(it, TagGroup.ROUTINE) } + listOf(
        "nicotine_users", "socialising", "alone", "after_argument",
    ).map { BuiltInTag(it, TagGroup.PEOPLE) } + listOf(
        "strong_craving", "withdrawal", "tired", "hungry", "cant_focus",
    ).map { BuiltInTag(it, TagGroup.BODY) } + listOf(
        "autopilot",
    ).map { BuiltInTag(it, TagGroup.HABIT) }

    private val byKey = all.associateBy { it.key }

    fun find(key: String): BuiltInTag? = byKey[key]
}
