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
}
