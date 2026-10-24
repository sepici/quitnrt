package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.DemonLook
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.data.repo.SpendResult
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekGoals
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
import java.time.LocalTime

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

    @Test
    fun aTypedDayOfACatalogueProductNotYetAddedStepsDownItsLine() = runTest {
        data.settings.setCountry("GB")
        val gum = checkNotNull(TestData.catalogue.find(GUM_4)).toUserProduct()
        val kept = data.programmes.keepUsualDay(UsualDay.typed(gum to 8))

        // The gum is one of the user's products now, as the catalogue has it, though not on the quick log.
        val stored = checkNotNull(data.products.get(GUM_4))
        assertEquals(GUM_4, stored.catalogueId)
        assertEquals(gum.label, stored.label)
        assertNull(stored.quickLogPosition)
        assertFalse(stored.isHidden)
        assertEquals(stored, kept.usualDay?.mix?.products?.single())

        // PLAN's second example: 6 × 4 mg in week 1, 4 × 4 mg in week 2, then 8 × 2 mg.
        val plan = checkNotNull(data.programmes.buildPlan())
        assertEquals(
            listOf(168, 112, 112, 98, 84, 70, 56, 42, 28, 14, 0, 0).map { Dose.mg(it) },
            plan.weeks.map { it.target.weeklyAllowance },
        )
        assertEquals(mapOf(GUM_4 to 6), plan.week(1).target.mix.items.associate { it.product.id to it.units })
        assertEquals(mapOf(GUM_2 to 8), plan.week(3).target.mix.items.associate { it.product.id to it.units })

        data.programmes.startPlan(plan)
        assertEquals(stored, data.products.get(GUM_4))
    }

    @Test
    fun aProductTheStepsUseIsNeverLeftHidden() = runTest {
        data.settings.setCountry("GB")
        val gum = data.products.addFromCatalogue(GUM_4)
        data.programmes.keepUsualDay(UsualDay.typed(gum to 8))
        val plan = checkNotNull(data.programmes.buildPlan())
        // The gum is gone from the list by the time the plan starts, and its steps bring it back.
        data.database.products().deleteAll()

        data.programmes.startPlan(plan)
        val back = checkNotNull(data.products.get(GUM_4))
        assertEquals(GUM_4, back.catalogueId)
        assertFalse(back.isHidden)
        assertEquals(gum.label, back.label)
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
    fun weekGoalsAreKeptThroughEditsAndRepeats() = runTest {
        data.settings.setCountry("GB")
        val gum = data.products.addFromCatalogue(GUM_4)
        data.programmes.keepUsualDay(UsualDay.typed(gum to 8))
        val built = checkNotNull(data.programmes.buildPlan())
        // Week n: at most 13 − n pieces, 90 minutes apart, the first not before 07:00 plus 15n minutes.
        fun goals(number: Int) = WeekGoals(mapOf(GUM_4 to 13 - number), 90, LocalTime.of(7, 0).plusMinutes(15L * number))
        val plan = built.copy(weeks = built.weeks.map { it.copy(goals = goals(it.number)) })

        data.programmes.startPlan(plan)
        assertEquals((1..12).map(::goals), data.programmes.current()?.plan?.weeks?.map { it.goals })
        data.programmes.editWeek(5, checkNotNull(plan.week(5).target.step) + 1)

        data.chewAllWeekOne()
        data.setTime("2026-11-01T09:00")
        assertTrue(data.programmes.chooseAfterMiss(1, MissedWeekChoice.REPEAT))
        // The repeat takes week 1's goals, and the weeks after keep their own.
        val weeks = checkNotNull(data.programmes.current()?.plan).weeks
        assertEquals(listOf(goals(1), goals(1)) + (2..12).map(::goals), weeks.map { it.goals })
    }

    @Test
    fun goalsAreSetForTheWeekRunningAndCarryIntoTheWeeksAfter() = runTest {
        data.startGumPlan()
        val goals = WeekGoals(mapOf(GUM_4 to 5), minGapMinutes = 90, firstDoseNotBefore = LocalTime.of(9, 0))
        // Before the plan starts, the goals are week 1's.
        assertTrue(data.programmes.setGoals(goals))
        var weeks = checkNotNull(data.programmes.current()?.plan).weeks
        assertEquals(goals, weeks[0].goals)
        assertEquals(LocalTime.of(9, 15), weeks[1].goals.firstDoseNotBefore)
        // Week 3 switches to 2 mg gum, which starts at its own mix: 8 pieces.
        assertEquals(mapOf(GUM_2 to 8), weeks[2].goals.maxUnitsPerDay)
        assertEquals(WeekGoals.NONE, weeks[11].goals)

        // In week 3, turning the caps off leaves weeks 1 and 2 as they were.
        data.setTime("2026-11-10T12:00")
        assertTrue(data.programmes.setGoals(goals.copy(maxUnitsPerDay = emptyMap(), firstDoseNotBefore = LocalTime.of(9, 30))))
        weeks = checkNotNull(data.programmes.current()?.plan).weeks
        assertEquals(goals, weeks[0].goals)
        assertEquals(mapOf(GUM_4 to 5), weeks[1].goals.maxUnitsPerDay)
        assertEquals(WeekGoals(minGapMinutes = 90, firstDoseNotBefore = LocalTime.of(9, 30)), weeks[2].goals)
        assertEquals(LocalTime.of(9, 45), weeks[3].goals.firstDoseNotBefore)

        // NRT-free weeks have nothing to pace.
        data.setTime("2027-01-04T12:00")
        assertFalse(data.programmes.setGoals(goals))
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
        data.settings.setDemon("Gary", Creature.DRAGON, "ember")
        val first = data.startGumPlan()
        data.game.restyleDemon("Gareth", Creature.DRAGON, "frost")
        data.setTime("2026-11-08T09:00")
        assertFalse(data.programmes.end())
        assertTrue(data.programmes.abandon())
        assertNull(data.programmes.current())
        assertFalse(data.programmes.abandon())
        // The demon is the one the next plan starts with, as it was when the plan stopped.
        assertEquals(DemonLook("Gareth", Creature.DRAGON, "frost"), data.programmes.nextDemon())

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

    /** Every hit spent: two 5× attacks and two hits. */
    private suspend fun spendEveryHit() {
        repeat(2) { assertTrue(data.game.comboAttack() is SpendResult.Spent) }
        repeat(2) { assertTrue(data.game.spendHit() is SpendResult.Spent) }
    }

    /**
     * The gum plan played to the end: nothing logged, so every week is met, and the last result
     * comes in on Sunday 17 January. Then every hit is spent, the demon falls and the plan ends.
     */
    private suspend fun defeatTheGumPlan() {
        data.setTime("2027-01-17T09:00")
        spendEveryHit()
        assertTrue(data.game.markDefeated())
        assertTrue(data.programmes.end())
    }

    /** A rematch from the current baseline, or a new one: 4 pieces of 4 mg gum over 8 weeks. */
    private suspend fun startRematch(): ProgrammeRecord {
        val gum = checkNotNull(data.products.get(GUM_4))
        data.programmes.keepUsualDay(UsualDay.typed(gum to 4))
        return data.programmes.startPlan(checkNotNull(data.programmes.buildPlan(ProgrammeLength.WEEKS_8)))
    }

    @Test
    fun aCompleteProgrammeEndsOnceItsDemonIsDefeated() = runTest {
        val first = data.startGumPlan()
        data.setTime("2027-01-17T09:00")
        val record = checkNotNull(data.programmes.current())
        assertEquals(ProgrammeStatus.COMPLETE, record.status)
        assertEquals(12, data.database.game().allLedger().size)
        assertFalse(data.programmes.abandon())

        // 12 hits are banked and the demon still has its 120 HP, so the programme stays.
        assertFalse(data.programmes.end())
        assertFailsWith<IllegalStateException> { data.programmes.startNew() }
        assertEquals(first.id, data.programmes.current()?.id)
        assertEquals(120, data.game.demon()?.balance?.hp)

        spendEveryHit()
        assertTrue(data.programmes.end())
        assertNull(data.programmes.current())
        // Ending lands the defeat if the battle hadn't marked it.
        val demon = data.database.game().allDemons().single()
        assertEquals(data.clock.now.toEpochMilli(), demon.defeatedAt)
        assertFalse(data.programmes.end())
    }

    @Test
    fun aDefeatedDemonsProgrammeClosesWhenANewOneStarts() = runTest {
        data.startGumPlan()
        data.setTime("2027-01-17T09:00")
        spendEveryHit()
        data.setTime("2027-01-18T09:00")
        val next = data.programmes.startNew()
        assertEquals(ProgrammeStatus.BASELINE, next.status)
        val old = data.database.programmes().all().map { it.programme }.single { it.id != next.id }
        assertEquals(ProgrammeStatus.COMPLETE, old.status)
        assertEquals(data.clock.now.toEpochMilli(), data.database.game().allDemons().single().defeatedAt)
    }

    @Test
    fun theRematchBringsTheLastDemonBack() = runTest {
        data.settings.setDemon("Gary", Creature.DRAGON, "ember")
        data.startGumPlan()
        // Restyled during the plan, which leaves settings as they were until it closes.
        data.game.restyleDemon("Gary the Second", Creature.DRAGON, "frost")
        assertEquals("Gary", data.settings.current().demonName)
        defeatTheGumPlan()

        assertEquals(DemonLook("Gary the Second", Creature.DRAGON, "frost"), data.programmes.nextDemon())
        val rematch = startRematch()
        assertEquals("Gary the Second", rematch.demon?.name)
        assertEquals("frost", rematch.demon?.variant)
        assertEquals(80, rematch.demon?.maxHp)
        assertNotNull(rematch.plan)
    }

    @Test
    fun aRestyleBeforeTheRematchStartsIsTheDemonItBrings() = runTest {
        data.settings.setDemon("Gary", Creature.DRAGON, "ember")
        data.startGumPlan()
        defeatTheGumPlan()

        data.programmes.startBaseline()
        data.game.restyleDemon(" Mildred ", Creature.DRAGON, "frost")
        assertEquals(DemonLook("Mildred", Creature.DRAGON, "frost"), data.programmes.nextDemon())
        val rematch = startRematch()
        assertEquals("Mildred", rematch.demon?.name)
        assertEquals("frost", rematch.demon?.variant)
    }
}
