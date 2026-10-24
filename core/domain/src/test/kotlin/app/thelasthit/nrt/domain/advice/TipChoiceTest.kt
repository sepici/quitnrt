package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.programme.planExample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Which tip a dose gets after logging, given what has had one today, and which advice Insights'
 * top trigger gets.
 */
class TipChoiceTest {

    private val matcher = AdviceMatcher(AdviceLibrary.bundled())
    private val gum = setOf(ProductForm.GUM)

    private fun dose(at: String, vararg tags: String, mg: Int = 2): Entry = entry(at, perUnit = Dose.mg(mg), tags = tags.toSet())

    @Test
    fun `a tag that has had today's tip is left out, so the best of the others comes`() {
        val both = dose("2026-10-23T15:05", "stressed", "bored")
        assertEquals("breathing_timer", matcher.afterLogging(both, builtInGroups)!!.strategy.key)

        val bored = matcher.afterLogging(both, builtInGroups, shown = setOf("stressed"))!!
        assertEquals(listOf("busy_hands", "bored"), listOf(bored.strategy.key, bored.tag))

        assertNull(matcher.afterLogging(dose("2026-10-23T16:05", "stressed"), builtInGroups, shown = setOf("stressed")))
        assertNull(matcher.afterLogging(both, builtInGroups, shown = setOf("stressed", "bored")))
    }

    @Test
    fun `once a slip has had its tip, a dose gets its own tag's`() {
        val stressed = dose("2026-10-23T15:05", "stressed")
        assertEquals("after_a_slip", matcher.afterLogging(stressed, builtInGroups, Slip.OVER_GUIDE)!!.strategy.key)
        assertEquals("breathing_timer", matcher.afterLogging(stressed, builtInGroups, Slip.OVER_GUIDE, setOf(TipCard.SLIP))!!.strategy.key)
    }

    @Test
    fun `a patch gets the tip after a slip, and no other`() {
        val patch = entry("2026-10-23T07:00", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14), tags = setOf("stressed"))
        assertEquals("after_a_slip", matcher.afterLogging(patch, builtInGroups, Slip.OVER_GUIDE)!!.strategy.key)
        assertNull(matcher.afterLogging(patch, builtInGroups))
        assertNull(matcher.afterLogging(patch, builtInGroups, Slip.OVER_GUIDE, setOf(TipCard.SLIP)))
    }

    /** A day of PLAN's first example, in week 1 with a 20 mg guide, logged a dose at a time as Today does. */
    @Test
    fun `after the slip's tip is closed, the next dose gets its own tag's tip`() {
        val plan = planExample()
        val today = LocalDate.parse("2026-10-27")
        val day = mutableListOf<Entry>()
        var card: TipCard? = null
        fun log(dose: Entry): ShowingTip? {
            day += dose
            val slip = Slips.afterLog(plan, dose, day, today)
            val tip = matcher.afterLogging(dose, builtInGroups, slip, card?.takeIf { it.date == today }?.shown.orEmpty())
            card = TipCard.afterLog(card, today, tip, dose.id)
            return card?.showingOn(today)
        }

        assertNull(log(dose("2026-10-27T09:00", mg = 16)))
        assertEquals(Slip.OVER_GUIDE, log(dose("2026-10-27T13:00", "after_meal", mg = 6))?.slip)
        card = TipCard.close(card, today)

        val stressed = log(dose("2026-10-27T15:00", "stressed"))
        assertEquals(listOf("breathing_timer", "stressed"), listOf(stressed?.strategy, stressed?.tag))
        assertEquals(setOf(TipCard.SLIP, "stressed"), card?.shown)

        // The same tag again brings nothing new, and the tip showing stays.
        assertEquals(stressed, log(dose("2026-10-27T17:00", "stressed")))
    }

    @Test
    fun `Insights has the top trigger's timing tip whatever the day`() {
        val meals = daily("13:00", 14, setOf("after_meal")) + daily("19:05", 10, setOf("after_meal")) +
            daily("10:00", 14, setOf("coffee_tea")) + daily("16:00", 14)
        val triggers = TopTriggers.of(meals, TODAY, builtInGroups)
        val tips = TimingTips.of(meals, TODAY, DAY_START)

        val advice = matcher.insights(triggers, tips, gum)!!
        assertEquals("dose_before_meal", advice.strategy.key)
        assertEquals("after_meal", advice.timing?.tag)
        assertEquals("after_meal", (advice.reason as TodayAdvice.Reason.Trigger).trigger.key)
        // Today's card turns daily, so on most days it has no timing tip.
        val todays = (0L..2L).map { matcher.today(triggers, tips, gum, TODAY.plusDays(it))!!.timing }
        assertEquals(2, todays.count { it == null })
        assertEquals(advice, matcher.personal(meals, TODAY, DAY_START, builtInGroups, gum).insights)
    }

    @Test
    fun `without a timing tip, Insights has the trigger's best strategy, and never a slip's`() {
        val stressed = daily("21:00", 14, setOf("stressed")) + daily("10:00", 14)
        val triggers = TopTriggers.of(stressed, TODAY, builtInGroups)
        assertEquals("breathing_timer", matcher.insights(triggers, emptyList(), gum)!!.strategy.key)
        assertNull(matcher.insights(triggers, emptyList(), gum)!!.timing)

        val slipped = matcher.personal(stressed, TODAY, DAY_START, builtInGroups, gum, Slip.MISSED_WEEK)
        assertEquals("after_a_slip", slipped.today!!.strategy.key)
        assertEquals("breathing_timer", slipped.insights!!.strategy.key)

        assertNull(matcher.insights(TopTriggers.of(daily("13:00", 9, setOf("after_meal")), TODAY, builtInGroups), emptyList(), gum))
    }
}
