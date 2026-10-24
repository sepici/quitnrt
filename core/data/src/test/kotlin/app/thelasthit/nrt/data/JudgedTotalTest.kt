package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.WeekReport
import app.thelasthit.nrt.domain.programme.WeekResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The total each week's result was judged on: written once with the result, and carried by backups from version 3. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JudgedTotalTest {

    private lateinit var data: TestData
    private lateinit var other: TestData

    @Before
    fun setUp() {
        data = TestData()
        other = TestData()
    }

    @After
    fun tearDown() {
        data.close()
        other.close()
    }

    /** A gum plan whose week 1 is missed at 224 mg, with its result in at 09:00 on Sunday 1 November. */
    private suspend fun TestData.missWeekOne() {
        startGumPlan()
        chewAllWeekOne()
        setTime("2026-11-01T09:00")
    }

    @Test
    fun aResultIsStoredWithTheTotalItWasJudgedOn() = runTest {
        data.missWeekOne()
        val week = checkNotNull(data.programmes.current()?.plan).week(1)
        assertEquals(WeekResult.MISSED, week.result)
        assertEquals(Dose.mg(224), week.resultTotal)
        val row = checkNotNull(data.database.programmes().current()).weeks.sortedBy { it.number }
        assertEquals(224_000L, row[0].resultTotalMicrograms)
        assertNull(row[1].resultTotalMicrograms)
    }

    @Test
    fun aDoseBackdatedAfterTheResultLeavesTheTotalAsItWasJudged() = runTest {
        data.missWeekOne()
        data.programmes.settle()
        data.chew("2026-10-29T10:00", pieces = 2)
        data.setTime("2026-11-08T09:00")
        val plan = checkNotNull(data.programmes.current()?.plan)
        assertEquals(Dose.mg(224), plan.week(1).resultTotal)
        // Week 2 came in at nothing, as nothing was logged in it.
        assertEquals(Dose.ZERO, plan.week(2).resultTotal)

        val entries = data.entries.observeRange(PLAN_START, LocalDate.parse("2026-11-08")).first()
        assertEquals(Dose.mg(232), plan.week(1).totalOf(entries))
        val report = WeekReport.of(plan, entries, LocalDate.parse("2026-11-08")).first()
        assertEquals(Dose.mg(224), report.total)
        assertFalse(report.changedSince)
    }

    @Test
    fun aBackupCarriesTheJudgedTotals() = runTest {
        data.missWeekOne()
        val backup = data.backups.export()
        assertEquals(3, backup.version)
        val weeks = backup.programmes.single().weeks
        assertEquals(224_000L, weeks.first().resultTotalMicrograms)
        assertTrue(weeks.drop(1).all { it.resultTotalMicrograms == null })
        assertTrue("\"resultTotalUg\": 224000" in backup.encode())

        other.setTime("2026-11-01T09:00")
        other.backups.import(Backup.decode(backup.encode()), ImportMode.REPLACE)
        assertEquals(Dose.mg(224), checkNotNull(other.programmes.current()?.plan).week(1).resultTotal)
        assertEquals(data.programmes.current()?.plan, other.programmes.current()?.plan)
    }

    @Test
    fun aVersion2BackupRestoresItsResultsWithNoTotal() = runTest {
        data.missWeekOne()
        // As a version 2 app wrote it: the same file without the totals.
        val text = data.backups.export().copy(version = 2).encode().replace(Regex("\\n\\s*\"resultTotalUg\": [^\\n]*"), "")
        assertFalse("resultTotalUg" in text)
        val versionTwo = Backup.decode(text)
        assertEquals(2, versionTwo.version)

        other.setTime("2026-11-01T09:00")
        other.backups.import(versionTwo, ImportMode.REPLACE)
        val week = checkNotNull(other.programmes.current()?.plan).week(1)
        assertEquals(WeekResult.MISSED, week.result)
        assertNull(week.resultTotal)
    }

    @Test
    fun aTotalOnAWeekStillToComeIsLeftOut() = runTest {
        data.missWeekOne()
        val exported = data.backups.export()
        val odd = exported.copy(
            programmes = exported.programmes.map { programme ->
                programme.copy(weeks = programme.weeks.map { if (it.number == 2) it.copy(resultTotalMicrograms = 5_000) else it })
            },
        )
        other.setTime("2026-11-01T09:00")
        other.backups.import(odd, ImportMode.REPLACE)
        val plan = checkNotNull(other.programmes.current()?.plan)
        assertEquals(Dose.mg(224), plan.week(1).resultTotal)
        assertNull(plan.week(2).resultTotal)
    }
}
