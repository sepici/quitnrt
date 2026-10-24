package app.thelasthit.nrt.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import app.thelasthit.nrt.LastHitApp

/** What the widget's taps carry. They become the tag picker's intent extras too. */
object WidgetKeys {
    val PRODUCT = ActionParameters.Key<String>("app.thelasthit.nrt.widget.PRODUCT")
    val ENTRY = ActionParameters.Key<String>("app.thelasthit.nrt.widget.ENTRY")
}

private val Context.quickLog get() = (applicationContext as LastHitApp).quickLog

/** A favourite tapped: logs one now, from the widget. */
class LogFavourite : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val product = parameters[WidgetKeys.PRODUCT] ?: return
        context.quickLog.logFromWidget(product)
    }
}

/** Undo on "Logged". */
class UndoWidgetLog : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = parameters[WidgetKeys.ENTRY] ?: return
        context.quickLog.undo(entry)
    }
}

/** "Keep both", when a tap looked like a dose logged a moment ago. */
class KeepBothWidgetLogs : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.quickLog.keepBoth()
    }
}

/** "Discard", the other answer. */
class DiscardWidgetLog : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.quickLog.discard()
    }
}
