package app.thelasthit.nrt.agents

import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.advice.AdviceLibrary
import app.thelasthit.nrt.domain.advice.AdviceWindow
import app.thelasthit.nrt.domain.agent.AgentAdvice
import app.thelasthit.nrt.domain.agent.AgentAdviceAnswer
import app.thelasthit.nrt.domain.agent.AgentAdviceResult
import app.thelasthit.nrt.domain.agent.AgentDemon
import app.thelasthit.nrt.domain.agent.AgentLogAsk
import app.thelasthit.nrt.domain.agent.AgentLogReader
import app.thelasthit.nrt.domain.agent.AgentLogReading
import app.thelasthit.nrt.domain.agent.AgentLogRequest
import app.thelasthit.nrt.domain.agent.AgentRefusal
import app.thelasthit.nrt.domain.agent.AgentSummary
import app.thelasthit.nrt.domain.agent.AgentUndo
import app.thelasthit.nrt.domain.agent.PlanStage
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.matcher.ProductMatcher
import app.thelasthit.nrt.domain.matcher.TagMatcher
import app.thelasthit.nrt.domain.matcher.TagName
import app.thelasthit.nrt.domain.product.UserProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/** What came of an agent's request to log. */
sealed interface AgentLogOutcome {
    /** Saved, with the pack-limit note when the day went over it, and the day and week after. */
    data class Logged(val entry: Entry, val warning: LabelWarning?, val summary: AgentSummary) : AgentLogOutcome

    /**
     * Nothing saved: [existing] is the same product logged within a minute from somewhere else.
     * Logging again with keepDuplicate keeps both.
     */
    data class PossibleDuplicate(val existing: Entry, val request: AgentLogRequest) : AgentLogOutcome

    /** Nothing saved: [choices] are the products the description could mean, most likely first. */
    data class Ambiguous(val text: String, val choices: List<AgentLogRequest>) : AgentLogOutcome

    data class Refused(val reason: AgentRefusal) : AgentLogOutcome
}

sealed interface AgentUndoOutcome {
    /** [entry] is deleted, as History deletes, and [summary] is the day and week after. */
    data class Undone(val entry: Entry, val summary: AgentSummary) : AgentUndoOutcome

    data class Refused(val reason: AgentRefusal) : AgentUndoOutcome
}

/** An answer, or why there isn't one. */
sealed interface AgentAnswer<out T> {
    data class Given<T>(val value: T) : AgentAnswer<T>

    data class Refused(val reason: AgentRefusal) : AgentAnswer<Nothing>
}

/**
 * What the five agent functions do (PLAN §12), on the app's data, without the AppFunctions
 * library: [NrtAppFunctions] only turns these into the library's types. The rules are in
 * `:core:domain` (`domain.agent`); this reads and writes through the same repositories as the
 * rest of the app.
 *
 * Logging goes through the normal logging path, [app.thelasthit.nrt.data.repo.EntryRepository.log],
 * with [EntrySource.APP_FUNCTION] and its duplicate check, as a link or the log sheet would.
 * Every call is refused until set-up is finished, since there are no products until then.
 */
