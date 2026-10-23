package app.thelasthit.nrt.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.log.LogTime
import app.thelasthit.nrt.domain.log.QuickTime
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.TagOrder
import app.thelasthit.nrt.domain.product.UserProduct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** When the dose was taken: one of the quick choices, or a time from the picker. */
sealed interface TimeChoice {
    data class Quick(val time: QuickTime) : TimeChoice
    data class At(val instant: Instant) : TimeChoice
}

/** What the user has filled in so far. */
data class LogForm(
    val editing: Entry? = null,
    val productId: String? = null,
    val quantity: Int = 1,
    val time: TimeChoice = TimeChoice.Quick(QuickTime.NOW),
    val kind: EntryKind = EntryKind.UNSPECIFIED,
    val strength: Int? = null,
    val tags: Set<String> = emptySet(),
    val note: String = "",
    val showOtherProducts: Boolean = false,
    val timeInFuture: Boolean = false,
    val duplicate: Entry? = null,
)

data class TagChips(val group: TagGroup, val tags: List<TagEntity>)

data class LogSheetState(
    val form: LogForm,
    /** Quick-log products first, in their order, then the rest of the user's products. */
    val products: List<UserProduct>,
    val quickLogCount: Int,
    val tagGroups: List<TagChips>,
    val todayTotal: Dose,
    val now: Instant,
) {
    val product: UserProduct? get() = products.firstOrNull { it.id == form.productId }
    val dose: Dose get() = product?.perUnit?.times(form.quantity) ?: Dose.ZERO
    val canSave: Boolean get() = product != null
}

/** A finished save, for Today's snackbar and label note. */
data class SavedDose(val entry: Entry, val warning: LabelWarning?, val wasEdit: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class LogSheetViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val form = MutableStateFlow(LogForm())

    /** When the sheet opened. "Now" means this until the user saves. */
    private val openedAt = MutableStateFlow(graph.clock.instant())

    private val tagGroups: Flow<List<TagChips>> = openedAt.flatMapLatest { now ->
        combine(graph.tags.all, graph.tags.observeUses(now.minus(TagOrder.USE_WINDOW))) { tags, uses ->
            TagGroup.entries.mapNotNull { group ->
                val inGroup = tags.filter { it.group == group && !it.isHidden }
                if (inGroup.isEmpty()) null else TagChips(group, TagOrder.byUse(inGroup, uses) { it.key })
            }
        }
    }

    private val todayTotal: Flow<Dose> = combine(openedAt, graph.settings.settings) { now, settings ->
        settings.dayBoundary.logicalDate(now, zone())
    }.flatMapLatest { date -> graph.entries.observeDay(date) }.map { it.total }

    val state: StateFlow<LogSheetState?> = combine(
        form,
        combine(graph.products.quickLog, graph.products.all) { quick, all -> quick to all },
        tagGroups,
        todayTotal,
        openedAt,
    ) { form, (quick, all), groups, total, now ->
        val quickIds = quick.map { it.id }.toSet()
        val products = quick + all.filter { it.id !in quickIds }
        val productId = form.productId ?: products.firstOrNull()?.id
        LogSheetState(
            form = form.copy(productId = productId),
            products = products,
            quickLogCount = quick.size,
            tagGroups = groups,
            todayTotal = total,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Starts a fresh dose, optionally with a product picked, such as from a long press on a pill. */
    fun openNew(productId: String? = null) {
        openedAt.value = graph.clock.instant()
        form.value = LogForm(productId = productId)
    }

    /** Opens an existing entry for editing. */
    fun openEdit(entry: Entry) {
        openedAt.value = graph.clock.instant()
        form.value = LogForm(
            editing = entry,
            productId = entry.productId,
            quantity = entry.quantity,
            time = TimeChoice.At(entry.occurredAt),
            kind = entry.kind,
            strength = entry.cravingStrength,
            tags = entry.tagKeys,
            note = entry.note.orEmpty(),
            showOtherProducts = true,
        )
    }

    fun pickProduct(id: String) = form.update { it.copy(productId = id) }

    fun showOtherProducts() = form.update { it.copy(showOtherProducts = true) }

    fun changeQuantity(by: Int) = form.update { it.copy(quantity = (it.quantity + by).coerceIn(1, MAX_QUANTITY)) }

    /** A picked time in the future is flagged straight away rather than on save. */
    fun pickTime(time: TimeChoice) = form.update {
        it.copy(time = time, timeInFuture = !LogTime.isAcceptable(resolvedTime(time), graph.clock.instant()))
    }

    /** Tapping the chosen option again clears it, since the kind is optional. */
    fun pickKind(kind: EntryKind) = form.update { current ->
        val next = if (current.kind == kind) EntryKind.UNSPECIFIED else kind
        current.copy(kind = next, strength = if (next == EntryKind.PLANNED) null else current.strength)
    }

    fun pickStrength(strength: Int) = form.update { it.copy(strength = if (it.strength == strength) null else strength) }

    fun toggleTag(key: String) = form.update { it.copy(tags = if (key in it.tags) it.tags - key else it.tags + key) }

    fun changeNote(note: String) = form.update { it.copy(note = note.take(MAX_NOTE)) }

    fun dismissDuplicate() = form.update { it.copy(duplicate = null) }

    /** The moment the dose was taken, worked out now, so "1 h ago" means an hour before saving. */
    fun resolvedTime(time: TimeChoice = form.value.time): Instant = when (time) {
        is TimeChoice.Quick -> time.time.resolve(graph.clock.instant(), zone())
        is TimeChoice.At -> time.instant
    }

    /** Saves the dose. [onSaved] runs once it's stored; a question or a refusal shows on the sheet instead. */
    fun save(allowDuplicate: Boolean = false, onSaved: (SavedDose) -> Unit) {
        val current = state.value ?: return
        val productId = current.form.productId ?: return
        val editing = current.form.editing
        val request = LogRequest(
            productId = productId,
            quantity = current.form.quantity,
            at = resolvedTime(current.form.time),
            kind = current.form.kind,
            cravingStrength = current.form.strength,
            tagKeys = current.form.tags,
            note = current.form.note.trim().ifBlank { null },
            allowDuplicate = allowDuplicate,
        )
        viewModelScope.launch {
            val result = if (editing != null) graph.entries.edit(editing.id, request) else graph.entries.log(request)
            when (result) {
                is LogResult.Saved -> {
                    form.update { it.copy(duplicate = null) }
                    onSaved(SavedDose(result.entry, result.labelWarning, wasEdit = editing != null))
                }
                is LogResult.PossibleDuplicate -> form.update { it.copy(duplicate = result.existing) }
                LogResult.TimeInFuture -> form.update { it.copy(timeInFuture = true) }
                // The product list only offers the user's own products, and an edited entry exists.
                LogResult.UnknownProduct, LogResult.NotFound -> Unit
            }
        }
    }

    private companion object {
        const val MAX_QUANTITY = 99
        const val MAX_NOTE = 500
    }
}
