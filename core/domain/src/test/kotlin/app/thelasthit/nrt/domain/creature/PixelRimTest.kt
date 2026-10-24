package app.thelasthit.nrt.domain.creature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PixelRimTest {

    private val rim = 0xFFBCA97E.toInt()

    /** Rows of '#' for opaque and '.' for transparent, as ARGB. */
    private fun art(vararg rows: String): IntArray =
        rows.joinToString("").map { if (it == '#') 0xFF000000.toInt() else 0 }.toIntArray()

    /** The rim as rows of 'r' and '.', to read like the art. */
    private fun text(pixels: IntArray, width: Int): List<String> =
        pixels.toList().chunked(width).map { row -> row.joinToString("") { if (it == rim) "r" else if (it == 0) "." else "?" } }

    @Test
    fun `the rim sits one whole pixel outside the art, on a canvas padded by one a side`() {
        val pixels = art(
            "...",
            ".#.",
            "...",
        )
        val out = PixelRim.around(pixels, 3, 3, rim)
        assertEquals(25, out.size)
        assertEquals(
            listOf(
                ".....",
                "..r..",
                ".r.r.",
                "..r..",
                ".....",
            ),
            text(out, 5),
        )
    }

    @Test
    fun `art that runs to the edge gets its rim on the padding, and none under the feet`() {
        val pixels = art(
            ".#.",
            "###",
            "#.#",
        )
        assertEquals(
            listOf(
                "..r..",
                ".r.r.",
                "r...r",
                "r.r.r",
                // The row under the art is where the feet meet the rock: no rim there.
                ".....",
            ),
            text(PixelRim.around(pixels, 3, 3, rim), 5),
        )
    }

    @Test
    fun `every frame of every creature has a rim that never covers the sprite`() {
        for (creature in Creature.entries) {
            val sheet = requireNotNull(SpriteSheet.inResources(creature))
            for (frame in SpriteFrame.entries) {
                val sprite = sheet.argbPixels(frame)
                val out = sheet.rimPixels(frame, rim)
                val side = sheet.size + 2 * PixelRim.PAD
                assertEquals(side * side, out.size)
                for (i in sprite.indices) {
                    val x = i % sheet.size + PixelRim.PAD
                    val y = i / sheet.size + PixelRim.PAD
                    if (sprite[i] != 0) assertEquals(0, out[y * side + x], "$creature $frame at $x, $y")
                }
                assertTrue(out.any { it == rim }, "$creature $frame has a rim")
                assertTrue(out.all { it == 0 || it == rim }, "$creature $frame uses only the rim colour")
            }
        }
    }

    @Test
    fun `the hit flash is the sprite's own shape, lit up inside its outline`() {
        val flash = 0xFFFBF8E8.toInt()
        for (creature in Creature.entries) {
            val sheet = requireNotNull(SpriteSheet.inResources(creature))
            val outline = sheet.palette("ember")[0]
            for (frame in SpriteFrame.entries) {
                val sprite = sheet.argbPixels(frame, "ember")
                val lit = sheet.flashPixels(frame, flash, "ember")
                for (i in sprite.indices) {
                    when {
                        sprite[i] == 0 -> assertEquals(0, lit[i], "$creature $frame stays clear outside")
                        sprite[i] == outline -> assertEquals(outline, lit[i], "$creature $frame keeps its outline")
                        else -> assertEquals(flash, lit[i], "$creature $frame lights up")
                    }
                }
            }
        }
    }

    @Test
    fun `the art has to be the size it says`() {
        assertThrows<IllegalArgumentException> { PixelRim.around(IntArray(8), 3, 3, rim) }
    }
}
