package app.thelasthit.nrt.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.HistoryFilter
import app.thelasthit.nrt.domain.log.LoggedDay
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.product.UserProduct
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
import kotlinx.coroutines.flow.flowOf
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
    /** Under a [filter], only the days with a match, each holding only its matches. */
    val days: List<LoggedDay>,
    /** Whether there's more before the days shown, so "Show earlier days" has something to show. */
    val hasEarlier: Boolean,
    val productNames: Map<String, String>,
    val tags: Map<String, TagEntity>,
    val dayStart: LocalTime,
    val filter: HistoryFilter? = null,
    val options: FilterOptions = FilterOptions.NONE,
) {
    fun nameOf(entry: Entry): String = productNames[entry.productId] ?: entry.productName

    /** How many doses the filter matches across all of History, for the count beside its chip. */
    val matches: Int get() = options.count(filter)

    /** Nothing logged at all, as opposed to nothing matching a filter. */
    val isEmpty: Boolean get() = filter == null && days.isEmpty() && !hasEarlier
}

/** A product the filter offers, with its quick-log style label: "Gum 2 mg". */
data class ProductChoice(val product: UserProduct, val label: PillLabel)

/** One group's tags that have been used, most used first. */
data class TagChoices(val group: TagGroup, val tags: List<TagEntity>)

/**
 * What the filter sheet offers: the products and tags with at least one dose, most used first,
 * and how many doses each would show.
 */
data class FilterOptions(
    val products: List<ProductChoice>,
    val tagGroups: List<TagChoices>,
    private val productUses: Map<String, Int>,
    private val tagUses: Map<String, Int>,
) {
    /** How many doses [filter] shows, across all of History. With no filter, that's every dose. */
    fun count(filter: HistoryFilter?): Int = when (filter) {
        null -> productUses.values.sum()
        is HistoryFilter.Product -> productUses[filter.productId] ?: 0
        is HistoryFilter.Tag -> tagUses[filter.tagKey] ?: 0
    }

    fun labelOf(productId: String): PillLabel? = products.firstOrNull { it.product.id == productId }?.label

    companion object {
        val NONE = FilterOptions(emptyList(), emptyList(), emptyMap(), emptyMap())
    }
}

/** A dose just deleted from History, for the snackbar with Undo. */
data class Deleted(val entry: Entry, val name: String)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    /**
     * How many days back History shows, or under a filter how many days with a match. "Show
     * earlier days" adds another [PAGE_DAYS].
     */
    private val shownDays = MutableStateFlow(PAGE_DAYS)
    private val filter = MutableStateFlow<HistoryFilter?>(null)
    private val _deleted = Channel<Deleted>(Channel.BUFFERED)
    val deleted: Flow<Deleted> = _deleted.receiveAsFlow()

    private val today: Flow<LocalDate> = combine(minuteTicks(graph.clock), graph.settings.settings) { now, settings ->
        settings.dayBoundary.logicalDate(now, zone())
    }.distinctUntilChanged()

    /** The days to show. It carries its filter, so the chip and the list change together. */
    private data class Page(val filter: HistoryFilter?, val days: List<LoggedDay>, val hasEarlier: Boolean)

    private val page: Flow<Page> = combine(today, shownDays, filter) { today, count, filter -> Triple(today, count, filter) }
        .flatMapLatest { (today, count, filter) -> if (filter == null) everyDay(today, count) else matchingDays(filter, count) }

    private fun everyDay(today: LocalDate, count: Int): Flow<Page> {
        val from = today.minusDays(count - 1L)
        return combine(graph.entries.observeRange(from, today), graph.entries.observeFirstDay()) { entries, firstDay ->
            Page(null, LoggedDay.groupByDay(entries), hasEarlier = firstDay != null && firstDay < from)
        }
    }

    /**
     * Under a filter a page is [count] days with a match, however far back they go, so "Show
     * earlier days" always brings more. Each day also counts every dose it had, for "1 of 5 doses".
     */
    private fun matchingDays(filter: HistoryFilter, count: Int): Flow<Page> =
        graph.entries.observeMatching(filter).flatMapLatest { matching ->
            val all = LoggedDay.groupByDay(matching)
            val shown = all.take(count)
            if (shown.isEmpty()) {
                flowOf(Page(filter, emptyList(), hasEarlier = false))
            } else {
                graph.entries.observeDosesPerDay(shown.last().date, shown.first().date).map { dosesOn ->
                    Page(filter, HistoryFilter.days(shown.flatMap { it.entries }, dosesOn), hasEarlier = all.size > count)
                }
            }
        }

    private val options: Flow<FilterOptions> = combine(
        graph.products.everything,
        graph.entries.observeProductUses(),
        graph.tags.all,
        graph.tags.observeAllUses(),
    ) { products, productUses, tags, tagUses ->
        val used = HistoryFilter.choices(products, productUses) { it.id }
        val labels = PillLabel.of(used)
        FilterOptions(
            products = used.map { ProductChoice(it, labels.getValue(it.id)) },
            tagGroups = TagGroup.entries.mapNotNull { group ->
                val inGroup = HistoryFilter.choices(tags.filter { it.group == group }, tagUses) { it.key }
                if (inGroup.isEmpty()) null else TagChoices(group, inGroup)
            },
            productUses = productUses,
            tagUses = tagUses,
        )
    }

    val state: StateFlow<HistoryState?> = combine(
        today,
        page,
        combine(graph.products.shortNames, graph.tags.all) { names, tags -> names to tags.associateBy { it.key } },
        graph.settings.settings,
        options,
    ) { today, page, (names, tags), settings, options ->
        HistoryState(
            today = today,
            days = page.days,
            hasEarlier = page.hasEarlier,
            productNames = names,
            tags = tags,
            dayStart = settings.dayStart,
            filter = page.filter,
            options = options,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showEarlier() = shownDays.update { it + PAGE_DAYS }

    /** Shows only doses with one product or one tag, from the newest day. Null shows everything. */
    fun applyFilter(filter: HistoryFilter?) {
        shownDays.value = PAGE_DAYS
        this.filter.value = filter
    }

    fun clearFilter() = applyFilter(null)

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
