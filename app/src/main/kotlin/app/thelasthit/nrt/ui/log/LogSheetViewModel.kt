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
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.log.LogTime
import app.thelasthit.nrt.domain.log.QuickTime
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.TagOrder
import app.thelasthit.nrt.domain.matcher.LogLinkParser
import app.thelasthit.nrt.domain.matcher.LogLinkRequest
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.programme.GapNote
import app.thelasthit.nrt.ui.absorbed.AbsorbedFigures
import app.thelasthit.nrt.ui.absorbed.absorbedFigures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
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
    /** Where the sheet was opened from, which a new dose records. */
    val source: EntrySource = EntrySource.APP,
    /**
     * The products a link could mean, in its order, each with the amount the link asks for in
     * that product's unit. Empty unless a link was unclear. Until one is picked, none is.
     */
    val choices: Map<String, Int> = emptyMap(),
)

data class TagChips(val group: TagGroup, val tags: List<TagEntity>)

data class LogSheetState(
    val form: LogForm,
    /** A link's choices or the quick log's products first, in their order, then the rest of the user's products. */
    val products: List<UserProduct>,
    /** How many of [products] show before "More": a link's choices, or the quick log. */
    val leadingCount: Int,
    val tagGroups: List<TagChips>,
    val todayTotal: Dose,
    val now: Instant,
    /** Estimated absorbed amounts, while the setting is on (ROADMAP P9-1). Only for showing. */
    val absorbed: AbsorbedFigures? = null,
) {
    val product: UserProduct? get() = products.firstOrNull { it.id == form.productId }
    val dose: Dose get() = product?.perUnit?.times(form.quantity) ?: Dose.ZERO
    val canSave: Boolean get() = product != null
    val isChoosing: Boolean get() = form.choices.isNotEmpty()

    /** About how much of [dose] reaches the blood, while the setting is on. */
    val absorbedDose: Dose? get() = product?.let { absorbed?.of(it, dose) }
}

/** A finished save, for Today's snackbar and label note. */
data class SavedDose(val entry: Entry, val warning: LabelWarning?, val wasEdit: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class LogSheetViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val form = MutableStateFlow(LogForm())

    /** The edited entry's product, kept on the sheet even if it has since been hidden. */
    private val editingProduct = MutableStateFlow<UserProduct?>(null)

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
        combine(graph.products.quickLog, graph.products.all, editingProduct, graph.absorbedFigures()) { quick, all, editing, absorbed ->
            Triple(quick, (all + listOfNotNull(editing)).distinctBy { it.id }, absorbed)
        },
        tagGroups,
        todayTotal,
        openedAt,
    ) { form, (quick, all, absorbed), groups, total, now ->
        val quickIds = quick.map { it.id }.toSet()
        val usual = quick + all.filter { it.id !in quickIds }
        val chosen = form.choices.keys.mapNotNull { id -> usual.firstOrNull { it.id == id } }
        val products = chosen + usual.filter { it !in chosen }
        // A link that was unclear waits for a pick rather than guessing.
        val productId = form.productId ?: if (form.choices.isEmpty()) products.firstOrNull()?.id else null
        LogSheetState(
            form = form.copy(productId = productId),
            products = products,
            leadingCount = if (form.choices.isEmpty()) quick.size else chosen.size,
            tagGroups = groups,
            todayTotal = total,
            now = now,
            absorbed = absorbed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What the spacing note needs from the form: when the dose is, on which day, and whether it's a patch. */
    private data class GapQuery(val at: Instant, val date: LocalDate, val isPatch: Boolean, val editing: String?)

    /**
     * The spacing note (PLAN §6.3): with a gap goal on in the dose's week, how close the dose is
     * to the nearest one that day, when that's under the goal. It never stops the dose being logged.
     */
    val gapNote: StateFlow<GapNote?> = combine(state, graph.settings.settings) { current, settings ->
        val product = current?.product ?: return@combine null
        val at = when (val time = current.form.time) {
            is TimeChoice.Quick -> time.time.resolve(current.now, zone())
            is TimeChoice.At -> time.instant
        }
        GapQuery(at, settings.dayBoundary.logicalDate(at, zone()), product.form.isBackground, current.form.editing?.id)
    }.distinctUntilChanged().flatMapLatest { query ->
        if (query == null || query.isPatch) {
            flowOf(null)
        } else {
            combine(graph.programmes.observeCurrent(), graph.entries.observeDay(query.date)) { record, day ->
                GapNote.of(GapNote.goalOn(record?.plan, query.date), query.at, query.date, query.isPatch, day.entries, query.editing)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Starts a fresh dose, optionally with a product picked, such as from a long press on a pill. */
    fun openNew(productId: String? = null, source: EntrySource = EntrySource.APP) {
        openedAt.value = graph.clock.instant()
        editingProduct.value = null
        form.value = LogForm(productId = productId, source = source)
    }

    /**
     * Starts a dose from a link: a blank sheet for `lasthit://log`, or, when the link's product
     * could be several, those [choices] to pick from, with the link's amount, time and tags.
     */
    fun openFromLink(source: EntrySource, choices: List<LogLinkRequest> = emptyList()) {
        val first = choices.firstOrNull() ?: return openNew(source = source)
        openedAt.value = graph.clock.instant()
        editingProduct.value = null
        form.value = LogForm(
            quantity = first.quantity,
            time = first.at?.let { TimeChoice.At(it) } ?: TimeChoice.Quick(QuickTime.NOW),
            tags = first.tagKeys,
            source = source,
            choices = choices.associate { it.product.id to it.quantity },
        )
    }

    /** Opens an existing entry for editing. */
    fun openEdit(entry: Entry) {
        openedAt.value = graph.clock.instant()
        editingProduct.value = null
        viewModelScope.launch { editingProduct.value = graph.products.get(entry.productId) }
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

    /** A link's choice brings the amount the link asks for in that product's unit. */
    fun pickProduct(id: String) = form.update { it.copy(productId = id, quantity = it.choices[id] ?: it.quantity) }

    fun showOtherProducts() = form.update { it.copy(showOtherProducts = true) }

    /** Stops where a link does, so there's one limit. */
    fun changeQuantity(by: Int) = form.update { it.copy(quantity = (it.quantity + by).coerceIn(1, LogLinkParser.MAX_QUANTITY)) }

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
            source = current.form.source,
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
        const val MAX_NOTE = 500
    }
}
