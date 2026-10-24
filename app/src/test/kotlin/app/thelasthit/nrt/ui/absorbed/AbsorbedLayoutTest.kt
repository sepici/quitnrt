package app.thelasthit.nrt.ui.absorbed

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.history.HistoryScreen
import app.thelasthit.nrt.ui.theme.LastHitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With the estimate on, History's rows keep the lines they have with it off: each product's
 * name and each time stay on one line at a Pixel 7's width, at the normal font and the large one,
 * as the signed-off History mockup has them. Where the estimate would crowd a row, it takes a line
 * of its own. Text is measured for real here, so the test draws with native graphics.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AbsorbedLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private class Shown(val text: String, val lines: Int, val bounds: Rect)

    /** Every piece of text drawn, with how many lines it takes and where it is. */
    private fun texts(): List<Shown> = compose
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: return@mapNotNull null
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            Shown(text, results.single().lineCount, node.boundsInRoot)
        }

    private fun List<Shown>.lines(): Map<String, Set<Int>> = groupBy { it.text }.mapValues { (_, shown) -> shown.map { it.lines }.toSet() }

    /** Shows History with the estimate off, then on, and returns what each drew. */
    private fun historyOffThenOn(): Pair<List<Shown>, List<Shown>> {
        app.setUp()
        app.seedWeekForHistory()
        compose.setContent { LastHitTheme { HistoryScreen(snackbars = remember { SnackbarHostState() }) } }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Today, 8${NB}mg so far"), timeoutMillis = 5_000)
        compose.waitForIdle()
        val off = texts()
        app.showAbsorbed()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Today, 8${NB}mg so far, about 4${NB}mg absorbed"), timeoutMillis = 5_000)
        compose.waitForIdle()
        return off to texts()
    }

    private fun assertRowsKeepTheirLines(oneLineEach: Boolean) {
        val (off, on) = historyOffThenOn()
        val before = off.lines()
        val after = on.lines()
        val rows = listOf("Nicorette gum, 3${NB}pieces", "QuickMist, 2${NB}sprays", "NiQuitin patch", "01:20 Friday, Before bed", "08:10, With coffee or tea")
        rows.forEach { assertTrue("$it isn't on screen: ${after.keys}", it in after) }

        val changed = before.filter { (text, lines) -> text in after && after.getValue(text) != lines }
        assertEquals("text that wraps differently with the estimate on", emptyMap<String, Set<Int>>(), changed)
        if (oneLineEach) {
            val wrapped = after.filterKeys { it in rows }.filterValues { it != setOf(1) }
            assertEquals("names and times on more than one line", emptyMap<String, Set<Int>>(), wrapped)
        }

        // No estimate sits over a name or a time. Delete waits behind each row for a swipe.
        val drawn = on.filter { it.text != "Delete" }
        val estimates = compose.onAllNodes(hasTestTag(ROW_ESTIMATE_TAG), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("only ${estimates.size} estimates", estimates.size >= 10)
        estimates.forEach { estimate ->
            drawn.forEach { text -> assertTrue("an estimate at $estimate overlaps ${text.text} at ${text.bounds}", !estimate.overlaps(text.bounds)) }
        }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun `at the normal font, every name and time keeps its one line`() = assertRowsKeepTheirLines(oneLineEach = true)

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun `at the large font, every name and time keeps its one line`() = assertRowsKeepTheirLines(oneLineEach = true)

    @Test @Config(qualifiers = NARROW_PAGE)
    fun `on a narrower phone, rows wrap only where they do without it`() = assertRowsKeepTheirLines(oneLineEach = false)

    @Test @Config(qualifiers = NARROW_PAGE, fontScale = LARGE_FONT)
    fun `on a narrower phone at the large font, rows wrap only where they do without it`() = assertRowsKeepTheirLines(oneLineEach = false)
}

/** A whole scrolling screen on a 360 dp phone, narrower than the Pixel 7 the other looks use. */
const val NARROW_PAGE = "en-rGB-w360dp-h1900dp-normal-long-notround-any-420dpi-keyshidden-nonav"
