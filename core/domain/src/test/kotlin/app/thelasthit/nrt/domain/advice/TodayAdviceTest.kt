package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.insights.InsightWindow
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TodayAdviceTest {

    private val matcher = AdviceMatcher(AdviceLibrary.bundled())
    private val gum = setOf(ProductForm.GUM)

    /** The first date from today that starts the rotation, so its pick is the best match. */
    private val firstOfRotation: LocalDate = generateSequence(TODAY) { it.plusDays(1) }.first { it.toEpochDay() % 3 == 0L }

    /** After a meal at lunch and dinner, coffee first thing, and an untagged afternoon dose. */
    private val meals: List<Entry> = daily(
        "12:50", "13:00", "13:10", "13:05", "12:55", "13:00", "13:15", "13:00", "12:45", "13:20", "13:00", "13:05", "12:55", "13:00",
        tags = setOf("after_meal"),
    ) + daily("19:05", 10, setOf("after_meal")) + daily("10:00", 14, setOf("coffee_tea")) + daily("16:00", 14)

    private fun advise(entries: List<Entry>, date: LocalDate, forms: Set<ProductForm> = gum, slip: Slip? = null): TodayAdvice? {
        val triggers = TopTriggers.of(entries, TODAY, builtInGroups)
        return matcher.today(triggers, TimingTips.of(entries, TODAY, DAY_START), forms, date, slip)
    }

    @Test
    fun `the top trigger's best match, with the tag, its share and the period for the reason line`() {
        val advice = advise(meals, firstOfRotation)!!

        assertEquals("dose_before_meal", advice.strategy.key)
        val reason = advice.reason as TodayAdvice.Reason.Trigger
        assertEquals(TopTrigger("after_meal", TagGroup.ROUTINE, 24, 24 / 52.0), reason.trigger)
        assertEquals(InsightWindow(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-22")), reason.window)
    }

    @Test
    fun `it turns daily through the top trigger's first 3, and a date always gives the same one`() {
        val days = (0L..5L).map { advise(meals, firstOfRotation.plusDays(it))!!.strategy.key }
        assertEquals(
            listOf("dose_before_meal", "leave_the_table", "gum_and_drinks", "dose_before_meal", "leave_the_table", "gum_and_drinks"),
            days,
        )
        assertEquals(advise(meals, firstOfRotation.plusDays(1)), advise(meals, firstOfRotation.plusDays(1)))
    }

    @Test
    fun `the rotation keeps to the products in use`() {
        val spray = setOf(ProductForm.MOUTH_SPRAY)
        val days = (0L..2L).map { advise(meals, firstOfRotation.plusDays(it), spray)!!.strategy.key }
        assertEquals(listOf("dose_before_meal", "leave_the_table", "busy_mouth"), days)
    }

    @Test
    fun `the timing tip comes with the advice on the days its strategy is shown`() {
        val timing = advise(meals, firstOfRotation)!!.timing!!
        assertEquals("after_meal", timing.tag)
        assertEquals("dose_before_meal", timing.strategy)
        assertNull(advise(meals, firstOfRotation.plusDays(1))!!.timing)
    }

    @Test
    fun `nothing when there's too little data or no dose has a tag`() {
        assertNull(advise(daily("13:00", 9, setOf("after_meal")), firstOfRotation))
        assertNull(advise(daily("13:00", 14) + daily("19:00", 14), firstOfRotation))
        assertNull(advise(emptyList(), firstOfRotation))
    }

    @Test
    fun `after a slip comes first when the caller says there was one, and never otherwise`() {
        val low = daily("21:00", 14, setOf("low")) + daily("10:00", 14)
        val picks = (0L..5L).map { advise(low, firstOfRotation.plusDays(it))!!.strategy.key }
        assertTrue("after_a_slip" !in picks, "$picks")
        assertEquals("low_mood", picks.first())

        for (slip in Slip.entries) {
            val advice = advise(low, firstOfRotation, slip = slip)!!
            assertEquals("after_a_slip", advice.strategy.key)
            assertEquals(TodayAdvice.Reason.AfterSlip(slip), advice.reason)
            assertNull(advice.timing)
        }
        // A missed week is worth a word even before there's enough to show triggers.
        assertEquals("after_a_slip", advise(emptyList(), firstOfRotation, slip = Slip.MISSED_WEEK)!!.strategy.key)
    }

    @Test
    fun `personal advice brings the triggers, their strategies, the tips and Today's card together`() {
        val advice = matcher.personal(meals, TODAY, DAY_START, builtInGroups, gum)

        assertEquals(listOf("after_meal", "coffee_tea"), advice.triggers.top.map { it.key })
        assertEquals(listOf("after_meal", "coffee_tea"), advice.forTriggers.map { it.trigger.key })
        assertEquals(listOf("after_meal"), advice.tips.map { it.tag })
        assertEquals(advise(meals, TODAY), advice.today)

        val sparse = matcher.personal(daily("13:00", 9, setOf("after_meal")), TODAY, DAY_START, builtInGroups, gum)
        assertTrue(sparse.forTriggers.isEmpty() && sparse.tips.isEmpty())
        assertNull(sparse.today)

        val slipped = matcher.personal(meals, TODAY, DAY_START, builtInGroups, gum, Slip.OVER_GUIDE)
        assertEquals("after_a_slip", slipped.today!!.strategy.key)
    }
}
