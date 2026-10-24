package app.thelasthit.nrt.domain.reminders

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.PATCH
import app.thelasthit.nrt.domain.programme.ZONE
import app.thelasthit.nrt.domain.programme.date
import app.thelasthit.nrt.domain.programme.dose
import app.thelasthit.nrt.domain.time.DayBoundary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

class DailyRemindersTest {

    private val boundary = DayBoundary()
    private val friday = date("2026-10-23")

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(ZONE).toInstant()

    private fun time(text: String): LocalTime = LocalTime.parse(text)

    /** A first dose at [time] on each of the [days] days before Friday, and a later one too. */
    private fun mornings(time: String, days: Int = 5): List<Entry> = (1..days).flatMap { back ->
        val day = friday.minusDays(back.toLong())
        listOf(dose("${day}T$time", Dose.mg(2)), dose("${day}T19:00", Dose.mg(2)))
    }

    // The evening check-in.

    @Test
    fun `the check-in sums the day and keeps the guide`() {
        val day = listOf(dose("2026-10-23T08:10", Dose.mg(4)), dose("2026-10-23T10:55", Dose.mg(2)))
        assertEquals(CheckIn(Dose.mg(6), 2, Dose.mg(16)), CheckIn.of(day, Dose.mg(16)))
        assertEquals(CheckIn(Dose.ZERO, 0, null), CheckIn.of(emptyList(), null))
    }

    @Test
    fun `the next check-in is tonight's, or tomorrow's once tonight's is sent`() {
        val now = at("2026-10-23T15:05")
        assertEquals(at("2026-10-23T21:30"), CheckIn.nextAt(time("21:30"), now, ZONE, boundary, sentOn = null))
        assertEquals(at("2026-10-23T21:30"), CheckIn.nextAt(time("21:30"), now, ZONE, boundary, sentOn = date("2026-10-22")))
        assertEquals(at("2026-10-24T21:30"), CheckIn.nextAt(time("21:30"), now, ZONE, boundary, sentOn = friday))
        assertEquals(at("2026-10-24T21:30"), CheckIn.nextAt(time("21:30"), at("2026-10-23T21:31"), ZONE, boundary, sentOn = null))
    }

    @Test
    fun `a check-in set after midnight belongs to the evening before`() {
        // 00:30 on Saturday is still Friday with a 04:00 day start, so Friday's sent one moves it a day.
        val next = CheckIn.nextAt(time("00:30"), at("2026-10-23T22:00"), ZONE, boundary, sentOn = friday)
        assertEquals(at("2026-10-25T00:30"), next)
    }

    @Test
    fun `a check-in only goes out near its time`() {
        assertTrue(CheckIn.isDue(time("21:30"), at("2026-10-23T21:30"), ZONE))
        assertTrue(CheckIn.isDue(time("21:30"), at("2026-10-23T22:15"), ZONE))
        assertFalse(CheckIn.isDue(time("21:30"), at("2026-10-23T23:00"), ZONE))
        assertFalse(CheckIn.isDue(time("21:30"), at("2026-10-23T15:05"), ZONE))
        // Just after midnight, for a check-in at 23:45.
        assertTrue(CheckIn.isDue(time("23:45"), at("2026-10-24T00:10"), ZONE))
    }

    // Forgot to log.

    @Test
    fun `the usual first log needs 3 logged days in the last 14`() {
        assertNull(ForgotToLog.usualFirstLog(mornings("08:00", days = 2), friday, boundary.dayStart))
        assertEquals(time("08:00"), ForgotToLog.usualFirstLog(mornings("08:00", days = 3), friday, boundary.dayStart))
        // Days more than 14 back don't count.
        val old = (15..20).map { back -> dose("${friday.minusDays(back.toLong())}T08:00", Dose.mg(2)) }
        assertNull(ForgotToLog.usualFirstLog(old, friday, boundary.dayStart))
    }

    @Test
    fun `the usual first log is the average of each day's first`() {
        val entries = listOf(
            dose("2026-10-20T07:00", Dose.mg(2)),
            dose("2026-10-21T08:00", Dose.mg(2)),
            dose("2026-10-21T09:00", Dose.mg(2)),
            dose("2026-10-22T09:00", Dose.mg(2)),
        )
        assertEquals(time("08:00"), ForgotToLog.usualFirstLog(entries, friday, boundary.dayStart))
    }

    @Test
    fun `today's own logs don't set its usual time`() {
        val entries = mornings("08:00") + dose("2026-10-23T05:00", Dose.mg(2))
        assertEquals(time("08:00"), ForgotToLog.usualFirstLog(entries, friday, boundary.dayStart))
    }

    @Test
    fun `a patch put on each morning is that day's first log`() {
        val entries = (1..3).map { back -> dose("${friday.minusDays(back.toLong())}T07:30", Dose.mg(21), productId = PATCH) }
        assertEquals(time("07:30"), ForgotToLog.usualFirstLog(entries, friday, boundary.dayStart))
    }

    @Test
    fun `a reminder is due 2 hours after the usual first log`() {
        assertEquals(at("2026-10-23T10:00"), ForgotToLog.dueOn(friday, mornings("08:00"), boundary, ZONE))
        assertNull(ForgotToLog.dueOn(friday, emptyList(), boundary, ZONE))
    }

    @Test
    fun `no reminder that would land after the day ends`() {
        // First logs at 02:30, which count towards the evening before, make a due time of 04:30:
        // the next day.
        val late = (0..2).map { back -> dose("${friday.minusDays(back.toLong())}T02:30", Dose.mg(2)) }
        assertEquals(time("02:30"), ForgotToLog.usualFirstLog(late, friday, boundary.dayStart))
        assertNull(ForgotToLog.dueOn(friday, late, boundary, ZONE))
    }

    @Test
    fun `it reminds once it's due, nothing is logged today, and today hasn't had one`() {
        val history = mornings("08:00")
        assertFalse(ForgotToLog.shouldRemind(at("2026-10-23T09:59"), history, boundary, ZONE, remindedOn = null))
        assertTrue(ForgotToLog.shouldRemind(at("2026-10-23T10:00"), history, boundary, ZONE, remindedOn = null))
        assertTrue(ForgotToLog.shouldRemind(at("2026-10-23T15:05"), history, boundary, ZONE, remindedOn = date("2026-10-22")))
        assertFalse(ForgotToLog.shouldRemind(at("2026-10-23T15:05"), history, boundary, ZONE, remindedOn = friday))
        val logged = history + dose("2026-10-23T08:10", Dose.mg(4))
        assertFalse(ForgotToLog.shouldRemind(at("2026-10-23T10:00"), logged, boundary, ZONE, remindedOn = null))
    }

    @Test
    fun `the next look is today's due time, or tomorrow's`() {
        val history = mornings("08:00")
        assertEquals(at("2026-10-23T10:00"), ForgotToLog.nextCheck(at("2026-10-23T06:00"), history, boundary, ZONE, remindedOn = null))
        assertEquals(at("2026-10-24T10:00"), ForgotToLog.nextCheck(at("2026-10-23T15:05"), history, boundary, ZONE, remindedOn = null))
        assertEquals(at("2026-10-24T10:00"), ForgotToLog.nextCheck(at("2026-10-23T06:00"), history, boundary, ZONE, remindedOn = friday))
        assertNull(ForgotToLog.nextCheck(at("2026-10-23T06:00"), emptyList(), boundary, ZONE, remindedOn = null))
    }
}
