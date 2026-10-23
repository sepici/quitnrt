package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdviceMatchingTest {

    private val bundled = AdviceMatcher(AdviceLibrary.bundled())
    private val gum = setOf(ProductForm.GUM)
    private val spray = setOf(ProductForm.MOUTH_SPRAY)

    private fun keys(matches: List<StrategyMatch>) = matches.map { it.strategy.key }

    private fun trigger(key: String, group: TagGroup) = TopTrigger(key, group, doses = 10, share = 0.3)

    private fun group(key: String) = BuiltInTags.find(key)!!.group

    @Test
    fun `the tag's own strategies come first, then its group's, then the any-time ones`() {
        val matcher = AdviceMatcher(
            library(
                strategy("any_time", groups = EVERY_GROUP),
                strategy("feelings", groups = listOf(TagGroup.FEELINGS)),
                strategy("any_time_named", tags = listOf("stressed"), groups = EVERY_GROUP),
                strategy("wide", tags = listOf("stressed", "anxious", "angry", "bored")),
                strategy("first_narrow", tags = listOf("stressed")),
                strategy("second_narrow", tags = listOf("stressed")),
                strategy("body", tags = listOf("tired")),
            ),
            planOrder = emptyMap(),
        )
        val matches = matcher.matches("stressed", TagGroup.FEELINGS, gum)

        // Narrower before wider within a level, and the library's order between equals.
        assertEquals(listOf("first_narrow", "second_narrow", "wide", "any_time_named", "feelings", "any_time"), keys(matches))
        assertEquals(
            listOf(MatchLevel.TAG, MatchLevel.TAG, MatchLevel.TAG, MatchLevel.TAG, MatchLevel.GROUP, MatchLevel.ANY_TIME),
            matches.map { it.level },
        )
    }

    @Test
    fun `the strategies PLAN's table gives a tag lead its own, in the table's order`() {
        val library = library(
            strategy("narrow", tags = listOf("strong_craving")),
            strategy("pace", tags = listOf("strong_craving", "withdrawal")),
            strategy("any_time", tags = listOf("strong_craving"), groups = EVERY_GROUP),
            strategy("group", groups = listOf(TagGroup.BODY)),
        )
        val plan = mapOf("strong_craving" to listOf("any_time", "pace"))
        val matches = AdviceMatcher(library, plan).matches("strong_craving", TagGroup.BODY, gum)
        assertEquals(listOf("any_time", "pace", "narrow", "group"), keys(matches))
        // Without the table, the narrowest comes first.
        assertEquals(listOf("narrow", "pace", "any_time", "group"), keys(AdviceMatcher(library, emptyMap()).matches("strong_craving", TagGroup.BODY, gum)))
        // Another tag keeps the usual order.
        assertEquals(listOf("pace", "group", "any_time"), keys(AdviceMatcher(library, plan).matches("withdrawal", TagGroup.BODY, gum)))
        // A strategy that only suits the tag's group never jumps the tag's own.
        val groupFirst = mapOf("strong_craving" to listOf("group", "pace"))
        assertEquals(listOf("pace", "narrow", "any_time", "group"), keys(AdviceMatcher(library, groupFirst).matches("strong_craving", TagGroup.BODY, gum)))
    }

    @Test
    fun `the bundled advice follows PLAN's table for every trigger in it`() {
        for ((key, row) in AdviceMatcher.PLAN_ORDER) {
            assertEquals(row, keys(bundled.matches(key, group(key), gum)).take(row.size), key)
        }
        // Every row of PLAN §9: the 4 Ds first for withdrawal and cravings, then asking about the pace.
        assertEquals(listOf("four_ds", "ask_about_pace"), keys(bundled.matches("strong_craving", TagGroup.BODY, spray)).take(2))
        assertEquals(listOf("four_ds", "ask_about_pace"), keys(bundled.matches("withdrawal", TagGroup.BODY, spray)).take(2))
        for (key in listOf("drink", "socialising")) assertEquals("drinks_limit", keys(bundled.matches(key, group(key), gum)).first())
        for (key in listOf("stressed", "anxious", "angry", "after_argument")) {
            assertEquals("breathing_timer", keys(bundled.matches(key, group(key), gum)).first())
        }
        for (key in listOf("bored", "screen_time", "autopilot")) assertEquals("busy_hands", keys(bundled.matches(key, group(key), gum)).first())
        for (key in listOf("happy", "excited", "celebrating")) {
            assertEquals(listOf("another_treat", "milestone_treats"), keys(bundled.matches(key, group(key), gum)).take(2))
        }
        assertEquals("dose_before_meal", keys(bundled.matches("after_meal", TagGroup.ROUTINE, gum)).first())
        assertEquals("leave_the_table", keys(bundled.matches("coffee_tea", TagGroup.ROUTINE, gum)).first())
        assertEquals("morning_routine", keys(bundled.matches("woke_up", TagGroup.ROUTINE, gum)).first())
        // Any time: the 4 Ds.
        assertEquals("four_ds", keys(bundled.matches("school_run", TagGroup.ROUTINE, gum)).first())

        val tired = bundled.matches("tired", TagGroup.BODY, gum)
        assertEquals(listOf("wind_down", "foggy_day"), keys(tired.filter { it.level == MatchLevel.TAG }))
        assertEquals(listOf("withdrawal_passes", "ride_the_wave"), keys(tired.filter { it.level == MatchLevel.GROUP }))
        assertEquals(listOf("four_ds", "delay_timer", "reasons_list", "quit_coach"), keys(tired.filter { it.level == MatchLevel.ANY_TIME }))
    }

    @Test
    fun `PLAN's table names built-in tags and strategies that suit them`() {
        val library = AdviceLibrary.bundled()
        for ((key, row) in AdviceMatcher.PLAN_ORDER) {
            val tag = BuiltInTags.find(key)
            assertTrue(tag != null, key)
            for (strategy in row) assertTrue(key in library.find(strategy)!!.tags, "$key: $strategy")
        }
    }

    @Test
    fun `a tag people added gets its group's strategies and the any-time ones`() {
        val own = bundled.matches("work_deadline", TagGroup.FEELINGS, gum)
        assertEquals("talk_back", own.first().strategy.key)
        assertEquals(MatchLevel.GROUP, own.first().level)
        assertTrue(own.drop(1).all { it.level == MatchLevel.ANY_TIME })

        val routine = bundled.matches("school_run", TagGroup.ROUTINE, gum)
        assertTrue(routine.isNotEmpty() && routine.all { it.level == MatchLevel.ANY_TIME }, "$routine")
    }

    @Test
    fun `advice about one kind of product is kept to people who use it`() {
        assertTrue("gum_and_drinks" in keys(bundled.matches("drink", TagGroup.ROUTINE, gum)))
        assertTrue("gum_and_drinks" in keys(bundled.matches("drink", TagGroup.ROUTINE, setOf(ProductForm.MOUTH_SPRAY, ProductForm.LOZENGE))))

        val sprayAndPatch = keys(bundled.matches("drink", TagGroup.ROUTINE, setOf(ProductForm.MOUTH_SPRAY, ProductForm.PATCH_24H)))
        assertFalse("gum_and_drinks" in sprayAndPatch)
        assertEquals(listOf("drinks_limit", "ready_line"), sprayAndPatch.take(2))

        // How to use gum and lozenges is for people who use them.
        assertEquals("use_it_right", keys(bundled.matches("strong_craving", TagGroup.BODY, gum))[2])
        for (forms in listOf(spray, setOf(ProductForm.INHALATOR), setOf(ProductForm.MOUTH_SPRAY, ProductForm.PATCH_24H))) {
            assertFalse("use_it_right" in keys(bundled.matches("strong_craving", TagGroup.BODY, forms)), "$forms")
            assertFalse("use_it_right" in keys(bundled.matches("withdrawal", TagGroup.BODY, forms)), "$forms")
        }

        // With no products known, only advice for every form is left.
        assertFalse("gum_and_drinks" in keys(bundled.matches("after_meal", TagGroup.ROUTINE, emptySet())))
        assertTrue("dose_before_meal" in keys(bundled.matches("after_meal", TagGroup.ROUTINE, emptySet())))
    }

    @Test
    fun `after a slip only comes up when the caller says there was one`() {
        assertFalse("after_a_slip" in keys(bundled.matches("low", TagGroup.FEELINGS, gum)))
        for (slip in Slip.entries) {
            val match = bundled.matches("low", TagGroup.FEELINGS, gum, slip).single { it.strategy.key == "after_a_slip" }
            assertEquals(MatchLevel.TAG, match.level)
        }
        // A slip doesn't make it suit tags it isn't for.
        assertFalse("after_a_slip" in keys(bundled.matches("bored", TagGroup.FEELINGS, gum, Slip.MISSED_WEEK)))
        val lists = bundled.forTriggers(listOf(trigger("low", TagGroup.FEELINGS), trigger("bored", TagGroup.FEELINGS)), gum, each = 10)
        assertTrue(lists.flatMap { keys(it.strategies) }.none { it == "after_a_slip" })
    }

    @Test
    fun `no strategy is repeated across the top triggers`() {
        val lists = bundled.forTriggers(
            listOf(trigger("after_meal", TagGroup.ROUTINE), trigger("coffee_tea", TagGroup.ROUTINE), trigger("stressed", TagGroup.FEELINGS)),
            gum,
        )

        assertEquals(listOf("after_meal", "coffee_tea", "stressed"), lists.map { it.trigger.key })
        assertEquals(listOf("dose_before_meal", "leave_the_table", "short_walk"), keys(lists[0].strategies))
        // Getting up from the table and the short walk suit coffee and stress too, but after a meal has them.
        assertEquals(listOf("gum_and_drinks", "swap_drink", "four_ds"), keys(lists[1].strategies))
        assertEquals(listOf("breathing_timer", "muscle_relaxation", "ride_the_wave"), keys(lists[2].strategies))
        val all = lists.flatMap { keys(it.strategies) }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `a trigger keeps its best match when the triggers before it took everything`() {
        val matcher = AdviceMatcher(
            library(strategy("shared", tags = listOf("stressed", "anxious")), strategy("anxious_own", tags = listOf("anxious"))),
        )
        val stressed = trigger("stressed", TagGroup.FEELINGS)

        val partly = matcher.forTriggers(listOf(stressed, trigger("anxious", TagGroup.FEELINGS)), gum)
        assertEquals(listOf(listOf("shared"), listOf("anxious_own")), partly.map { keys(it.strategies) })

        val angry = trigger("angry", TagGroup.FEELINGS)
        val onlyShared = AdviceMatcher(library(strategy("shared", tags = listOf("stressed", "angry"))))
        assertEquals(listOf(listOf("shared"), listOf("shared")), onlyShared.forTriggers(listOf(stressed, angry), gum).map { keys(it.strategies) })
    }

    @Test
    fun `each trigger gets at most 3, and the same input always gives the same lists`() {
        val triggers = listOf(trigger("bored", TagGroup.FEELINGS), trigger("strong_craving", TagGroup.BODY))
        val lists = bundled.forTriggers(triggers, gum)
        assertTrue(lists.all { it.strategies.size == AdviceMatcher.PER_TRIGGER })
        assertEquals(listOf("busy_hands", "change_rooms", "dose_decision"), keys(lists[0].strategies))
        assertEquals(listOf("four_ds", "ask_about_pace", "use_it_right"), keys(lists[1].strategies))
        assertEquals(listOf("four_ds", "ask_about_pace", "distraction_list"), keys(bundled.forTriggers(triggers, spray)[1].strategies))
        assertEquals(lists, bundled.forTriggers(triggers, gum))
        assertTrue(bundled.forTriggers(emptyList(), gum).isEmpty())
    }
}
