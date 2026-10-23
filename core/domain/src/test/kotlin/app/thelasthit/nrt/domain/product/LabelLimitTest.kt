package app.thelasthit.nrt.domain.product

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class LabelLimitTest {

    @Test
    fun `reaching the daily limit is fine and going over it is flagged`() {
        val gum = LabelLimit(maxUnitsPerDay = 15)
        assertFalse(gum.isOverDaily(15))
        assertTrue(gum.isOverDaily(16))
    }

    @Test
    fun `rolling windows work the same way`() {
        val quickMist = LabelLimit(maxUnitsPerDay = 64, window = RollingLimit(4, Duration.ofHours(1)))
        assertFalse(quickMist.isOverWindow(4))
        assertTrue(quickMist.isOverWindow(5))
    }

    @Test
    fun `no limit means never over`() {
        assertFalse(LabelLimit.NONE.isOverDaily(1000))
        assertFalse(LabelLimit.NONE.isOverWindow(1000))
    }

    @Test
    fun `limits must be positive`() {
        assertThrows<IllegalArgumentException> { LabelLimit(maxUnitsPerDay = 0) }
        assertThrows<IllegalArgumentException> { RollingLimit(0, Duration.ofHours(1)) }
        assertThrows<IllegalArgumentException> { RollingLimit(4, Duration.ZERO) }
    }
}
