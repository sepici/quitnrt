package app.thelasthit.nrt.domain.creature

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpriteSheetTest {

    private val dragon = requireNotNull(SpriteSheet.bundled(Creature.DRAGON))

    /** Every creature's file, including the ones waiting for review. */
    private val sheets = Creature.entries.associateWith { requireNotNull(SpriteSheet.inResources(it)) { "No sprite file for $it" } }

    private val conditionFrames = SpriteFrame.entries - SpriteFrame.DEFEAT.toSet()

    @Test
    fun `the dragon loads with every frame and the app's palette as default`() {
        assertEquals(32, dragon.size)
        assertEquals("forest", dragon.defaultVariant)
        assertTrue(dragon.variantNames.containsAll(listOf("forest", "ember", "moss", "frost")))
        for (frame in SpriteFrame.entries) {
            val pixels = dragon.argbPixels(frame)
            assertEquals(32 * 32, pixels.size)
            assertTrue(pixels.any { it == 0 }, "$frame has a transparent background")
        }
        for (frame in conditionFrames) {
            assertTrue(dragon.argbPixels(frame).count { it != 0 } > 200, "$frame has a body")
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
    fun `every creature has every frame and the same four colours as the dragon`() {
        for ((creature, sheet) in sheets) {
            assertEquals(creature.key, sheet.creature)
            assertEquals(32, sheet.size, "$creature")
            assertEquals("forest", sheet.defaultVariant, "$creature")
            assertEquals(dragon.variantNames, sheet.variantNames, "$creature")
            for (frame in conditionFrames) {
                val body = sheet.argbPixels(frame).count { it != 0 }
                assertTrue(body in 200..32 * 32 - 64, "$creature $frame has a body and room around it: $body")
            }
            for (variant in sheet.variantNames) {
                assertEquals(sheet.palette(variant).size, sheet.palette(variant).toSet().size, "$creature $variant has a colour per slot")
            }
        }
    }

    @Test
    fun `every creature stands on the bottom row`() {
        for ((creature, sheet) in sheets) {
            for (frame in conditionFrames + SpriteFrame.DEFEAT_1) {
                val pixels = sheet.argbPixels(frame)
                assertTrue((0 until 32).any { pixels[31 * 32 + it] != 0 }, "$creature $frame reaches the ground")
            }
        }
    }

    @Test
    fun `every colour comes from the master palette`() {
        val text = requireNotNull(SpriteSheet::class.java.getResourceAsStream("/palette.json")).bufferedReader().use { it.readText() }
        val master = Json.parseToJsonElement(text).jsonObject.getValue("ramps").jsonObject.values
            .flatMap { ramp -> ramp.jsonArray.map { (0xFF000000 or it.jsonPrimitive.content.substring(1).toLong(16)).toInt() } }
            .toSet()
        for ((creature, sheet) in sheets) {
            for (variant in sheet.variantNames) {
                val off = sheet.palette(variant).filter { it !in master }.map { "#%06x".format(it and 0xFFFFFF) }
                assertTrue(off.isEmpty(), "$creature $variant uses colours outside the master palette: $off")
            }
        }
    }

    @Test
    fun `broken sheets are rejected`() {
        fun sheet(rows: List<String>, slots: String = """{"o":"outline"}""", colours: String = """{"o":"#000000"}""", frames: List<SpriteFrame> = SpriteFrame.entries) =
            """{"creature":"x","size":8,"slots":$slots,"variants":{"v":$colours},"defaultVariant":"v",
               "frames":{${frames.joinToString(",") { "\"${it.key}\":" + rows.joinToString(",", "[", "]") { r -> "\"$r\"" } }}}}"""
        val good = List(8) { "oooo...." }
        SpriteSheet.parse(sheet(good))
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(good.drop(1))) }
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(List(8) { "oooo..." })) }
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(List(8) { "ooox...." })) }
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(good, colours = """{"o":"black"}""")) }
        assertThrows<IllegalArgumentException> { SpriteSheet.parse(sheet(good, frames = SpriteFrame.entries - SpriteFrame.DEFEAT_4)) }
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

    @Test
    fun `every creature watches from the dark with a pair of eyes in its upper half`() {
        val shape = 0xFF3F3829.toInt()
        val eyes = 0xFFF5B12B.toInt()
        for ((creature, sheet) in sheets) {
            val silhouette = sheet.silhouettePixels(SpriteFrame.IDLE_1, shape, eyes)
            val eyePixels = silhouette.indices.filter { silhouette[it] == eyes }
            assertTrue(eyePixels.size in 1..16, "$creature has ${eyePixels.size} eye pixels")
            assertTrue(eyePixels.all { it / 32 < 20 }, "$creature's eyes are up in its head")
        }
    }

    @Test
    fun `the defeat breaks the creature apart into pixels`() {
        for ((creature, sheet) in sheets) {
            val counts = sheet.defeatPixels().map { frame -> frame.count { it != 0 } }
            assertEquals(4, counts.size)
            assertTrue(counts[0] > 200, "$creature is knocked out in one piece: ${counts[0]}")
            assertTrue(counts[0] > counts[1] && counts[1] > counts[2] && counts[2] > counts[3], "$creature thins out: $counts")
            assertTrue(counts[3] in 4..80, "$creature leaves a few pixels at the end: ${counts[3]}")
        }
    }

    @Test
    fun `a knocked-out creature has its eyes shut`() {
        val shape = 0xFF3F3829.toInt()
        val eyes = 0xFFF5B12B.toInt()
        for ((creature, sheet) in sheets) {
            for (frame in SpriteFrame.DEFEAT) {
                assertTrue(sheet.silhouettePixels(frame, shape, eyes).none { it == eyes }, "$creature $frame")
            }
        }
    }

    @Test
    fun `the defeat takes the chosen colours`() {
        val ember = dragon.palette("ember").toSet()
        for (frame in dragon.defeatPixels("ember")) {
            assertTrue(frame.filter { it != 0 }.all { it in ember })
        }
        assertEquals(dragon.argbPixels(SpriteFrame.DEFEAT_1).toList(), dragon.defeatPixels().first().toList())
    }
}
