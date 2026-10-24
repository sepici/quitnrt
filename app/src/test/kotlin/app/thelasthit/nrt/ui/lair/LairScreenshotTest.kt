package app.thelasthit.nrt.ui.lair

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
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

/** The lair tab with Gary lurking, inside the shell so the tab bar shows too. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LairScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = PHONE)
    fun lair() = captureLair()

    @Test @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun lairLarge() = captureLair()

    private fun captureLair() {
        app.setUp()
        compose.captureLooks(
            "lair",
            ready = hasText("Lair"),
            before = {
                onNodeWithText("Lair").performClick()
                waitUntil(5_000) { onAllNodes(hasText("No hits yet")).fetchSemanticsNodes().isNotEmpty() }
                // The gear row and the shelf come in after the demon, and take the stage's spare sky.
                waitUntil(5_000) { onAllNodes(hasText("Trophy shelf")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { AppShell() }
    }
}
