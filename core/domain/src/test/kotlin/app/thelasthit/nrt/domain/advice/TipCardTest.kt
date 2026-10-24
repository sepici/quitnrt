package app.thelasthit.nrt.domain.advice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TipCardTest {

    private val library = AdviceLibrary.bundled()
    private val breathing = LoggedTip(library.find("breathing_timer")!!, "stressed", slip = null)
    private val meal = LoggedTip(library.find("dose_before_meal")!!, "after_meal", slip = null)
    private val slip = LoggedTip(library.find(AdviceMatcher.AFTER_A_SLIP)!!, tag = null, slip = Slip.OVER_GUIDE)
    private val tomorrow = TODAY.plusDays(1)

    @Test
    fun `a tip shows after a dose until the day ends`() {
        val card = TipCard.afterLog(null, TODAY, breathing, "dose-1")
        assertEquals(ShowingTip("breathing_timer", "stressed", null, "dose-1"), card?.showingOn(TODAY))
        assertNull(card?.showingOn(tomorrow))
    }

    @Test
    fun `each tag gets one tip a day`() {
        val first = TipCard.afterLog(null, TODAY, breathing, "dose-1")
        val closed = TipCard.close(first, TODAY)
        assertNull(closed?.showingOn(TODAY))

        // Stressed again the same day: nothing new, and the closed tip stays closed.
        val again = TipCard.afterLog(closed, TODAY, breathing, "dose-2")
        assertNull(again?.showingOn(TODAY))

        // The next day starts over.
        assertEquals("dose-3", TipCard.afterLog(again, tomorrow, breathing, "dose-3")?.showingOn(tomorrow)?.entryId)
    }

    @Test
    fun `a tip for another tag takes the place of the one showing`() {
        val first = TipCard.afterLog(null, TODAY, breathing, "dose-1")
        val second = TipCard.afterLog(first, TODAY, meal, "dose-2")
        assertEquals("dose_before_meal", second?.showingOn(TODAY)?.strategy)
        assertEquals(setOf("stressed", "after_meal"), second?.shown)

        // A dose with the first tag again doesn't bring its tip back.
        assertEquals("dose_before_meal", TipCard.afterLog(second, TODAY, breathing, "dose-3")?.showingOn(TODAY)?.strategy)
    }

    @Test
    fun `a dose with no tip leaves the card as it is`() {
        val card = TipCard.afterLog(null, TODAY, breathing, "dose-1")
        assertEquals(card, TipCard.afterLog(card, TODAY, null, "dose-2"))
        assertNull(TipCard.afterLog(card, tomorrow, null, "dose-3")?.showingOn(tomorrow))
    }

    @Test
    fun `a slip gets one tip a day, whatever tags the doses have`() {
        val card = TipCard.afterLog(null, TODAY, slip, "dose-1")
        assertEquals(ShowingTip(AdviceMatcher.AFTER_A_SLIP, null, Slip.OVER_GUIDE, "dose-1"), card?.showingOn(TODAY))
        val closed = TipCard.close(card, TODAY)
        assertNull(TipCard.afterLog(closed, TODAY, slip, "dose-2")?.showingOn(TODAY))
        assertEquals("breathing_timer", TipCard.afterLog(closed, TODAY, breathing, "dose-3")?.showingOn(TODAY)?.strategy)
    }

    @Test
    fun `taking the dose back takes its tip, and its tag can have one again`() {
        val card = TipCard.afterLog(null, TODAY, breathing, "dose-1")
        val undone = TipCard.undo(card, "dose-1")
        assertNull(undone?.showingOn(TODAY))
        assertEquals(emptySet<String>(), undone?.shown)
        assertEquals("dose-2", TipCard.afterLog(undone, TODAY, breathing, "dose-2")?.showingOn(TODAY)?.entryId)

        // Taking back another dose leaves the tip.
        assertEquals(card, TipCard.undo(card, "dose-9"))
    }

    @Test
    fun `closing on another day changes nothing`() {
        val card = TipCard.afterLog(null, TODAY, breathing, "dose-1")
        assertNull(TipCard.close(card, tomorrow))
        assertNull(TipCard.close(null, TODAY))
    }
}
