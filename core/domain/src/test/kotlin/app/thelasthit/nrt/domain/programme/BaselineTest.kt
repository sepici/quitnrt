package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BaselineTest {

    private val today = date("2026-10-23")

    /** [mg] on each day of October listed, at 10:00. */
    private fun daily(vararg days: Pair<Int, Int>) = days.map { (day, mg) -> dose("2026-10-%02dT10:00".format(day), Dose.mg(mg)) }

    @Test
    fun `the last 7 full days are a baseline at once when 5 of them have doses`() {
        val entries = daily(16 to 20, 17 to 24, 19 to 28, 21 to 22, 22 to 26, 23 to 40)
        val days = BaselineDays.measure(today, entries)
        assertEquals(date("2026-10-16"), days.start)
        assertEquals(date("2026-10-22"), days.end)
        assertEquals(5, days.daysWithDoses)
        assertTrue(days.isReady)
        // The days with nothing logged are left out of the average, and so is today.
        assertEquals(Baseline(Dose.mg(24), BaselineSource.MEASURED), days.baseline)
    }

    @Test
    fun `4 days with doses aren't enough`() {
        val days = BaselineDays.measure(today, daily(16 to 20, 17 to 24, 19 to 28, 21 to 22))
        assertTrue(days.isComplete)
        assertFalse(days.isReady)
        assertNull(days.baseline)
        assertEquals(Dose.mg(23.5), days.averageSoFar)
    }

    @Test
    fun `a baseline started this week builds over its own 7 days`() {
        val days = BaselineDays.measure(today, daily(19 to 20, 20 to 22, 21 to 24, 22 to 26, 23 to 10), startedOn = date("2026-10-19"))
        assertEquals(date("2026-10-19"), days.start)
        assertEquals(date("2026-10-25"), days.end)
        assertEquals(listOf(true, true, true, true, false, false, false), days.days.map { it.isOver })
        assertEquals(4, days.daysWithDoses)
        assertFalse(days.isComplete)
        assertNull(days.baseline)
        assertEquals(Dose.mg(23), days.averageSoFar)
    }

    @Test
    fun `a started baseline is ready once its seventh day is over`() {
        val entries = daily(19 to 20, 20 to 22, 22 to 26, 24 to 30, 25 to 22)
        val started = date("2026-10-19")
        val building = BaselineDays.measure(date("2026-10-25"), entries, started)
        assertEquals(started, building.start)
        assertFalse(building.isReady)
        val days = BaselineDays.measure(date("2026-10-26"), entries, started)
        assertEquals(started, days.start)
        assertEquals(Dose.mg(24), days.baseline?.daily)
    }

    @Test
    fun `days logged before a baseline was started count when there are enough of them`() {
        val entries = daily(16 to 20, 17 to 24, 18 to 24, 19 to 28, 20 to 24, 21 to 24)
        val days = BaselineDays.measure(today, entries, startedOn = today)
        assertEquals(date("2026-10-16"), days.start)
        assertTrue(days.isReady)
    }

    @Test
    fun `when its 7 days end with too few logged, the window moves on a day at a time`() {
        val entries = daily(12 to 20, 14 to 20, 16 to 20, 18 to 20, 19 to 30, 20 to 30)
        val started = date("2026-10-12")
        val first = BaselineDays.measure(date("2026-10-19"), entries, started)
        assertEquals(started, first.start)
        assertFalse(first.isReady)
        val next = BaselineDays.measure(date("2026-10-20"), entries, started)
        assertEquals(date("2026-10-13"), next.start)
        assertFalse(next.isReady)
        val ready = BaselineDays.measure(date("2026-10-21"), entries, started)
        assertEquals(date("2026-10-14"), ready.start)
        assertEquals(Dose.mg(24), ready.baseline?.daily)
    }

    @Test
    fun `a dose after midnight counts toward the evening before`() {
        val entries = daily(16 to 20, 17 to 20, 18 to 20, 19 to 20) + dose("2026-10-23T01:30", Dose.mg(4))
        val days = BaselineDays.measure(today, entries)
        assertEquals(Dose.mg(4), days.days.last().total)
        assertTrue(days.isReady)
    }

    @Test
    fun `the main product is the one with the most mg on the days counted`() {
        val gum = (16..22).map { day -> dose("2026-10-${day}T09:00", Dose.mg(18)) }
        val patches = (16..22).map { day -> dose("2026-10-${day}T07:00", Dose.mg(21), PATCH) }
        assertEquals(PATCH, BaselineDays.measure(today, gum + patches).mainProductId)
        // Today is still going, so a lot of gum today doesn't count.
        assertEquals(PATCH, BaselineDays.measure(today, gum + patches + dose("2026-10-23T09:00", Dose.mg(100))).mainProductId)
        assertEquals(GUM, BaselineDays.measure(today, gum + patches + dose("2026-10-22T20:00", Dose.mg(22))).mainProductId)
        assertNull(BaselineDays.measure(today, emptyList()).mainProductId)
    }

    @Test
    fun `a typed usual amount is a baseline straight away`() {
        val typed = Baseline.typed(Dose.mg(24))
        assertEquals(BaselineSource.MANUAL, typed.source)
        assertEquals(Dose.mg(168), typed.weekly)
        assertThrows<IllegalArgumentException> { Baseline.typed(Dose.ZERO) }
    }
}
