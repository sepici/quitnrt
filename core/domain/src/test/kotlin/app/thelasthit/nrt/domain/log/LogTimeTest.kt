package app.thelasthit.nrt.domain.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class LogTimeTest {

    private val now = london("2026-10-23T15:05")

    @Test
    fun `quick times count back from now`() {
        assertEquals(now, QuickTime.NOW.resolve(now, LONDON))
        assertEquals(london("2026-10-23T14:50"), QuickTime.MINUTES_15_AGO.resolve(now, LONDON))
        assertEquals(london("2026-10-23T14:35"), QuickTime.MINUTES_30_AGO.resolve(now, LONDON))
        assertEquals(london("2026-10-23T14:05"), QuickTime.HOUR_AGO.resolve(now, LONDON))
        assertEquals(london("2026-10-23T13:05"), QuickTime.TWO_HOURS_AGO.resolve(now, LONDON))
        assertEquals(london("2026-10-22T15:05"), QuickTime.YESTERDAY.resolve(now, LONDON))
    }

    @Test
    fun `yesterday keeps the wall-clock time across a clock change`() {
        val afterChange = london("2026-10-25T15:05")
        val yesterday = QuickTime.YESTERDAY.resolve(afterChange, LONDON)
        assertEquals(london("2026-10-24T15:05"), yesterday)
        assertEquals(Duration.ofHours(25), Duration.between(yesterday, afterChange))
    }

    @Test
    fun `a few minutes into the future is fine but more is refused`() {
        assertTrue(LogTime.isAcceptable(now.plus(Duration.ofMinutes(5)), now))
        assertFalse(LogTime.isAcceptable(now.plus(Duration.ofMinutes(6)), now))
        assertTrue(LogTime.isAcceptable(now.minus(Duration.ofDays(30)), now))
    }
}
