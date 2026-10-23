package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.insights.InsightWindow
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TopTriggersTest {

    @Test
    fun `the window is the 14 full days before today`() {
        assertEquals(InsightWindow(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-22")), AdviceWindow.of(TODAY))
        assertEquals(14, AdviceWindow.of(TODAY).days)
    }

    @Test
    fun `tags are ranked by doses, with shares of every dose in the window`() {
        val entries = daily("13:00", 14, setOf("after_meal")) +
            daily("08:00", 10, setOf("coffee_tea", "stressed")) +
            daily("18:00", 14)
        val triggers = TopTriggers.of(entries, TODAY, builtInGroups)

        assertEquals(38, triggers.doses)
        assertEquals(14, triggers.days)
        assertEquals(listOf("after_meal", "coffee_tea", "stressed"), triggers.ranked.map { it.key })
        assertEquals(TopTrigger("after_meal", TagGroup.ROUTINE, 14, 14 / 38.0), triggers.ranked.first())
        assertEquals(TagGroup.FEELINGS, triggers.ranked.last().group)
        assertTrue(triggers.enoughData)
        assertEquals(triggers.ranked, triggers.top)
    }

    @Test
    fun `only the top 3 are the top triggers, and ties go by key`() {
        val entries = daily("08:00", 5, setOf("woke_up")) + daily("10:00", 5, setOf("bored")) +
            daily("13:00", 5, setOf("after_meal")) + daily("20:00", 4, setOf("drink"))
        val triggers = TopTriggers.of(entries, TODAY, builtInGroups)

        assertEquals(listOf("after_meal", "bored", "woke_up", "drink"), triggers.ranked.map { it.key })
        assertEquals(listOf("after_meal", "bored", "woke_up"), triggers.top.map { it.key })
    }

    @Test
    fun `patches are left out`() {
        val patches = daily("07:30", 14, setOf("woke_up"), form = ProductForm.PATCH_24H)
        val entries = patches + daily("09:00", 12, setOf("bored"))
        val triggers = TopTriggers.of(entries, TODAY, builtInGroups)

        assertEquals(12, triggers.doses)
        assertEquals(listOf("bored"), triggers.ranked.map { it.key })
    }

    @Test
    fun `today and days before the window don't count`() {
        val inside = daily("10:00", 14)
        val todayDose = entry("2026-10-23T09:00", tags = setOf("bored"))
        val tooEarly = (0..4).map { entry("2026-10-08T1$it:00", tags = setOf("bored")) }
        // 01:30 on the 9th counts towards the 8th, so it's out too.
        val lateNight = entry("2026-10-09T01:30", tags = setOf("bored"))
        val triggers = TopTriggers.of(inside + todayDose + tooEarly + lateNight, TODAY, builtInGroups)

        assertEquals(14, triggers.doses)
        assertTrue(triggers.ranked.isEmpty())
    }

    @Test
    fun `a tag needs 3 doses and a group`() {
        val entries = daily("10:00", 12) + daily("13:00", 2, setOf("bored")) + daily("15:00", 3, setOf("my_own"))
        assertTrue(TopTriggers.of(entries, TODAY, builtInGroups).ranked.isEmpty())

        val withOwnTags: (String) -> TagGroup? = { if (it == "my_own") TagGroup.ROUTINE else builtInGroups(it) }
        val triggers = TopTriggers.of(entries, TODAY, withOwnTags)
        assertEquals(listOf(TopTrigger("my_own", TagGroup.ROUTINE, 3, 3 / 17.0)), triggers.ranked)
    }

    @Test
    fun `there's too little data under 10 doses or 3 days`() {
        val nine = TopTriggers.of(daily("10:00", 9, setOf("bored")), TODAY, builtInGroups)
        assertFalse(nine.enoughData)
        assertEquals(listOf("bored"), nine.ranked.map { it.key })
        assertTrue(nine.top.isEmpty())

        val twoDays = (0..5).flatMap { hour ->
            listOf(entry("2026-10-21T1$hour:00", tags = setOf("bored")), entry("2026-10-22T1$hour:00", tags = setOf("bored")))
        }
        assertFalse(TopTriggers.of(twoDays, TODAY, builtInGroups).enoughData)

        val enough = daily("10:00", 3, setOf("bored")) + daily("12:00", 3) + daily("14:00", 3) + daily("16:00", 1)
        val triggers = TopTriggers.of(enough, TODAY, builtInGroups)
        assertTrue(triggers.enoughData)
        assertEquals(listOf("bored"), triggers.top.map { it.key })
    }

    @Test
    fun `a dose with two tags counts for both`() {
        val entries = daily("10:00", 10, setOf("bored", "screen_time")) + daily("12:00", 10)
        val triggers = TopTriggers.of(entries, TODAY, builtInGroups)
        assertEquals(listOf(0.5, 0.5), triggers.ranked.map { it.share })
    }
}
