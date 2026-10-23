package app.thelasthit.nrt.ui.log

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.log.QuickTime
import app.thelasthit.nrt.setUp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LogSheetViewModelTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var viewModel: LogSheetViewModel
    private var latest: LogSheetState? = null

    @Before
    fun setUpViewModel() {
        app.setUp()
        viewModel = LogSheetViewModel(app.graph)
        scope.launch { viewModel.state.collect { latest = it } }
    }

    @After
    fun tearDown() = scope.cancel()

    /** The sheet's state, once [check] passes, collected as the sheet itself does. */
    private fun awaitState(check: (LogSheetState) -> Boolean): LogSheetState {
        idleUntil { latest?.let(check) == true }
        return requireNotNull(latest)
    }

    private fun idleUntil(check: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (check()) return
            Thread.sleep(10)
        }
        error("Condition never met")
    }

    private fun entriesOn(date: String) = runBlocking { app.graph.entries.observeDay(LocalDate.parse(date)).first().entries }

    @Test
    fun `a product picked on opening is the one saved`() {
        viewModel.openNew(QUICKMIST)
        assertEquals(QUICKMIST, awaitState { it.product != null }.form.productId)

        var saved: SavedDose? = null
        viewModel.save { saved = it }
        idleUntil { saved != null }

        assertEquals(QUICKMIST, entriesOn("2026-10-23").single().productId)
    }

    @Test
    fun `with no product picked the first quick-log product is used`() {
        viewModel.openNew()
        assertEquals(GUM_2, awaitState { it.product != null }.form.productId)
    }

    @Test
    fun `a picked time in the future is flagged at once`() {
        viewModel.openNew()
        awaitState { it.product != null }
        viewModel.pickTime(TimeChoice.At(TestApp.NOW.plus(Duration.ofHours(2))))
        assertTrue(awaitState { true }.form.timeInFuture)

        viewModel.pickTime(TimeChoice.Quick(QuickTime.HOUR_AGO))
        assertFalse(awaitState { true }.form.timeInFuture)
        assertNotNull(awaitState { true }.product)
    }
}
