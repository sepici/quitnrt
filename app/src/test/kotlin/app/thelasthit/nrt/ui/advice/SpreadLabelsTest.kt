package app.thelasthit.nrt.ui.advice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The suggested times over the hour strip: centred on their carets, unless that makes them overlap. */
class SpreadLabelsTest {

    private val strip = 364

    @Test
    fun `times far apart sit centred over their carets`() {
        assertEquals(listOf(84, 184), spreadLabels(listOf(100, 200), listOf(32, 32), strip, gap = 4))
    }

    @Test
    fun `two times 90 minutes apart move apart around their middle, and no longer overlap`() {
        // 10:00 and 11:30 on a 04:00 day: 23 apart, for labels 32 wide.
        val lefts = spreadLabels(listOf(91, 114), listOf(32, 32), strip, gap = 4)

        assertEquals(lefts[0] + 32 + 4, lefts[1])
        // Each moves out by the same amount, so between them they're still centred on the carets.
        assertNear(91 + 114, lefts[0] + 16 + lefts[1] + 16, within = 1)
    }

    @Test
    fun `three close times spread out around the middle one`() {
        val lefts = spreadLabels(listOf(100, 123, 146), listOf(32, 32, 32), strip, gap = 4)

        assertEquals(listOf(71, 107, 143), lefts)
    }

    @Test
    fun `times at the ends stay inside the strip`() {
        assertEquals(listOf(0), spreadLabels(listOf(5), listOf(32), strip, gap = 4))
        assertEquals(listOf(strip - 32), spreadLabels(listOf(360), listOf(32), strip, gap = 4))

        val lefts = spreadLabels(listOf(350, 362), listOf(32, 32), strip, gap = 4)
        assertEquals(listOf(strip - 32 - 4 - 32, strip - 32), lefts)
    }

    @Test
    fun `a time moved out of the way can push into the next one, which moves too`() {
        val lefts = spreadLabels(listOf(100, 120, 170), listOf(40, 40, 40), strip, gap = 4)

        assertTrue(lefts.zipWithNext().all { (left, right) -> left + 40 + 4 <= right })
    }

    private fun assertNear(expected: Int, actual: Int, within: Int) =
        assertTrue("$actual isn't within $within of $expected", kotlin.math.abs(expected - actual) <= within)
}
