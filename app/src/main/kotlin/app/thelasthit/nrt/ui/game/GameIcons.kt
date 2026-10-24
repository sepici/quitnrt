package app.thelasthit.nrt.ui.game

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.IconSheet
import app.thelasthit.nrt.domain.game.PixelIcon
import kotlin.math.abs
import kotlin.math.floor

/** The badge and gear icons (16 × 16, from the master palette), parsed once and kept as bitmaps, as [GameArt] keeps the sprites. */
object GameIcons {
    private val badges: IconSheet by lazy { IconSheet.bundled(IconSheet.Kind.BADGES) }
    private val gearSheet: IconSheet by lazy { IconSheet.bundled(IconSheet.Kind.GEAR) }
    private val bitmaps = mutableMapOf<Any, ImageBitmap>()

    fun badge(achievement: Achievement): ImageBitmap = bitmaps.getOrPut(achievement) { badges.icon(achievement).full() }

    fun gear(gear: Gear): ImageBitmap = bitmaps.getOrPut(gear) { gearSheet.icon(gear).full() }

    /**
     * The gear's own colours for the pixels a hit throws off, so wood throws splinters, iron
     * sparks and the fire staff embers (PLAN 8.2): the three that stand out most against [sky],
     * leaving out its darkest, the outline.
     */
    fun gearColours(gear: Gear, sky: Color): List<Color> = sparks.getOrPut(gear to sky) {
        val colours = gearSheet.icon(gear).argbPixels().filter { it != 0 }.distinct().map { Color(it) }.sortedBy { it.luminance() }
        val sky = sky.luminance()
        colours.drop(1).ifEmpty { colours }.sortedByDescending { abs(it.luminance() - sky) }.take(3)
    }

    private val sparks = mutableMapOf<Pair<Gear, Color>, List<Color>>()

    /** A badge not earned yet: its shape as a plain outline in [colour]. */
    fun badgeOutline(achievement: Achievement, colour: Color): ImageBitmap =
        bitmaps.getOrPut(Triple("outline", achievement, colour)) { badges.icon(achievement).outline(colour) }

    /** Gear still to come: its shape as a plain outline in [colour]. */
    fun gearOutline(gear: Gear, colour: Color): ImageBitmap =
        bitmaps.getOrPut(Triple("outline", gear, colour)) { gearSheet.icon(gear).outline(colour) }

    private fun PixelIcon.full(): ImageBitmap = bitmap(argbPixels(), size)

    private fun PixelIcon.outline(colour: Color): ImageBitmap = bitmap(outlinePixels(colour.toArgb()), size)

    private fun bitmap(pixels: IntArray, size: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * A pixel icon at the largest whole number of screen pixels per art pixel that fits in [size],
 * centred on whole pixels, so every art pixel comes out the same size and stays crisp.
 */
@Composable
fun PixelImage(image: ImageBitmap, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val room = minOf(this.size.width / image.width, this.size.height / image.height)
        val scale = floor(room).coerceAtLeast(1f)
        val left = floor((this.size.width - image.width * scale) / 2)
        val top = floor((this.size.height - image.height * scale) / 2)
        drawPixels(image, Offset(left, top), scale)
    }
}
