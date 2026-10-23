package app.thelasthit.nrt.link

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.PATCH_14
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.settings.QuickLogScreen
import app.thelasthit.nrt.ui.theme.LastHitTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launcher shortcuts follow the quick log as it changes in Settings. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class QuickLogShortcutsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val gum2 = "Gum 2 mg"
    private val gum4 = "Gum 4 mg"
    private val spray = "Mouth spray 1 mg"
    private val patch = "Patch 14 mg"

    @Before
    fun setUpApp() {
        app.setUp()
        compose.setContent { LastHitTheme { QuickLogScreen(onBack = {}) } }
        compose.waitUntilExactlyOneExists(hasText("On the quick log"), timeoutMillis = 5_000)
    }

    private fun shortcuts(): List<ShortcutInfoCompat> = ShortcutManagerCompat.getDynamicShortcuts(app).sortedBy { it.rank }

    /**
     * The quick log as the screen lists it, top to bottom, by each row's "Take … off" button.
     * Waiting for the whole of it means a change has finished saving before the next one starts,
     * since the quick log is saved one row at a time.
     */
    private fun quickLogShown(): List<String> {
        val removable = SemanticsMatcher("removable") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.endsWith(" off the quick log") }
        }
        return compose.onAllNodes(removable).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { node -> node.config[SemanticsProperties.ContentDescription].first().removePrefix("Take ").removeSuffix(" off the quick log") }
    }

    /**
     * Waits for the screen to list [pills], then for the shortcuts to be [productIds]. The
     * shortcuts aren't on screen, so that wait lets Compose run the main thread's work inside it,
     * which a plain poll never does (see TodayTest).
     */
    private fun waitFor(pills: List<String>, vararg productIds: String) {
        compose.waitUntil(timeoutMillis = 5_000) { quickLogShown() == pills }
        val expected = productIds.map { QuickLogShortcuts.ID_PREFIX + it }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shortcuts().map { it.id } == expected
        }
    }

    @Test
    fun `the first three quick-log products are shortcuts, each through its link`() {
        waitFor(listOf(gum2, gum4, spray, patch), GUM_2, GUM_4, QUICKMIST)

        val shortcuts = shortcuts()
        assertEquals(listOf(gum2, gum4, spray), shortcuts.map { it.shortLabel.toString() })
        assertEquals(
            listOf("Log Nicorette gum 2 mg", "Log Nicorette gum 4 mg", "Log QuickMist 1 mg"),
            shortcuts.map { it.longLabel.toString() },
        )
        val intent = shortcuts.first().intent
        assertEquals("lasthit://log?product=$GUM_2", intent.data.toString())
        assertEquals(IncomingLink("lasthit://log?product=$GUM_2", EntrySource.SHORTCUT), LogLinks.from(intent))
    }

    @Test
    fun `the shortcut list follows the quick log`() {
        waitFor(listOf(gum2, gum4, spray, patch), GUM_2, GUM_4, QUICKMIST)

        compose.onNodeWithText(gum2).performCustomAccessibilityActionWithLabel("Move down")
        waitFor(listOf(gum4, gum2, spray, patch), GUM_4, GUM_2, QUICKMIST)

        compose.onNodeWithContentDescription("Take $spray off the quick log").performClick()
        waitFor(listOf(gum4, gum2, patch), GUM_4, GUM_2, PATCH_14)

        compose.onNodeWithContentDescription("Take $gum4 off the quick log").performClick()
        waitFor(listOf(gum2, patch), GUM_2, PATCH_14)

        compose.onNodeWithContentDescription("Take $gum2 off the quick log").performClick()
        waitFor(listOf(patch), PATCH_14)
    }
}
