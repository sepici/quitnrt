package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.BackupRepository
import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.SpendResult
import app.thelasthit.nrt.data.repo.StoredCounts
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.programme.HitBalance
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekGoals
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRepositoryTest {

    private lateinit var source: TestData
    private lateinit var target: TestData
    private val today = LocalDate.parse("2026-10-23")

    private fun backups(data: TestData) = data.backups

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

    // Version 2: the programme and the game.

    /**
     * Two weeks of a gum plan with Gary: week 1 missed and carried on from, week 2 met, which
     * pays it back, and one hit spent. Plus a badge, gear, a breathing session and the sound off.
     */
    private suspend fun TestData.playTwoWeeks() {
        settings.setDemon("Gary", Creature.DRAGON, "ember")
        startGumPlan()
        chewAllWeekOne()
        setTime("2026-11-01T09:00")
        assertTrue(programmes.chooseAfterMiss(1, MissedWeekChoice.CARRY_ON))
        setTime("2026-11-08T09:00")
        programmes.settle()
        assertTrue(game.spendHit() is SpendResult.Spent)
        game.unlockAchievement("first_week_met", london("2026-11-08T04:00"))
        game.unlockGear("wooden_sword", london("2026-11-08T04:00"))
        game.equip("wooden_sword")
        settings.setSoundOn(false)
        breathing.record(BreathingPattern.BOX, london("2026-11-02T10:00"), Duration.ofSeconds(64), rounds = 4, ranToEnd = true)
    }

    private suspend fun assertSameGame(expected: TestData, actual: TestData) {
        assertEquals(expected.programmes.current(), actual.programmes.current())
        assertEquals(expected.database.game().allLedger(), actual.database.game().allLedger())
        assertEquals(expected.game.demon(), actual.game.demon())
        assertEquals(expected.game.achievements.first(), actual.game.achievements.first())
        assertEquals(expected.game.gear.first(), actual.game.gear.first())
        assertEquals(expected.breathing.completedCount(), actual.breathing.completedCount())
    }

    @Test
    fun aVersion2BackupRestoresTheProgrammeAndTheGame() = runTest {
        source.playTwoWeeks()
        target.setTime("2026-11-08T09:00")
        val summary = target.backups.import(Backup.decode(source.backups.export().encode()), ImportMode.REPLACE)
        assertEquals(1, summary.programmes)

        assertSameGame(source, target)
        val record = checkNotNull(target.programmes.current())
        assertEquals(listOf(WeekResult.CAUGHT_UP, WeekResult.MET), record.plan?.weeks?.take(2)?.map { it.result })
        assertEquals(MissedWeekChoice.CARRY_ON, record.choices[1]?.choice)
        assertEquals(1, target.game.demon()?.balance?.banked)
        assertEquals(110, target.game.demon()?.balance?.hp)
        val settings = target.settings.current()
        assertEquals("Gary", settings.demonName)
        assertFalse(settings.soundOn)
        assertEquals("wooden_sword", settings.equippedGear)
    }

    @Test
    fun mergingAVersion2BackupTwiceAddsItOnce() = runTest {
        source.playTwoWeeks()
        target.setTime("2026-11-08T09:00")
        val backup = source.backups.export()
        assertEquals(1, target.backups.import(backup, ImportMode.MERGE).programmes)
        val again = target.backups.import(backup, ImportMode.MERGE)
        assertEquals(0, again.programmes)
        assertSameGame(source, target)
        // Merging keeps the phone's own settings.
        assertTrue(target.settings.current().soundOn)
    }

    @Test
    fun mergingNeverMakesASecondRunningProgramme() = runTest {
        // The backup has an abandoned plan and a baseline running after it.
        source.startGumPlan()
        source.setTime("2026-11-08T09:00")
        source.programmes.abandon()
        source.programmes.startNew()
        // The phone has a plan of its own running.
        val own = target.startGumPlan()

        val summary = target.backups.import(source.backups.export(), ImportMode.MERGE)
        assertEquals(1, summary.programmes)
        // The backup's baseline is left out, and the summary says so.
        assertEquals(1, summary.programmesSkipped)
        assertEquals(own.id, target.programmes.current()?.id)
        val stored = target.database.programmes().all().map { it.programme.status }
        assertEquals(listOf(ProgrammeStatus.ACTIVE, ProgrammeStatus.ABANDONED), stored.sorted())
        // The abandoned plan comes with its demon.
        assertEquals(2, target.database.game().allDemons().size)
    }

    @Test
    fun aPlanFromTheBackupTakesOverFromABaselineOnThePhone() = runTest {
        source.playTwoWeeks()
        // A new phone: a baseline started, then the backup added.
        target.setTime("2026-11-08T09:00")
        val baseline = target.programmes.startBaseline()

        val summary = target.backups.import(source.backups.export(), ImportMode.MERGE)
        assertEquals(1, summary.programmes)
        assertEquals(0, summary.programmesSkipped)
        assertSameGame(source, target)
        assertEquals(1, target.game.demon()?.balance?.banked)
        // The phone's baseline closes as abandoned.
        val closed = target.database.programmes().all().single { it.programme.id == baseline.id }.programme
        assertEquals(ProgrammeStatus.ABANDONED, closed.status)
        assertEquals(target.clock.now.toEpochMilli(), closed.endedAt)
    }

    @Test
    fun aPlanOnThePhoneStaysAndTheBackupsIsCountedAsLeftOut() = runTest {
        source.playTwoWeeks()
        val own = target.startGumPlan()

        val summary = target.backups.import(source.backups.export(), ImportMode.MERGE)
        assertEquals(0, summary.programmes)
        assertEquals(1, summary.programmesSkipped)
        assertEquals(own.id, target.programmes.current()?.id)
        assertEquals(1, target.database.programmes().all().size)
        // The backup's demon and hits stay out with its plan.
        assertEquals(own.demon?.id, target.database.game().allDemons().single().id)
        assertTrue(target.database.game().allLedger().isEmpty())
    }

    @Test
    fun aBaselineOnThePhoneStaysWhenTheBackupsIsABaselineToo() = runTest {
        source.programmes.startBaseline()
        val own = target.programmes.startBaseline()
        val summary = target.backups.import(source.backups.export(), ImportMode.MERGE)
        assertEquals(0, summary.programmes)
        assertEquals(1, summary.programmesSkipped)
        assertEquals(own.id, target.programmes.current()?.id)
    }

    @Test
    fun theCountsBeforeAReplaceNameThePlanAndTheGame() = runTest {
        val empty = target.backups.counts()
        assertEquals(StoredCounts(0, 0, null, null, programmes = 0, achievements = 0, gear = 0, breathingSessions = 0), empty)

        target.playTwoWeeks()
        val counts = target.backups.counts()
        assertEquals(7, counts.entries)
        assertEquals(2, counts.products)
        assertEquals(ProgrammeStatus.ACTIVE, counts.currentProgramme)
        assertEquals("Gary", counts.demon?.demon?.name)
        assertEquals(HitBalance(maxHp = 120, earned = 2, spent = 1), counts.demon?.balance)
        assertEquals(1, counts.programmes)
        assertEquals(1, counts.achievements)
        assertEquals(1, counts.gear)
        assertEquals(1, counts.breathingSessions)
    }

    @Test
    fun anExportBringsInTheResultsThatAreDue() = runTest {
        source.startGumPlan()
        // Weeks 1 and 2 are over and their results are due, though nothing has read the plan.
        source.setTime("2026-11-08T09:00")
        val backup = source.backups.export()
        assertEquals(listOf(WeekResult.MET, WeekResult.MET), backup.programmes.single().weeks.take(2).map { it.result })
        assertEquals(listOf(1, 2), backup.hitLedger.map { it.week })
    }

    @Test
    fun weekGoalsComeThroughABackup() = runTest {
        val gum = source.products.addFromCatalogue(GUM_4)
        source.programmes.keepUsualDay(UsualDay.typed(gum to 8))
        val built = checkNotNull(source.programmes.buildPlan())
        val goals = WeekGoals(maxUnitsPerDay = 6, minGapMinutes = 90, firstDoseNotBefore = LocalTime.of(7, 30))
        source.programmes.startPlan(built.copy(weeks = built.weeks.map { if (it.number == 1) it.copy(goals = goals) else it }))

        val file = source.backups.export().encode()
        assertTrue("\"firstDoseNotBefore\": \"07:30\"" in file)
        target.backups.import(Backup.decode(file), ImportMode.REPLACE)
        val weeks = checkNotNull(target.programmes.current()?.plan).weeks
        assertEquals(goals, weeks.first().goals)
        assertTrue(weeks.drop(1).all { it.goals == WeekGoals.NONE })
    }

    @Test
    fun replacingWipesTheProgrammeAndTheGame() = runTest {
        target.playTwoWeeks()
        target.backups.import(source.backups.export(), ImportMode.REPLACE)
        assertNull(target.programmes.current())
        assertTrue(target.database.programmes().all().isEmpty())
        assertTrue(target.database.game().allLedger().isEmpty())
        assertTrue(target.database.game().allDemons().isEmpty())
        assertTrue(target.game.achievements.first().isEmpty())
        assertTrue(target.game.gear.first().isEmpty())
        assertEquals(0, target.breathing.completedCount())
    }

    /** [backup] as a version 1 app would have written it: no programmes, game or new settings. */
    private fun versionOne(backup: Backup): Backup = Backup.decode(
        backup.copy(
            version = 1,
            settings = Backup.Settings(backup.settings.dayStart, backup.settings.country, backup.settings.stoppedSmokingOn),
            programmes = emptyList(), demons = emptyList(), hitLedger = emptyList(),
            achievements = emptyList(), gear = emptyList(), breathingSessions = emptyList(),
        ).encode(),
    )

    @Test
    fun aVersion1BackupReplacesTheDataButLeavesTheGameSettings() = runTest {
        target.playTwoWeeks()
        val summary = target.backups.import(versionOne(source.backups.export()), ImportMode.REPLACE)
        assertEquals(2, summary.entries)
        assertNull(target.programmes.current())
        val settings = target.settings.current()
        assertEquals("Gary", settings.demonName)
        assertFalse(settings.soundOn)
        // The gear went with the rest of the game, so none is equipped.
        assertNull(settings.equippedGear)
    }

    @Test
    fun aVersion1BackupMergesWithoutTouchingTheProgramme() = runTest {
        target.playTwoWeeks()
        val before = target.programmes.current()
        val summary = target.backups.import(versionOne(source.backups.export()), ImportMode.MERGE)
        assertEquals(2, summary.entries)
        assertEquals(0, summary.programmes)
        assertEquals(before, target.programmes.current())
        assertEquals(2, target.database.game().allLedger().count { it.hits > 0 })
    }

    @Test
    fun mergingDosesIntoAWeekWhoseResultIsDueLeavesTheResultAsItWas() = runTest {
        source.products.addFromCatalogue(GUM_4)
        source.setTime("2026-10-31T00:30")
        source.chew("2026-10-30T12:00", pieces = 50)
        target.startGumPlan()
        target.setTime("2026-11-01T09:00")
        target.backups.import(source.backups.export(), ImportMode.MERGE)
        assertEquals(WeekResult.MET, target.resultOf(1))
    }

    @Test
    fun aRestoreSettlesBeforeItWritesAnything() = runTest {
        val seen = mutableListOf<Int>()
        val dao = target.database.entries()
        val backups = BackupRepository(target.database, target.settings, target.entries, results = { seen += dao.countLive() }, clock = target.clock)
        target.products.addFromCatalogue("nicorette-quickmist-uk-1mg")
        target.entries.log(LogRequest("nicorette-quickmist-uk-1mg"))
        backups.import(source.backups.export(), ImportMode.REPLACE)
        backups.import(source.backups.export(), ImportMode.MERGE)
        assertEquals(listOf(1, 2), seen)
    }

    @Test
    fun brokenProgrammesAndRowsAreSkipped() = runTest {
        source.playTwoWeeks()
        val backup = source.backups.export()
        val programme = backup.programmes.single()
        val broken = backup.copy(
            programmes = listOf(programme.copy(weeks = programme.weeks.drop(1))),
            achievements = backup.achievements + Backup.Unlock("bad_time", "yesterday"),
        )
        val summary = target.backups.import(broken, ImportMode.REPLACE)
        assertEquals(0, summary.programmes)
        assertNull(target.programmes.current())
        // Its demon and ledger go with it, and the achievement with a broken time is left out.
        assertTrue(target.database.game().allDemons().isEmpty())
        assertTrue(target.database.game().allLedger().isEmpty())
        assertEquals(setOf("first_week_met"), target.game.achievements.first().keys)
    }
}

