package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.BackupRepository
import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.backup.Backup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRepositoryTest {

    private lateinit var source: TestData
    private lateinit var target: TestData
    private val today = LocalDate.parse("2026-10-23")

    private fun backups(data: TestData) = BackupRepository(data.database, data.settings, data.entries, data.clock)

    @Before
    fun setUp() = runTest {
        source = TestData()
        target = TestData()
        source.tags.ensureBuiltIns()
        source.settings.setCountry("GB")
        source.products.addFromCatalogue("nicorette-gum-uk-2mg")
        source.entries.log(LogRequest("nicorette-gum-uk-2mg", at = london("2026-10-23T08:10"), tagKeys = setOf("coffee_tea")))
        source.entries.log(LogRequest("nicorette-gum-uk-2mg", at = london("2026-10-23T13:40"), note = "After lunch, \"finally\""))
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun anExportRestoresOntoAnotherPhone() = runTest {
        val file = backups(source).export().encode()
        val summary = backups(target).import(Backup.decode(file), ImportMode.REPLACE)
        assertEquals(1, summary.products)
        assertEquals(2, summary.entries)

        val restored = target.entries.observeDay(today).first()
        assertEquals(source.entries.observeDay(today).first(), restored)
        assertEquals("GB", target.settings.current().country)
        assertEquals(listOf("nicorette-gum-uk-2mg"), target.products.quickLog.first().map { it.id })
    }

    @Test
    fun mergingTwiceDoesntDuplicateAnything() = runTest {
        val backup = backups(source).export()
        backups(target).import(backup, ImportMode.MERGE)
        val again = backups(target).import(backup, ImportMode.MERGE)
        assertEquals(0, again.entries)
        assertEquals(2, again.skipped)
        assertEquals(2, target.entries.observeDay(today).first().entries.size)
    }

    @Test
    fun replaceWipesWhatWasThere() = runTest {
        target.products.addFromCatalogue("nicorette-quickmist-uk-1mg")
        target.entries.log(LogRequest("nicorette-quickmist-uk-1mg"))
        backups(target).import(backups(source).export(), ImportMode.REPLACE)
        val day = target.entries.observeDay(today).first()
        assertTrue(day.entries.none { it.productId == "nicorette-quickmist-uk-1mg" })
        assertEquals(2, day.entries.size)
    }

    @Test
    fun brokenEntriesAreSkippedAndEntriesMoveToThisPhonesDayStart() = runTest {
        val backup = backups(source).export()
        val broken = backup.entries.first().copy(id = "broken", occurredAt = "not a time")
        target.settings.setDayStart(LocalTime.of(9, 0))
        val summary = backups(target).import(backup.copy(entries = backup.entries + broken), ImportMode.MERGE)
        assertEquals(1, summary.skipped)
        // With a 09:00 day start the 08:10 dose belongs to the day before.
        assertEquals(1, target.entries.observeDay(today).first().entries.size)
    }

    @Test
    fun csvHasAHeaderAndARowPerEntry() = runTest {
        val csv = backups(source).exportCsv().trimEnd().lines()
        assertEquals(3, csv.size)
        assertTrue(csv[2].contains("\"After lunch, \"\"finally\"\"\""))
    }
}
