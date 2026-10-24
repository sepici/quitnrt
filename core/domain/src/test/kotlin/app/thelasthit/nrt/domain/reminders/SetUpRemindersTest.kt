package app.thelasthit.nrt.domain.reminders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class SetUpRemindersTest {

    @Test
    fun `set-up offers the four reminders on by default, in Settings' order, and not Forgot to log`() {
        assertEquals(
            listOf(Reminder.ALLOWANCE, Reminder.CHECK_IN, Reminder.WEEKLY_RESULT, Reminder.ACHIEVEMENTS),
            SetUpReminders.offered,
        )
        assertEquals(ReminderSettings.DEFAULT_ON, SetUpReminders.offered.toSet())
        assertFalse(Reminder.FORGOT_TO_LOG in SetUpReminders.offered)
    }

    @Test
    fun `it starts with all four on`() {
        assertEquals(SetUpReminders.offered.toSet(), SetUpReminders.initial)
    }

    @Test
    fun `the choices switch only what set-up offers, and keep the rest of the settings`() {
        val stored = ReminderSettings(
            on = setOf(Reminder.ALLOWANCE, Reminder.FORGOT_TO_LOG),
            checkInTime = LocalTime.of(20, 0),
            quietHours = QuietHours(start = LocalTime.of(23, 0), end = LocalTime.of(6, 30)),
        )

        val applied = SetUpReminders.applied(stored, setOf(Reminder.CHECK_IN, Reminder.ACHIEVEMENTS))

        assertEquals(setOf(Reminder.CHECK_IN, Reminder.ACHIEVEMENTS, Reminder.FORGOT_TO_LOG), applied.on)
        assertEquals(LocalTime.of(20, 0), applied.checkInTime)
        assertEquals(stored.quietHours, applied.quietHours)
    }

    @Test
    fun `a choice of a reminder set-up doesn't offer changes nothing`() {
        val applied = SetUpReminders.applied(ReminderSettings(), SetUpReminders.initial + Reminder.FORGOT_TO_LOG)

        assertEquals(ReminderSettings.DEFAULT_ON, applied.on)
    }

    @Test
    fun `notifications are needed while any one is on`() {
        assertTrue(SetUpReminders.needsNotifications(SetUpReminders.initial))
        assertTrue(SetUpReminders.needsNotifications(setOf(Reminder.ACHIEVEMENTS)))
        assertFalse(SetUpReminders.needsNotifications(emptySet()))
        assertFalse(SetUpReminders.needsNotifications(setOf(Reminder.FORGOT_TO_LOG)))
    }
}
