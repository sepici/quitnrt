package app.thelasthit.nrt.reminders

import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.reminders.Delivery
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.setUp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The allowance nudge fires from the log itself, whichever way the dose came in. The gum plan's
 * week 1 runs from Saturday 24 October: 24 mg a day, 168 mg for the week, so 80% is 134.4 mg.
 */
@RunWith(RobolectricTestRunner::class)
class AllowanceNudgesTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val nbsp = Char(0xA0)

    @Before
    fun setUp() {
        app.setUp()
        app.allowNotifications()
        app.startGumPlan()
        app.setTime("2026-10-26T12:00")
    }

    private fun present(id: Int, title: String? = null) =
        app.awaitNotification(id) { title == null || it.title == title }.also { assertNotNull(it) }!!

    @Test
    fun aLogThatTakesTheWeekPast80Percent() {
        // 132 mg on Saturday, under 80%, then 4 mg today.
        app.chew("2026-10-24T09:00", pieces = 33)
        app.chew("2026-10-26T09:00")
        val notice = present(Notice.ID_ALLOWANCE)
        assertEquals("80% of this week's allowance", notice.title)
        assertEquals("136 of 168${nbsp}mg so far, with 32${nbsp}mg left for the rest of the week.", notice.text)
    }

    @Test
    fun aLogThatTakesTodayPastTheGuide() {
        app.chew("2026-10-26T09:00", pieces = 6)
        app.chew("2026-10-26T11:00")
        val notice = present(Notice.ID_ALLOWANCE)
        assertEquals("Past today's guide", notice.title)
        assertEquals("Today 28${nbsp}mg, guide 24${nbsp}mg. The week has 140${nbsp}mg left.", notice.text)
    }

    @Test
    fun aLogThatTakesTheWeekPastItsAllowance() {
        // 168 mg on Saturday, which is past 80% but within the allowance, then 4 mg today.
        app.chew("2026-10-24T09:00", pieces = 42)
        app.chew("2026-10-26T09:00")
        val notice = present(Notice.ID_ALLOWANCE, title = "This week is past its allowance")
        assertEquals("172 of 168${nbsp}mg so far. Meet next week and you get this one back.", notice.text)
    }

    @Test
    fun theWidgetsLogsNudgeToo() {
        app.chew("2026-10-24T09:00", pieces = 33)
        val result = runBlocking { app.quickLog.logFromWidget(GUM_4) }
        assertEquals(true, result is LogResult.Saved)
        assertEquals("80% of this week's allowance", present(Notice.ID_ALLOWANCE).title)
    }

    // What a log brings is checked straight from it here, since a nudge from another test's
    // logs could still be on its way to the shade.

    @Test
    fun withinTheGuideNothingComes() {
        val entry = app.chew("2026-10-26T09:00", pieces = 6)
        assertNull(runBlocking { app.reminders.afterLog(entry) })
    }

    @Test
    fun inQuietHoursTheNudgeIsDropped() {
        app.setTime("2026-10-26T23:00")
        app.chew("2026-10-26T09:00", pieces = 6)
        val over = app.chew("2026-10-26T22:50")
        assertEquals(Delivery.Dropped, runBlocking { app.reminders.afterLog(over) })
    }

    @Test
    fun switchedOffNothingComes() {
        runBlocking { app.graph.reminders.setOn(Reminder.ALLOWANCE, false) }
        app.chew("2026-10-26T09:00", pieces = 6)
        val over = app.chew("2026-10-26T11:00")
        assertEquals(Delivery.Off, runBlocking { app.reminders.afterLog(over) })
    }

    @After
    fun stop() {
        // This test's app stops listening, so its nudges can't land in the next test.
        app.appScope.cancel()
    }
}
