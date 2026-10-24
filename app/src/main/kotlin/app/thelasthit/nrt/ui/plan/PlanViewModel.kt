package app.thelasthit.nrt.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.DemonState
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.data.settings.Settings
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.insights.Triggers
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.programme.BaselineDays
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.ExtraGoals
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.MissedWeekChoices
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeClock
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekChoice
import app.thelasthit.nrt.ui.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** What the Plan tab shows, from no programme to the end of one. */
sealed interface PlanState {
    val band: BandLook

    /**
     * No programme yet: the ways to start. When the last 7 full days have enough doses, they're
     * offered as the baseline at once (ROADMAP P3-1), with the [usual] day they make.
     */
    data class Start(val lastSeven: BaselineDays, val usual: UsualDay?, override val band: BandLook) : PlanState

    /** A baseline building, day by day, with the usual day the days so far point to. */
    data class Building(
        val days: BaselineDays,
        val soFar: UsualDay?,
        val startedOn: LocalDate,
        val today: LocalDate,
        override val band: BandLook,
    ) : PlanState

    /** The baseline is ready: the [usual] day it measured is waiting to be checked. */
    data class Ready(val days: BaselineDays, val usual: UsualDay, override val band: BandLook) : PlanState

    /** A usual day kept, and the plan still to build. [measured] when it came from the logs. */
    data class Kept(val usual: UsualDay, val measured: Boolean, override val band: BandLook) : PlanState

    /** A plan running, in its weeks or in overtime. */
    data class Running(
        val plan: Programme,
        val clock: ProgrammeClock,
        /** The week running today, the last one when they're all over, or null before the plan starts. */
        val focus: ProgrammeWeek?,
        /** Every dose logged in the plan's weeks. */
        val entries: List<Entry>,
        val choices: Map<Int, WeekChoice>,
        /** The missed week whose choice hasn't been made: the sheet comes up for it. */
        val awaiting: ProgrammeWeek?,
        /** A missed week carried on from today, which can still become a repeat until the day ends. */
        val canStillRepeat: ProgrammeWeek?,
        /** How many extra goals are on this week, or null when there's no week to set them for. */
        val goalsOn: Int?,
        val demonName: String?,
        val hp: Int,
        override val band: BandLook,
    ) : PlanState {
        val today: LocalDate get() = clock.today

        fun totalOf(week: ProgrammeWeek): Dose = week.totalOf(entries)

        fun totalOn(date: LocalDate): Dose = entries.filter { it.logicalDate == date }.map { it.total }.sum()
    }

    /** The plan is complete. A rematch can start once the demon is at 0 HP. */
    data class Done(
        val plan: Programme,
        val today: LocalDate,
        val demonName: String?,
        val creature: Creature,
        val defeated: Boolean,
        val hp: Int,
        val hitsLanded: Int,
        val hitsEarned: Int,
        /** When the last hit landed, if the demon is defeated. */
        val defeatedOn: LocalDate?,
        /** The most used triggers of the usual day the plan came down from, most first. */
        val triggers: List<TagEntity>,
        override val band: BandLook,
    ) : PlanState
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private data class Inputs(
        val record: ProgrammeRecord?,
        val clock: ProgrammeClock,
        val settings: Settings,
        val demon: DemonState?,
    ) {
        /** The days whose doses the state needs. */
        val range: ClosedRange<LocalDate>
            get() {
                val plan = record?.plan
                val today = clock.today
                if (plan == null) return today.minusDays(BaselineDays.DAYS.toLong())..today
                val from = minOf(record.baselineDays?.start ?: plan.startDate, plan.startDate)
                return from..maxOf(plan.weeks.last().endDate, today)
            }
    }

    private val clocks: Flow<ProgrammeClock> = combine(minuteTicks(graph.clock), graph.settings.settings) { now, settings ->
        ProgrammeClock(now, zone(), settings.dayBoundary)
    }

    private val inputs: Flow<Inputs> =
        combine(graph.programmes.observeCurrent(), clocks, graph.settings.settings, graph.game.demon) { record, clock, settings, demon ->
            Inputs(record, clock, settings, demon)
        }

