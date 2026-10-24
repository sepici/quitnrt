package app.thelasthit.nrt.ui.plan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Taller than [FULL_PAGE], for the plan's longest pages. */
private const val TALL = "en-rGB-w411dp-h2300dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** For the longest pages at the large font. */
private const val TALLER = "en-rGB-w411dp-h3000dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** Every Plan mockup state, light and dark, at normal and large font. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpApp() {
        app.setUp()
    }

    // Before a programme.

    @Test @Config(qualifiers = FULL_PAGE)
    fun start() = tab("plan-start", "Use your last 7 days") { app.seedLastSevenDays() }

    @Test @Config(qualifiers = TALL, fontScale = LARGE_FONT)
    fun startLarge() = start()

    @Test @Config(qualifiers = FULL_PAGE)
    fun baseline() = tab("plan-baseline", "When it ends") { app.seedBaselineBuilding() }

    @Test @Config(qualifiers = TALL, fontScale = LARGE_FONT)
    fun baselineLarge() = baseline()

    @Test @Config(qualifiers = FULL_PAGE)
    fun confirm() {
        app.seedLastSevenDays()
        runBlocking { app.graph.programmes.startBaseline() }
        compose.captureLooks("plan-confirm", ready = hasText("What your logs show")) {
            UsualDayScreen(UsualDayMode.MEASURED, onBack = {}, onKept = {}, onOpenCatalogue = {})
        }
    }

    @Test @Config(qualifiers = TALL, fontScale = LARGE_FONT)
    fun confirmLarge() = confirm()

    @Test @Config(qualifiers = PHONE)
    fun usualDay() {
        app.keepMockupUsualDay()
        compose.captureLooks("plan-usual-day", ready = hasText("Use this usual day")) {
            UsualDayScreen(UsualDayMode.TYPED, onBack = {}, onKept = {}, onOpenCatalogue = {})
        }
    }

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun usualDayLarge() = usualDay()

    @Test @Config(qualifiers = TALL)
    fun build() {
        app.keepMockupUsualDay()
        compose.captureLooks("plan-build", ready = hasText("Week by week")) { BuildScreen(onBack = {}, onStarted = {}) }
    }

    @Test @Config(qualifiers = TALLER, fontScale = LARGE_FONT)
    fun buildLarge() = build()

    @Test @Config(qualifiers = PHONE)
    fun editWeek() = editWeek("plan-edit-week", "8\u00A0pieces of 2\u00A0mg gum and 3\u00A0sprays")

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun editWeekLarge() = editWeek()

    @Test @Config(qualifiers = PHONE)
    fun editWeekLower() = editWeek("plan-edit-week-lower", "1\u00A0piece of 2\u00A0mg gum and 3\u00A0sprays")

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun editWeekLowerLarge() = editWeekLower()

    private fun editWeek(name: String, pick: String) {
        app.keepMockupUsualDay()
        compose.captureLooks(
            name,
            ready = hasText("Week by week"),
            before = {
                openWeek(4)
                pickStep(pick)
            },
        ) { BuildScreen(onBack = {}, onStarted = {}) }
    }

    // During a programme.

    @Test @Config(qualifiers = TALL)
    fun week() = tab("plan-week", "Schedule") { app.seedWeekFour() }

    @Test @Config(qualifiers = TALLER, fontScale = LARGE_FONT)
    fun weekLarge() = week()

    @Test @Config(qualifiers = TALL)
    fun weekPending() = tab("plan-week-pending", "Schedule") { app.seedWeekFivePending() }

    @Test @Config(qualifiers = TALLER, fontScale = LARGE_FONT)
    fun weekPendingLarge() = weekPending()

    @Test @Config(qualifiers = PHONE)
    fun missed() = tab("plan-missed", "Missed this one") { app.seedWeekSixMissed() }

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun missedLarge() = missed()

    @Test @Config(qualifiers = FULL_PAGE)
    fun overtime() = tab("plan-overtime", "Schedule") { app.seedOvertime() }

    @Test @Config(qualifiers = TALL, fontScale = LARGE_FONT)
    fun overtimeLarge() = overtime()

    @Test @Config(qualifiers = FULL_PAGE)
    fun done() = tab("plan-done", "Programme done") { app.seedDone() }

    @Test @Config(qualifiers = TALL, fontScale = LARGE_FONT)
    fun doneLarge() = done()

    @Test @Config(qualifiers = PHONE)
    fun goals() {
        app.seedGoals()
        compose.captureLooks("plan-goals", ready = hasText("First dose not before")) { GoalsScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun goalsLarge() = goals()

    /** The Plan tab inside the shell, so the tab bar shows, once [seed] has set it up. */
    private fun tab(name: String, ready: String, seed: () -> Unit) {
        seed()
        compose.captureLooks(
            name,
            ready = planTab,
            before = {
                onNode(planTab).performClick()
                waitUntilAtLeastOneExists(hasText(ready), timeoutMillis = 10_000)
            },
        ) { AppShell() }
    }
}

/** The Plan tab in the tab bar. */
internal val planTab: SemanticsMatcher = hasText("Plan") and hasClickAction()

/** Opens week [number] of the plan being built, which starts below the fold. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeContentTestRule.openWeek(number: Int) {
    val row = hasContentDescription("Week $number, from", substring = true)
    waitUntilAtLeastOneExists(row, timeoutMillis = 5_000)
    onNode(row).performScrollTo().performClick()
    waitUntilAtLeastOneExists(hasText("Daily mix"), timeoutMillis = 5_000)
}

/** Picks the step with mix [text] in the week editor, scrolling its list to it. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeContentTestRule.pickStep(text: String) {
    val option = hasText(text) and hasClickAction()
    onNode(hasScrollToNodeAction() and hasContentDescription("Daily mix for week", substring = true)).performScrollToNode(option)
    onNode(option).performClick()
    waitForIdle()
}
