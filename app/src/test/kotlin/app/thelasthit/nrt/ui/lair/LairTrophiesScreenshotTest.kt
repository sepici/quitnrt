package app.thelasthit.nrt.ui.lair

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.game.seedShelf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The lair's gear row and trophy shelf four weeks into a plan, and the gear sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LairTrophiesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = FULL_PAGE)
    fun shelf() = captureShelf()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun shelfLarge() = captureShelf()

    @Test @Config(qualifiers = PHONE)
    fun gearSheet() = captureGearSheet()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun gearSheetLarge() = captureGearSheet()

    /** The whole lair, with the honest ledger picked so its line shows under the logging badges. */
    private fun captureShelf() {
        app.setUp()
        app.seedShelf()
        compose.captureLooks(
            "lair-shelf",
            ready = hasText("Trophy shelf"),
            before = {
                onNode(hasContentDescription("Honest ledger, earned 27 September")).performScrollTo().performClick()
                waitUntil(5_000) { onAllNodes(hasText("A forgotten dose logged after the fact.")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { LairScreen() }
    }

    private fun captureGearSheet() {
        app.setUp()
        app.seedShelf()
        compose.captureLooks(
            "lair-gear-sheet",
            ready = hasText("Wooden sword"),
            before = {
                onNode(hasText("Wooden sword") and hasClickAction()).performScrollTo().performClick()
                waitUntil(5_000) { onAllNodes(hasText("Gear changes how your hits look and sound, and nothing else.")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { LairScreen() }
    }
}
