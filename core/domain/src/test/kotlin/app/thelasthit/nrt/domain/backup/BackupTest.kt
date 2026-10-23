package app.thelasthit.nrt.domain.backup

import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupTest {

    private val backup = Backup(
        exportedAt = "2026-10-23T14:05:00Z",
        settings = Backup.Settings(dayStart = "04:00", country = "GB"),
        products = listOf(
            Backup.Product(
                id = "nicorette-gum-uk-2mg", catalogueId = "nicorette-gum-uk-2mg", name = "Nicorette gum 2 mg",
                shortName = "Nicorette gum", form = ProductForm.GUM, unit = DoseUnit.PIECE,
                perUnitMicrograms = 2000, labelMaxPerDay = 15, quickLogPosition = 0,
            ),
        ),
        tags = listOf(Backup.Tag("after_meal", TagGroup.ROUTINE, isBuiltIn = true, sortOrder = 10)),
        entries = listOf(
            Backup.Entry(
                id = "e1", productId = "nicorette-gum-uk-2mg", productName = "Nicorette gum 2 mg",
                form = ProductForm.GUM, unit = DoseUnit.PIECE, perUnitMicrograms = 2000, quantity = 1,
                totalMicrograms = 2000, occurredAt = "2026-10-23T12:40:00Z", zone = "Europe/London",
                logicalDate = "2026-10-23", kind = EntryKind.CRAVING, cravingStrength = 3, tags = listOf("after_meal"),
            ),
        ),
    )

    @Test
    fun `a backup survives a round trip`() {
        assertEquals(backup, Backup.decode(backup.encode()))
    }

    @Test
    fun `the file is readable and says what it is`() {
        val text = backup.encode()
        assertTrue("\"format\": \"the-last-hit-backup\"" in text, text)
        assertTrue("\"perUnitUg\": 2000" in text, text)
    }

    @Test
    fun `other files and newer versions are refused`() {
        assertThrows<BackupException> { Backup.decode("""{"hello": "world"}""") }
        assertThrows<BackupException> { Backup.decode(backup.copy(format = "something-else").encode()) }
        assertThrows<BackupException> { Backup.decode(backup.copy(version = 2).encode()) }
    }
}
