package app.thelasthit.nrt.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R

val Grotesk = FontFamily(
    Font(R.font.schibsted_grotesk_regular, FontWeight.Normal),
    Font(R.font.schibsted_grotesk_medium, FontWeight.Medium),
    Font(R.font.schibsted_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.schibsted_grotesk_bold, FontWeight.Bold),
    Font(R.font.schibsted_grotesk_extrabold, FontWeight.ExtraBold),
    Font(R.font.schibsted_grotesk_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Numbers only, in the game layer. Capitals only, so never for names or sentences. */
val Pixel = FontFamily(Font(R.font.silkscreen_regular, FontWeight.Normal))

/** The type scale from the tokens. Tracking is converted from px to em. */
@Immutable
data class LastHitType(
    val hero: TextStyle,
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val flavour: TextStyle,
    val pixel: TextStyle,
    val pixelLarge: TextStyle,
)

val LastHitTypography = LastHitType(
    hero = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.ExtraBold, fontSize = 112.sp, lineHeight = 92.sp, letterSpacing = (-0.055).em),
    display = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.045).em),
    headline = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.03).em),
    title = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    label = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    // Balanced, so a flavour line that wraps splits evenly instead of leaving one word on its own.
    flavour = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 24.sp,
        lineBreak = LineBreak.Heading,
    ),
    pixel = TextStyle(fontFamily = Pixel, fontSize = 24.sp, lineHeight = 24.sp, letterSpacing = (-0.125).em),
    pixelLarge = TextStyle(fontFamily = Pixel, fontSize = 32.sp, lineHeight = 32.sp, letterSpacing = (-0.125).em),
)

val LocalLastHitType = staticCompositionLocalOf { LastHitTypography }
