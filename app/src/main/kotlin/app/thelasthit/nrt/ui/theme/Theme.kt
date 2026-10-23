package app.thelasthit.nrt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

// Dynamic colour stays off on purpose, so the app always matches its pixel art.

private fun materialScheme(c: LastHitColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.hero, onPrimaryContainer = c.onHero,
        secondary = c.link, onSecondary = c.onPrimary,
        secondaryContainer = c.iconTile, onSecondaryContainer = c.onIconTile,
        tertiary = c.marigold, onTertiary = c.onMarigold,
        tertiaryContainer = c.marigoldSoft, onTertiaryContainer = c.onMarigoldSoft,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.sunk, onSurfaceVariant = c.text2,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.bg,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        outline = c.lineStrong, outlineVariant = c.line,
        error = c.error, onError = c.onError,
        scrim = c.scrim,
    )
} else {
    lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.hero, onPrimaryContainer = c.onHero,
        secondary = c.link, onSecondary = c.onPrimary,
        secondaryContainer = c.iconTile, onSecondaryContainer = c.onIconTile,
        tertiary = c.marigold, onTertiary = c.onMarigold,
        tertiaryContainer = c.marigoldSoft, onTertiaryContainer = c.onMarigoldSoft,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.sunk, onSurfaceVariant = c.text2,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.bg,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        outline = c.lineStrong, outlineVariant = c.line,
        error = c.error, onError = c.onError,
        scrim = c.scrim,
    )
}

private fun materialType(t: LastHitType) = Typography(
    displayLarge = t.hero,
    displayMedium = t.display,
    headlineLarge = t.headline.copy(fontSize = t.headline.fontSize * 1.08f),
    headlineMedium = t.headline,
    headlineSmall = t.headline.copy(fontSize = t.headline.fontSize * 0.92f),
    titleLarge = t.title.copy(fontSize = t.title.fontSize * 1.25f),
    titleMedium = t.title,
    titleSmall = t.label.copy(fontWeight = t.title.fontWeight),
    bodyLarge = t.body,
    bodyMedium = t.label.copy(fontWeight = t.body.fontWeight),
    bodySmall = t.caption.copy(fontWeight = t.body.fontWeight),
    labelLarge = t.label,
    labelMedium = t.caption,
    labelSmall = t.caption,
)

private val LastHitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LastHitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalLastHitColors provides colors,
        LocalLastHitType provides LastHitTypography,
    ) {
        MaterialTheme(
            colorScheme = materialScheme(colors, darkTheme),
            typography = materialType(LastHitTypography),
            shapes = LastHitShapes,
            content = content,
        )
    }
}

/** Shorthand for the app's own tokens: `LastHit.colors.used`, `LastHit.type.hero`. */
object LastHit {
    val colors: LastHitColors
        @Composable @ReadOnlyComposable get() = LocalLastHitColors.current

    val type: LastHitType
        @Composable @ReadOnlyComposable get() = LocalLastHitType.current
}
