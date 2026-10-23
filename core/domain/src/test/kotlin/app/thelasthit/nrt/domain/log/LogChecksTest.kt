package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.RollingLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class LogChecksTest {

    private val gumAt1340 = entry("2026-10-23T13:40:00", source = EntrySource.WIDGET)

    @Test
    fun `the same product from another source within a minute looks like a duplicate`() {
        val found = LogChecks.possibleDuplicateOf(
            "nicorette-gum-uk-2mg", london("2026-10-23T13:40:45"), EntrySource.APP_FUNCTION, listOf(gumAt1340),
        )
        assertEquals(gumAt1340, found)
    }

    @Test
    fun `same source, another product or more than a minute apart is not a duplicate`() {
        val existing = listOf(gumAt1340)
        assertNull(LogChecks.possibleDuplicateOf("nicorette-gum-uk-2mg", london("2026-10-23T13:40:30"), EntrySource.WIDGET, existing))
        assertNull(LogChecks.possibleDuplicateOf("nicorette-gum-uk-4mg", london("2026-10-23T13:40:30"), EntrySource.APP, existing))
        assertNull(LogChecks.possibleDuplicateOf("nicorette-gum-uk-2mg", london("2026-10-23T13:41:01"), EntrySource.APP, existing))
    }

    @Test
    fun `inhalator cartridge limits convert to sessions and puffs`() {
        val sixCartridges = LabelLimit(maxUnitsPerDay = 6)
        assertEquals(48, LogChecks.limitInLoggedUnit(sixCartridges, DoseUnit.CARTRIDGE, DoseUnit.SESSION).maxUnitsPerDay)
        assertEquals(6, LogChecks.limitInLoggedUnit(sixCartridges, DoseUnit.CARTRIDGE, DoseUnit.CARTRIDGE).maxUnitsPerDay)
        assertEquals(480, LogChecks.limitInLoggedUnit(sixCartridges, DoseUnit.CARTRIDGE, DoseUnit.PUFF, 80).maxUnitsPerDay)
        assertEquals(LabelLimit.NONE, LogChecks.limitInLoggedUnit(sixCartridges, DoseUnit.CARTRIDGE, DoseUnit.PUFF))
    }

    @Test
    fun `going over the daily limit is flagged, reaching it is not`() {
        val limit = LabelLimit(maxUnitsPerDay = 15)
        val fourteen = List(14) { entry("2026-10-23T%02d:00".format(5 + it)) }
        assertNull(LogChecks.labelWarning(limit, 1, london("2026-10-23T20:00"), fourteen))
        val fifteen = fourteen + entry("2026-10-23T20:00")
        assertEquals(LabelWarning.OverDaily(16, 15), LogChecks.labelWarning(limit, 1, london("2026-10-23T21:00"), fifteen))
    }

    @Test
    fun `rolling windows only count recent doses`() {
        val hourly = RollingLimit(4, Duration.ofHours(1))
        val limit = LabelLimit(maxUnitsPerDay = 64, window = hourly)
        val spray = { at: String -> entry(at, productId = "quickmist", form = ProductForm.MOUTH_SPRAY, perUnit = Dose.mg(1), quantity = 2) }
        val recent = listOf(spray("2026-10-23T14:10"), spray("2026-10-23T14:40"))
        assertEquals(LabelWarning.OverWindow(5, hourly), LogChecks.labelWarning(limit, 1, london("2026-10-23T15:05"), recent))

        val older = listOf(spray("2026-10-23T14:04"), spray("2026-10-23T14:40"))
        assertNull(LogChecks.labelWarning(limit, 1, london("2026-10-23T15:05"), older))
    }
}
