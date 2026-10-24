package app.thelasthit.nrt.widget

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget's tag picker over the home screen, in light and dark, so its sheet can be looked at
 * beside the app's own. Images are only written by `./gradlew :app:recordRoborazziDebug`.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class QuickTagsScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private var activity: ActivityController<QuickTagsActivity>? = null

    @Before
    fun setUpApp() {
        app.setUp()
    }

    @After
    fun close() {
        activity?.pause()?.stop()?.destroy()
    }

    @Test
    fun light() = capture("light")

    @Test @Config(qualifiers = "+night")
    fun dark() = capture("dark")

    private fun capture(look: String) {
        val intent = Intent(app, QuickTagsActivity::class.java).putExtra(WidgetKeys.PRODUCT.name, GUM_2)
        activity = Robolectric.buildActivity(QuickTagsActivity::class.java, intent).setup()
        compose.waitUntilExactlyOneExists(hasText("Save tags"), timeoutMillis = 5_000)
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/widget-tags-$look.png")
    }
}
