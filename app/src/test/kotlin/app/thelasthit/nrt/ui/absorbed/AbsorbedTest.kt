package app.thelasthit.nrt.ui.absorbed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.R
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.absorbed.Absorption
import app.thelasthit.nrt.domain.absorbed.SourceKind
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedPlan
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.history.HistoryScreen
import app.thelasthit.nrt.ui.insights.InsightsScreen
import app.thelasthit.nrt.ui.settings.SettingsLinks
import app.thelasthit.nrt.ui.settings.SettingsScreen
import app.thelasthit.nrt.ui.theme.LastHitTheme
import app.thelasthit.nrt.ui.today.TodayScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Estimated absorbed amounts (ROADMAP P9-1): off by default, then beside the labelled mg on
 * Today's hero and rows, History's rows and day totals and the log sheet, and nowhere else. The week in
 * [seedWeek] has 8 mg of gum today (one 4 mg piece and two 2 mg ones), so about 4 mg absorbed,
 * and a 14 mg patch on Thursday, so 11.2 mg, about 11.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class AbsorbedTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun show(screen: @Composable (SnackbarHostState) -> Unit) {
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    screen(snackbars)
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    private fun showToday() = show { TodayScreen(snackbars = it, onOpenSettings = {}, onOpenLair = {}) }

    private fun waitForText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), timeoutMillis = 5_000)

    private fun waitForDescription(text: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(text), timeoutMillis = 5_000)

    private fun assertNoAbsorbedFigures() {
        compose.onAllNodes(hasText("absorbed", substring = true), useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("found ${it.size} absorbed figures", it.isEmpty())
        }
        compose.onAllNodes(hasContentDescription("absorbed", substring = true), useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("found ${it.size} absorbed descriptions", it.isEmpty())
        }
    }

    private fun setting() = runBlocking { app.graph.settings.settings.first().showAbsorbed }

    private fun heroEstimate() = compose.onAllNodes(hasTestTag(HERO_ESTIMATE_TAG), useUnmergedTree = true).fetchSemanticsNodes()

    @Test
    fun `it's off by default, so Today shows the labelled mg alone`() {
        app.setUp()
        app.seedWeek()
        assertFalse(setting())
        showToday()

        waitForDescription("8${NB}mg today")
        assertNoAbsorbedFigures()
        assertTrue("the hero shows an estimate", heroEstimate().isEmpty())
    }

    @Test
    fun `Today's hero shows it beside the labelled mg`() {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed()
        showToday()

        val total = hasContentDescription("8${NB}mg today, about 4${NB}mg absorbed")
        compose.waitUntilAtLeastOneExists(total, timeoutMillis = 5_000)
        // The line screen readers skip, since the total's description has it, is drawn under the number.
        val number = compose.onNode(total, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val line = heroEstimate().single().boundsInRoot
        assertTrue("the estimate isn't under the number: $line against $number", line.top >= number.bottom)
        assertTrue("the estimate has no size", line.height > 0f && line.width > 0f)
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun `Today's rows give each dose's estimate too`() {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed()
        showToday()

        // Screen readers hear each in full. On screen it's "about 1 mg" under the mg, and the hero says "absorbed".
        waitForText("about 2${NB}mg absorbed")
        compose.onAllNodes(hasText("about 1${NB}mg absorbed")).fetchSemanticsNodes().let { assertEquals(2, it.size) }
        val rows = compose.onAllNodes(hasTestTag(ROW_ESTIMATE_TAG), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, rows.size)
        // Each under its own mg, at the row's end.
        val amounts = compose.onAllNodes(hasText("2${NB}mg") or hasText("4${NB}mg"), useUnmergedTree = true).fetchSemanticsNodes()
        rows.forEach { estimate ->
            val above = amounts.filter { it.boundsInRoot.bottom <= estimate.boundsInRoot.top && it.boundsInRoot.right == estimate.boundsInRoot.right }
            assertTrue("no mg above the estimate at ${estimate.boundsInRoot}", above.isNotEmpty())
        }
    }

    @Test
    fun `with nothing logged today, there's no estimate to show`() {
        app.setUp()
        app.showAbsorbed()
        showToday()

        waitForDescription("0${NB}mg today")
        assertNoAbsorbedFigures()
    }

    @Test
    fun `the guide, the meter and the week's allowance stay in labelled mg`() {
        app.setUp()
        app.seedWeek()
        app.seedPlan()
        app.showAbsorbed()
        showToday()

        waitForDescription("8${NB}mg today, about 4${NB}mg absorbed")
        compose.waitUntilAtLeastOneExists(
            hasContentDescription("Daily guide 14${NB}mg. 6${NB}mg left today.", substring = true),
            timeoutMillis = 5_000,
        )
        compose.onNode(hasContentDescription("61${NB}mg used of the 98${NB}mg allowance this week")).assertExists()
        compose.onNodeWithText("Week 4 of 12, day 5. 37${NB}mg left this week.").assertExists()
        // Thursday's 14 mg patch is at the 14 mg guide in labelled mg, whatever its estimate.
        compose.onNode(hasContentDescription("Thursday 14${NB}mg, at or under the guide", substring = true)).assertExists()
    }

    @Test
    fun `History shows it on each row and each day's total`() {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed()
        show { HistoryScreen(snackbars = it) }

        waitForDescription("Today, 8${NB}mg so far, about 4${NB}mg absorbed")
        assertEquals("about 1${NB}mg", app.getString(R.string.absorbed_about_short, "1"))
        // The 4 mg piece, the two 2 mg ones and a spray further down (under 1 mg, said in whole mg),
        // which screen readers hear in full.
        compose.onAllNodes(hasText("about 2${NB}mg absorbed")).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        compose.onAllNodes(hasText("about 1${NB}mg absorbed")).fetchSemanticsNodes().let { assertEquals(3, it.size) }
        // Beside the labelled mg, never in place of it.
        compose.onAllNodes(hasText("2${NB}mg") and hasText("about 1${NB}mg absorbed")).fetchSemanticsNodes().let { assertEquals(2, it.size) }
    }

    @Test
    fun `a patch day's total and row use the patch's share`() {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed()
        show { HistoryScreen(snackbars = it) }

        waitForDescription("Thursday 22 October, 14${NB}mg, about 11${NB}mg absorbed")
    }

    @Test
    fun `switching it off takes History back to the labelled mg`() {
        app.setUp()
        app.seedWeek()
        app.showAbsorbed()
        show { HistoryScreen(snackbars = it) }
        waitForDescription("Today, 8${NB}mg so far, about 4${NB}mg absorbed")

        app.showAbsorbed(on = false)
        waitForDescription("Today, 8${NB}mg so far")
        assertNoAbsorbedFigures()
    }

    @Test
    fun `the log sheet's amount line gives it for the dose being logged`() {
        app.setUp()
        app.showAbsorbed()
        showToday()

        compose.waitUntilAtLeastOneExists(hasContentDescription("Log a dose"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Log a dose").performClick()
        // The first quick-log product is 2 mg gum.
        waitForText("2${NB}mg this dose, about 1${NB}mg absorbed")
        compose.onNodeWithContentDescription("One more").performClick()
        waitForText("4${NB}mg this dose, about 2${NB}mg absorbed")
    }

    @Test
    fun `the log sheet's amount line is labelled mg alone while it's off`() {
        app.setUp()
        showToday()

        compose.waitUntilAtLeastOneExists(hasContentDescription("Log a dose"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Log a dose").performClick()
        waitForText("2${NB}mg this dose")
        assertNoAbsorbedFigures()
    }

    @Test
    fun `its page switches it on and off`() {
        app.setUp()
        show { AbsorbedScreen(onBack = {}) }

        val switch = hasText("Show estimated absorbed amounts")
        compose.waitUntilAtLeastOneExists(switch, timeoutMillis = 5_000)
        compose.onNode(switch).assertIsOff()
        compose.onNode(switch).performClick()
        compose.waitUntilAtLeastOneExists(switch and isOn(), timeoutMillis = 5_000)
        assertTrue(setting())
        compose.onNode(switch).performClick()
        compose.waitUntilAtLeastOneExists(switch and isOff(), timeoutMillis = 5_000)
        assertFalse(setting())
    }

    @Test
    fun `its page cites the sources and says it isn't medical advice`() {
        app.setUp()
        show { AbsorbedScreen(onBack = {}) }

        waitForText("This isn't medical advice. A pharmacist can tell you more about the products you use.")
        compose.onNode(hasText("Gum") and hasText("50%")).assertExists()
        compose.onNode(hasText("Strip") and hasText("48%")).assertExists()
        compose.onNode(hasText("1 and 2${NB}mg lozenges from Nicotinell, Boots NicAssist, Thrive and Habitrol") and hasText("80%")).assertExists()
        compose.onNode(hasText("Patch") and hasText("80%")).assertExists()
        compose.onNode(hasText("Last checked October 2026.", substring = true)).assertExists()
    }

    @Test
    fun `its line of sources names every source the figures come from`() {
        val line = app.getString(R.string.absorbed_sources, "October 2026")
        assertTrue(line.endsWith("Last checked October 2026."))
        Absorption.sources.forEach { source ->
            assertTrue("${source.short} isn't named: ${source.title}", source.short in line)
            if (source.kind == SourceKind.STUDY) {
                // "Benowitz (1987 and 2009)"
                val years = line.substringAfter("${source.short} (", missingDelimiterValue = "").substringBefore(")")
                assertTrue("${source.short}'s year ${source.year} isn't given: $years", "${source.year}" in years)
            }
        }
        // A short paragraph, as About's, rather than a list of citations.
        assertTrue("${line.length} characters", line.length < 600)
    }

    @Test
    fun `Settings shows whether it's on, and opens its page`() {
        app.setUp()
        var opened = false
        show { SettingsScreen(it, onBack = {}, links = SettingsLinks({}, {}, {}, {}, {}, absorbed = { opened = true })) }

        val row = hasText("Absorbed amounts")
        compose.waitUntilAtLeastOneExists(row and hasText("Off"), timeoutMillis = 5_000)
        app.showAbsorbed()
        compose.waitUntilAtLeastOneExists(row and hasText("On"), timeoutMillis = 5_000)
        compose.onNode(row).performClick()
        assertTrue(opened)
    }

    @Test
    fun `Insights stays in labelled mg`() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        app.showAbsorbed()
        show { InsightsScreen(snackbars = it) }

        compose.waitUntilAtLeastOneExists(hasText("7${NB}days"), timeoutMillis = 5_000)
        compose.waitForIdle()
        assertNoAbsorbedFigures()
    }
}
