package app.thelasthit.nrt.reminders

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.advice.DelayRun
import app.thelasthit.nrt.ui.advice.DelayEnding
import java.time.Instant

/**
 * The delay timer's end as a notice, on a channel of its own that buzzes without a sound
 * ([NoticeChannel.DELAY]). It's booked with the alarm manager when the timer starts, so it comes
 * even after Android has closed the app, and taken back when the timer is stopped or its end shows
 * in the app.
 *
 * You start the timer yourself, minutes before, so quiet hours don't hold it back, as they don't
 * the tile's note. An exact time needs a permission the app doesn't ask for on Android 12 and
 * newer, so there the booking can come a little late. While the app runs, the timer's own wake
 * lock ends it on time and sends the notice then (see DelayViewModel).
 */
class DelayNotices(
    private val context: Context,
    private val poster: NoticePoster = NoticePoster(context),
    /** Whether one of the app's screens is showing, so its own page tells of the end. */
    private val onScreen: () -> Boolean = { isAppOnScreen() },
) : DelayEnding {

    private val alarms: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override fun book(run: DelayRun) {
        poster.cancel(Notice.ID_DELAY)
        val alarms = alarms ?: return
        set(alarms, run.endsAt.toEpochMilli(), booking(run))
    }

    /**
     * Below Android 12 an exact alarm needs no permission, so the end comes on time. From 12 on,
     * the app doesn't ask for the exact alarm permission, and the booking can come a little late.
     */
    @SuppressLint("MissingPermission")
    private fun set(alarms: AlarmManager, at: Long, booking: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, booking)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, booking)
        }
    }

    override fun clear() {
        unbook()
        poster.cancel(Notice.ID_DELAY)
    }

    override fun ended(run: DelayRun, now: Instant): Boolean {
        unbook()
        val first = DelayEnds.claim(run)
        if (onScreen()) {
            // The page says it's done, so a notice would only repeat it.
            poster.cancel(Notice.ID_DELAY)
            return first && run.buzzesAt(now)
        }
        if (first && run.buzzesAt(now)) {
            createChannels()
            poster.post(notice(run))
        }
        return false
    }

    /**
     * The booking came for [run]. With the app on screen, its page tells of the end. Otherwise the
     * notice goes up, unless the app has told of this end already.
     */
    fun bookingCame(run: DelayRun) {
        if (onScreen() || !DelayEnds.claim(run)) return
        // After a restart the app may not have made its channels yet.
        createChannels()
        poster.post(notice(run))
    }

    /** "5 minutes done", with what the end frame says. Tapping it opens the app where it was left. */
    fun notice(run: DelayRun): Notice {
        val resources = context.resources
        val length = resources.getQuantityString(R.plurals.timer_minutes, run.minutes, run.minutes)
        return Notice(
            reminder = null,
            channel = NoticeChannel.DELAY,
            id = Notice.ID_DELAY,
            title = resources.getQuantityString(R.plurals.delay_done_title, run.minutes, run.minutes),
            text = resources.getString(R.string.delay_done_say, length),
        )
    }

    private fun createChannels() = poster.createChannels()

    private fun unbook() {
        val existing = PendingIntent.getBroadcast(context, REQUEST, Intent(context, DelayEndReceiver::class.java), FLAGS or PendingIntent.FLAG_NO_CREATE)
        if (existing != null) {
            alarms?.cancel(existing)
            existing.cancel()
        }
    }

    private fun booking(run: DelayRun): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, DelayEndReceiver::class.java)
            .putExtra(EXTRA_STARTED_AT, run.startedAt.toEpochMilli())
            .putExtra(EXTRA_MINUTES, run.minutes),
        FLAGS or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val EXTRA_STARTED_AT = "app.thelasthit.nrt.extra.DELAY_STARTED_AT"
        const val EXTRA_MINUTES = "app.thelasthit.nrt.extra.DELAY_MINUTES"
        private const val REQUEST = 7100
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE

        /** Whether one of the app's screens is showing, by how Android ranks the app's process. */
        fun isAppOnScreen(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }
    }
}

/**
 * The ends already told of while the app has been running, so the app's own timer and its
 * booking never both tell of the same one. A run is known by when it started.
 */
internal object DelayEnds {
    private val told = mutableSetOf<Long>()

    /** True the first time [run]'s end is claimed. */
    fun claim(run: DelayRun): Boolean = synchronized(told) { told.add(run.startedAt.toEpochMilli()) }
}

/** The booked end of a delay timer, from the alarm manager. */
class DelayEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val startedAt = intent.getLongExtra(DelayNotices.EXTRA_STARTED_AT, -1)
        val minutes = intent.getIntExtra(DelayNotices.EXTRA_MINUTES, 0)
        if (startedAt < 0 || minutes !in DelayRun.MINUTES) return
        DelayNotices(context).bookingCame(DelayRun(Instant.ofEpochMilli(startedAt), minutes))
    }
}
