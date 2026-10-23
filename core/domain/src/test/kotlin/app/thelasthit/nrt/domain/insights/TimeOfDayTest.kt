package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.insights.TimeOfDay.AFTERNOON
import app.thelasthit.nrt.domain.insights.TimeOfDay.EVENING
import app.thelasthit.nrt.domain.insights.TimeOfDay.MORNING
import app.thelasthit.nrt.domain.insights.TimeOfDay.NIGHT
import app.thelasthit.nrt.domain.log.entry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId

class TimeOfDayTest {

    @Test
    fun `the day splits at 05, 12, 17 and 22`() {
        fun at(text: String) = TimeOfDay.of(LocalTime.parse(text))
        assertEquals(NIGHT, at("00:00"))
        assertEquals(NIGHT, at("04:59"))
        assertEquals(MORNING, at("05:00"))
        assertEquals(MORNING, at("11:59"))
        assertEquals(AFTERNOON, at("12:00"))
        assertEquals(AFTERNOON, at("16:59"))
        assertEquals(EVENING, at("17:00"))
        assertEquals(EVENING, at("21:59"))
        assertEquals(NIGHT, at("22:00"))
    }

    @Test
    fun `a dose's part of the day is read off its own clock`() {
        assertEquals(MORNING, TimeOfDay.of(entry("2026-10-21T08:15", zone = ZoneId.of("America/New_York"))))
    }
}
