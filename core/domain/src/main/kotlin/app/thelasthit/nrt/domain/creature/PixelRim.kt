package app.thelasthit.nrt.domain.creature

/**
 * A light rim around pixel art, so a dark creature stays visible on the dark-mode sky. It's drawn
 * in whole art pixels, as the mockups' sprites have it, rather than a hairline finer than the
 * pixels, which reads as a cut-out.
 *
 * Sprites run to the edges of their grid, so the rim goes on a canvas 1 pixel bigger on each side:
 * draw it one art pixel up and to the left of the sprite, then the sprite over it.
 */
object PixelRim {
    /** The rim canvas is this many pixels bigger than the art on each side. */
    const val PAD = 1

    /**
     * The rim around the opaque pixels of [pixels] ([width] × [height], ARGB, 0 for transparent),
     * as ARGB on a canvas of (width + 2) × (height + 2). A rim pixel is a transparent one that
     * touches an opaque pixel along a side, so corners stay clean, as hand-drawn outlines do.
     *
     * The padding row under the art is left out: that's where the creature's feet meet its rock,
     * and a light line there would lift it off the ground.
     */
    fun around(pixels: IntArray, width: Int, height: Int, argb: Int): IntArray {
        require(pixels.size == width * height) { "${pixels.size} pixels for $width × $height" }
        val outWidth = width + 2 * PAD
        val outHeight = height + 2 * PAD
        fun opaque(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && pixels[y * width + x] != 0
        return IntArray(outWidth * outHeight) { i ->
            val x = i % outWidth - PAD
            val y = i / outWidth - PAD
            val touches = !opaque(x, y) && (opaque(x - 1, y) || opaque(x + 1, y) || opaque(x, y - 1) || opaque(x, y + 1))
            if (touches && y < height) argb else 0
        }
    }
}
