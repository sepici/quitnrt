package app.thelasthit.nrt.domain.matcher

import app.thelasthit.nrt.domain.log.BuiltInTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TagMatcherTest {

    /** As the app would pass them: each built-in tag's text, plus a renamed one and two of the user's own. */
    private val matcher = TagMatcher(
        listOf(
            TagName("stressed", listOf("Stressed")),
            TagName("after_meal", listOf("After a meal")),
            TagName("coffee_tea", listOf("With coffee or tea")),
            TagName("cant_focus", listOf("Can't focus")),
            TagName("work_break", listOf("Work break", "Smoko")),
            TagName("custom_1a2b", listOf("Gaming")),
            TagName("custom_3c4d", listOf("Stressed")),
            TagName("custom_5e6f", listOf("Gym")),
            TagName("custom_7a8b", listOf("Gym")),
        ),
    )

    private fun found(key: String) = TagMatch.Found(key)

    @Test
    fun `keys match with hyphens, underscores, spaces or capitals`() {
        listOf("after_meal", "after-meal", "After Meal", " after  meal ").forEach {
            assertEquals(found("after_meal"), matcher.match(it), it)
        }
        assertEquals(found("stressed"), matcher.match("STRESSED"))
        assertEquals(found("cant_focus"), matcher.match("cant-focus"))
    }

    @Test
    fun `names the caller supplies match too`() {
        assertEquals(found("after_meal"), matcher.match("After a meal"))
        assertEquals(found("after_meal"), matcher.match("after-a-meal"))
        assertEquals(found("coffee_tea"), matcher.match("with coffee or tea"))
        assertEquals(found("cant_focus"), matcher.match("Can't focus"))
        assertEquals(found("cant_focus"), matcher.match("can’t focus"))
        assertEquals(found("work_break"), matcher.match("smoko"))
        assertEquals(found("custom_1a2b"), matcher.match("gaming"))
    }

    @Test
    fun `a key wins over another tag's name`() {
        assertEquals(found("stressed"), matcher.match("Stressed"))
    }

    @Test
    fun `a name two tags share is ambiguous`() {
        assertEquals(TagMatch.Ambiguous(listOf("custom_5e6f", "custom_7a8b")), matcher.match("gym"))
    }

    @Test
    fun `nothing fuzzier than that`() {
        listOf("", "  ", ",", "stress", "coffee", "after meals", "vaping").forEach {
            assertEquals(TagMatch.Unknown, matcher.match(it), it)
        }
    }

    @Test
    fun `the built-in tags by key alone`() {
        val builtIns = TagMatcher.builtIns()
        BuiltInTags.all.forEach { assertEquals(found(it.key), builtIns.match(it.key.replace('_', '-')), it.key) }
        assertEquals(TagMatch.Unknown, builtIns.match("After a meal"))
    }
}
