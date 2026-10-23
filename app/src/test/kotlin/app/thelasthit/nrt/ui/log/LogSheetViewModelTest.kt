package app.thelasthit.nrt.ui.log

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.QuickTime
import app.thelasthit.nrt.domain.matcher.LogLinkParser
import app.thelasthit.nrt.domain.matcher.LogLinkRequest
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
import org.junit.Assert.assertNull
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

    @Test
    fun `a link's choices wait for a pick, and each brings the link's amount`() {
        val gum2 = requireNotNull(runBlocking { app.graph.products.get(GUM_2) })
        val gum4 = requireNotNull(runBlocking { app.graph.products.get(GUM_4) })
        val at = TestApp.london("2026-10-23T14:30")
        viewModel.openFromLink(
            EntrySource.DEEP_LINK,
            listOf(LogLinkRequest(gum4, 2, at, setOf("stressed")), LogLinkRequest(gum2, 3, at, setOf("stressed"))),
        )

        val choosing = awaitState { it.isChoosing && it.products.isNotEmpty() }
        assertNull(choosing.product)
        assertFalse(choosing.canSave)
        assertEquals(listOf(GUM_4, GUM_2), choosing.products.take(choosing.leadingCount).map { it.id })
        assertEquals(TimeChoice.At(at), choosing.form.time)

        viewModel.pickProduct(GUM_2)
        assertEquals(3, awaitState { it.form.productId == GUM_2 }.form.quantity)
        viewModel.pickProduct(GUM_4)
        assertEquals(2, awaitState { it.form.productId == GUM_4 }.form.quantity)

        var saved: SavedDose? = null
        viewModel.save { saved = it }
        idleUntil { saved != null }

        val entry = entriesOn("2026-10-23").single()
        assertEquals(GUM_4, entry.productId)
        assertEquals(2, entry.quantity)
        assertEquals(at, entry.occurredAt)
        assertEquals(setOf("stressed"), entry.tagKeys)
        assertEquals(EntrySource.DEEP_LINK, entry.source)
    }

    @Test
    fun `the stepper stops where a link does`() {
        viewModel.openNew()
        awaitState { it.product != null }
        repeat(LogLinkParser.MAX_QUANTITY + 5) { viewModel.changeQuantity(1) }
        assertEquals(LogLinkParser.MAX_QUANTITY, awaitState { true }.form.quantity)
    }
}
