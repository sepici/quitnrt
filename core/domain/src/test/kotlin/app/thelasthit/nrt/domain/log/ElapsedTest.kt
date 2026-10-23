package app.thelasthit.nrt.domain.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ElapsedTest {

    @Test
    fun `hours and minutes under a day`() {
        assertEquals(Elapsed(0, 1, 25), Elapsed.of(Duration.ofMinutes(85)))
        assertEquals(Elapsed(0, 0, 25), Elapsed.of(Duration.ofMinutes(25).plusSeconds(59)))
        assertEquals(Elapsed(0, 23, 59), Elapsed.of(Duration.ofHours(24).minusSeconds(1)))
    }

    @Test
    fun `minutes drop off from a day`() {
        assertEquals(Elapsed(1, 0, 0), Elapsed.of(Duration.ofHours(24)))
        assertEquals(Elapsed(2, 3, 0), Elapsed.of(Duration.ofHours(51).plusMinutes(40)))
    }

    @Test
    fun `under a minute and the future are just now`() {
        assertTrue(Elapsed.of(Duration.ofSeconds(59)).isJustNow)
        assertTrue(Elapsed.of(Duration.ofMinutes(-3)).isJustNow)
        assertFalse(Elapsed.of(Duration.ofMinutes(1)).isJustNow)
    }
}
