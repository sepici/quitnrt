package app.thelasthit.nrt.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.DemonState
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.DaySummary
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.log.WeekSummary
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.programme.PlanToday
import app.thelasthit.nrt.ui.absorbed.AbsorbedFigures
import app.thelasthit.nrt.ui.absorbed.absorbedFigures
import app.thelasthit.nrt.ui.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class QuickLogPill(val product: UserProduct, val label: PillLabel)

data class TodayState(
    val now: Instant,
    val date: LocalDate,
    val day: DaySummary,
    val week: WeekSummary,
    val pills: List<QuickLogPill>,
    /** Product ID to its short name ("Nicorette gum"), for rows that show the product. */
    val productNames: Map<String, String>,
    val tags: Map<String, TagEntity>,
    val demonName: String?,
    val creature: Creature,
    /** A label limit crossed by the latest dose, shown until dismissed. */
    val labelNote: LabelWarning?,
    /** Today in the running plan, with its guide: null before the plan starts, after it's done, or with none. */
    val plan: PlanToday? = null,
    /** The day a plan that hasn't started yet begins. */
    val planStartsOn: LocalDate? = null,
    /**
     * The demon once a plan is confirmed, with its HP and banked hits from the ledger. It steps
     * out on the plan's first day: until then, [planStartsOn] is set and it lurks.
     */
    val demon: DemonState? = null,
    /** Estimated absorbed amounts, while the setting is on (ROADMAP P9-1). Only for showing. */
    val absorbed: AbsorbedFigures? = null,
) {
    /** About how much of today's labelled mg reached the blood, while the setting is on and there's a dose. */
    val absorbedToday: Dose? get() = absorbed?.of(day.entries)

    fun nameOf(entry: Entry): String = productNames[entry.productId] ?: entry.productName
}

/** A dose logged from a quick-log pill or the log sheet, for the snackbar with Undo. */
data class Logged(val entry: Entry, val name: String)

/** A dose that looks like one already logged within a minute, waiting for the user to decide. */
data class PendingDuplicate(val request: LogRequest, val existing: Entry)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val clock: Clock = graph.clock
    private val labelNote = MutableStateFlow<LabelWarning?>(null)
    private val _duplicate = MutableStateFlow<PendingDuplicate?>(null)
    private val _logged = Channel<Logged>(Channel.BUFFERED)

    val duplicate: StateFlow<PendingDuplicate?> = _duplicate.asStateFlow()
    val logged: Flow<Logged> = _logged.receiveAsFlow()

    private val now: StateFlow<Instant> = minuteTicks(clock)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), clock.instant())

    private val today: Flow<LocalDate> = combine(now, graph.settings.settings) { instant, settings ->
        settings.dayBoundary.logicalDate(instant, zone())
    }.distinctUntilChanged()

    /** Today's place in the running plan, if one has started or is about to. */
    private data class PlanView(val today: PlanToday?, val startsOn: LocalDate?)

    private data class Days(val day: DaySummary, val week: WeekSummary, val plan: PlanView)

    /**
     * Today and its week: Monday to Sunday, or once a plan runs, its programme week (ROADMAP
     * P3-2). Every day of a programme week up to today counts, logged or not, as its result does.
     */
    private val days: Flow<Days> = combine(today, graph.programmes.observeCurrent()) { date, record -> date to record?.plan }
        .distinctUntilChanged()
        .flatMapLatest { (date, plan) ->
            val planToday = plan?.let { PlanToday.of(it, date) }
            val view = PlanView(planToday, plan?.startDate?.takeIf { it > date })
            val start = planToday?.week?.startDate ?: WeekSummary.mondayOf(date)
            combine(
                graph.entries.observeDay(date),
                graph.entries.observeRange(start, start.plusDays(6)),
                graph.entries.observeFirstDay(),
            ) { day, week, firstDay ->
                Days(day, WeekSummary.from(start, date, week, if (planToday != null) start else firstDay), view)
            }
        }

    private val lists: Flow<Triple<List<QuickLogPill>, Map<String, String>, Map<String, TagEntity>>> =
        combine(graph.products.quickLog, graph.products.shortNames, graph.tags.all) { quickLog, names, tags ->
            val labels = PillLabel.of(quickLog)
            Triple(quickLog.map { QuickLogPill(it, labels.getValue(it.id)) }, names, tags.associateBy { it.key })
        }

    private val look = combine(graph.settings.settings, graph.game.demon, graph.absorbedFigures(), ::Triple)

    val state: StateFlow<TodayState?> = combine(now, days, lists, look, labelNote) { instant, days, (pills, names, tags), (settings, demon, absorbed), note ->
        TodayState(
            now = instant,
            date = days.day.date,
            day = days.day,
            week = days.week,
            pills = pills,
            productNames = names,
            tags = tags,
            demonName = settings.demonName,
            creature = settings.demonCreature,
            labelNote = note,
            plan = days.plan.today,
            planStartsOn = days.plan.startsOn,
            demon = demon,
            absorbed = absorbed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun quickLog(product: UserProduct) {
        log(LogRequest(product.id))
    }

    fun keepBoth() {
        val pending = _duplicate.value ?: return
        _duplicate.value = null
        log(pending.request.copy(allowDuplicate = true))
    }

    fun discardDuplicate() {
        _duplicate.value = null
    }

    /** The log sheet saved a dose, so Today shows the snackbar and any label note. */
    fun onSaved(entry: Entry, warning: LabelWarning?) {
        if (warning != null) labelNote.value = warning
        viewModelScope.launch { _logged.send(Logged(entry, nameOf(entry))) }
    }

    fun undo(entry: Entry) {
        viewModelScope.launch { graph.entries.delete(entry.id) }
    }

    fun dismissLabelNote() {
        labelNote.value = null
    }

    private fun log(request: LogRequest) {
        viewModelScope.launch {
            when (val result = graph.entries.log(request)) {
                is LogResult.Saved -> onSaved(result.entry, result.labelWarning)
                is LogResult.PossibleDuplicate -> _duplicate.value = PendingDuplicate(request, result.existing)
                // A quick-log product is always the user's own and always now, so these can't happen.
                LogResult.UnknownProduct, LogResult.NotFound, LogResult.TimeInFuture -> Unit
            }
        }
    }

    private fun nameOf(entry: Entry): String = state.value?.nameOf(entry) ?: entry.productName
}
