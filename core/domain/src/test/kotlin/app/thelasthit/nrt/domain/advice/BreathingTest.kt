package app.thelasthit.nrt.domain.advice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

class BreathingTest {

    private fun session(rounds: Int, ranToEnd: Boolean, seconds: Long = 60) =
        BreathingSession("s", BreathingPattern.BOX, Instant.parse("2026-10-23T13:00:00Z"), Duration.ofSeconds(seconds), rounds, ranToEnd)

    @Test
    fun `a session counts towards the badges when it runs to the end`() {
        assertTrue(session(rounds = 4, ranToEnd = true).counts)
        assertFalse(session(rounds = 2, ranToEnd = false).counts)
    }

    @Test
    fun `a session with no whole round done doesn't count`() {
        assertFalse(session(rounds = 0, ranToEnd = true, seconds = 5).counts)
    }

    @Test
    fun `rounds and time can't be negative`() {
        assertThrows<IllegalArgumentException> { session(rounds = -1, ranToEnd = false) }
        assertThrows<IllegalArgumentException> { session(rounds = 1, ranToEnd = false, seconds = -1) }
    }
}
