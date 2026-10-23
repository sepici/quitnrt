package app.thelasthit.nrt.domain.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInTagsTest {

    @Test
    fun `keys are unique and safe to use as resource names`() {
        val keys = BuiltInTags.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { assertTrue(Regex("[a-z]+(_[a-z]+)*").matches(it), it) }
    }

    @Test
    fun `every group has tags`() {
        val counts = BuiltInTags.all.groupingBy { it.group }.eachCount()
        assertEquals(TagGroup.entries.toSet(), counts.keys)
        assertEquals(10, counts[TagGroup.FEELINGS])
        assertEquals(10, counts[TagGroup.ROUTINE])
    }

    @Test
    fun `tags can be looked up by key`() {
        assertEquals(TagGroup.ROUTINE, BuiltInTags.find("after_meal")?.group)
        assertNull(BuiltInTags.find("nope"))
    }
}
