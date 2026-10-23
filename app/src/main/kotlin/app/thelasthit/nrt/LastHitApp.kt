package app.thelasthit.nrt

import android.app.Application
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.link.QuickLogShortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the one [DataGraph] the whole app shares. */
open class LastHitApp : Application() {

    /** Created on first use. Tests override [createGraph] to swap in an in-memory one. */
    val graph: DataGraph by lazy { createGraph() }

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    protected open fun createGraph(): DataGraph = DataGraph(this)

    override fun onCreate() {
        super.onCreate()
        startupScope.launch {
            graph.tags.ensureBuiltIns()
            graph.entries.purgeDeleted()
        }
        // The quick log only changes while the app runs, so its shortcuts follow it from here.
        startupScope.launch { QuickLogShortcuts(this@LastHitApp).follow(graph.products.quickLog) }
    }
}
