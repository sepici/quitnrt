package app.thelasthit.nrt.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.thelasthit.nrt.LastHitApp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.quicklog.TileOutcome
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.pillLabelText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Quick Settings tile (PLAN §11): a tap logs the first product on the quick log, with a note
 * that has Undo, or opens the log sheet if the user would rather (Settings, Widget and tile). On
 * a locked phone it asks for the unlock first, since doses are private.
 */
open class LogTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app: LastHitApp get() = application as LastHitApp

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::tap) else tap()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun tap() {
        // The log runs on the app's own scope, so it finishes even if the panel closes first.
        app.appScope.launch {
            val outcome = app.quickLog.tapTile()
            withContext(Dispatchers.Main) {
                when (outcome) {
                    is TileOutcome.Open -> open(outcome.intent)
                    is TileOutcome.Logged -> refresh()
                }
            }
        }
    }

    /** Opens the app over the panel and closes it. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected open fun open(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** The tile names what a tap logs, or that it opens the sheet. */
    private suspend fun refresh() {
        val tile = qsTile ?: return
        val graph = app.graph
        val first = graph.products.quickLog.first().let { list -> list.firstOrNull()?.let { it to PillLabel.of(list).getValue(it.id) } }
        val opensSheet = graph.reminders.currentQuickLog().tileOpensSheet
        val setUp = graph.settings.current().onboardingComplete
        tile.label = getString(R.string.shortcut_log)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_log)
        tile.state = if (setUp) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val subtitle = when {
            !setUp -> null
            opensSheet || first == null -> getString(R.string.tile_opens_sheet)
            else -> resources.pillLabelText(first.second)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.contentDescription = if (setUp && !opensSheet && first != null) {
            val product = first.first
            getString(R.string.today_quick_log_description, getString(R.string.dose_named, product.shortName, product.perUnit.mgText()))
        } else {
            getString(R.string.shortcut_log)
        }
        tile.updateTile()
    }
}
