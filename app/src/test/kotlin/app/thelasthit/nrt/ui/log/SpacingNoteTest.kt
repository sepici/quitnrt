package app.thelasthit.nrt.ui.log

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.PATCH_14
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.log.QuickTime
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.plan.seedGoalWeeks
import app.thelasthit.nrt.ui.plan.setUpForPlan
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The log sheet's spacing note (PLAN §6.3): with a gap goal on, a dose sooner than the goal after
 * the last one gets a calm note, and logs all the same. In [seedGoalWeeks] the goal is 90 minutes,
 * and the last dose today was at 14:25, 40 minutes before the clock's 15:05.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpacingNoteTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val lastDose = "Your gap goal is 1 hour 30 minutes. The last dose was 40 minutes ago."

    /** The sheet open on a new dose of [productId], which shows "Saved" under it once a dose is saved. */
    private fun showSheet(productId: String = GUM_2): LogSheetViewModel {
        val viewModel = LogSheetViewModel(app.graph)
        viewModel.openNew(productId)
        compose.setContent {
            LastHitTheme {
                var saved by remember { mutableStateOf(false) }
                LogSheet(viewModel, onDismiss = {}, onSaved = { saved = true })
                if (saved) Text("Saved")
            }
        }
        return viewModel
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a dose sooner than the gap goal gets a note, and logs all the same`() {
        app.setUpForPlan()
        app.seedGoalWeeks()
        showSheet()

        compose.waitUntilAtLeastOneExists(hasText(lastDose), timeoutMillis = 10_000)
        // It comes in as a time is picked, so a screen reader says it then.
        compose.onNode(hasText(lastDose)).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNode(hasText("Log") and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Saved"), timeoutMillis = 10_000)
        val today = runBlocking { app.graph.entries.observeDay(LocalDate.parse("2026-11-18")).first().entries }
        assertEquals(5, today.size)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a dose picked for earlier is held against the dose nearest it`() {
        app.setUpForPlan()
        app.seedGoalWeeks()
        val viewModel = showSheet()
        compose.waitUntilAtLeastOneExists(hasText(lastDose), timeoutMillis = 10_000)

        // 13:05: 25 minutes after the spray at 12:40, and an hour and 20 minutes before 14:25.
        viewModel.pickTime(TimeChoice.Quick(QuickTime.TWO_HOURS_AGO))
        compose.waitUntilAtLeastOneExists(
            hasText("Your gap goal is 1 hour 30 minutes. This dose is 25 minutes after the one at 12:40."),
            timeoutMillis = 10_000,
        )
        // 14:05 is 20 minutes before 14:25.
        viewModel.pickTime(TimeChoice.Quick(QuickTime.HOUR_AGO))
        compose.waitUntilAtLeastOneExists(
            hasText("Your gap goal is 1 hour 30 minutes. This dose is 20 minutes before the one at 14:25."),
            timeoutMillis = 10_000,
        )
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a patch gets no note, and nor does a dose once the goal is off`() {
        app.setUpForPlan()
        app.seedGoalWeeks()
        val viewModel = showSheet(PATCH_14)
        compose.waitUntilAtLeastOneExists(hasText("patch", substring = true), timeoutMillis = 10_000)
        compose.waitForIdle()
        compose.onNode(hasText(lastDose)).assertDoesNotExist()

        viewModel.pickProduct(GUM_2)
        compose.waitUntilAtLeastOneExists(hasText(lastDose), timeoutMillis = 10_000)

        runBlocking { app.graph.programmes.updateGoals { week -> week.goals.copy(minGapMinutes = null) } }
        compose.waitUntilDoesNotExist(hasText(lastDose), timeoutMillis = 10_000)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `with no plan there's no note`() {
        app.setUp()
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, at = app.clock.now.minusSeconds(600))) }
        showSheet()
        compose.waitUntilAtLeastOneExists(hasText("Planned or craving"), timeoutMillis = 10_000)
        compose.waitForIdle()
        compose.onNode(hasText("Your gap goal", substring = true)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun sheet() = captureSheet()

    @Test
    @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun sheetLarge() = captureSheet()

    private fun captureSheet() {
        app.setUpForPlan()
        app.seedGoalWeeks()
        // The quick log's first product, 4 mg gum, as a tap on "Log a dose" opens it.
        val viewModel = LogSheetViewModel(app.graph)
        viewModel.openNew()
        compose.captureLooks("log-sheet-gap-note", ready = hasText(lastDose)) {
            LogSheet(viewModel, onDismiss = {}, onSaved = {})
        }
    }
}
