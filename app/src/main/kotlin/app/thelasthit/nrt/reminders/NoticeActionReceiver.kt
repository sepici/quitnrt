package app.thelasthit.nrt.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.thelasthit.nrt.LastHitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The buttons on a notice that don't open the app: "All good" on the check-in, which clears it,
 * and Undo on the tile's note, which takes the dose back as Today's Undo does.
 */
class NoticeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(NoticePoster.EXTRA_NOTICE_ID, 0)
        when (intent.action) {
            NoticeAction.ALL_GOOD.name -> NotificationManagerCompat.from(context).cancel(id)
            NoticeAction.UNDO.name -> {
                NotificationManagerCompat.from(context).cancel(id)
                val entryId = intent.getStringExtra(NoticePoster.EXTRA_ENTRY_ID) ?: return
                val app = context.applicationContext as LastHitApp
                // Null when called outside a broadcast, as a test does.
                val pending: PendingResult? = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.quickLog.undo(entryId)
                    } finally {
                        pending?.finish()
                    }
                }
            }
        }
    }
}
