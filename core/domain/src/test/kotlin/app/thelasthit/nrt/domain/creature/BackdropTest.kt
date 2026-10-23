package app.thelasthit.nrt.domain.creature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackdropTest {

    @Test
    fun `both scenes load in light and dark with room for the sprite`() {
        for (scene in Backdrop.Scene.entries) {
            val backdrop = Backdrop.bundled(scene)
            for (dark in listOf(false, true)) {
                val pixels = backdrop.argbPixels(dark)
                assertEquals(backdrop.width * backdrop.height, pixels.size)
                assertTrue(pixels.all { it ushr 24 == 0xFF }, "$scene is opaque")
            }
            assertNotEquals(backdrop.argbPixels(false).toList(), backdrop.argbPixels(true).toList())
            assertTrue(backdrop.spriteX + 32 <= backdrop.width && backdrop.spriteY + 32 <= backdrop.height, "$scene fits a 32 px sprite")
        }
    }

    @Test
    fun `the band is the mockup's size and only dark mode has a rim`() {
        val band = Backdrop.bundled(Backdrop.Scene.BAND)
        assertEquals(138 to 44, band.width to band.height)
        assertNull(band.rim(dark = false))
        assertEquals(0xFFBCA97E.toInt(), band.rim(dark = true))
    }

    @Test
    fun `the band's text stays above the hills`() {
        val band = Backdrop.bundled(Backdrop.Scene.BAND)
        assertEquals(31, band.horizon)
        assertTrue(band.spriteY < band.horizon, "the creature stands in front of the hills")
    }

    @Test
    fun `with no horizon the whole backdrop is sky`() {
        val text = """{"width":1,"height":3,"sprite":{"x":0,"y":0},"palettes":{"light":{"S":"#ffffff"},"dark":{"S":"#000000"}},
            "rows":{"light":["S","S","S"],"dark":["S","S","S"]}}"""
        assertEquals(3, Backdrop.parse(Backdrop.Scene.BAND, text).horizon)
    }

    @Test
    fun `an unknown letter is refused`() {
        val text = """{"width":2,"height":1,"sprite":{"x":0,"y":0},"palettes":{"light":{"S":"#ffffff"},"dark":{"S":"#000000"}},
            "rows":{"light":["SX"],"dark":["SS"]}}"""
        assertThrows<IllegalArgumentException> { Backdrop.parse(Backdrop.Scene.BAND, text) }
    }
}
