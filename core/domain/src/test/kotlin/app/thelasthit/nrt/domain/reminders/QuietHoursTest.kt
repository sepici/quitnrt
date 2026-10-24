package app.thelasthit.nrt.domain.reminders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class QuietHoursTest {

    private val london: ZoneId = ZoneId.of("Europe/London")

    private fun at(text: String, zone: ZoneId = london): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun time(text: String): LocalTime = LocalTime.parse(text)

    @Test
    fun `the default runs overnight from 22 00 to 07 00, the end not included`() {
        val quiet = QuietHours()
        assertTrue(time("22:00") in quiet)
        assertTrue(time("23:59") in quiet)
        assertTrue(time("00:00") in quiet)
        assertTrue(time("06:59") in quiet)
        assertFalse(time("07:00") in quiet)
        assertFalse(time("21:59") in quiet)
        assertFalse(time("12:00") in quiet)
    }

    @Test
    fun `a window within one day, such as a nap, and an empty one`() {
        val nap = QuietHours(start = time("13:00"), end = time("15:00"))
        assertTrue(time("13:00") in nap)
        assertTrue(time("14:30") in nap)
        assertFalse(time("15:00") in nap)
        assertFalse(time("23:00") in nap)
        val empty = QuietHours(start = time("09:00"), end = time("09:00"))
        assertFalse(time("09:00") in empty)
        assertFalse(time("21:00") in empty)
    }

    @Test
    fun `it's read off the phone's clock, wherever the phone is`() {
        val quiet = QuietHours()
        val moment = at("2026-10-23T23:00")
        assertTrue(quiet.isQuiet(moment, london))
        // 23:00 in London is 18:00 in New York.
        assertFalse(quiet.isQuiet(moment, ZoneId.of("America/New_York")))
    }

    @Test
    fun `switched off, nothing is quiet`() {
        assertFalse(QuietHours(enabled = false).isQuiet(at("2026-10-23T23:00"), london))
    }

    @Test
    fun `quiet hours end at the next end time`() {
        val quiet = QuietHours()
        assertEquals(at("2026-10-24T07:00"), quiet.endAfter(at("2026-10-23T23:00"), london))
        assertEquals(at("2026-10-24T07:00"), quiet.endAfter(at("2026-10-24T03:00"), london))
        val noon = at("2026-10-24T12:00")
        assertEquals(noon, quiet.endAfter(noon, london))
    }

    @Test
    fun `quiet hours end on time on the night the clocks go back`() {
        // The clocks go back at 02:00 BST on Sunday 25 October 2026, so the night is an hour longer.
        assertEquals(at("2026-10-25T07:00"), QuietHours().endAfter(at("2026-10-24T23:30"), london))
    }

    @Test
    fun `moments are dropped in quiet hours, and news waits for the morning`() {
        val settings = ReminderSettings()
        val night = at("2026-10-23T23:00")
        assertEquals(Delivery.Dropped, settings.delivery(Reminder.ALLOWANCE, night, london))
        assertEquals(Delivery.Dropped, settings.delivery(Reminder.CHECK_IN, night, london))
        assertEquals(Delivery.Off, settings.delivery(Reminder.FORGOT_TO_LOG, night, london))
        assertEquals(Delivery.Dropped, settings.with(Reminder.FORGOT_TO_LOG, true).delivery(Reminder.FORGOT_TO_LOG, night, london))
        val morning = Delivery.At(at("2026-10-24T07:00"))
        assertEquals(morning, settings.delivery(Reminder.WEEKLY_RESULT, night, london))
        assertEquals(morning, settings.delivery(Reminder.ACHIEVEMENTS, night, london))
        assertEquals(Delivery.Now, settings.delivery(Reminder.WEEKLY_RESULT, at("2026-10-23T15:05"), london))
    }

    @Test
    fun `a reminder switched off never goes out`() {
        val off = ReminderSettings().with(Reminder.WEEKLY_RESULT, false)
        assertEquals(Delivery.Off, off.delivery(Reminder.WEEKLY_RESULT, at("2026-10-23T15:05"), london))
    }

    @Test
    fun `the defaults follow PLAN, with Forgot to log off`() {
        val settings = ReminderSettings()
        assertEquals(setOf(Reminder.ALLOWANCE, Reminder.CHECK_IN, Reminder.WEEKLY_RESULT, Reminder.ACHIEVEMENTS), settings.on)
        assertEquals(time("21:30"), settings.checkInTime)
        assertFalse(settings.checkInIsQuiet)
        assertTrue(settings.copy(checkInTime = time("22:30")).checkInIsQuiet)
        assertFalse(settings.copy(checkInTime = time("22:30"), quietHours = QuietHours(enabled = false)).checkInIsQuiet)
    }

    @Test
    fun `the next time the clock reads a time`() {
        assertEquals(at("2026-10-23T21:30"), ReminderTimes.nextAt(time("21:30"), at("2026-10-23T15:05"), london))
        assertEquals(at("2026-10-24T21:30"), ReminderTimes.nextAt(time("21:30"), at("2026-10-23T21:30"), london))
        assertEquals(at("2026-10-24T21:30"), ReminderTimes.nextAt(time("21:30"), at("2026-10-23T22:00"), london))
    }

    @Test
    fun `a time the clocks skip comes when they jump`() {
        // The clocks go forward at 01:00 GMT on Sunday 28 March 2027, straight to 02:00 BST.
        val spring = ReminderTimes.nextAt(time("01:30"), at("2027-03-28T00:30"), london)
        assertEquals(LocalDateTime.parse("2027-03-28T02:30"), LocalDateTime.ofInstant(spring, london))
    }
}
