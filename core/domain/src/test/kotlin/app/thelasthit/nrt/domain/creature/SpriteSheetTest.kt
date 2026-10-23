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
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow

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
            val pair = clusters(silhouette.indices.filter { silhouette[it] == eyes }.toSet(), sheet.size)
            assertEquals(2, pair.size, "$creature has ${pair.size} separate eyes")
            val rows = silhouette.indices.filter { silhouette[it] != 0 }.map { it / sheet.size }
            val middle = (rows.min() + rows.max()) / 2.0
            for (eye in pair) {
                assertTrue(eye.all { it / sheet.size < middle }, "$creature's eyes are in the upper half of its body")
            }
        }
    }

    @Test
    fun `clusters keep apart pixels that only touch at a corner`() {
        // . e . .
        // e e . e
        // . . e .
        assertEquals(listOf(3, 1, 1), clusters(setOf(1, 4, 5, 7, 10), size = 4).map { it.size })
        // a pixel at the end of a row doesn't join the start of the next
        assertEquals(2, clusters(setOf(3, 4), size = 4).size)
    }

    @Test
    fun `the hit and each condition show at a glance`() {
        for ((creature, sheet) in sheets) {
            for ((from, to) in listOf(
                SpriteFrame.IDLE_1 to SpriteFrame.HURT,
                SpriteFrame.IDLE_1 to SpriteFrame.ROUGH,
                SpriteFrame.ROUGH to SpriteFrame.BARELY,
            )) {
                val changed = visibleChanges(sheet.argbPixels(from), sheet.argbPixels(to))
                assertTrue(changed >= 40, "$creature's $to differs from $from in only $changed pixels you'd notice")
            }
        }
    }

    @Test
    fun `a step along a colour ramp isn't a visible change, and a step out of the shadow is`() {
        val forest = dragon.palette("forest")
        val outline = forest[0]
        val dark = forest[1]
        val mid = forest[2]
        val light = forest[3]
        val belly = forest[4]
        assertEquals(0, visibleChanges(intArrayOf(outline, dark, mid), intArrayOf(dark, mid, light)))
        assertEquals(3, visibleChanges(intArrayOf(dark, mid, light), intArrayOf(light, belly, 0)))
        // outline against nothing shows on the light sky, though not on the dark one
        assertEquals(1, visibleChanges(intArrayOf(outline), intArrayOf(0)))
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

    /** [pixels], as indices into a [size] x [size] frame, grouped into 4-connected clusters. */
    private fun clusters(pixels: Set<Int>, size: Int): List<Set<Int>> {
        val left = pixels.toMutableSet()
        val found = mutableListOf<Set<Int>>()
        while (left.isNotEmpty()) {
            val start = left.first().also { left.remove(it) }
            val cluster = mutableSetOf(start)
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                val x = p % size
                for (n in listOfNotNull((p - 1).takeIf { x > 0 }, (p + 1).takeIf { x < size - 1 }, p - size, p + size)) {
                    if (left.remove(n)) {
                        cluster += n
                        queue += n
                    }
                }
            }
            found += cluster
        }
        return found
    }

    /**
     * How many pixels change between two frames in a way you'd notice at the lair's size: a
     * lightness change of at least 20 in CIE L*, with transparent pixels taking the lair's light or
     * dark sky (the theme's stageSky), whichever shows the change. One step along a creature's
     * colour ramp, outline to dark, dark to mid or mid to light, is less than that.
     */
    private fun visibleChanges(a: IntArray, b: IntArray): Int = a.indices.count { i ->
        a[i] != b[i] && SKIES.any { sky ->
            abs(lightness(if (a[i] == 0) sky else a[i]) - lightness(if (b[i] == 0) sky else b[i])) >= 20
        }
    }

    private fun lightness(argb: Int): Double {
        fun linear(channel: Int): Double = (channel / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        val y = 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)
        return if (y <= 216.0 / 24389) y * 24389 / 27 else 116 * cbrt(y) - 16
    }

    private companion object {
        val SKIES = listOf(0xFFFBEFD0.toInt(), 0xFF16120C.toInt())
    }
}