    val state: StateFlow<PlanState?> = inputs.flatMapLatest { input ->
        combine(
            graph.entries.observeRange(input.range.start, input.range.endInclusive),
            graph.products.everything,
            graph.tags.all,
        ) { entries, products, tags -> build(input, entries, products, tags) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(input: Inputs, entries: List<Entry>, products: List<UserProduct>, tags: List<TagEntity>): PlanState {
        val record = input.record
        val today = input.clock.today
        val lurk = BandLook.Lurking(input.settings.demonName, input.settings.demonCreature)
        if (record == null) {
            val days = BaselineDays.measure(today, entries)
            return PlanState.Start(days, days.usualDay(products), lurk)
        }
        val plan = record.plan
        return when {
            record.status == ProgrammeStatus.BASELINE || plan == null -> baseline(record, today, entries, products, lurk)
            record.status == ProgrammeStatus.COMPLETE -> done(record, plan, today, input, entries, tags)
            else -> running(record, plan, input, entries)
        }
    }

    private fun baseline(record: ProgrammeRecord, today: LocalDate, entries: List<Entry>, products: List<UserProduct>, band: BandLook): PlanState {
        record.usualDay?.let { return PlanState.Kept(it, it.source == BaselineSource.MEASURED, band) }
        val days = BaselineDays.measure(today, entries, record.baselineStartedOn)
        val usual = days.usualDay(products)
        return if (usual != null) {
            PlanState.Ready(days, usual, band)
        } else {
            PlanState.Building(days, days.usualDaySoFar(products), record.baselineStartedOn ?: today, today, band)
        }
    }

    private fun running(record: ProgrammeRecord, plan: Programme, input: Inputs, entries: List<Entry>): PlanState.Running {
        val clock = input.clock
        val today = clock.today
        val focus = plan.weekOn(today) ?: plan.weeks.last().takeIf { today > it.endDate }
        val inPlan = entries.filter { it.logicalDate >= plan.startDate }
        val canStillRepeat = plan.weeks.lastOrNull { week ->
            record.choices[week.number]?.choice == MissedWeekChoice.CARRY_ON &&
                MissedWeekChoices.canChoose(plan, week.number, MissedWeekChoice.REPEAT, record.choices, clock)
        }
        val goalsWeek = if (record.status == ProgrammeStatus.ACTIVE) ExtraGoals.weekToSet(plan, today) else null
        return PlanState.Running(
            plan = plan,
            clock = clock,
            focus = focus,
            entries = inPlan,
            choices = record.choices,
            awaiting = MissedWeekChoices.awaiting(plan, record.choices, clock),
            canStillRepeat = canStillRepeat,
            goalsOn = goalsWeek?.goals?.onCount,
            demonName = record.demon?.name,
            hp = input.demon?.balance?.hp ?: plan.maxHp,
            band = outLook(record, input.demon, plan),
        )
    }

    private fun done(record: ProgrammeRecord, plan: Programme, today: LocalDate, input: Inputs, entries: List<Entry>, tags: List<TagEntity>): PlanState.Done {
        val balance = input.demon?.balance
        val hp = balance?.hp ?: 0
        val defeated = balance != null && hp == 0
        val demon = record.demon
        val baselineDays = record.baselineDays ?: plan.startDate..plan.week(1).endDate
        val usualDayEntries = entries.filter { it.logicalDate in baselineDays }
        val byKey = tags.associateBy { it.key }
        val triggers = Triggers.of(usualDayEntries, previous = null) { byKey[it]?.group }.tags
            .mapNotNull { byKey[it.key] }
            .take(TRIGGERS)
        val defeatedOn = demon?.defeatedAt?.let { input.settings.dayBoundary.logicalDate(it, zone()) }
            ?: if (defeated) today else null
        return PlanState.Done(
            plan = plan,
            today = today,
            demonName = demon?.name,
            creature = demon?.creature ?: input.settings.demonCreature,
            defeated = defeated,
            hp = hp,
            hitsLanded = balance?.spent ?: 0,
            hitsEarned = balance?.earned ?: plan.earnedHits.size,
            defeatedOn = defeatedOn,
            triggers = triggers,
            band = if (defeated) {
                BandLook.Defeated(demon?.name, demon?.creature ?: input.settings.demonCreature, demon?.variant, balance?.maxHp ?: plan.maxHp)
            } else {
                outLook(record, input.demon, plan)
            },
        )
    }

    private fun outLook(record: ProgrammeRecord, state: DemonState?, plan: Programme): BandLook {
        val demon = record.demon ?: return BandLook.Lurking(null, Creature.DRAGON)
        val balance = state?.balance
        return BandLook.Out(
            name = demon.name,
            creature = demon.creature,
            variant = demon.variant,
            hp = balance?.hp ?: plan.maxHp,
            maxHp = balance?.maxHp ?: plan.maxHp,
            banked = balance?.banked ?: 0,
        )
    }

    // Starting.

    /** The start or rematch a tap set going, so a quick second tap does nothing while it saves. */
    private var starting: Job? = null

    /**
     * Runs [work] for a tap on the page shown while [on] holds, unless a tap's work is running
     * already. Once it has saved, [then] runs, and taps are still ignored until that page has gone.
     */
    private fun startOnce(on: (PlanState?) -> Boolean, work: suspend () -> Unit, then: () -> Unit) {
        if (starting?.isActive == true) return
        starting = viewModelScope.launch {
            if (runCatching { work() }.isFailure) return@launch
            then()
            state.first { !on(it) }
        }
    }

    /** Starts a baseline, which the last 7 days fill at once, then goes on to check the usual day. */
    fun useLastSevenDays(then: () -> Unit) = startOnce({ it is PlanState.Start }, { graph.programmes.startBaseline() }, then)

    /** Starts measuring a 7-day baseline from today. */
    fun startBaseline() = startOnce({ it is PlanState.Start }, { graph.programmes.startBaseline() }, then = {})

    // During the plan.

    /** Moves week [week] to [step] on the ladder, before it starts. */
    fun editWeek(week: Int, step: Int) {
        viewModelScope.launch { runCatching { graph.programmes.editWeek(week, step) } }
    }

    /** The choice after missed week [week]. Closing the sheet counts as carrying on. */
    fun choose(week: Int, choice: MissedWeekChoice) {
        viewModelScope.launch { graph.programmes.chooseAfterMiss(week, choice) }
    }

    // After it.

    /** A rematch from current use, with the same demon back: a new baseline starts today. */
    fun rematch(then: () -> Unit = {}) = startOnce({ it is PlanState.Done }, { graph.programmes.startNew() }, then)

    private companion object {
        const val TRIGGERS = 2
    }
}
