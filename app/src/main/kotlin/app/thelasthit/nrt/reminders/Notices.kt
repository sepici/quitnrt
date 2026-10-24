package app.thelasthit.nrt.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import app.thelasthit.nrt.MainActivity
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.link.LogLinks
import java.time.Duration

/**
 * Where each kind of notice goes. Each reminder has a channel of its own, so the phone's settings
 * can quieten one without the rest.
 */
enum class NoticeChannel(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val detail: Int,
    val importance: Int,
) {
    ALLOWANCE("allowance", R.string.channel_allowance, R.string.channel_allowance_detail, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    CHECK_IN("check_in", R.string.channel_check_in, R.string.channel_check_in_detail, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    RESULTS("weekly_results", R.string.channel_results, R.string.channel_results_detail, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    ACHIEVEMENTS("achievements", R.string.channel_achievements, R.string.channel_achievements_detail, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    FORGOT("forgot_to_log", R.string.channel_forgot, R.string.channel_forgot_detail, NotificationManagerCompat.IMPORTANCE_DEFAULT),

    /** The tile's note with Undo. Low, so it sits in the shade without a sound. */
    LOGGED("logged", R.string.channel_logged, R.string.channel_logged_detail, NotificationManagerCompat.IMPORTANCE_LOW),
    ;

    companion object {
        fun of(reminder: Reminder): NoticeChannel = when (reminder) {
            Reminder.ALLOWANCE -> ALLOWANCE
            Reminder.CHECK_IN -> CHECK_IN
            Reminder.WEEKLY_RESULT -> RESULTS
            Reminder.ACHIEVEMENTS -> ACHIEVEMENTS
            Reminder.FORGOT_TO_LOG -> FORGOT
        }
    }
}

/** Where tapping a notice opens the app, for the notices that have a page of their own. */
enum class NoticeTarget {
    /** The Plan tab: a week's result, and after a missed week, the choice to carry on or repeat it. */
    PLAN,

    /** The lair, at its trophy shelf. */
    SHELF,
    ;

    companion object {
        const val EXTRA = "app.thelasthit.nrt.extra.OPEN"

        fun of(reminder: Reminder?): NoticeTarget? = when (reminder) {
            Reminder.WEEKLY_RESULT -> PLAN
            Reminder.ACHIEVEMENTS -> SHELF
            else -> null
        }

        /**
         * Where [intent] asks the app to open, if anywhere. Opening the app from Recents repeats
         * the intent it came with, whose page was opened then, so that one is skipped.
         */
        fun from(intent: Intent?): NoticeTarget? {
            if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            val name = intent.getStringExtra(EXTRA) ?: return null
            return NoticeTarget.entries.firstOrNull { it.name == name }
        }
    }
}

/** A button on a notice. */
enum class NoticeAction {
    /** Opens the log sheet, recorded as from a notification. */
    LOG,

    /** Clears the check-in. */
    ALL_GOOD,

    /** Takes back the dose in [Notice.entryId]. */
    UNDO,
}

/**
 * One notification, in the app's words. It's plain data, so one held through quiet hours can wait
 * in WorkManager. [reminder] is null for the tile's note, which isn't a reminder.
 */
data class Notice(
    val reminder: Reminder?,
    val channel: NoticeChannel,
    val id: Int,
    val title: String,
    val text: String?,
    val actions: List<NoticeAction> = emptyList(),
    val entryId: String? = null,
    /** Gone after this long, as the tile's Undo is. */
    val timeout: Duration? = null,
) {
    fun toData(): Data = Data.Builder()
        .putString(KEY_REMINDER, reminder?.name)
        .putString(KEY_CHANNEL, channel.name)
        .putInt(KEY_ID, id)
        .putString(KEY_TITLE, title)
        .putString(KEY_TEXT, text)
        .putStringArray(KEY_ACTIONS, actions.map { it.name }.toTypedArray())
        .putString(KEY_ENTRY, entryId)
        .putLong(KEY_TIMEOUT, timeout?.toMillis() ?: -1)
        .build()

    companion object {
        /** Notification IDs: one each, so a new notice replaces the last of its kind. */
        const val ID_ALLOWANCE = 1
        const val ID_CHECK_IN = 2
        const val ID_FORGOT = 3
        const val ID_LOGGED = 5

        /** Weekly results: one per week, from here up. */
        const val ID_RESULTS = 100

        /**
         * Achievements: one per unlock, from here up, so a badge that unlocks while another's
         * notice is showing, or held through quiet hours, doesn't take its place.
         */
        const val ID_ACHIEVEMENTS = 1000
        private const val ACHIEVEMENT_IDS = 9000

        /** The ID for the notice of these unlocks, which only unlock once each. */
        fun achievementsId(badges: List<String>, gear: List<String>): Int =
            ID_ACHIEVEMENTS + Math.floorMod((badges + "" + gear).hashCode(), ACHIEVEMENT_IDS)

        private const val KEY_REMINDER = "reminder"
        private const val KEY_CHANNEL = "channel"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_TEXT = "text"
        private const val KEY_ACTIONS = "actions"
        private const val KEY_ENTRY = "entry"
        private const val KEY_TIMEOUT = "timeout"

        fun fromData(data: Data): Notice? {
            val channel = data.getString(KEY_CHANNEL)?.let { name -> NoticeChannel.entries.firstOrNull { it.name == name } } ?: return null
            return Notice(
                reminder = data.getString(KEY_REMINDER)?.let { name -> Reminder.entries.firstOrNull { it.name == name } },
                channel = channel,
                id = data.getInt(KEY_ID, 0),
                title = data.getString(KEY_TITLE) ?: return null,
                text = data.getString(KEY_TEXT),
                actions = data.getStringArray(KEY_ACTIONS).orEmpty().mapNotNull { name -> NoticeAction.entries.firstOrNull { it.name == name } },
                entryId = data.getString(KEY_ENTRY),
                timeout = data.getLong(KEY_TIMEOUT, -1).takeIf { it >= 0 }?.let(Duration::ofMillis),
            )
        }
    }
}

/** Posts notices as notifications, once the phone allows them. */
class NoticePoster(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** The app's channels. Creating them again changes nothing the user has set. */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            NoticeChannel.entries.map { channel ->
                NotificationChannelCompat.Builder(channel.id, channel.importance)
                    .setName(context.getString(channel.title))
                    .setDescription(context.getString(channel.detail))
                    .build()
            },
        )
    }

    /** Whether notifications can show: allowed for the app, and on Android 13 and newer, permitted. */
    fun canPost(): Boolean = manager.areNotificationsEnabled() && hasPermission(context)

    /** Shows [notice], if notifications can show. True when it went up. */
    fun post(notice: Notice): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        manager.notify(notice.id, build(notice))
        return true
    }

    fun cancel(id: Int) = manager.cancel(id)

    private fun build(notice: Notice) = NotificationCompat.Builder(context, notice.channel.id)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(context, if (notice.channel == NoticeChannel.ACHIEVEMENTS) R.color.notice_game else R.color.notice_accent))
        .setContentTitle(notice.title)
        .apply { if (notice.text != null) setContentText(notice.text).setStyle(NotificationCompat.BigTextStyle().bigText(notice.text)) }
        .setContentIntent(openApp(notice))
        .setAutoCancel(true)
        // A reminder alerts each time, even over yesterday's still in the shade. The tile's note
        // is quiet on a low channel already.
        .setOnlyAlertOnce(notice.reminder == null)
        .setCategory(if (notice.reminder == Reminder.CHECK_IN || notice.reminder == Reminder.FORGOT_TO_LOG) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
        // Doses are health details, so the lock screen shows only what kind of notice it is.
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            NotificationCompat.Builder(context, notice.channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(notice.channel.title))
                .build(),
        )
        .apply { notice.timeout?.let { setTimeoutAfter(it.toMillis()) } }
        .apply { notice.actions.forEach { addAction(0, actionLabel(it), actionIntent(notice, it)) } }
        .build()

    private fun actionLabel(action: NoticeAction): String = context.getString(
        when (action) {
            NoticeAction.LOG -> R.string.today_log
            NoticeAction.ALL_GOOD -> R.string.check_in_all_good
            NoticeAction.UNDO -> R.string.action_undo
        },
    )

    private fun actionIntent(notice: Notice, action: NoticeAction): PendingIntent = when (action) {
        // An activity straight from the button: Android doesn't let a receiver start one.
        NoticeAction.LOG -> PendingIntent.getActivity(
            context,
            notice.id * 10 + action.ordinal,
            LogLinks.appIntent(context, LogLinks.blank, LogLinks.SOURCE_NOTIFICATION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_NOTICE_ID, notice.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NoticeAction.ALL_GOOD, NoticeAction.UNDO -> PendingIntent.getBroadcast(
            context,
            notice.id * 10 + action.ordinal,
            Intent(context, NoticeActionReceiver::class.java)
                .setAction(action.name)
                .putExtra(EXTRA_NOTICE_ID, notice.id)
                .putExtra(EXTRA_ENTRY_ID, notice.entryId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Opens the app where it was left, or for a weekly result or a badge, at the page it's about.
     * The screen is singleTop, so an app already open gets the page through onNewIntent.
     */
    private fun openApp(notice: Notice): PendingIntent = PendingIntent.getActivity(
        context,
        notice.id * 10 + OPEN_REQUEST,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { NoticeTarget.of(notice.reminder)?.let { putExtra(NoticeTarget.EXTRA, it.name) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val EXTRA_NOTICE_ID = "app.thelasthit.nrt.extra.NOTICE_ID"
        const val EXTRA_ENTRY_ID = "app.thelasthit.nrt.extra.ENTRY_ID"
        private const val OPEN_REQUEST = 9

        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        /** A Log button opened the app: its notice has done its job. */
        fun dismissFrom(context: Context, intent: Intent?) {
            val id = intent?.getIntExtra(EXTRA_NOTICE_ID, 0) ?: 0
            if (id != 0) NotificationManagerCompat.from(context).cancel(id)
        }
    }
}
