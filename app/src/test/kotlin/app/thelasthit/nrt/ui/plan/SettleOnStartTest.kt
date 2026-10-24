package app.thelasthit.nrt.ui.plan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Results arrive when the app looks (ROADMAP P3-3): the shell settles each time the app comes
 * to the foreground, so a result that fell due while it was away doesn't wait for the next dose.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class SettleOnStartTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    /** A lifecycle the test moves by hand, as the app going to the background and back. */
    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun TestOwner.move(state: Lifecycle.State) = compose.runOnUiThread { registry.currentState = state }

    @Test
    fun `it settles each time the app comes to the foreground`() {
        val owner = TestOwner().apply { registry.currentState = Lifecycle.State.CREATED }
        var calls = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { SettleOnStart { calls++ } }
        }
        compose.waitForIdle()
        assertEquals(0, calls)

        owner.move(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            calls == 1
        }
        // In the background, then back.
        owner.move(Lifecycle.State.CREATED)
        owner.move(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            calls == 2
        }
    }

    @Test
    fun `a result that fell due while the app was away comes in when it's back`() {
        app.setUp()
        app.startMockupPlan()
        val owner = TestOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { LastHitTheme { AppShell() } }
        }
        compose.waitUntilAtLeastOneExists(planTab, timeoutMillis = 5_000)
        compose.onNode(planTab).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Schedule"), timeoutMillis = 5_000)

        // Away over the first week: its result came due at 04:00 on Sunday 1 November, a day after it ended.
        owner.move(Lifecycle.State.CREATED)
        app.setTime("2026-11-01T09:00")
        owner.move(Lifecycle.State.RESUMED)

        val met = hasContentDescription("Week 1, 24 to 30 October", substring = true) and hasContentDescription("Met", substring = true)
        compose.waitUntilAtLeastOneExists(met, timeoutMillis = 10_000)
        compose.onNode(met).performScrollTo()
    }
}
