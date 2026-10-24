package app.thelasthit.nrt.reminders

import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.reminders.QuietHours
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.setUp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

/** The reminders' workers, each run as WorkManager would run it, against the app's data. */
@RunWith(RobolectricTestRunner::class)
class ReminderWorkersTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        app.setUp()
        app.startWorkManager()
        app.allowNotifications()
    }

    @After
    fun stop() {
        // This test's app stops listening, so a nudge from its logs can't land in the next test.
        app.appScope.cancel()
    }

    private inline fun <reified W : CoroutineWorker> run(): ListenableWorker.Result =
        runBlocking { TestListenableWorkerBuilder<W>(app).build().doWork() }

    private fun store() = app.graph.reminders

    // The evening check-in.

    @Test
    fun theCheckInSumsTheDayWithLogAndAllGood() {
        app.chew("2026-10-23T08:10", pieces = 2)
        app.setTime("2026-10-23T21:30")
        assertEquals(ListenableWorker.Result.success(), run<CheckInWorker>())

        val notice = present(app.notification(Notice.ID_CHECK_IN))
        assertEquals("Evening check-in", notice.title)
        assertEquals(nbsp("Today 8 mg. Anything missing?"), notice.text)
        assertEquals(listOf("Log", "All good"), notice.actionLabels)
        // Tomorrow's is booked.
        assertEquals(WorkInfo.State.ENQUEUED, app.work(ReminderScheduler.CHECK_IN)?.state)
    }

    @Test
    fun theCheckInComesOnceADay() {
        app.setTime("2026-10-23T21:30")
        run<CheckInWorker>()
        assertEquals("Nothing logged today. Anything missing?", app.notification(Notice.ID_CHECK_IN)?.text)
        app.clearNotifications()
        app.setTime("2026-10-23T21:40")
        run<CheckInWorker>()
        assertNull(app.notification(Notice.ID_CHECK_IN))
    }

    @Test
    fun theCheckInHasTheGuideWhileAPlanRuns() {
        app.startGumPlan()
        app.setTime("2026-10-24T21:30")
        app.chew("2026-10-24T09:00", pieces = 3)
        run<CheckInWorker>()
        assertEquals(nbsp("Today 12 mg, guide 24 mg. Anything missing?"), app.notification(Notice.ID_CHECK_IN)?.text)
    }

    @Test
    fun aCheckInWokenHoursLateWaitsForTomorrow() {
        app.setTime("2026-10-23T15:05")
        run<CheckInWorker>()
        assertNull(app.notification(Notice.ID_CHECK_IN))
    }

    @Test
    fun aCheckInInQuietHoursDoesNotShow() {
        runBlocking { store().setCheckInTime(LocalTime.of(22, 30)) }
        app.setTime("2026-10-23T22:30")
        run<CheckInWorker>()
        assertNull(app.notification(Notice.ID_CHECK_IN))
        // Without quiet hours, it does.
        runBlocking { store().setQuietHours(QuietHours(enabled = false)) }
        run<CheckInWorker>()
        assertNotNull(app.notification(Notice.ID_CHECK_IN))
    }

    @Test
    fun aCheckInSwitchedOffNeitherShowsNorBooks() {
        runBlocking { store().setOn(Reminder.CHECK_IN, false) }
        app.setTime("2026-10-23T21:30")
        run<CheckInWorker>()
        assertNull(app.notification(Notice.ID_CHECK_IN))
        assertTrue(app.work(ReminderScheduler.CHECK_IN)?.state.let { it == null || it == WorkInfo.State.CANCELLED })
    }

    @Test
    fun nothingShowsWithoutThePermission() {
        app.allowNotifications(false)
        app.setTime("2026-10-23T21:30")
        run<CheckInWorker>()
        assertNull(app.notification(Notice.ID_CHECK_IN))
    }

    // Forgot to log.

    private fun usualMornings() {
        for (day in 19..22) app.chew("2026-10-${day}T08:00")
    }

    @Test
    fun forgotToLogIsOffByDefault() {
        usualMornings()
        app.setTime("2026-10-23T10:30")
        run<ForgotToLogWorker>()
        assertNull(app.notification(Notice.ID_FORGOT))
    }

    @Test
    fun forgotToLogComesTwoHoursAfterTheUsualFirstDose() {
        runBlocking { store().setOn(Reminder.FORGOT_TO_LOG, true) }
        usualMornings()
        app.setTime("2026-10-23T09:59")
        run<ForgotToLogWorker>()
        assertNull(app.notification(Notice.ID_FORGOT))

        app.setTime("2026-10-23T10:05")
        run<ForgotToLogWorker>()
        val notice = present(app.notification(Notice.ID_FORGOT))
        assertEquals("Nothing logged yet today", notice.title)
        assertEquals("Forgot one?", notice.text)
        assertEquals(listOf("Log"), notice.actionLabels)
        assertEquals(WorkInfo.State.ENQUEUED, app.work(ReminderScheduler.FORGOT)?.state)
    }

    @Test
    fun forgotToLogStaysQuietOnceSomethingIsLogged() {
        runBlocking { store().setOn(Reminder.FORGOT_TO_LOG, true) }
        usualMornings()
        app.chew("2026-10-23T08:30")
        app.setTime("2026-10-23T10:30")
        run<ForgotToLogWorker>()
        assertNull(app.notification(Notice.ID_FORGOT))
    }

    @Test
    fun aDoseLoggedTakesForgotToLogAway() {
        runBlocking { store().setOn(Reminder.FORGOT_TO_LOG, true) }
        usualMornings()
        app.setTime("2026-10-23T10:30")
        run<ForgotToLogWorker>()
        assertNotNull(app.notification(Notice.ID_FORGOT))
        app.chew("2026-10-23T10:29")
        val end = System.currentTimeMillis() + 5_000
        while (app.notification(Notice.ID_FORGOT) != null && System.currentTimeMillis() < end) Thread.sleep(20)
        assertNull(app.notification(Notice.ID_FORGOT))
    }

    // Weekly results.

    @Test
    fun aWeekMetComesWithItsHit() {
        app.startGumPlan()
        app.setTime("2026-10-26T12:00")
        app.chew("2026-10-26T09:00", pieces = 20)
        app.setTime("2026-11-01T09:00")
        run<ResultWorker>()
        val notice = present(app.notification(Notice.ID_RESULTS + 1))
        assertEquals("Week 1 met", notice.title)
        assertEquals("1 hit ready for Gary.", notice.text)
        // Next week's result is booked, and this one isn't told again.
        assertEquals(WorkInfo.State.ENQUEUED, app.work(ReminderScheduler.RESULTS)?.state)
        app.clearNotifications()
        run<ResultWorker>()
        assertNull(app.notification(Notice.ID_RESULTS + 1))
    }

    @Test
    fun aWeekMissedIsToldKindly() {
        app.startGumPlan()
        app.setTime("2026-10-26T12:00")
        app.chew("2026-10-26T09:00", pieces = 45)
        app.setTime("2026-11-01T09:00")
        run<ResultWorker>()
        val notice = present(app.notification(Notice.ID_RESULTS + 1))
        assertEquals("Week 1 missed", notice.title)
        assertEquals("Missed this one. Meet next week and you get it back.", notice.text)
    }

    @Test
    fun resultsWhileTheReminderIsOffAreNotToldLater() {
        runBlocking { store().setOn(Reminder.WEEKLY_RESULT, false) }
        app.startGumPlan()
        app.setTime("2026-11-01T09:00")
        run<ResultWorker>()
        assertNull(app.notification(Notice.ID_RESULTS + 1))
        runBlocking { store().setOn(Reminder.WEEKLY_RESULT, true) }
        run<ResultWorker>()
        assertNull(app.notification(Notice.ID_RESULTS + 1))
    }

    @Test
    fun aResultInQuietHoursWaitsForTheMorning() {
        app.startGumPlan()
        app.setTime("2026-11-01T04:05")
        // The day start is 04:00, inside quiet hours, so the result is held until 07:00.
        run<ResultWorker>()
        assertNull(app.notification(Notice.ID_RESULTS + 1))
        val held = present(app.work(ReminderScheduler.held(Notice.ID_RESULTS + 1)))
        assertEquals(WorkInfo.State.ENQUEUED, held.state)

        app.setTime("2026-11-01T07:00")
        WorkManagerTestInitHelper.getTestDriver(app)!!.setInitialDelayMet(held.id)
        // A CoroutineWorker runs off the test thread, so this waits for it.
        assertEquals("Week 1 met", app.awaitNotification(Notice.ID_RESULTS + 1)?.title)
    }

    // Booking.

    @Test
    fun reschedulingBooksOnlyWhatIsOn() {
        app.startGumPlan()
        runBlocking { app.reminders.reschedule() }
        assertEquals(WorkInfo.State.ENQUEUED, app.work(ReminderScheduler.CHECK_IN)?.state)
        assertEquals(WorkInfo.State.ENQUEUED, app.work(ReminderScheduler.RESULTS)?.state)
        assertEquals(WorkInfo.State.ENQUEUED, app.work(ReminderScheduler.WIDGET_DAY)?.state)
        assertNull(app.work(ReminderScheduler.FORGOT))
    }

    // Achievements, for the game layer.

    @Test
    fun aBadgeIsToldByName() {
        app.setTime("2026-10-23T15:05")
        runBlocking { app.reminders.achievementsUnlocked(listOf("First week met"), emptyList()) }
        val notice = present(app.notification(Notice.ID_ACHIEVEMENTS))
        assertEquals("First week met", notice.title)
        assertEquals("New badge. It's on the trophy shelf in the lair.", notice.text)
    }

    @Test
    fun badgesAndGearTogether() {
        app.setTime("2026-10-23T15:05")
        runBlocking { app.reminders.achievementsUnlocked(listOf("First week met", "Logged on 7 days"), listOf("iron sword")) }
        val notice = present(app.notification(Notice.ID_ACHIEVEMENTS))
        assertEquals("2 new badges", notice.title)
        assertEquals("First week met and Logged on 7 days. They're on the trophy shelf in the lair. New gear too: iron sword.", notice.text)
    }

    @Test
    fun manyBadgesAtOnceAreOneSummary() {
        app.setTime("2026-10-23T15:05")
        runBlocking { app.reminders.achievementsUnlocked(listOf("A", "B", "C", "D", "E"), emptyList()) }
        val notice = present(app.notification(Notice.ID_ACHIEVEMENTS))
        assertEquals("5 new badges", notice.title)
        assertEquals("They're on the trophy shelf in the lair.", notice.text)
    }

    @Test
    fun gearAlone() {
        app.setTime("2026-10-23T15:05")
        runBlocking { app.reminders.achievementsUnlocked(emptyList(), listOf("iron sword")) }
        assertEquals("New gear: iron sword", app.notification(Notice.ID_ACHIEVEMENTS)?.title)
    }

    private fun <T> present(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    private fun nbsp(text: String) = text.replace(" mg", "${Char(0xA0)}mg")
}
