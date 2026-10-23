package app.thelasthit.nrt.ui.game

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.thelasthit.nrt.domain.creature.Backdrop
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.domain.creature.SpriteSheet
import kotlin.math.roundToInt

/** The game layer's pixel art, parsed once and kept as bitmaps. */
object GameArt {
    private val backdrops = mutableMapOf<Pair<Backdrop.Scene, Boolean>, ImageBitmap>()
    private val sheets = mutableMapOf<Creature, SpriteSheet?>()

    val band: Backdrop by lazy { Backdrop.bundled(Backdrop.Scene.BAND) }
    val stage: Backdrop by lazy { Backdrop.bundled(Backdrop.Scene.STAGE) }

    fun backdrop(backdrop: Backdrop, dark: Boolean): ImageBitmap = backdrops.getOrPut(backdrop.scene to dark) {
        bitmap(backdrop.argbPixels(dark), backdrop.width, backdrop.height)
    }

    /** Only drawn creatures have a sheet; the dragon stands in for the rest until they're drawn. */
    fun sheet(creature: Creature): SpriteSheet = sheets.getOrPut(creature) { SpriteSheet.bundled(creature) }
        ?: requireNotNull(sheets.getOrPut(Creature.DRAGON) { SpriteSheet.bundled(Creature.DRAGON) })

    fun sprite(sheet: SpriteSheet, frame: SpriteFrame, variant: String): ImageBitmap =
        bitmap(sheet.argbPixels(frame, variant), sheet.size, sheet.size)

    fun silhouette(sheet: SpriteSheet, frame: SpriteFrame, colour: Color): ImageBitmap =
        bitmap(sheet.silhouettePixels(frame, colour.toArgb()), sheet.size, sheet.size)

    private fun bitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Draws [image] at [scale] screen pixels per art pixel, with no smoothing, so it stays crisp. */
fun DrawScope.drawPixels(image: ImageBitmap, topLeft: Offset, scale: Float) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
        filterQuality = FilterQuality.None,
    )
}

/**
 * Draws [backdrop] to cover the whole area, anchored to the bottom left so the ground and the
 * creature's rock always show; extra height or width crops the sky. Returns the scale and the
 * backdrop's top left, so a sprite can be placed on it.
 */
fun DrawScope.drawBackdrop(backdrop: Backdrop, image: ImageBitmap): Pair<Float, Offset> {
    val scale = maxOf(size.width / backdrop.width, size.height / backdrop.height)
    val origin = Offset(0f, size.height - backdrop.height * scale)
    drawPixels(image, origin, scale)
    return scale to origin
}
