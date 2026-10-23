package app.thelasthit.nrt.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.log.DaySummary
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.log.WeekSummary
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.product.UserProduct
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
) {
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

    private val days: Flow<Pair<DaySummary, WeekSummary>> = today.flatMapLatest { date ->
        val monday = WeekSummary.mondayOf(date)
        combine(
            graph.entries.observeDay(date),
            graph.entries.observeRange(monday, monday.plusDays(6)),
            graph.entries.observeFirstDay(),
        ) { day, week, firstDay ->
            day to WeekSummary.of(date, week, firstDay)
        }
    }

    private val lists: Flow<Triple<List<QuickLogPill>, Map<String, String>, Map<String, TagEntity>>> =
        combine(graph.products.quickLog, graph.products.shortNames, graph.tags.all) { quickLog, names, tags ->
            val labels = PillLabel.of(quickLog)
            Triple(quickLog.map { QuickLogPill(it, labels.getValue(it.id)) }, names, tags.associateBy { it.key })
        }

    val state: StateFlow<TodayState?> = combine(now, days, lists, graph.settings.settings, labelNote) { instant, (day, week), (pills, names, tags), settings, note ->
        TodayState(
            now = instant,
            date = day.date,
            day = day,
            week = week,
            pills = pills,
            productNames = names,
            tags = tags,
            demonName = settings.demonName,
            creature = settings.demonCreature,
            labelNote = note,
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
