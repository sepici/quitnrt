package app.thelasthit.nrt.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The phone's animation speed, from its developer or accessibility settings: 1 is normal, and 0
 * means animations are off ("remove animations"), so moving things keep still. It's read again
 * each time the app comes back, so turning animations off in Settings counts at once.
 */
@Composable
fun rememberAnimatorScale(): Float {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    var scale by remember(resolver) { mutableFloatStateOf(read()) }
    LifecycleResumeEffect(resolver) {
        scale = read()
        onPauseOrDispose { }
    }
    return scale
}
