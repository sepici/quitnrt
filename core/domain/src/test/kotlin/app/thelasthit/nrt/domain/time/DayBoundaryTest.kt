package app.thelasthit.nrt.domain.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class DayBoundaryTest {

    private val london = ZoneId.of("Europe/London")
    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val boundary = DayBoundary()

    private fun at(text: String, zone: ZoneId = london): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    @Test
    fun `a dose after midnight but before the day start counts toward the evening before`() {
        assertEquals(date("2026-10-22"), boundary.logicalDate(at("2026-10-23T01:30"), london))
    }

    @Test
    fun `the day start itself belongs to the new day`() {
        assertEquals(date("2026-10-23"), boundary.logicalDate(at("2026-10-23T04:00"), london))
        assertEquals(date("2026-10-22"), boundary.logicalDate(at("2026-10-23T03:59:59"), london))
    }

    @Test
    fun `a midnight day start gives calendar days`() {
        val midnight = DayBoundary(LocalTime.MIDNIGHT)
        assertEquals(date("2026-10-23"), midnight.logicalDate(at("2026-10-23T00:00"), london))
        assertEquals(date("2026-10-22"), midnight.logicalDate(at("2026-10-22T23:59"), london))
    }

    @Test
    fun `the same moment can fall on different logical days in different zones`() {
        val moment = Instant.parse("2026-10-23T23:30:00Z")
        assertEquals(date("2026-10-23"), boundary.logicalDate(moment, london)) // 00:30 local
        assertEquals(date("2026-10-23"), boundary.logicalDate(moment, istanbul)) // 02:30 local
        assertEquals(date("2026-10-24"), boundary.logicalDate(moment, tokyo)) // 08:30 local
    }

    @Test
    fun `the night the clocks go forward is 23 hours long`() {
        // UK clocks go forward at 01:00 GMT on 29 March 2026.
        val window = boundary.window(date("2026-03-28"), london)
        assertEquals(Duration.ofHours(23), Duration.between(window.start, window.endExclusive))
    }

    @Test
    fun `the night the clocks go back is 25 hours long and both 01_30s count toward the same day`() {
        // UK clocks go back at 02:00 BST on 25 October 2026, so 01:30 happens twice.
        val window = boundary.window(date("2026-10-24"), london)
        assertEquals(Duration.ofHours(25), Duration.between(window.start, window.endExclusive))

        val firstPass = LocalDateTime.parse("2026-10-25T01:30").atOffset(ZoneOffset.ofHours(1)).toInstant()
        val secondPass = LocalDateTime.parse("2026-10-25T01:30").atOffset(ZoneOffset.UTC).toInstant()
        assertEquals(date("2026-10-24"), boundary.logicalDate(firstPass, london))
        assertEquals(date("2026-10-24"), boundary.logicalDate(secondPass, london))
    }

    @Test
    fun `a day start inside the skipped hour begins the day when the clocks jump`() {
        val boundary = DayBoundary(LocalTime.of(1, 30))
        val window = boundary.window(date("2026-03-29"), london)
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), window.start)

        assertEquals(date("2026-03-29"), boundary.logicalDate(Instant.parse("2026-03-29T01:10:00Z"), london))
        assertEquals(date("2026-03-28"), boundary.logicalDate(Instant.parse("2026-03-29T00:59:00Z"), london))
    }

    @Test
    fun `a day start inside the repeated hour never sends an entry back a day`() {
        val boundary = DayBoundary(LocalTime.of(1, 30))
        val firstPassStart = Instant.parse("2026-10-25T00:30:00Z") // 01:30 BST
        assertEquals(firstPassStart, boundary.window(date("2026-10-25"), london).start)

        val secondPass0115 = Instant.parse("2026-10-25T01:15:00Z") // 01:15 GMT, after the first 01:30
        assertEquals(date("2026-10-25"), boundary.logicalDate(secondPass0115, london))

        val firstPass0115 = Instant.parse("2026-10-25T00:15:00Z") // 01:15 BST
        assertEquals(date("2026-10-24"), boundary.logicalDate(firstPass0115, london))
    }

    @Test
    fun `every moment inside a day's window maps back to that day`() {
        val zones = listOf(london, istanbul, tokyo, ZoneId.of("America/New_York"), ZoneId.of("Australia/Sydney"))
        val starts = listOf(LocalTime.MIDNIGHT, LocalTime.of(1, 30), LocalTime.of(4, 0), LocalTime.of(6, 0))
        val dates = generateSequence(date("2026-01-01")) { it.plusDays(1) }.takeWhile { it.year == 2026 }

        for (zone in zones) for (start in starts) {
            val boundary = DayBoundary(start)
            for (day in dates) {
                val window = boundary.window(day, zone)
                assertTrue(window.start < window.endExclusive, "$zone $start $day has an empty window")
                for (probe in listOf(window.start, window.endExclusive.minusSeconds(1))) {
                    assertEquals(day, boundary.logicalDate(probe, zone), "$zone $start $day at $probe")
                }
                assertEquals(day.plusDays(1), boundary.logicalDate(window.endExclusive, zone), "$zone $start $day end")
            }
        }
    }
}
