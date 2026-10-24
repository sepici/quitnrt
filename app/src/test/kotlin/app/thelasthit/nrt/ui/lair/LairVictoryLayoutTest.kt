package app.thelasthit.nrt.ui.lair

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.game.LocalGameSounds
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
 * Where the victory's values sit beside their labels, with the app's own fonts measured, as on a
 * phone: beside the label, as plan-done.html has them, unless that leaves the value no room.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LairVictoryLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun showVictory() {
        app.defeatedWithTriggers()
        compose.setContent {
            LastHitTheme { CompositionLocalProvider(LocalGameSounds provides RecordedSounds()) { LairScreen() } }
        }
        compose.waitUntilExactlyOneExists(hasText("Victory"), timeoutMillis = 5_000)
        compose.onNodeWithText(TRIGGERS).performScrollTo()
    }

    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    @Config(qualifiers = PHONE)
    fun `at the normal font, each value sits beside its label`() {
        showVictory()
        val label = bounds("Triggers you got on top of")
        val value = bounds(TRIGGERS)
        assertEquals(label.top, value.top)
        assertTrue(value.left > label.right)
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun `at a large font, a value with no room beside its label goes under it`() {
        showVictory()
        val label = bounds("Triggers you got on top of")
        val value = bounds(TRIGGERS)
        assertEquals(label.left, value.left)
        assertTrue(value.top >= label.bottom)
        // A short label keeps its value beside it.
        assertEquals(bounds("Weeks").top, bounds("12").top)
    }

    private companion object {
        const val TRIGGERS = "After a meal, With coffee or tea"
    }
}
