package app.thelasthit.nrt.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.SeedProducts
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.keepPlanUsualDay
import app.thelasthit.nrt.seedMockupPlan
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.advice.StrategyScreen
import app.thelasthit.nrt.ui.game.LocalGameSounds
import app.thelasthit.nrt.ui.insights.InsightsScreen
import app.thelasthit.nrt.ui.lair.RecordedSounds
import app.thelasthit.nrt.ui.plan.BuildScreen
import app.thelasthit.nrt.ui.plan.planTab
import app.thelasthit.nrt.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tall enough for the plan being built, and for the Plan tab down to the end of its schedule. */
private const val STORE_TALL = "en-rGB-w411dp-h2300dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** Tall enough for the whole of Insights with a plan. */
private const val STORE_INSIGHTS = "en-rGB-w411dp-h4100dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * The renders the Play listing's screenshots are cut from (`docs/store/tools/store_art.py`), all
 * from one set of data so they agree with each other: the mockups' plan on Friday 23 October, in
 * week 4 of 12 at a 14 mg guide, with weeks 1 and 3 met, week 2 paid back, and Gary at 100 of 120
 * HP with a hit banked. The products are the catalogue's generic ones, so no brand shows in the
 * store. The plan being built is the same plan, the Sunday before it started.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun seed() {
        app.setUp(SeedProducts.GENERIC)
        app.seedMockupPlan(SeedProducts.GENERIC)
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun today() {
        seed()
        compose.captureLooks("store-today", ready = hasContentDescription("mg used. Daily guide", substring = true)) { Today() }
    }

    /** Sunday 27 September, with the usual day typed: the plan Today runs, before it starts. */
    @Test @Config(qualifiers = STORE_TALL)
    fun plan() {
        app.setUp(SeedProducts.GENERIC)
        app.clock.now = london("2026-09-27T15:05")
        app.keepPlanUsualDay(SeedProducts.GENERIC)
        compose.captureLooks("store-plan", ready = hasText("Week by week")) { BuildScreen(onBack = {}, onStarted = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun lair() {
        seed()
        compose.captureLooks(
            "store-lair",
            ready = hasText("Lair"),
            before = {
                onNodeWithText("Lair").performClick()
                waitUntilAtLeastOneExists(hasText("Gary eyes you from the rocks."), timeoutMillis = 5_000)
            },
        ) {
            CompositionLocalProvider(LocalGameSounds provides RecordedSounds()) { AppShell() }
        }
    }

    @Test @Config(qualifiers = STORE_TALL)
    fun schedule() {
        seed()
        compose.captureLooks(
            "store-schedule",
            ready = planTab,
            before = {
                onNode(planTab).performClick()
                waitUntilAtLeastOneExists(hasText("Schedule"), timeoutMillis = 10_000)
            },
        ) { AppShell() }
    }

    @Test @Config(qualifiers = STORE_INSIGHTS)
    fun insights() {
        seed()
        compose.captureLooks("store-insights", ready = hasText("Week by week")) { InsightsScreen(remember { SnackbarHostState() }) }
    }

    /** The strategy Today's tip points to, for the top trigger, After a meal. */
    @Test @Config(qualifiers = FULL_PAGE)
    fun advice() {
        seed()
        compose.captureLooks("store-advice", ready = hasText("doses were tagged After a meal", substring = true)) {
            StrategyScreen("leave_the_table", onBack = {}, onAllAdvice = {}, onStrategy = {}, onTimer = {})
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun log() {
        seed()
        compose.captureLooks(
            "store-log",
            ready = hasContentDescription("Log a dose"),
            before = {
                onNodeWithContentDescription("Log a dose").performClick()
                waitUntilAtLeastOneExists(hasText("Planned or craving"), timeoutMillis = 5_000)
            },
        ) { Today() }
    }

    @Composable
    private fun Today() = TodayScreen(snackbars = remember { SnackbarHostState() }, onOpenSettings = {}, onOpenLair = {})
}
