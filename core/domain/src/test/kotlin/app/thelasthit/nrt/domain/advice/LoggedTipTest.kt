package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoggedTipTest {

    private val matcher = AdviceMatcher(AdviceLibrary.bundled())

    private fun tip(vararg tags: String, form: ProductForm = ProductForm.GUM, slip: Slip? = null): LoggedTip? =
        matcher.afterLogging(entry("2026-10-23T15:05", form = form, tags = tags.toSet()), builtInGroups, slip)

    @Test
    fun `stressed gets the breathing timer, as an interactive strategy that names the tag comes first`() {
        assertEquals(LoggedTip(AdviceLibrary.bundled().find("breathing_timer")!!, "stressed", slip = null), tip("stressed"))
        assertEquals("breathing_timer", tip("after_argument")!!.strategy.key)
        assertEquals("delay_timer", tip("autopilot")!!.strategy.key)
        // Without that rule, keeping your hands busy would come first, as PLAN's table has it.
        assertEquals("busy_hands", matcher.matches("autopilot", TagGroup.HABIT, setOf(ProductForm.GUM)).first().strategy.key)
    }

    @Test
    fun `an interactive strategy only jumps ahead among those that name the tag`() {
        // The delay timer suits any time, but a meal has advice of its own.
        assertEquals("dose_before_meal", tip("after_meal")!!.strategy.key)
        assertEquals("wind_down", tip("tired")!!.strategy.key)

        val matcher = AdviceMatcher(
            library(
                strategy("timer", groups = listOf(TagGroup.FEELINGS), interactive = Interactive.DELAY_TIMER),
                strategy("anxious_own", tags = listOf("anxious")),
            ),
        )
        val dose = entry("2026-10-23T15:05", tags = setOf("anxious"))
        assertEquals("anxious_own", matcher.afterLogging(dose, builtInGroups)!!.strategy.key)
    }

    @Test
    fun `a dose with two tags gets the best match across them`() {
        assertEquals(listOf("breathing_timer", "stressed"), tip("coffee_tea", "stressed")!!.let { listOf(it.strategy.key, it.tag) })
        assertEquals(listOf("dose_before_meal", "after_meal"), tip("bored", "after_meal")!!.let { listOf(it.strategy.key, it.tag) })
    }

    @Test
    fun `the dose's own form decides advice about one kind of product`() {
        val matcher = AdviceMatcher(
            library(
                strategy("chewing", tags = listOf("drink"), forms = listOf(ProductForm.GUM, ProductForm.MINI_LOZENGE)),
                strategy("any_form", tags = listOf("drink", "socialising")),
            ),
        )
        fun drinkTip(form: ProductForm) = matcher.afterLogging(entry("2026-10-23T21:05", form = form, tags = setOf("drink")), builtInGroups)!!.strategy.key
        assertEquals("chewing", drinkTip(ProductForm.GUM))
        assertEquals("chewing", drinkTip(ProductForm.MINI_LOZENGE))
        assertEquals("any_form", drinkTip(ProductForm.MOUTH_SPRAY))
        // In the bundled advice, deciding your limit leads for a drink, whatever the form.
        assertEquals("drinks_limit", tip("drink")!!.strategy.key)
        assertEquals("drinks_limit", tip("drink", form = ProductForm.MOUTH_SPRAY)!!.strategy.key)
    }

    @Test
    fun `no tip for a patch, a dose with no tags, or tags with no group`() {
        val patch = entry("2026-10-23T07:00", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14), tags = setOf("stressed"))
        assertNull(matcher.afterLogging(patch, builtInGroups))
        assertNull(tip())
        assertNull(tip("deleted_tag"))

        val withOwn: (String) -> TagGroup? = { if (it == "school_run") TagGroup.ROUTINE else builtInGroups(it) }
        val own = matcher.afterLogging(entry("2026-10-23T08:40", tags = setOf("school_run")), withOwn)!!
        assertEquals(listOf("four_ds", "school_run"), listOf(own.strategy.key, own.tag))
    }

    @Test
    fun `after a slip, the tip is the advice for that, and never otherwise`() {
        assertEquals("low_mood", tip("low")!!.strategy.key)
        for (slip in Slip.entries) {
            val tip = tip("low", slip = slip)!!
            assertEquals("after_a_slip", tip.strategy.key)
            assertNull(tip.tag)
            assertEquals(slip, tip.slip)
        }
        assertEquals("after_a_slip", tip("bored", slip = Slip.OVER_GUIDE)!!.strategy.key)
        assertEquals("after_a_slip", tip(slip = Slip.OVER_GUIDE)!!.strategy.key)
    }
}
