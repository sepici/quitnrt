package app.thelasthit.nrt

import android.app.Application
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.link.QuickLogShortcuts
import app.thelasthit.nrt.quicklog.QuickLog
import app.thelasthit.nrt.reminders.Reminders
import app.thelasthit.nrt.widget.LastHitWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the one [DataGraph] the whole app shares. */
open class LastHitApp : Application() {

    /** Created on first use. Tests override [createGraph] to swap in an in-memory one. */
    val graph: DataGraph by lazy { createGraph() }

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** For work that should finish even when the screen or service that started it has gone. */
    val appScope: CoroutineScope get() = startupScope

    /** The reminders and their checks on WorkManager. */
    val reminders: Reminders by lazy { Reminders(this, graph) }

    /** Logging from the widget and the Quick Settings tile. */
    val quickLog: QuickLog by lazy {
        QuickLog(this, graph, startupScope, onChange = { LastHitWidget.update(this) }, afterMoment = reminders::refreshWidgetAt)
    }

    protected open fun createGraph(): DataGraph = DataGraph(this)

    override fun onCreate() {
        super.onCreate()
        startupScope.launch {
            graph.tags.ensureBuiltIns()
            graph.entries.purgeDeleted()
        }
        // The quick log only changes while the app runs, so its shortcuts follow it from here.
        startupScope.launch { QuickLogShortcuts(this@LastHitApp).follow(graph.products.quickLog) }
        // Nudges after each log, the checks on WorkManager, and the widget kept up to date.
        startupScope.launch { reminders.follow() }
    }
}
