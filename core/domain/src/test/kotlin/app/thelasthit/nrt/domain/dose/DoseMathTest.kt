package app.thelasthit.nrt.domain.dose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class DoseMathTest {

    @Test
    fun `quantity times dose per unit`() {
        assertEquals(Dose.mg(6), DoseMath.total(Dose.mg(2), 3))
        assertEquals(Dose.mg(2), DoseMath.total(Dose.mg(1), 2)) // two QuickMist sprays
    }

    @Test
    fun `quantity must be at least one`() {
        assertThrows<IllegalArgumentException> { DoseMath.total(Dose.mg(2), 0) }
    }

    @Test
    fun `an inhalator session is an eighth of a cartridge and eight of them add back up exactly`() {
        val session = DoseMath.inhalatorPerUnit(Dose.mg(15), InhalatorUnit.SESSION)
        assertEquals(Dose(1875), session)
        assertEquals(Dose.mg(15), List(8) { session }.sum())
    }

    @Test
    fun `a whole cartridge counts in full`() {
        assertEquals(Dose.mg(15), DoseMath.inhalatorPerUnit(Dose.mg(15), InhalatorUnit.CARTRIDGE))
    }

    @Test
    fun `puffs need the user's own puffs-per-cartridge figure`() {
        assertThrows<IllegalArgumentException> { DoseMath.inhalatorPerUnit(Dose.mg(15), InhalatorUnit.PUFF) }
        assertThrows<IllegalArgumentException> { DoseMath.inhalatorPerUnit(Dose.mg(15), InhalatorUnit.PUFF, 0) }
        // 15 mg over 80 puffs is 187.5 µg, rounded up to 188.
        assertEquals(Dose(188), DoseMath.inhalatorPerUnit(Dose.mg(15), InhalatorUnit.PUFF, 80))
    }

    @Test
    fun `a patch counts in full unless it came off early`() {
        assertEquals(Dose.mg(21), DoseMath.patch(Dose.mg(21), ratedHours = 24))
        assertEquals(Dose.mg(10.5), DoseMath.patch(Dose.mg(21), ratedHours = 24, worn = Duration.ofHours(12)))
        assertEquals(Dose.mg(15), DoseMath.patch(Dose.mg(15), ratedHours = 16, worn = Duration.ofHours(16)))
        assertEquals(Dose.ZERO, DoseMath.patch(Dose.mg(21), ratedHours = 24, worn = Duration.ZERO))
    }

    @Test
    fun `wearing a patch past its rated hours doesn't count extra`() {
        assertEquals(Dose.mg(25), DoseMath.patch(Dose.mg(25), ratedHours = 16, worn = Duration.ofHours(20)))
    }

    @Test
    fun `negative wear time is rejected`() {
        assertThrows<IllegalArgumentException> {
            DoseMath.patch(Dose.mg(21), ratedHours = 24, worn = Duration.ofHours(-1))
        }
    }
}
