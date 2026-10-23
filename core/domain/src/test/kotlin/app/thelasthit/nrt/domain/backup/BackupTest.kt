package app.thelasthit.nrt.domain.backup

import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult
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
        assertEquals(BackupException.Reason.NOT_A_BACKUP, assertThrows<BackupException> { Backup.decode("""{"hello": "world"}""") }.reason)
        assertEquals(
            BackupException.Reason.NOT_A_BACKUP,
            assertThrows<BackupException> { Backup.decode(backup.copy(format = "something-else").encode()) }.reason,
        )
        assertEquals(BackupException.Reason.NEWER_VERSION, assertThrows<BackupException> { Backup.decode(backup.copy(version = 3).encode()) }.reason)
    }

    @Test
    fun `a version 2 backup carries the programme and the game`() {
        val gum = Backup.Units("nicorette-gum-uk-2mg", "Nicorette gum 2 mg", ProductForm.GUM, DoseUnit.PIECE, 2000, 12)
        val full = backup.copy(
            settings = backup.settings.copy(demonName = "Gary", demonCreature = "dragon", demonVariant = "ember", soundOn = false, equippedGear = "wooden_stick"),
            programmes = listOf(
                Backup.Programme(
                    id = "p1", status = ProgrammeStatus.ACTIVE, createdAt = "2026-10-16T09:00:00Z",
                    baselineStartedOn = "2026-10-16", baselineStart = "2026-10-16", baselineEnd = "2026-10-22",
                    baselineSource = BaselineSource.MEASURED, baselineMeasuredMicrograms = 23_400,
                    lengthWeeks = 12, nrtFreeWeeks = 2, startDate = "2026-10-24",
                    usualDay = listOf(gum),
                    ladder = listOf(Backup.Step(1, listOf(gum.copy(unitsPerDay = 11)))),
                    weeks = listOf(
                        Backup.Week(
                            number = 1, startDate = "2026-10-24", step = 1, dailyGuideMicrograms = 22_000,
                            weeklyAllowanceMicrograms = 154_000, units = listOf(gum.copy(unitsPerDay = 11)),
                            result = WeekResult.MISSED, resultAt = "2026-11-01T04:00:00Z",
                            choice = MissedWeekChoice.CARRY_ON, choiceAt = "2026-11-01T09:00:00Z",
                        ),
                        Backup.Week(number = 12, startDate = "2027-01-09", kind = WeekKind.PLANNED, dailyGuideMicrograms = 0, weeklyAllowanceMicrograms = 0),
                    ),
                ),
            ),
            demons = listOf(Backup.Demon("d1", "p1", "Gary", "dragon", "ember", maxHp = 120, createdAt = "2026-10-23T14:00:00Z")),
            hitLedger = listOf(Backup.LedgerRow("h1", "p1", LedgerKind.WEEK_MET, 1, week = 1, earnedBy = 1, at = "2026-11-01T04:00:00Z")),
            achievements = listOf(Backup.Unlock("first_entry", "2026-10-16T09:10:00Z")),
            gear = listOf(Backup.Unlock("wooden_stick", "2026-10-24T04:00:00Z")),
            breathingSessions = listOf(Backup.BreathingSession("b1", BreathingPattern.BOX, "2026-10-23T13:00:00Z", 120, 7, true)),
        )
        assertEquals(full, Backup.decode(full.encode()))
        assertTrue("\"weeklyAllowanceUg\": 154000" in full.encode())
    }

    @Test
    fun `a version 1 file still reads, with no programmes or game`() {
        val text = """
            {
              "format": "the-last-hit-backup",
              "version": 1,
              "exportedAt": "2026-10-23T14:05:00Z",
              "settings": { "dayStart": "04:00", "country": "GB", "stoppedSmokingOn": null },
              "products": [],
              "tags": [],
              "entries": []
            }
        """.trimIndent()
        val read = Backup.decode(text)
        assertEquals(1, read.version)
        assertEquals("GB", read.settings.country)
        assertEquals(null, read.settings.demonName)
        assertEquals(null, read.settings.soundOn)
        assertTrue(read.programmes.isEmpty() && read.demons.isEmpty() && read.hitLedger.isEmpty())
        assertTrue(read.achievements.isEmpty() && read.gear.isEmpty() && read.breathingSessions.isEmpty())
    }

    @Test
    fun `new backups are version 2`() {
        assertEquals(2, Backup.VERSION)
        assertTrue("\"version\": 2" in backup.encode())
    }
}
