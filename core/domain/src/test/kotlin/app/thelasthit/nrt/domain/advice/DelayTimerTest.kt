package app.thelasthit.nrt.domain.advice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

class DelayTimerTest {

    /** 15:03:12 in London, so 15:05 shows 3:12 left of 5 minutes, as in the mockup. */
    private val start: Instant = Instant.parse("2026-10-23T14:03:12Z")
    private val run = DelayRun(start, 5)

    @Test
    fun `it counts down from its start, whenever it's looked at`() {
        assertEquals(Instant.parse("2026-10-23T14:08:12Z"), run.endsAt)
        assertEquals(Duration.ofSeconds(192), run.remaining(Instant.parse("2026-10-23T14:05:00Z")))
        assertEquals(0.36, run.progress(Instant.parse("2026-10-23T14:05:00Z")), 1e-9)
        assertEquals(Duration.ofMinutes(5), run.remaining(start))
        // Rounded up, so it only reads 0:00 at the end.
        assertEquals(Duration.ofSeconds(1), run.remaining(run.endsAt.minusMillis(400)))
        assertFalse(run.isDone(run.endsAt.minusMillis(1)))
        assertTrue(run.isDone(run.endsAt))
        assertEquals(Duration.ZERO, run.remaining(run.endsAt.plusSeconds(600)))
        assertEquals(1.0, run.progress(run.endsAt.plusSeconds(600)))
    }

    @Test
    fun `a clock before the start shows the whole time`() {
        assertEquals(Duration.ofMinutes(5), run.remaining(start.minusSeconds(30)))
        assertEquals(0.0, run.progress(start.minusSeconds(30)))
    }

    @Test
    fun `it runs for 5 to 10 minutes`() {
        assertEquals(Duration.ofMinutes(10), DelayRun(start, 10).length)
        assertThrows<IllegalArgumentException> { DelayRun(start, 4) }
        assertThrows<IllegalArgumentException> { DelayRun(start, 11) }
    }

    @Test
    fun `it buzzes when the end is found soon after, not long after`() {
        assertFalse(run.buzzesAt(run.endsAt.minusSeconds(1)))
        assertTrue(run.buzzesAt(run.endsAt))
        assertTrue(run.buzzesAt(run.endsAt.plusSeconds(59)))
        assertFalse(run.buzzesAt(run.endsAt.plusSeconds(60)))
    }
}
