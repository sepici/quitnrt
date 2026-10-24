package app.thelasthit.nrt.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.data.settings.ReminderStore
import app.thelasthit.nrt.data.settings.TimerSettings
import app.thelasthit.nrt.domain.advice.ShowingTip
import app.thelasthit.nrt.domain.advice.Slip
import app.thelasthit.nrt.domain.advice.TipCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** The timers' buzz and Today's tip after logging, kept with this phone's other settings, out of backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneNotesStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "phone-${System.nanoTime()}.preferences_pb")
    private val store = ReminderStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    private val today = LocalDate.parse("2026-10-23")

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun breathingBuzzIsOnUntilSwitchedOff() = runTest {
        assertEquals(TimerSettings(breathingBuzz = true), store.timers.first())
        store.setBreathingBuzz(false)
        assertFalse(store.timers.first().breathingBuzz)
    }

    @Test
    fun theTipIsKeptAsItWas() = runTest {
        assertNull(store.adviceTip.first())

        val stressed = TipCard(today, setOf("stressed"), ShowingTip("breathing_timer", "stressed", slip = null, entryId = "dose-1"))
        store.updateAdviceTip { stressed }
        assertEquals(stressed, store.adviceTip.first())

        val slip = TipCard(today, setOf("stressed", TipCard.SLIP), ShowingTip("after_a_slip", tag = null, slip = Slip.OVER_GUIDE, entryId = "dose-2"))
        store.updateAdviceTip { current ->
            assertEquals(stressed, current)
            slip
        }
        assertEquals(slip, store.adviceTip.first())

        store.updateAdviceTip { it?.copy(showing = null) }
        assertEquals(TipCard(today, setOf("stressed", TipCard.SLIP), showing = null), store.adviceTip.first())

        store.updateAdviceTip { null }
        assertNull(store.adviceTip.first())
    }
}
