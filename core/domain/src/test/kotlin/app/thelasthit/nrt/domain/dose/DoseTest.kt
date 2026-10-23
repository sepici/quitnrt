package app.thelasthit.nrt.domain.dose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DoseTest {

    @Test
    fun `milligram helpers convert to micrograms`() {
        assertEquals(Dose(2000), Dose.mg(2))
        assertEquals(Dose(500), Dose.mg(0.5))
        assertEquals(Dose(1500), Dose.mg(1.5))
        assertEquals(1.875, Dose(1875).milligrams)
    }

    @Test
    fun `sums are exact`() {
        val tenths = List(10) { Dose.mg(0.1) }
        assertEquals(Dose.mg(1), tenths.sum())
    }

    @Test
    fun `doses compare by amount`() {
        assertTrue(Dose.mg(14) < Dose.mg(14.5))
        assertTrue(Dose.mg(98) <= Dose.mg(98))
    }

    @Test
    fun `negative doses are rejected`() {
        assertThrows<IllegalArgumentException> { Dose(-1) }
    }

    @Test
    fun `a typed strength becomes whole micrograms`() {
        assertEquals(Dose(2_000), Dose.parseMg("2"))
        assertEquals(Dose(1_500), Dose.parseMg("1.5"))
        assertEquals(Dose(1_500), Dose.parseMg(" 1,5 "))
        assertEquals(Dose(1_875), Dose.parseMg("1.875"))
        assertEquals(Dose(100_000), Dose.parseMg("100"))
    }

    @Test
    fun `nonsense strengths are refused`() {
        for (text in listOf("", "0", "0.0", "-2", "abc", "1.2345", "101", "2 mg", "1e3", ".5")) {
            assertEquals(null, Dose.parseMg(text), text)
        }
    }
}
