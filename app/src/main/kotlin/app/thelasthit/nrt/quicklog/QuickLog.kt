package app.thelasthit.nrt.quicklog

import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.thelasthit.nrt.MainActivity
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.link.LogLinks
import app.thelasthit.nrt.reminders.NoticePoster
import app.thelasthit.nrt.reminders.NoticeTexts
import app.thelasthit.nrt.ui.format.mgText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/** What the widget shows for a moment after a tap, in place of its favourites. */
sealed interface WidgetMoment {

    /** A dose went in: "Logged 2 mg of Nicorette gum", with Undo and Tags. */
    data class Logged(val entry: Entry, val name: String, val warning: LabelWarning?) : WidgetMoment

    /** The tap looks like a dose logged within a minute from somewhere else, so it asks first. */
    data class Duplicate(val request: LogRequest, val existing: Entry) : WidgetMoment
}

/** What the tile does after a tap: it logged a dose, or the app should open. */
sealed interface TileOutcome {
    data class Logged(val entry: Entry) : TileOutcome
    data class Open(val intent: Intent) : TileOutcome
}

/**
 * Logging from outside the app, by the widget's favourites and the Quick Settings tile (PLAN §11).
 * Both go through [app.thelasthit.nrt.data.repo.EntryRepository.log] like every other way in,
 * each with its own source, the duplicate check and Undo.
 */
class QuickLog(
    private val context: Context,
    private val graph: DataGraph,
    private val scope: CoroutineScope,
    /** Draws the widget again. */
    private val onChange: suspend () -> Unit,
    /** Books a fallback redraw for when a moment ends, in case the app is gone by then. */
    private val afterMoment: (Instant) -> Unit = {},
    private val hold: Duration = HOLD,
) {
    private val _moment = MutableStateFlow<WidgetMoment?>(null)
    private var expiry: Job? = null

    /** The widget's moment, until it ends or is answered. */
    val moment: StateFlow<WidgetMoment?> = _moment.asStateFlow()

    // The widget.

    /** A favourite tapped on the widget: logs one now, then shows "Logged" with Undo (P6-2). */
    suspend fun logFromWidget(productId: String): LogResult = log(LogRequest(productId, source = EntrySource.WIDGET))

    /** The widget's answer to the duplicate question: a separate dose after all. */
    suspend fun keepBoth(): LogResult? {
        val pending = _moment.value as? WidgetMoment.Duplicate ?: return null
        return log(pending.request.copy(allowDuplicate = true))
    }

    /** The widget's other answer: the same dose, so this tap is dropped. */
    suspend fun discard() {
        if (_moment.value is WidgetMoment.Duplicate) show(null)
        onChange()
    }

    /** Undo from the widget or the tile's note: the dose is deleted, as Today's Undo does. */
    suspend fun undo(entryId: String) {
        graph.entries.delete(entryId)
        if ((_moment.value as? WidgetMoment.Logged)?.entry?.id == entryId) show(null)
        onChange()
    }

    private suspend fun log(request: LogRequest): LogResult {
        val result = graph.entries.log(request)
        when (result) {
            is LogResult.Saved -> show(WidgetMoment.Logged(result.entry, nameOf(result.entry), result.labelWarning))
            is LogResult.PossibleDuplicate -> show(WidgetMoment.Duplicate(request, result.existing))
            // A favourite gone since the widget was drawn: drawing it again shows the quick log as it is.
            LogResult.UnknownProduct, LogResult.NotFound, LogResult.TimeInFuture -> show(null)
        }
        onChange()
        return result
    }

    /**
     * Shows [moment] until it times out: about 5 seconds for "Logged", longer when there's a
     * label note or a question to read.
     */
    private fun show(moment: WidgetMoment?) {
        expiry?.cancel()
        _moment.value = moment
        if (moment == null) return
        val lasts = when {
            moment is WidgetMoment.Logged && moment.warning == null -> hold
            else -> hold.multipliedBy(LONG_HOLD_FACTOR)
        }
        afterMoment(graph.clock.instant().plus(lasts).plusSeconds(1))
        expiry = scope.launch {
            delay(lasts.toMillis())
            if (_moment.compareAndSet(moment, null)) onChange()
        }
    }

    private suspend fun nameOf(entry: Entry): String = graph.products.shortNames.first()[entry.productId] ?: entry.productName

    // The tile.

    /**
     * A tap on the tile: logs one of the first product on the quick log, with a note that has
     * Undo, unless the tile is set to open the log sheet. When the dose looks like one logged a
     * moment ago from somewhere else, the app opens on it to ask, as a link would.
     */
    suspend fun tapTile(): TileOutcome {
        if (!graph.settings.current().onboardingComplete) return TileOutcome.Open(openApp())
        val first = graph.products.quickLog.first().firstOrNull()
        if (first == null || graph.reminders.currentQuickLog().tileOpensSheet) {
            return TileOutcome.Open(LogLinks.appIntent(context, LogLinks.blank, LogLinks.SOURCE_TILE))
        }
        return when (val result = graph.entries.log(LogRequest(first.id, source = EntrySource.TILE))) {
            is LogResult.Saved -> {
                val entry = result.entry
                val note = NoticeTexts(context.resources).loggedFromTile(entry.id, entry.total, nameOf(entry), result.labelWarning, TILE_NOTE)
                if (!NoticePoster(context).post(note)) {
                    // Without notifications, a toast at least says it worked.
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.today_logged, entry.total.mgText(), nameOf(entry)), Toast.LENGTH_SHORT).show()
                    }
                }
                onChange()
                TileOutcome.Logged(entry)
            }
            is LogResult.PossibleDuplicate -> TileOutcome.Open(LogLinks.appIntent(context, LogLinks.forProduct(first.id), LogLinks.SOURCE_TILE))
            LogResult.UnknownProduct, LogResult.NotFound, LogResult.TimeInFuture ->
                TileOutcome.Open(LogLinks.appIntent(context, LogLinks.blank, LogLinks.SOURCE_TILE))
        }
    }

    private fun openApp(): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)

    companion object {
        /** About 5 seconds, as ROADMAP P6-2 has it. */
        val HOLD: Duration = Duration.ofSeconds(5)

        /** A label note or the duplicate question stays twice as long, to be read. */
        const val LONG_HOLD_FACTOR = 2L

        /** The tile's note stays as long as the app's own snackbar with Undo. */
        val TILE_NOTE: Duration = Duration.ofSeconds(10)
    }
}
