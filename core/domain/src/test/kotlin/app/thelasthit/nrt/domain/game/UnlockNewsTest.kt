package app.thelasthit.nrt.domain.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import app.thelasthit.nrt.domain.time.DayBoundary
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class UnlockNewsTest {

    @Test
    fun `nothing new shows nothing`() {
        assertEquals(emptyList<UnlockNews>(), NewUnlocks.NONE.news)
    }

    @Test
    fun `a few badges get a toast each, then their gear gets its card`() {
        val unlocks = NewUnlocks(
            listOf(Achievement.FIRST_WEEK_MET, Achievement.FIRST_DAY_IN_GUIDE, Achievement.WEEK_10_PERCENT_UNDER),
            listOf(Gear.WOODEN_SWORD, Gear.IRON_SWORD),
        )
        assertEquals(
            listOf(
                UnlockNews.Badge(Achievement.FIRST_WEEK_MET),
                UnlockNews.Badge(Achievement.FIRST_DAY_IN_GUIDE),
                UnlockNews.Badge(Achievement.WEEK_10_PERCENT_UNDER),
                UnlockNews.NewGear(Gear.WOODEN_SWORD),
                UnlockNews.NewGear(Gear.IRON_SWORD),
            ),
            unlocks.news,
        )
    }

    @Test
    fun `gear on its own gets its card`() {
        assertEquals(listOf(UnlockNews.NewGear(Gear.WAR_HAMMER)), NewUnlocks(emptyList(), listOf(Gear.WAR_HAMMER)).news)
    }

    @Test
    fun `more than 3 badges at once come in one summary card with their gear`() {
        val badges = listOf(
            Achievement.FIRST_ENTRY,
            Achievement.LOGGED_3_DAYS,
            Achievement.LOGGED_7_DAYS,
            Achievement.BASELINE_DONE,
        )
        val unlocks = NewUnlocks(badges, listOf(Gear.WOODEN_SWORD))
        assertEquals(listOf(UnlockNews.Summary(badges, listOf(Gear.WOODEN_SWORD))), unlocks.news)
    }

    @Test
    fun `the shelf holds every badge in PLAN's groups and order, earned or not`() {
        val at = Instant.parse("2026-10-23T14:05:00Z")
        val shelf = TrophyShelf.of(mapOf("first_entry" to at, "weeks_met_3" to at, "from_a_later_version" to at), DayBoundary(), LONDON)

        assertEquals(Achievement.Group.entries, shelf.groups.map { it.group })
        assertEquals(Achievement.entries, shelf.badges.map { it.achievement })
        assertEquals(33, shelf.total)
        assertEquals(2, shelf.earned)
        val first = shelf.badges.first()
        assertTrue(first.isEarned)
        assertEquals(at, first.earnedAt)
        assertEquals(LocalDate.parse("2026-10-23"), first.earnedOn)
        val later = shelf.badges.first { it.achievement == Achievement.LOGGED_3_DAYS }
        assertFalse(later.isEarned)
        assertNull(later.earnedAt)
        assertNull(later.earnedOn)
    }

    @Test
    fun `a badge earned before the day starts counts toward the day before, as History has it`() {
        val late = LocalDateTime.parse("2026-10-24T02:30").atZone(LONDON).toInstant()
        val shelf = TrophyShelf.of(mapOf("first_entry" to late), DayBoundary(), LONDON)
        assertEquals(LocalDate.parse("2026-10-23"), shelf.badges.first().earnedOn)

        // With the day starting at midnight, it's the calendar day.
        val midnight = TrophyShelf.of(mapOf("first_entry" to late), DayBoundary(LocalTime.MIDNIGHT), LONDON)
        assertEquals(LocalDate.parse("2026-10-24"), midnight.badges.first().earnedOn)
    }

    @Test
    fun `an outline keeps only an icon's edge pixels, in one colour`() {
        val badges = IconSheet.bundled(IconSheet.Kind.BADGES)
        val outline = 0xFF858A81.toInt()
        for (achievement in Achievement.entries) {
            val icon = badges.icon(achievement)
            val full = icon.argbPixels()
            val edge = icon.outlinePixels(outline)
            assertEquals(full.size, edge.size)
            assertTrue(edge.all { it == 0 || it == outline }, "${achievement.key} is drawn in the outline colour only")
            assertTrue(edge.indices.all { edge[it] == 0 || full[it] != 0 }, "${achievement.key}'s outline stays on the icon")
            val drawn = edge.count { it != 0 }
            assertTrue(drawn in 20 until full.count { it != 0 }, "${achievement.key} has an outline and leaves its inside empty: $drawn")
        }
    }

    @Test
    fun `an outline is the shape's edge, with the inside left out`() {
        val text = """
            {"size": 8, "colours": {"k": "#0b2017"}, "icons": {"block": [
              "........",
              ".kkkkkk.",
              ".kkkkkk.",
              ".kkkkkk.",
              ".kkkkkk.",
              ".kkkkkk.",
              ".kkkkkk.",
              "........"
            ]}}
        """.trimIndent()
        val icon = requireNotNull(IconSheet.parse(text, MasterPalette.bundled())["block"])
        val rows = icon.outlinePixels(1).toList().chunked(8).map { row -> row.joinToString("") { if (it == 0) "." else "o" } }
        assertEquals(
            listOf("........", ".oooooo.", ".o....o.", ".o....o.", ".o....o.", ".o....o.", ".oooooo.", "........"),
            rows,
        )
    }
}

private val LONDON: ZoneId = ZoneId.of("Europe/London")
