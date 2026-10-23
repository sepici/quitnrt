package app.thelasthit.nrt.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.LoggedDay
import app.thelasthit.nrt.ui.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class HistoryState(
    val today: LocalDate,
    val days: List<LoggedDay>,
    /** Whether anything was logged before the days shown, so "Show earlier days" has something to show. */
    val hasEarlier: Boolean,
    val productNames: Map<String, String>,
    val tags: Map<String, TagEntity>,
    val dayStart: LocalTime,
) {
    fun nameOf(entry: Entry): String = productNames[entry.productId] ?: entry.productName
}

/** A dose just deleted from History, for the snackbar with Undo. */
data class Deleted(val entry: Entry, val name: String)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    /** How many days back History shows. "Show earlier days" adds another [PAGE_DAYS]. */
    private val shownDays = MutableStateFlow(PAGE_DAYS)
    private val _deleted = Channel<Deleted>(Channel.BUFFERED)
    val deleted: Flow<Deleted> = _deleted.receiveAsFlow()

    private val today: Flow<LocalDate> = combine(minuteTicks(graph.clock), graph.settings.settings) { now, settings ->
        settings.dayBoundary.logicalDate(now, zone())
    }.distinctUntilChanged()

    private val window: Flow<Pair<LocalDate, LocalDate>> = combine(today, shownDays) { day, count ->
        day.minusDays(count - 1L) to day
    }

    private val days: Flow<List<LoggedDay>> = window.flatMapLatest { (from, to) ->
        graph.entries.observeRange(from, to).map(LoggedDay::groupByDay)
    }

    val state: StateFlow<HistoryState?> = combine(
        window,
        days,
        graph.entries.observeFirstDay(),
        combine(graph.products.shortNames, graph.tags.all) { names, tags -> names to tags.associateBy { it.key } },
        graph.settings.settings,
    ) { (from, to), loggedDays, firstDay, (names, tags), settings ->
        HistoryState(
            today = to,
            days = loggedDays,
            hasEarlier = firstDay != null && firstDay < from,
            productNames = names,
            tags = tags,
            dayStart = settings.dayStart,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showEarlier() = shownDays.update { it + PAGE_DAYS }

    /** Deletes softly, so Undo can bring it back. */
    fun delete(entry: Entry) {
        viewModelScope.launch {
            graph.entries.delete(entry.id)
            _deleted.send(Deleted(entry, state.value?.nameOf(entry) ?: entry.productName))
        }
    }

    fun undoDelete(entry: Entry) {
        viewModelScope.launch { graph.entries.undoDelete(entry.id) }
    }

    /** For an entry logged from History's empty state, which offers Undo like Today does. */
    fun undoLog(entry: Entry) {
        viewModelScope.launch { graph.entries.delete(entry.id) }
    }

    private companion object {
        const val PAGE_DAYS = 30
    }
}
