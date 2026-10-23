package app.thelasthit.nrt.domain.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TagOrderTest {

    @Test
    fun `most used first and ties keep the user's order`() {
        val tags = listOf("stressed", "anxious", "bored", "happy", "low")
        val uses = mapOf("bored" to 3, "happy" to 7, "low" to 3)
        assertEquals(listOf("happy", "bored", "low", "stressed", "anxious"), TagOrder.byUse(tags, uses) { it })
    }

    @Test
    fun `with no uses the order is unchanged`() {
        val tags = listOf("after_meal", "coffee_tea", "drink")
        assertEquals(tags, TagOrder.byUse(tags, emptyMap()) { it })
    }
}
