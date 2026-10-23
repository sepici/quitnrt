package app.thelasthit.nrt.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = PHONE)
    fun shell() = captureShell()

    @Test
    @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun shellLarge() = captureShell()

    private fun captureShell() {
        app.setUp()
        compose.captureLooks("shell", ready = hasText("You've had")) { AppShell() }
    }
}