class NrtAgent(
    private val graph: DataGraph,
    /** The names a tag goes by: its own name if renamed, and a built-in tag's text. */
    private val tagNames: (TagEntity) -> List<String>,
    /** The phone's zone, for reading an agent's time. */
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val advice by lazy { AgentAdvice(AdviceLibrary.bundled()) }

    /** The products that can be logged, quick-log ones first in their order, then by name. */
    suspend fun products(): AgentAnswer<List<UserProduct>> {
        notSetUp()?.let { return it }
        return AgentAnswer.Given(graph.products.all.first().sortedWith(PRODUCT_ORDER))
    }

    /** Logs what [ask] describes, or says why not. [keepDuplicate] keeps a likely duplicate. */
    suspend fun log(ask: AgentLogAsk, keepDuplicate: Boolean = false): AgentLogOutcome {
        if (!isSetUp()) return AgentLogOutcome.Refused(AgentRefusal.NotSetUp)
        val reading = read(ask)
        val request = when (reading) {
            is AgentLogReading.Ready -> reading.request
            is AgentLogReading.Ambiguous -> return AgentLogOutcome.Ambiguous(reading.text, reading.choices)
            is AgentLogReading.Refused -> return AgentLogOutcome.Refused(reading.reason)
        }
        val logged = graph.entries.log(
            LogRequest(
                productId = request.product.id,
                quantity = request.quantity,
                at = request.at,
                cravingStrength = request.cravingStrength,
                tagKeys = request.tagKeys,
                source = EntrySource.APP_FUNCTION,
                allowDuplicate = keepDuplicate,
            ),
        )
        return when (logged) {
            is LogResult.Saved -> AgentLogOutcome.Logged(logged.entry, logged.labelWarning, summaryNow())
            is LogResult.PossibleDuplicate -> AgentLogOutcome.PossibleDuplicate(logged.existing, request)
            // The request was read a moment ago, so these only happen if something changed since.
            LogResult.TimeInFuture -> AgentLogOutcome.Refused(
                AgentRefusal.TimeInFuture(LocalDateTime.ofInstant(request.at ?: graph.clock.instant(), zone())),
            )
            LogResult.UnknownProduct, LogResult.NotFound -> AgentLogOutcome.Refused(AgentRefusal.UnknownProduct(request.product.id))
        }
    }

    /** Today against the guide, the week against its allowance, the last dose and the demon. */
    suspend fun summary(): AgentAnswer<AgentSummary> {
        notSetUp()?.let { return it }
        return AgentAnswer.Given(summaryNow())
    }

    /**
     * Deletes the entry written last, if that was in the last day and after the last restore from
     * backup, which writes every entry it brings in at once ([AgentUndo]).
     */
    suspend fun undoLast(): AgentUndoOutcome {
        if (!isSetUp()) return AgentUndoOutcome.Refused(AgentRefusal.NotSetUp)
        val since = AgentUndo.since(graph.clock.instant(), restoredAt = graph.settings.restoredAt())
        val last = graph.entries.lastWritten(since = since)
            ?: return AgentUndoOutcome.Refused(AgentRefusal.NothingToUndo)
        graph.entries.delete(last.id)
        return AgentUndoOutcome.Undone(last, summaryNow())
    }

    /**
     * The best strategy for [tag], or for the top trigger of the last 14 days when it's null, with
     * the quitlines for the user's country.
     */
    suspend fun advice(tag: String?): AgentAnswer<AgentAdviceAnswer> {
        notSetUp()?.let { return it }
        val today = graph.programmes.clock().today
        val tags = graph.tags.all.first().filterNot { it.isHidden }
        val groups = tags.associate { it.key to it.group }
        val entries = graph.entries.observeRange(AdviceWindow.of(today).start, today).first()
        val forms = graph.products.all.first().map { it.form }.toSet()
        val country = graph.settings.current().country
        val result = withContext(Dispatchers.Default) {
            advice.pick(tag, TagMatcher(tags.map { TagName(it.key, tagNames(it)) }), groups::get, entries, today, forms, country)
        }
        return when (result) {
            is AgentAdviceResult.Answer -> AgentAnswer.Given(result.answer)
            is AgentAdviceResult.Refused -> AgentAnswer.Refused(result.reason)
        }
    }

    private suspend fun read(ask: AgentLogAsk): AgentLogReading {
        // Hidden ones too: the matcher leaves them out, but knows one by its ID.
        val products = graph.products.everything.first()
        val tags = graph.tags.all.first().filterNot { it.isHidden }.map { TagName(it.key, tagNames(it)) }
        // Off the main thread: the first use reads the whole catalogue.
        return withContext(Dispatchers.Default) {
            AgentLogReader(ProductMatcher(products, graph.catalogue), TagMatcher(tags)).read(ask, graph.clock.instant(), zone())
        }
    }

    private suspend fun summaryNow(): AgentSummary {
        // Reading the programme settles the results that are due first.
        val record = graph.programmes.current()
        val today = graph.programmes.clock().today
        val entries = graph.entries.observeRange(AgentSummary.from(today), today).first()
        val demon = graph.game.demon()?.let { state ->
            AgentDemon(state.demon.name, state.balance.hp, state.balance.maxHp, state.balance.banked)
        }
        return AgentSummary.of(today, entries, PlanStage.of(record?.status), record?.plan, demon)
    }

    private suspend fun isSetUp(): Boolean = graph.settings.current().onboardingComplete

    private suspend fun notSetUp(): AgentAnswer.Refused? = if (isSetUp()) null else AgentAnswer.Refused(AgentRefusal.NotSetUp)

    private companion object {
        val PRODUCT_ORDER: Comparator<UserProduct> = compareBy<UserProduct>(
            { it.quickLogPosition ?: Int.MAX_VALUE },
            { it.name.lowercase() },
            { it.id },
        )
    }
}
