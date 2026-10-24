package app.thelasthit.nrt.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.data.repo.NewDose
import app.thelasthit.nrt.data.settings.ReminderStore
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.reminders.QuietHours
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderStoreTest {

    private lateinit var data: TestData
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "reminders-${System.nanoTime()}.preferences_pb")
    private val store = ReminderStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))

    @Before
    fun setUp() {
        data = TestData()
    }

    @After
    fun tearDown() {
        data.close()
        scope.cancel()
        file.delete()
    }

    @Test
    fun defaultsFollowThePlan() = runTest {
        assertEquals(ReminderSettings(), store.currentReminders())
        assertFalse(store.currentReminders().isOn(Reminder.FORGOT_TO_LOG))
        assertFalse(store.currentQuickLog().tileOpensSheet)
        assertFalse(store.currentQuickLog().widgetAsksForTags)
        val marks = store.currentMarks()
        assertFalse(marks.permissionAsked)
        assertNull(marks.resultsNotifiedUpTo)
        assertNull(marks.checkInSentOn)
    }

    @Test
    fun changesAreKept() = runTest {
        store.setOn(Reminder.FORGOT_TO_LOG, true)
        store.setOn(Reminder.ALLOWANCE, false)
        store.setCheckInTime(LocalTime.of(20, 45))
        store.setQuietHours(QuietHours(enabled = false, start = LocalTime.of(23, 0), end = LocalTime.of(6, 30)))
        store.setTileOpensSheet(true)
        store.setWidgetAsksForTags(true)
        val reminders = store.currentReminders()
        assertTrue(reminders.isOn(Reminder.FORGOT_TO_LOG))
        assertFalse(reminders.isOn(Reminder.ALLOWANCE))
        assertEquals(LocalTime.of(20, 45), reminders.checkInTime)
        assertEquals(QuietHours(enabled = false, start = LocalTime.of(23, 0), end = LocalTime.of(6, 30)), reminders.quietHours)
        assertTrue(store.currentQuickLog().tileOpensSheet)
        assertTrue(store.currentQuickLog().widgetAsksForTags)
    }

    @Test
    fun marksNeverGoBackwards() = runTest {
        val later = london("2026-11-01T04:00")
        store.markResultsNotified(later)
        store.markResultsNotified(london("2026-10-25T04:00"))
        assertEquals(later, store.currentMarks().resultsNotifiedUpTo)
        store.markCheckInSent(LocalDate.parse("2026-10-23"))
        store.markForgotReminded(LocalDate.parse("2026-10-22"))
        store.markPermissionAsked()
        val marks = store.currentMarks()
        assertEquals(LocalDate.parse("2026-10-23"), marks.checkInSentOn)
        assertEquals(LocalDate.parse("2026-10-22"), marks.forgotRemindedOn)
        assertTrue(marks.permissionAsked)
    }

    @Test
    fun eachNewDoseIsToldOfWhicheverWayItCameIn() = runTest {
        data.products.addFromCatalogue(GUM_4)
        // Undispatched, so it's listening before anything is logged.
        val logged = async(start = CoroutineStart.UNDISPATCHED) { data.entries.logged.take(2).toList() }
        val first = (data.entries.log(LogRequest(GUM_4, source = EntrySource.WIDGET)) as LogResult.Saved).entry
        // An edit isn't a new dose.
        data.entries.edit(first.id, LogRequest(GUM_4, note = "after lunch"))
        val second = (data.entries.log(LogRequest(GUM_4, source = EntrySource.TILE, allowDuplicate = true)) as LogResult.Saved).entry
        val told: List<NewDose> = logged.await()
        assertEquals(listOf(first.id, second.id), told.map { it.entry.id })
        assertEquals(listOf(EntrySource.WIDGET, EntrySource.TILE), told.map { it.entry.source })
        // Each comes with the doses that stood once it was saved, not those logged after it.
        assertEquals(setOf(first.id), told[0].standing.map { it.id }.toSet())
        assertEquals(setOf(first.id, second.id), told[1].standing.map { it.id }.toSet())
    }
}
