package app.thelasthit.nrt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Placeholder palette until a design direction is chosen. Dynamic colour stays off on purpose,
// so the app always matches its pixel art.

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B4A44),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E5E0),
    onPrimaryContainer = Color(0xFF0F2420),
    secondary = Color(0xFF9A5A22),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF1B1D21),
    surface = Color(0xFFF6F3EC),
    onSurface = Color(0xFF1B1D21),
    surfaceVariant = Color(0xFFE6E1D6),
    onSurfaceVariant = Color(0xFF4A4A45),
    outline = Color(0xFF7A776E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7BC),
    onPrimary = Color(0xFF0F2420),
    primaryContainer = Color(0xFF2B4A44),
    onPrimaryContainer = Color(0xFFD5E5E0),
    secondary = Color(0xFFE0A56B),
    onSecondary = Color(0xFF2E1A08),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFE6E2D9),
    surface = Color(0xFF14161A),
    onSurface = Color(0xFFE6E2D9),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFC2BEB4),
    outline = Color(0xFF8C887F),
)

@Composable
fun LastHitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
