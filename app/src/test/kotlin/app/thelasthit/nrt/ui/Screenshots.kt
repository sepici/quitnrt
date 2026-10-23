package app.thelasthit.nrt.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.LastHitTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage

/** A window tall enough for a whole scrolling screen, at a Pixel 7's width and density. */
const val FULL_PAGE = "en-rGB-w411dp-h1900dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** A Pixel 7, for screens with a sheet or dialog. */
const val PHONE = "en-rGB-w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * The large font every screen is also checked at. Set it with `@Config(fontScale = LARGE_FONT)`
 * rather than in Compose, so sheets and dialogs, which have windows of their own, scale too.
 */
const val LARGE_FONT = 1.3f

/**
 * Shows [content] and captures the whole screen, sheets and dialogs included, in light and dark.
 * [ready] waits for the data to load first and [before] can open a sheet. Images are only written
 * by `./gradlew :app:recordRoborazziDebug`, into `app/build/outputs/roborazzi/`, with `-large` in
 * the name at [LARGE_FONT]. A normal test run still renders them, which catches crashes.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.captureLooks(
    name: String,
    ready: SemanticsMatcher? = null,
    before: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    var dark by mutableStateOf(false)
    setContent {
        LastHitTheme(darkTheme = dark) {
            // As the app's Scaffold does: the background, and text colour for anything that doesn't set one.
            Surface(Modifier.fillMaxSize(), color = LastHit.colors.bg, contentColor = LastHit.colors.text) { content() }
        }
    }
    ready?.let { waitUntilAtLeastOneExists(it, timeoutMillis = 5_000) }
    before()
    val large = ApplicationProvider.getApplicationContext<Context>().resources.configuration.fontScale > 1f
    for (isDark in listOf(false, true)) {
        dark = isDark
        waitForIdle()
        val look = (if (isDark) "dark" else "light") + if (large) "-large" else ""
        captureScreenRoboImage("build/outputs/roborazzi/$name-$look.png")
    }
}
