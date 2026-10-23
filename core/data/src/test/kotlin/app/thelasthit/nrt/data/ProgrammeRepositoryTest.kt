package app.thelasthit.nrt.data

import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgrammeRepositoryTest {

    private lateinit var data: TestData

    @Before
    fun setUp() {
        data = TestData()
    }

    @After
    fun tearDown() = data.close()

    private fun date(text: String) = LocalDate.parse(text)

    // The baseline.

    @Test
    fun aBaselineFromTheLastSevenLoggedDaysIsReadyAtOnce() = runTest {
        data.products.addFromCatalogue(GUM_4)
        // Friday 16 to Thursday 22 October, 8 pieces a day, with one day forgotten.
        for (day in 16..22) if (day != 19) data.chew("2026-10-${day}T12:00", pieces = 8)

        val started = data.programmes.startBaseline()
        assertEquals(ProgrammeStatus.BASELINE, started.status)
        assertEquals(date("2026-10-23"), started.baselineStartedOn)
        val days = checkNotNull(data.programmes.measureBaseline())
        assertTrue(days.isReady)
        assertEquals(6, days.daysWithDoses)

        val usual = checkNotNull(data.programmes.measuredUsualDay())
        assertEquals(BaselineSource.MEASURED, usual.source)
        assertEquals(mapOf(GUM_4 to 8), usual.mix.items.associate { it.product.id to it.units })
        val kept = data.programmes.keepUsualDay(usual)
        assertEquals(Dose.mg(32), kept.usualDay?.total)
        assertEquals(date("2026-10-16")..date("2026-10-22"), kept.baselineDays)
        assertNull(kept.plan)
    }

    @Test
    fun aNewBaselineBuildsDayByDay() = runTest {
        data.products.addFromCatalogue(GUM_4)
        data.programmes.startBaseline()
        assertFalse(checkNotNull(data.programmes.measureBaseline()).isReady)
        assertNull(data.programmes.measuredUsualDay())

        data.chew("2026-10-23T09:00", pieces = 3)
        // Today isn't over, so it doesn't count yet, though the window starts today.
        val building = checkNotNull(data.programmes.observeBaseline().first())
        assertEquals(date("2026-10-23"), building.start)
        assertEquals(0, building.daysWithDoses)

        data.setTime("2026-10-24T09:00")
        assertEquals(1, checkNotNull(data.programmes.observeBaseline().first()).daysWithDoses)
    }

    @Test
    fun aTypedUsualDayNeedsNoBaseline() = runTest {
        val gum = data.products.addFromCatalogue(GUM_4)
        val kept = data.programmes.keepUsualDay(UsualDay.typed(gum to 6))
        assertEquals(ProgrammeStatus.BASELINE, kept.status)
        assertNull(kept.baselineStartedOn)
        assertNull(kept.baselineDays)
        assertEquals(BaselineSource.MANUAL, kept.usualDay?.source)
        assertEquals(Dose.mg(24), kept.usualDay?.total)

        // Editing it again replaces it.
        val edited = data.programmes.keepUsualDay(UsualDay.typed(gum to 7))
        assertEquals(kept.id, edited.id)
        assertEquals(Dose.mg(28), edited.usualDay?.total)
    }

    @Test
    fun onlyOneProgrammeIsCurrent() = runTest {
        data.programmes.startBaseline()
        assertFailsWith<IllegalStateException> { data.programmes.startBaseline() }
    }

    // The plan.

    @Test
    fun startingAPlanSavesItsWeeksAddsTheLowerStrengthAndBringsTheDemonOut() = runTest {
        data.settings.setDemon("Gary", Creature.DRAGON, "ember")
        val record = data.startGumPlan()

        assertEquals(ProgrammeStatus.ACTIVE, record.status)
        val plan = checkNotNull(record.plan)
        assertEquals(PLAN_START, plan.startDate)
        assertEquals(ProgrammeLength.WEEKS_12, plan.length)
        assertEquals(
            listOf(168, 112, 112, 98, 84, 70, 56, 42, 28, 14, 0, 0).map { Dose.mg(it) },
            plan.weeks.map { it.target.weeklyAllowance },
        )
        assertEquals(mapOf(GUM_2 to 8), plan.week(3).target.mix.items.associate { it.product.id to it.units })
        assertTrue(plan.weeks.all { it.result == WeekResult.PENDING })

        // 2 mg gum is one of the user's products now, though not on the quick log.
        val added = checkNotNull(data.products.get(GUM_2))
        assertNull(added.quickLogPosition)
        assertFalse(added.isHidden)

        val demon = checkNotNull(record.demon)
        assertEquals("Gary", demon.name)
        assertEquals("ember", demon.variant)
        assertEquals(120, demon.maxHp)
        // Settings keep the onboarding choice.
        assertEquals("Gary", data.settings.current().demonName)
    }

    @Test
    fun aPlanReadsBackTheSameAsItWasBuilt() = runTest {
        val gum = data.products.addFromCatalogue(GUM_4)
        data.programmes.keepUsualDay(UsualDay.typed(gum to 8))
        val built = checkNotNull(data.programmes.buildPlan(ProgrammeLength.WEEKS_16, date("2026-10-26")))
        // A week edited before the plan starts: week 4 two steps further down.
        val edited = built.edit(4, checkNotNull(built.week(4).target.step) + 2, date("2026-10-23"))

        val stored = checkNotNull(data.programmes.startPlan(edited).plan)
        assertEquals(edited.weeks.map { it.target.weeklyAllowance }, stored.weeks.map { it.target.weeklyAllowance })
        assertEquals(edited.weeks.map { it.target.step }, stored.weeks.map { it.target.step })
        assertEquals(edited.ladder.steps.map { it.total }, stored.ladder.steps.map { it.total })
        assertEquals(edited.weeks.map { it.startDate }, stored.weeks.map { it.startDate })
        assertEquals(16, stored.weeks.size)
    }

    @Test
    fun aPlanHasToComeFromTheUsualDayKept() = runTest {
        val gum = data.products.addFromCatalogue(GUM_4)
        data.programmes.keepUsualDay(UsualDay.typed(gum to 8))
        val plan = checkNotNull(data.programmes.buildPlan())
        data.programmes.keepUsualDay(UsualDay.typed(gum to 6))
        assertFailsWith<IllegalArgumentException> { data.programmes.startPlan(plan) }
    }

    @Test
    fun weeksCanBeEditedBeforeTheyStart() = runTest {
        data.startGumPlan()
        val step = checkNotNull(data.programmes.current()?.plan?.week(5)?.target?.step)
        val edited = data.programmes.editWeek(5, step + 1)
        assertEquals(step + 1, edited.week(5).target.step)
        assertEquals(step + 1, data.programmes.current()?.plan?.week(5)?.target?.step)

        data.setTime("2026-10-25T12:00")
        // Week 1 has started.
        assertFailsWith<IllegalArgumentException> { data.programmes.editWeek(1, 2) }
    }

    // Results.

    @Test
    fun resultsComeIn24HoursAfterTheWeekEndsWithTheirHitsWrittenOnce() = runTest {
        data.startGumPlan()
        data.setTime("2026-10-24T10:00")
        data.chew("2026-10-24T09:00", pieces = 4)

        // Week 1 ends at 04:00 on Saturday 31 October; its result comes in a day later.
        data.setTime("2026-11-01T03:59")
        assertNull(data.programmes.settle())
        assertEquals(WeekResult.PENDING, data.resultOf(1))

        data.setTime("2026-11-01T04:00")
        val settlement = checkNotNull(data.programmes.settle())
        assertEquals(1, settlement.newHits.size)
        assertEquals(WeekResult.MET, data.resultOf(1))
        assertNull(data.programmes.settle())

        val ledger = data.database.game().allLedger()
        assertEquals(listOf(LedgerKind.WEEK_MET), ledger.map { it.kind })
        assertEquals(listOf(1), ledger.map { it.week })
    }

    @Test
    fun readingTheProgrammeSettlesWhatsDue() = runTest {
        data.startGumPlan()
        data.setTime("2026-11-08T09:00")
        val record = checkNotNull(data.programmes.observeCurrent().first())
        assertEquals(listOf(WeekResult.MET, WeekResult.MET), record.plan?.weeks?.take(2)?.map { it.result })
        assertEquals(2, data.database.game().allLedger().size)
    }

    @Test
    fun afterAMissedWeekCarryingOnCanBecomeARepeatThatDay() = runTest {
        data.startGumPlan()
        // 8 pieces of 4 mg on each day of week 1 is 224 mg, over its 168.
        data.chewAllWeekOne()
        data.setTime("2026-11-01T09:00")
        assertEquals(1, data.programmes.awaitingChoice()?.number)

        assertTrue(data.programmes.chooseAfterMiss(1, MissedWeekChoice.CARRY_ON))
        assertNull(data.programmes.awaitingChoice())
        assertEquals(MissedWeekChoice.CARRY_ON, data.programmes.current()?.choices?.get(1)?.choice)

        data.setTime("2026-11-01T20:00")
        assertTrue(data.programmes.chooseAfterMiss(1, MissedWeekChoice.REPEAT))
        val record = checkNotNull(data.programmes.current())
        val plan = checkNotNull(record.plan)
        assertEquals(13, plan.weeks.size)
        assertEquals(WeekKind.REPEAT, plan.week(2).kind)
        assertEquals(plan.week(1).target, plan.week(2).target)
        assertEquals(130, record.demon?.maxHp)
        assertEquals(MissedWeekChoice.REPEAT, record.choices[1]?.choice)
        // A repeat is final.
        assertFalse(data.programmes.chooseAfterMiss(1, MissedWeekChoice.CARRY_ON))
    }

    @Test
    fun carryingOnStandsAfterTheDayItWasPicked() = runTest {
        data.startGumPlan()
        data.chewAllWeekOne()
        data.setTime("2026-11-01T09:00")
        assertTrue(data.programmes.chooseAfterMiss(1, MissedWeekChoice.CARRY_ON))
        data.setTime("2026-11-02T09:00")
        assertFalse(data.programmes.chooseAfterMiss(1, MissedWeekChoice.REPEAT))
        assertEquals(12, data.programmes.current()?.plan?.weeks?.size)
        assertEquals(120, data.programmes.current()?.demon?.maxHp)
    }

    @Test
    fun meetingTheNextWeekPaysBackTheMissedOne() = runTest {
        data.startGumPlan()
        data.chewAllWeekOne()
        data.setTime("2026-11-08T09:00")
        val plan = checkNotNull(data.programmes.current()?.plan)
        assertEquals(listOf(WeekResult.CAUGHT_UP, WeekResult.MET), plan.weeks.take(2).map { it.result })
        val ledger = data.database.game().allLedger()
        assertEquals(setOf(LedgerKind.WEEK_MET to 2, LedgerKind.CATCH_UP to 1), ledger.map { it.kind to it.week }.toSet())
        assertEquals(listOf(2, 2), ledger.map { it.earnedBy })
    }

    // Ending.

    @Test
    fun abandoningClosesTheProgrammeAndANewOneStartsFromCurrentUse() = runTest {
        val first = data.startGumPlan()
        data.setTime("2026-11-08T09:00")
        assertFalse(data.programmes.end())
        assertTrue(data.programmes.abandon())
        assertNull(data.programmes.current())
        assertFalse(data.programmes.abandon())

        // The first two weeks were met, with nothing logged, so the last 7 days have no doses.
        val next = data.programmes.startNew()
        assertEquals(ProgrammeStatus.BASELINE, next.status)
        assertEquals(date("2026-11-08"), next.baselineStartedOn)
        assertTrue(next.id != first.id)
        // The old programme keeps its weeks and its hits.
        val old = data.database.programmes().all().first { it.programme.id == first.id }
        assertEquals(ProgrammeStatus.ABANDONED, old.programme.status)
        assertEquals(12, old.weeks.size)
        assertEquals(2, data.database.game().allLedger().size)
    }

    @Test
    fun aCompleteProgrammeEndsAndTheRematchKeepsTheDemon() = runTest {
        data.settings.setDemon("Gary", Creature.DRAGON, "ember")
        data.startGumPlan()
        // Nothing logged, so every week is met: the last result comes in on Sunday 17 January.
        data.setTime("2027-01-17T09:00")
        val record = checkNotNull(data.programmes.current())
        assertEquals(ProgrammeStatus.COMPLETE, record.status)
        assertEquals(12, data.database.game().allLedger().size)
        assertFalse(data.programmes.abandon())
        assertTrue(data.programmes.end())
        assertNull(data.programmes.current())

        data.settings.setDemon("Mildred", Creature.DRAGON, "frost")
        assertEquals("Gary", data.programmes.nextDemon().name)
        val gum = checkNotNull(data.products.get(GUM_4))
        data.programmes.startBaseline()
        data.programmes.keepUsualDay(UsualDay.typed(gum to 4))
        val rematch = data.programmes.startPlan(checkNotNull(data.programmes.buildPlan(ProgrammeLength.WEEKS_8)))
        assertEquals("Gary", rematch.demon?.name)
        assertEquals(80, rematch.demon?.maxHp)
        assertNotNull(rematch.plan)
    }
}
