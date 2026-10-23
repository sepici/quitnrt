package app.thelasthit.nrt.domain.product

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LabelLimitTest {

    @Test
    fun `reaching the daily limit is fine and going over it is flagged`() {
        val gum = LabelLimit(maxUnitsPerDay = 15)
        assertFalse(gum.isOverDaily(15))
        assertTrue(gum.isOverDaily(16))
    }

    @Test
    fun `hourly limits work the same way`() {
        val quickMist = LabelLimit(maxUnitsPerDay = 64, maxUnitsPerHour = 4)
        assertFalse(quickMist.isOverHourly(4))
        assertTrue(quickMist.isOverHourly(5))
    }

    @Test
    fun `no limit means never over`() {
        val unknown = LabelLimit()
        assertFalse(unknown.isOverDaily(1000))
        assertFalse(unknown.isOverHourly(1000))
    }

    @Test
    fun `limits must be positive`() {
        assertThrows<IllegalArgumentException> { LabelLimit(maxUnitsPerDay = 0) }
    }
}
