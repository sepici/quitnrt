package app.thelasthit.nrt.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PixelIconsTest {

    private val palette = MasterPalette.bundled()
    private val gear = IconSheet.bundled(IconSheet.Kind.GEAR, palette)
    private val badges = IconSheet.bundled(IconSheet.Kind.BADGES, palette)

    @Test
    fun `every gear has an icon and every icon is gear`() {
        for (item in Gear.entries) assertNotNull(gear[item.key], "${item.key} has an icon")
        assertEquals(Gear.entries.map { it.key }.toSet(), gear.keys)
    }

    @Test
    fun `every achievement has a badge and every badge is an achievement`() {
        for (achievement in Achievement.entries) assertNotNull(badges[achievement.key], "${achievement.key} has a badge")
        assertEquals(Achievement.entries.map { it.key }.toSet(), badges.keys)
    }

    @Test
    fun `every icon is 16 px square and uses at most 8 colours, all from the master palette`() {
        for (sheet in listOf(gear, badges)) {
            assertEquals(16, sheet.size)
            for (key in sheet.keys) {
                val icon = sheet[key]!!
                assertTrue(icon.colours.size <= 8, "$key uses ${icon.colours.size} colours")
                assertTrue(palette.letters.containsAll(icon.colours), "$key stays in the palette")
                val pixels = icon.argbPixels()
                assertEquals(16 * 16, pixels.size)
                val allowed = icon.colours.map { palette[it] }.toSet()
                assertTrue(pixels.filter { it != 0 }.all { it in allowed }, "$key draws only its own colours")
                assertTrue(pixels.any { it == 0 }, "$key has a transparent background")
                assertTrue(pixels.count { it != 0 } > 30, "$key has a body")
            }
        }
    }

    @Test
    fun `the master palette has about 24 colours, all different`() {
        assertTrue(palette.size in 20..24, "${palette.size} colours")
        assertEquals(palette.size, palette.letters.map { palette[it] }.toSet().size)
    }

    @Test
    fun `PLAN's badge table has 33 achievements in five groups, and the badge file groups them the same`() {
        val counts = Achievement.entries.groupingBy { it.group }.eachCount()
        assertEquals(
            mapOf(
                Achievement.Group.LOGGING to 14,
                Achievement.Group.MATCHING to 9,
                Achievement.Group.BEATING to 3,
                Achievement.Group.HABITS to 6,
                Achievement.Group.THE_END to 1,
            ),
            counts,
        )
        for (group in Achievement.Group.entries) {
            assertEquals(
                Achievement.entries.filter { it.group == group }.map { it.key },
                badges.groups[group.key],
                "${group.key} lists its badges in order",
            )
        }
    }

    @Test
    fun `badges in a group share their frame, so they read as a set`() {
        for (group in Achievement.Group.entries) {
            val shapes = Achievement.entries.filter { it.group == group }
                .map { badges.icon(it).silhouettePixels(1).toList() }
            assertTrue(shapes.all { it == shapes.first() }, "${group.key} badges share one outline")
        }
        val frames = Achievement.Group.entries.map { group ->
            badges.icon(Achievement.entries.first { it.group == group }).silhouettePixels(1).toList()
        }
        assertEquals(frames.size, frames.toSet().size, "each group has a frame of its own")
    }

    @Test
    fun `gear starts with the wooden stick and ends with the legendary blade`() {
        assertEquals(Gear.WOODEN_STICK, Gear.entries.first())
        assertEquals(Gear.LEGENDARY_BLADE, Gear.entries.last())
        assertEquals(Gear.AXE, Gear.fromKey("axe"))
        assertEquals(null, Gear.fromKey("nope"))
        assertEquals(Achievement.HONEST_LEDGER, Achievement.fromKey("honest_ledger"))
    }

    @Test
    fun `a locked badge is its outline in one colour`() {
        val icon = badges.icon(Achievement.DEMON_DEFEATED)
        val shade = 0xFF3F3829.toInt()
        val locked = icon.silhouettePixels(shade)
        assertEquals(icon.argbPixels().map { it == 0 }, locked.map { it == 0 })
        assertTrue(locked.filter { it != 0 }.all { it == shade })
    }

    @Test
    fun `broken icon files are refused`() {
        val small = MasterPalette.parse("""{"colours":{"k":"#000000","a":"#111111","b":"#222222","c":"#333333",
            "d":"#444444","e":"#555555","f":"#666666","g":"#777777","h":"#888888"}}""")
        fun sheet(rows: List<String>, groups: String = "{}") =
            """{"size":8,"groups":$groups,"icons":{"x":${rows.joinToString(",", "[", "]") { "\"$it\"" }}}}"""
        val good = List(8) { "kkkk...." }
        IconSheet.parse(sheet(good), small)
        assertThrows<IllegalArgumentException> { IconSheet.parse(sheet(good.drop(1)), small) }
        assertThrows<IllegalArgumentException> { IconSheet.parse(sheet(List(8) { "kkkk..." }), small) }
        assertThrows<IllegalArgumentException> { IconSheet.parse(sheet(List(8) { "kkkz...." }), small) }
        assertThrows<IllegalArgumentException> { IconSheet.parse(sheet(listOf("kabcdefg", "h.......") + List(6) { "........" }), small) }
        assertThrows<IllegalArgumentException> { IconSheet.parse(sheet(good, """{"g":["missing"]}"""), small) }
        assertThrows<IllegalArgumentException> { MasterPalette.parse("""{"colours":{"k":"black"}}""") }
    }
}
