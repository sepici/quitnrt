package app.thelasthit.nrt.domain.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StopDateTest {

    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `under 8 weeks in is early in the course`() {
        assertTrue(StopDate.isEarlyInCourse(LocalDate.parse("2026-09-07"), today))
        assertTrue(StopDate.isEarlyInCourse(today, today))
        assertFalse(StopDate.isEarlyInCourse(today.minusWeeks(8), today))
        assertFalse(StopDate.isEarlyInCourse(LocalDate.parse("2026-01-10"), today))
    }

    @Test
    fun `weeks and days since are counted whole`() {
        assertEquals(6, StopDate.weeksSince(LocalDate.parse("2026-09-07"), today))
        assertEquals(46, StopDate.daysSince(LocalDate.parse("2026-09-07"), today))
    }
}
