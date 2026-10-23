package app.thelasthit.nrt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.thelasthit.nrt.ui.theme.LastHitTheme
import com.github.takahirom.roborazzi.captureRoboImage

/** The four looks every screen is checked in: light and dark, at normal and large font. */
enum class Look(val dark: Boolean, val fontScale: Float) {
    LIGHT(dark = false, fontScale = 1f),
    DARK(dark = true, fontScale = 1f),
    LIGHT_LARGE(dark = false, fontScale = 1.3f),
    DARK_LARGE(dark = true, fontScale = 1.3f),
    ;

    val suffix: String get() = name.lowercase().replace('_', '-')
}

/**
 * Renders [content] in each [Look]. Images are only written by `./gradlew :app:recordRoborazziDebug`,
 * into `app/build/outputs/roborazzi/`. A normal test run still renders them, which catches crashes.
 */
fun captureLooks(name: String, content: @Composable () -> Unit) {
    Look.entries.forEach { look ->
        captureRoboImage(filePath = "build/outputs/roborazzi/$name-${look.suffix}.png") {
            LastHitTheme(darkTheme = look.dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, look.fontScale)) {
                    content()
                }
            }
        }
    }
}
