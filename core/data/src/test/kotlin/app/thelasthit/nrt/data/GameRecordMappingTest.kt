package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.data.repo.SpendResult
import app.thelasthit.nrt.data.repo.UnlockRepository
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.AchievementRules
import app.thelasthit.nrt.domain.game.BreathingSession
import app.thelasthit.nrt.domain.game.GameWeek
import app.thelasthit.nrt.domain.game.HitLedger
import app.thelasthit.nrt.domain.programme.WeekResult
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

/** The programme, the logs and the breathing sessions as stored, mapped onto the game's own record. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameRecordMappingTest {

    private lateinit var data: TestData
    private lateinit var unlocks: UnlockRepository

    @Before
    fun setUp() {
        data = TestData()
        unlocks = data.unlockRepository()
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun todayAndTheDayStartComeFromSettings() = runTest {
        data.settings.setDayStart(LocalTime.of(5, 30))
        data.setTime("2026-10-24T05:00")
        val record = unlocks.record()
        assertEquals(LocalDate.parse("2026-10-23"), record.today)
        assertEquals(LocalTime.of(5, 30), record.dayStart)
    }

    @Test
    fun anAbandonedProgrammeSetsEndedOnAndKeepsEveryPlannedWeek() = runTest {
        data.startGumPlan()
        // Weeks 1 and 2 are met with nothing logged, then the plan is abandoned on Sunday 8 November.
        data.setTime("2026-11-08T09:00")
        assertTrue(data.programmes.abandon())
        data.programmes.startNew()

        val record = unlocks.record()
        // The new programme has no usual day yet, so there's no baseline for the game to read.
        val programme = record.programmes.single()
        assertEquals(LocalDate.parse("2026-11-08"), programme.endedOn)
        assertEquals(12, programme.weeks.size)
        assertEquals((1..12).toList(), programme.weeks.map { it.number })
        assertEquals(120, programme.maxHp)
        assertEquals(120, programme.ledger.maxHp)
        assertEquals(2, programme.ledger.earned)
        assertEquals(
            listOf(GameWeek.Result.MET, GameWeek.Result.MET) + List(10) { GameWeek.Result.PENDING },
            programme.weeks.map { it.result },
        )
        // Days from the end on are outside the programme.
        assertTrue(programme.ranOn(LocalDate.parse("2026-11-07")))
        assertFalse(programme.ranOn(LocalDate.parse("2026-11-08")))
        assertFalse(programme.ledger.isDefeated)
    }

    @Test
    fun aRunningOrCompleteProgrammeHasNoEndDay() = runTest {
        data.startGumPlan()
        assertNull(unlocks.record().programmes.single().endedOn)

        // Every week met and every hit spent: complete, and closed.
        data.setTime("2027-01-17T09:00")
        repeat(2) { assertTrue(data.game.comboAttack() is SpendResult.Spent) }
        repeat(2) { assertTrue(data.game.spendHit() is SpendResult.Spent) }
        assertTrue(data.programmes.end())

        val programme = unlocks.record().programmes.single()
        assertNull(programme.endedOn)
        assertTrue(programme.ledger.isDefeated)
        assertTrue(AchievementRules.holds(Achievement.DEMON_DEFEATED, unlocks.record()))
    }

    @Test
    fun resultsAndWhenTheyCameInAreTakenAsRecorded() = runTest {
        data.startGumPlan()
        // Week 1 is missed, then week 2 is met and pays it back.
        data.chewAllWeekOne()
        data.setTime("2026-11-08T09:00")
        val stored = checkNotNull(data.programmes.current()?.plan)
        assertEquals(listOf(WeekResult.CAUGHT_UP, WeekResult.MET), stored.weeks.take(2).map { it.result })

        // Logs and edits after week 2's result is in take it over its 112 mg, which can't change
        // the result, and nor does the mapping.
        val late = data.chew("2026-11-05T12:00", pieces = 1)
        data.entries.edit(late.id, LogRequest(GUM_4, quantity = 30, at = late.occurredAt))
        data.entries.log(LogRequest(GUM_4, quantity = 30, at = london("2026-11-06T12:00"), allowDuplicate = true))

        val programme = unlocks.record().programmes.single()
        assertEquals(stored.weeks.map { it.result.name }, programme.weeks.map { it.result.name })
        assertEquals(stored.weeks.map { it.resultAt }, programme.weeks.map { it.resultAt })
        // The caught-up week keeps the time it was missed.
        assertEquals(london("2026-11-01T04:00"), programme.weeks[0].resultAt)
        assertEquals(london("2026-11-08T04:00"), programme.weeks[1].resultAt)
        assertEquals(GameWeek.Result.MET, programme.weeks[1].result)
        // Week 2's mix is 4 pieces of 4 mg gum: 16 mg a day.
        assertEquals(1, programme.weeks[1].mix.size)
        assertEquals(4, programme.weeks[1].mix.single().unitsPerDay)
        assertEquals(stored.weeks[1].target.dailyGuide, programme.weeks[1].dailyGuide)
        // The baseline is the typed usual day.
        assertEquals(8, programme.baseline.usualDay.single().unitsPerDay)
        assertNull(programme.baseline.measuredFrom)
    }

    @Test
    fun theLedgerComesAcrossWithEveryHitEarnedAndSpent() = runTest {
        data.startGumPlan()
        data.chewAllWeekOne()
        data.setTime("2026-11-08T09:00")
        assertTrue(data.game.spendHit() is SpendResult.Spent)

        val ledger = unlocks.record().programmes.single().ledger
        assertEquals(
            setOf(HitLedger.Kind.WEEK_MET to 2, HitLedger.Kind.CATCH_UP to 1),
            ledger.rows.filter { it.kind.isEarned }.map { it.kind to it.week }.toSet(),
        )
        assertEquals(HitLedger.Kind.HIT, ledger.rows.last().kind)
        assertEquals(1, ledger.banked)
        assertEquals(110, ledger.hp)
    }

    @Test
    fun aMeasuredBaselineKeepsTheDaysItCameFrom() = runTest {
        data.settings.setCountry("GB")
        data.products.addFromCatalogue(GUM_4)
        for (day in 16..22) data.chew("2026-10-${day}T12:00", pieces = 6)
        data.programmes.startBaseline()
        val usual = checkNotNull(data.programmes.measuredUsualDay())
        data.programmes.keepUsualDay(usual)

        val record = unlocks.record()
        assertEquals(LocalDate.parse("2026-10-16"), record.programmes.single().baseline.measuredFrom)
        assertTrue(record.programmes.single().weeks.isEmpty())
        assertTrue(AchievementRules.holds(Achievement.BASELINE_DONE, record))
    }

    @Test
    fun entriesComeWithTheirTagsAndWhenTheyWereWritten() = runTest {
        data.settings.setCountry("GB")
        data.products.addFromCatalogue(GUM_4)
        // Written at 15:05 for a dose at 08:10: backdated.
        val entry = data.entries.log(LogRequest(GUM_4, at = london("2026-10-23T08:10"), tagKeys = setOf("coffee_tea")))
        val logged = (entry as LogResult.Saved).entry

        val record = unlocks.record()
        assertEquals(setOf("coffee_tea"), record.entries.single().tagKeys)
        assertEquals(london("2026-10-23T15:05"), record.loggedAt[logged.id])
        assertTrue(AchievementRules.holds(Achievement.HONEST_LEDGER, record))
    }

    @Test
    fun restoredEntriesStayOutOfLoggedAt() = runTest {
        val source = TestData()
        try {
            source.settings.setCountry("GB")
            source.products.addFromCatalogue(GUM_4)
            source.entries.log(LogRequest(GUM_4, at = london("2026-10-22T08:10")))
            assertTrue(AchievementRules.holds(Achievement.HONEST_LEDGER, source.unlockRepository().record()))

            val backup = Backup.decode(source.backups.export().encode())
            for (mode in ImportMode.entries) {
                data.backups.import(backup, mode)
                val record = unlocks.record()
                assertEquals(1, record.entries.size)
                assertTrue(record.loggedAt.isEmpty())
                assertFalse(AchievementRules.holds(Achievement.HONEST_LEDGER, record))
            }

            // An entry logged on this phone after the restore says when it was written.
            data.clock.advance(Duration.ofHours(2))
            data.entries.log(LogRequest(GUM_4, at = london("2026-10-23T16:00")))
            val record = unlocks.record()
            assertEquals(2, record.entries.size)
            assertEquals(1, record.loggedAt.size)
        } finally {
            source.close()
        }
    }

    @Test
    fun breathingSessionsComeAcrossWithWhetherTheyCount() = runTest {
        data.breathing.record(BreathingPattern.BOX, london("2026-10-23T12:00"), Duration.ofSeconds(120), rounds = 7, ranToEnd = true)
        data.breathing.record(BreathingPattern.FOUR_SEVEN_EIGHT, london("2026-10-23T13:00"), Duration.ofSeconds(30), rounds = 1, ranToEnd = false)

        val sessions = unlocks.record().breathing.sortedBy { it.startedAt }
        assertEquals(listOf(BreathingSession.Type.BOX, BreathingSession.Type.FOUR_SEVEN_EIGHT), sessions.map { it.type })
        assertEquals(listOf(true, false), sessions.map { it.counts })
        assertEquals(Duration.ofSeconds(120), sessions[0].duration)
        assertEquals(7, sessions[0].rounds)
    }
}

/** The unlock checks on this test's database, wired as [app.thelasthit.nrt.data.DataGraph] wires them. */
internal fun TestData.unlockRepository() = UnlockRepository(database, settings, results = programmes, clock = clock)
