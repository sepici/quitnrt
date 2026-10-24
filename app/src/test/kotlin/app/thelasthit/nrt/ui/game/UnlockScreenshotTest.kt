package app.thelasthit.nrt.ui.game

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.UnlockNews
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The unlock toast, the summary card and the "new gear" card, each over Today. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnlockScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = PHONE)
    fun toast() = captureToast()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun toastLarge() = captureToast()

    @Test @Config(qualifiers = PHONE)
    fun summary() = captureSummary()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun summaryLarge() = captureSummary()

    /** At 360 dp, where the row of badges and the buttons have least room. */
    @Test @Config(qualifiers = NARROW_PHONE)
    fun summaryNarrow() = captureSummary("unlock-summary-narrow")

    @Test @Config(qualifiers = NARROW_PHONE, fontScale = LARGE_FONT)
    fun summaryNarrowLarge() = captureSummary("unlock-summary-narrow")

    @Test @Config(qualifiers = PHONE)
    fun gear() = captureGear()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun gearLarge() = captureGear()

    /** The week in the Today mockup, logged now: three badges, so three toasts, and this is the first. */
    private fun captureToast() {
        app.setUp()
        app.seedWeek()
        app.checkUnlocks()
        compose.captureLooks("unlock-toast", ready = hasText("First entry"), before = { todayIsIn() }) { AppShell() }
    }

    /** Two months of history when the game first looks: one card for every badge it brings. */
    private fun captureSummary(name: String = "unlock-summary") {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        val found = app.checkUnlocks()
        check(found.isSummary)
        compose.captureLooks(name, ready = hasText("See the shelf"), before = { todayIsIn() }) { AppShell() }
    }

    /** Week 1 met: its badge's toast has gone, and the iron sword's card is in. */
    private fun captureGear() {
        app.setUp()
        app.meetFirstWeek()
        app.checkUnlocks()
        app.graph.unlocks.shown(UnlockNews.Badge(Achievement.FIRST_WEEK_MET))
        compose.captureLooks("unlock-gear", ready = hasText("Iron sword"), before = { todayIsIn() }) { AppShell() }
    }

    /** Today under the card, so the capture shows what the card sits over, not a page still loading. */
    private fun ComposeContentTestRule.todayIsIn() {
        waitUntil(10_000) { onAllNodes(hasText("You've had")).fetchSemanticsNodes().isNotEmpty() }
    }
}
