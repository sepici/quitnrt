package app.thelasthit.nrt.ui.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.thelasthit.nrt.LastHitApp
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.matcher.LogLinkError
import app.thelasthit.nrt.domain.matcher.LogLinkParser
import app.thelasthit.nrt.domain.matcher.LogLinkRequest
import app.thelasthit.nrt.domain.matcher.LogLinkResult
import app.thelasthit.nrt.domain.matcher.ProductMatcher
import app.thelasthit.nrt.domain.matcher.TagMatcher
import app.thelasthit.nrt.domain.matcher.TagName
import app.thelasthit.nrt.link.IncomingLink
import app.thelasthit.nrt.ui.format.builtInTagName
import app.thelasthit.nrt.ui.log.SavedDose
import app.thelasthit.nrt.ui.today.PendingDuplicate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/** What came of a link, for the app's shell to show. */
sealed interface LinkOutcome {
    /**
     * A dose was saved, for the snackbar with Undo, and, when it took the day over the pack's
     * limit, the calm note after it.
     */
    data class Logged(val entry: Entry, val name: String, val warning: LabelWarning? = null) : LinkOutcome

    data class Refused(val error: LogLinkError) : LinkOutcome

    /**
     * Opens the log sheet, marked with where it came from. With [choices], it offers the products
     * an ambiguous link could mean, with the rest of the link filled in.
     */
    data class OpenSheet(val source: EntrySource, val choices: List<LogLinkRequest> = emptyList()) : LinkOutcome
}

/**
 * Handles the `lasthit://log` links the app is opened with, one at a time in the order they came.
 * A link logs through the same path as the log sheet: [app.thelasthit.nrt.data.repo.EntryRepository.log]
 * with the link's source, the duplicate check, and Undo. A link waiting on the duplicate question
 * holds back the ones after it until it's answered. Belongs to the activity, so a link that
 * arrives before the app's screens are up waits for them.
 */
class LinkViewModel(
    private val graph: DataGraph,
    /** The names a tag goes by: its own name if renamed, and a built-in tag's text. */
    private val tagNames: (TagEntity) -> List<String>,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val incoming = Channel<IncomingLink>(Channel.UNLIMITED)
    private val _outcomes = Channel<LinkOutcome>(Channel.BUFFERED)
    private val _duplicate = MutableStateFlow<PendingDuplicate?>(null)

    /** The answer to [duplicate]: true to keep both. */
    private val answers = Channel<Boolean>(Channel.CONFLATED)

    val outcomes: Flow<LinkOutcome> = _outcomes.receiveAsFlow()

    /** A dose that looks like one logged within a minute from somewhere else, waiting for an answer. */
    val duplicate: StateFlow<PendingDuplicate?> = _duplicate.asStateFlow()

    init {
        viewModelScope.launch {
            for (link in incoming) handle(link)
        }
    }

    fun open(link: IncomingLink) {
        incoming.trySend(link)
    }

    fun keepBoth() = answer(keep = true)

    fun discardDuplicate() = answer(keep = false)

    private fun answer(keep: Boolean) {
        if (_duplicate.value == null) return
        _duplicate.value = null
        answers.trySend(keep)
    }

    /** The log sheet a link opened saved a dose, so it gets the same snackbar and note. */
    fun onSaved(saved: SavedDose) {
        viewModelScope.launch { _outcomes.send(LinkOutcome.Logged(saved.entry, nameOf(saved.entry), saved.warning)) }
    }

    fun undo(entry: Entry) {
        viewModelScope.launch { graph.entries.delete(entry.id) }
    }

    private suspend fun handle(link: IncomingLink) {
        // Until set-up is done there are no products to log, and set-up is on screen anyway.
        if (!graph.settings.current().onboardingComplete) return
        val result = parse(link.link)
        when (result) {
            is LogLinkResult.Valid -> log(result.request.toLogRequest(link.source))
            LogLinkResult.Blank -> _outcomes.send(LinkOutcome.OpenSheet(link.source))
            is LogLinkResult.Ambiguous -> _outcomes.send(LinkOutcome.OpenSheet(link.source, result.choices))
            is LogLinkResult.Invalid -> _outcomes.send(LinkOutcome.Refused(result.error))
        }
    }

    private suspend fun parse(link: String): LogLinkResult {
        // Hidden ones too: the matcher leaves them out, but knows a pinned shortcut's for one.
        val products = graph.products.everything.first()
        val tags = graph.tags.all.first().filterNot { it.isHidden }.map { TagName(it.key, tagNames(it)) }
        // Off the main thread: the first use reads the whole catalogue.
        return withContext(Dispatchers.Default) {
            LogLinkParser(ProductMatcher(products, graph.catalogue), TagMatcher(tags)).parse(link, graph.clock.instant(), zone())
        }
    }

    private suspend fun log(request: LogRequest) {
        when (val result = graph.entries.log(request)) {
            is LogResult.Saved -> _outcomes.send(LinkOutcome.Logged(result.entry, nameOf(result.entry), result.labelWarning))
            is LogResult.PossibleDuplicate -> {
                // The next link waits for the answer, so its own question can't take this one's place.
                _duplicate.value = PendingDuplicate(request, result.existing)
                if (answers.receive()) log(request.copy(allowDuplicate = true))
            }
            // The link was checked a moment ago, so these only happen if something changed since.
            LogResult.TimeInFuture -> {
                val at = LocalDateTime.ofInstant(request.at ?: graph.clock.instant(), zone())
                _outcomes.send(LinkOutcome.Refused(LogLinkError.TimeInFuture(at)))
            }
            LogResult.UnknownProduct, LogResult.NotFound -> _outcomes.send(LinkOutcome.Refused(LogLinkError.UnknownProduct(request.productId)))
        }
    }

    private suspend fun nameOf(entry: Entry): String = graph.products.shortNames.first()[entry.productId] ?: entry.productName

    companion object {
        /** The same factory wherever the activity's one is asked for, by the activity or the shell. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as LastHitApp
                LinkViewModel(app.graph, tagNames = { tag -> listOfNotNull(tag.customName, app.resources.builtInTagName(tag.key)) })
            }
        }
    }
}

/** A valid link's request, logged with the link's source. */
fun LogLinkRequest.toLogRequest(source: EntrySource): LogRequest =
    LogRequest(productId = product.id, quantity = quantity, at = at, tagKeys = tagKeys, source = source)
