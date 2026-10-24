package app.thelasthit.nrt.reminders

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.domain.advice.DelayRun
import app.thelasthit.nrt.ui.advice.VibratorBuzzer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** The delay timer's end as a notice: booked as it starts, taken back when it's stopped, and told once. */
@RunWith(RobolectricTestRunner::class)
class DelayNoticesTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private var onScreen = false
    private val notices = DelayNotices(app, onScreen = { onScreen })

    /** A run per test, by its start, since an end is told of once for as long as the app runs. */
    private fun run(start: String = "15:00:00", minutes: Int = 5) = DelayRun(london("2026-10-23T$start"), minutes)

    private fun delayNotice(): Notification? = app.notification(Notice.ID_DELAY)

    @Before
    fun setUp() {
        app.allowNotifications()
        NoticePoster(app).createChannels()
    }

    @Test
    fun `starting books the end, and stopping takes it back`() {
        val run = run("10:00:00", minutes = 7)
        notices.book(run)
        val booked = checkNotNull(alarms.nextScheduledAlarm)
        assertEquals(london("2026-10-23T10:07:00").toEpochMilli(), booked.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, booked.type)

        notices.clear()
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun `the booking puts up the notice when the app isn't on screen`() {
        val run = run("11:00:00")
        notices.book(run)
        notices.bookingCame(run)

        val notice = checkNotNull(delayNotice())
        assertEquals("5\u00A0minutes done", notice.title)
        assertEquals("If the urge is still there, give it another 5\u00A0minutes. If it's gone, you rode it out.", notice.text)
        assertEquals(NoticeChannel.DELAY.id, notice.channelId)
        assertEquals(Notification.CATEGORY_ALARM, notice.category)

        // Told once: the app finding the end afterwards neither buzzes nor tells again.
        app.clearNotifications()
        assertFalse(notices.ended(run, run.endsAt.plusSeconds(2)))
        assertNull(delayNotice())
    }

    @Test
    fun `the app on screen tells of the end itself`() {
        onScreen = true
        val run = run("12:00:00")
        notices.book(run)
        notices.bookingCame(run)
        assertNull(delayNotice())

        assertTrue(notices.ended(run, run.endsAt))
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun `the app seeing the end behind another app puts up the notice at once`() {
        val run = run("13:00:00")
        notices.book(run)

        assertFalse(notices.ended(run, run.endsAt.plusMillis(300)))
        assertNotNull(delayNotice())
        // The booking isn't needed now.
        assertNull(alarms.nextScheduledAlarm)
        notices.bookingCame(run)
        assertEquals(1, app.notifications.allNotifications.count { it.channelId == NoticeChannel.DELAY.id })
    }

    @Test
    fun `found long after the end, the app doesn't tell of it out of nowhere`() {
        val run = run("14:00:00")
        assertFalse(notices.ended(run, run.endsAt + Duration.ofMinutes(30)))
        assertNull(delayNotice())
    }

    @Test
    fun `seeing the end frame clears the notice, and a new start clears an old one`() {
        val run = run("14:30:00")
        notices.ended(run, run.endsAt)
        assertNotNull(delayNotice())

        notices.clear()
        assertNull(delayNotice())

        val next = run("14:40:00")
        notices.ended(next, next.endsAt)
        assertNotNull(delayNotice())
        notices.book(run("14:50:00"))
        assertNull(delayNotice())
    }

    @Test
    fun `the channel buzzes as the timer does, without a sound`() {
        val channel = (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(NoticeChannel.DELAY.id)
        assertNull(channel.sound)
        assertTrue(channel.shouldVibrate())
        assertArrayEquals(VibratorBuzzer.ALARM, channel.vibrationPattern)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `the receiver reads the booking, and leaves the end to the app while it's on screen`() {
        val manager = shadowOf(app.getSystemService(ActivityManager::class.java))
        fun ranked(importance: Int) = manager.setProcesses(
            listOf(ActivityManager.RunningAppProcessInfo().also { it.processName = app.packageName; it.importance = importance }),
        )
        ranked(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        val shown = run("16:00:00")
        DelayEndReceiver().onReceive(app, intentOf(shown))
        assertNull(delayNotice())

        ranked(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
        val closed = run("16:10:00", minutes = 10)
        DelayEndReceiver().onReceive(app, intentOf(closed))
        assertEquals("10\u00A0minutes done", delayNotice()?.title)
    }

    private fun intentOf(run: DelayRun) = Intent(app, DelayEndReceiver::class.java)
        .putExtra(DelayNotices.EXTRA_STARTED_AT, run.startedAt.toEpochMilli())
        .putExtra(DelayNotices.EXTRA_MINUTES, run.minutes)
}
