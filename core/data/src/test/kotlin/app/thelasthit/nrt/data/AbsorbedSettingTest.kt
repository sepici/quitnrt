package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.dose.Dose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** "Show estimated absorbed amounts" (ROADMAP P9-1): off by default, kept, and carried by a backup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AbsorbedSettingTest {

    private lateinit var source: TestData
    private lateinit var target: TestData

    @Before
    fun setUp() {
        source = TestData()
        target = TestData()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun offUntilSwitchedOn() = runTest {
        assertFalse(source.settings.current().showAbsorbed)
        source.settings.setShowAbsorbed(true)
        assertTrue(source.settings.current().showAbsorbed)
        source.settings.setShowAbsorbed(false)
        assertFalse(source.settings.current().showAbsorbed)
    }

    @Test
    fun replacingFromABackupBringsTheSettingAndMergingKeepsThePhones() = runTest {
        source.settings.setShowAbsorbed(true)
        val file = source.backups.export().encode()
        assertEquals(true, Backup.decode(file).settings.showAbsorbed)

        target.backups.import(Backup.decode(file), ImportMode.MERGE)
        assertFalse(target.settings.current().showAbsorbed)
        target.backups.import(Backup.decode(file), ImportMode.REPLACE)
        assertTrue(target.settings.current().showAbsorbed)
    }

    @Test
    fun aBackupFromBeforeTheSettingLeavesThePhonesAsItIs() = runTest {
        target.settings.setShowAbsorbed(true)
        val older = source.backups.export().let { it.copy(settings = it.settings.copy(showAbsorbed = null)) }
        target.backups.import(older, ImportMode.REPLACE)
        assertTrue(target.settings.current().showAbsorbed)
    }

    /** The setting only changes what screens show: what's logged and totalled stays in labelled mg. */
    @Test
    fun switchingItOnLeavesTheLoggedAmountsAlone() = runTest {
        source.tags.ensureBuiltIns()
        source.products.addFromCatalogue("nicorette-gum-uk-4mg")
        source.entries.log(LogRequest("nicorette-gum-uk-4mg", quantity = 2, at = london("2026-10-23T08:10")))
        val day = LocalDate.parse("2026-10-23")
        val before = source.entries.observeDay(day).first()
        source.settings.setShowAbsorbed(true)
        val after = source.entries.observeDay(day).first()
        assertEquals(before, after)
        assertEquals(Dose.mg(8), after.total)
    }
}
