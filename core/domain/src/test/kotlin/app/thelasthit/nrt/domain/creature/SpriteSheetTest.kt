package app.thelasthit.nrt.domain.creature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpriteSheetTest {

    private val dragon = requireNotNull(SpriteSheet.bundled(Creature.DRAGON))

    @Test
    fun `the dragon loads with every frame and the app's palette as default`() {
        assertEquals(32, dragon.size)
        assertEquals("forest", dragon.defaultVariant)
        assertTrue(dragon.variantNames.containsAll(listOf("forest", "ember", "moss", "frost")))
        for (frame in SpriteFrame.entries) {
            val pixels = dragon.argbPixels(frame)
            assertEquals(32 * 32, pixels.size)
            assertTrue(pixels.any { it == 0 }, "$frame has a transparent background")
            assertTrue(pixels.count { it != 0 } > 200, "$frame has a body")
        }
    }

    @Test
    fun `pixels only use the chosen palette`() {
        val palette = dragon.palette("ember").toSet()
        assertTrue(dragon.argbPixels(SpriteFrame.IDLE_1, "ember").filter { it != 0 }.all { it in palette })
        assertEquals(dragon.argbPixels(SpriteFrame.IDLE_1).toList(), dragon.argbPixels(SpriteFrame.IDLE_1, "nope").toList())
    }

    @Test
    fun `every drawn creature has a sheet and the rest have none yet`() {
        for (creature in Creature.entries) {
            if (creature.isDrawn) assertNotNull(SpriteSheet.bundled(creature), "$creature") else assertNull(SpriteSheet.bundled(creature), "$creature")
        }
    }

    @Test
    fun `broken sheets are rejected`() {
        fun sheet(rows: List<String>, slots: String = """{"o":"outline"}""", colours: String = """{"o":"#000000"}""") =
            """{"creature":"x","size":8,"slots":$slots,"variants":{"v":$colours},"defaultVariant":"v",
               "frames":{${SpriteFrame.entries.joinToString(",") { "\"${it.key}\":" + rows.joinToString(",", "[", "]") { r -> "\"$r\"" } }}}}"""
        val good = List(8) { "oooo...." }
        SpriteSheet.parse(sheet(good))
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(good.drop(1))) }
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(List(8) { "oooo..." })) }
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(List(8) { "ooox...." })) }
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(good, colours = """{"o":"black"}""")) }
    }

    @Test
    fun `a silhouette fills the outline with one colour`() {
        val colour = 0xFF10221A.toInt()
        val silhouette = dragon.silhouettePixels(SpriteFrame.IDLE_1, colour)
        val normal = dragon.argbPixels(SpriteFrame.IDLE_1)
        assertEquals(normal.map { it == 0 }, silhouette.map { it == 0 })
        assertTrue(silhouette.filter { it != 0 }.all { it == colour })
    }

    @Test
    fun `a silhouette can keep its eyes`() {
        val shape = 0xFF3F3829.toInt()
        val eyes = 0xFFF5B12B.toInt()
        val silhouette = dragon.silhouettePixels(SpriteFrame.IDLE_1, shape, eyes)
        // The forest dragon's eyes are its only pixels in the eye colour.
        val eyeColour = 0xFFFFE27A.toInt()
        val normal = dragon.argbPixels(SpriteFrame.IDLE_1, "forest")
        assertEquals(normal.map { it == eyeColour }, silhouette.map { it == eyes })
        assertTrue(silhouette.count { it == eyes } in 1..4)
    }
}
