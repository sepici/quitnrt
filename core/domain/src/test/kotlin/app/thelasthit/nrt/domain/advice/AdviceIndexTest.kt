package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class AdviceIndexTest {

    private val library = AdviceLibrary.bundled()
    private val matcher = AdviceMatcher(library)
    private val gum = setOf(ProductForm.GUM)

    private fun section(key: String): TagGroup? = AdviceIndex.sectionOf(library.find(key)!!)

    @Test
    fun `every strategy sits in one section, with the ones for any moment first`() {
        val sections = AdviceIndex.sections(library, forms = emptySet())

        assertEquals(listOf(null) + TagGroup.entries, sections.map { it.group })
        assertEquals(library.strategies.map { it.key }.sorted(), sections.flatMap { s -> s.strategies.map { it.key } }.sorted())
        assertEquals(listOf("four_ds", "delay_timer", "reasons_list", "quit_coach"), sections.first().strategies.map { it.key })
    }

    @Test
    fun `a strategy sits with the group most of its tags are in, a group it names counting double`() {
        assertEquals(TagGroup.FEELINGS, section("breathing_timer"))
        assertEquals(TagGroup.ROUTINE, section("dose_before_meal"))
        assertEquals(TagGroup.PEOPLE, section("ready_line"))
        // It names Body, which outweighs its three feelings.
        assertEquals(TagGroup.BODY, section("ride_the_wave"))
        // It names Habit, and tags one habit, one feeling and two routines.
        assertEquals(TagGroup.HABIT, section("dose_decision"))
        // Equal on every group it tags, so the earlier group.
        assertEquals(TagGroup.FEELINGS, section("change_rooms"))
        assertNull(section("four_ds"))
    }

    @Test
    fun `strategies about some forms only show to people who use one`() {
        fun shown(forms: Set<ProductForm>) = AdviceIndex.sections(library, forms).flatMap { s -> s.strategies.map { it.key } }

        assertTrue("gum_and_drinks" in shown(gum))
        assertFalse("gum_and_drinks" in shown(setOf(ProductForm.PATCH_16H)))
        assertTrue("gum_and_drinks" in shown(emptySet()))
    }

    @Test
    fun `a row names up to 3 tags, its section's first`() {
        assertEquals(listOf("strong_craving", "withdrawal", "stressed"), AdviceIndex.rowTags(library.find("ride_the_wave")!!))
        assertEquals(listOf("after_meal"), AdviceIndex.rowTags(library.find("dose_before_meal")!!))
        assertEquals(listOf("strong_craving", "withdrawal", "after_exercise"), AdviceIndex.rowTags(library.find("four_ds")!!))
    }

    /** After a meal at lunch and dinner, coffee mid-morning and an untagged afternoon dose: 52 doses. */
    private val meals: List<Entry> = daily("13:00", 14, setOf("after_meal")) + daily("19:05", 10, setOf("after_meal")) +
        daily("10:00", 14, setOf("coffee_tea")) + daily("16:00", 14)

    private fun personal(entries: List<Entry>) = matcher.personal(entries, TODAY, DAY_START, builtInGroups, gum)

    private fun context(key: String, entries: List<Entry> = meals) =
        StrategyContext.of(library.find(key)!!, personal(entries), matcher, gum, builtInGroups)

    @Test
    fun `a strategy for a top trigger has the trigger, its timing tip and the trigger's other strategies`() {
        val context = context("dose_before_meal")

        assertEquals("after_meal", context.tag)
        assertEquals("after_meal", context.trigger!!.key)
        assertEquals("dose_before_meal", context.tip!!.strategy)
        assertEquals(52, context.doses)
        assertEquals(24, context.tagged)
        assertTrue(context.fromLogs)
        assertEquals(listOf("leave_the_table", "short_walk"), context.also.map { it.key })
    }

    @Test
    fun `the other strategies are the rest of the trigger's, as the list shows them`() {
        val advice = personal(meals).forTriggers.single { it.trigger.key == "coffee_tea" }
        assertEquals(listOf("gum_and_drinks", "swap_drink", "four_ds"), advice.strategies.map { it.strategy.key })

        val context = context("gum_and_drinks")
        assertEquals("coffee_tea", context.tag)
        assertNull(context.tip)
        assertEquals(listOf("swap_drink", "four_ds"), context.also.map { it.key })
    }

    @Test
    fun `a trigger left with one strategy fills the rest from the strategies for its tag`() {
        val both = listOf("after_meal", "coffee_tea")
        val small = library(
            strategy("one", tags = both),
            strategy("two", tags = both),
            strategy("three", tags = both),
            strategy("four", tags = listOf("coffee_tea")),
        )
        val smallMatcher = AdviceMatcher(small, planOrder = emptyMap())
        val personal = smallMatcher.personal(meals, TODAY, DAY_START, builtInGroups, gum)
        // After a meal takes the first three, so coffee is left with the fourth alone.
        assertEquals(listOf("four"), personal.forTriggers.single { it.trigger.key == "coffee_tea" }.strategies.map { it.strategy.key })

        val context = StrategyContext.of(small.find("four")!!, personal, smallMatcher, gum, builtInGroups)
        assertEquals("coffee_tea", context.tag)
        assertEquals(listOf("one", "two"), context.also.map { it.key })
    }

    @Test
    fun `a strategy for no top trigger is about its own first tag, with nothing from the logs`() {
        val context = context("hunger_check")

        assertEquals("hungry", context.tag)
        assertNull(context.trigger)
        assertNull(context.tip)
        assertFalse(context.fromLogs)
        assertTrue(context.also.none { it.key == "hunger_check" })
        assertTrue(context.also.all { "hungry" in it.tags })
    }

    @Test
    fun `with too little logged, a page still lists other strategies for its tag`() {
        val context = context("dose_before_meal", entries = daily("13:00", 2, setOf("after_meal")))

        assertNull(context.trigger)
        assertNull(context.tip)
        assertEquals(listOf("leave_the_table", "short_walk"), context.also.map { it.key })
    }

    @Test
    fun `the hour strip adds up half hours, from the day start`() {
        val tip = personal(meals).tips.single { it.tag == "after_meal" }
        val hours = tip.hourly

        assertEquals(24, hours.size)
        assertEquals(tip.doses, hours.sum())
        // 13:00 is 9 hours after a 04:00 day start, and 19:05 is 15.
        assertEquals(14, hours[9])
        assertEquals(10, hours[15])
        assertEquals(510, minutesIntoDay(LocalTime.of(12, 30), DAY_START))
        assertEquals(1410, minutesIntoDay(LocalTime.of(3, 30), DAY_START))
        assertEquals(0, minutesIntoDay(DAY_START, DAY_START))
    }
}
