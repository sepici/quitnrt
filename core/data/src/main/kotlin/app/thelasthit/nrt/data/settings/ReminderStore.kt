package app.thelasthit.nrt.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.thelasthit.nrt.domain.reminders.QuietHours
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** How the widget and the Quick Settings tile log (PLAN §11). */
data class QuickLogOptions(
    /** The tile opens the log sheet, rather than logging the first product on the quick log. */
    val tileOpensSheet: Boolean = false,
    /** A tap on the widget logs, then opens the quick tag picker. */
    val widgetAsksForTags: Boolean = false,
)

/** What the reminders keep note of, so each goes out once. */
data class ReminderMarks(
    /** Whether the notification permission has been asked for, on Android 13 and newer. */
    val permissionAsked: Boolean = false,
    /** The newest weekly result already notified. */
    val resultsNotifiedUpTo: Instant? = null,
    /** The logical day the last evening check-in went out. */
    val checkInSentOn: LocalDate? = null,
    /** The logical day the last "Forgot to log" went out. */
    val forgotRemindedOn: LocalDate? = null,
)

/**
 * Phase 6's settings: the reminder switches, the check-in time and quiet hours, the widget and
 * tile options, and the reminders' own notes. They belong to this phone, so they sit in a file of
 * their own, [FILE], which the backup rules leave out (res/xml/backup_rules.xml and
 * data_extraction_rules.xml): a new phone asks for notifications again.
 */
class ReminderStore(private val store: DataStore<Preferences>) {

    val reminders: Flow<ReminderSettings> = store.data.map { prefs ->
        ReminderSettings(
            on = Reminder.entries.filter { prefs[it.key] ?: it.onByDefault }.toSet(),
            checkInTime = prefs[CHECK_IN_MINUTES]?.let(::timeOf) ?: ReminderSettings.DEFAULT_CHECK_IN,
            quietHours = QuietHours(
                enabled = prefs[QUIET_ON] ?: true,
                start = prefs[QUIET_START_MINUTES]?.let(::timeOf) ?: QuietHours.DEFAULT_START,
                end = prefs[QUIET_END_MINUTES]?.let(::timeOf) ?: QuietHours.DEFAULT_END,
            ),
        )
    }.distinctUntilChanged()

    val quickLog: Flow<QuickLogOptions> = store.data.map { prefs ->
        QuickLogOptions(
            tileOpensSheet = prefs[TILE_OPENS_SHEET] ?: false,
            widgetAsksForTags = prefs[WIDGET_ASKS_FOR_TAGS] ?: false,
        )
    }.distinctUntilChanged()

    val marks: Flow<ReminderMarks> = store.data.map { prefs ->
        ReminderMarks(
            permissionAsked = prefs[PERMISSION_ASKED] ?: false,
            resultsNotifiedUpTo = prefs[RESULTS_NOTIFIED_UP_TO]?.let(Instant::ofEpochMilli),
            checkInSentOn = prefs[CHECK_IN_SENT_ON]?.let(LocalDate::ofEpochDay),
            forgotRemindedOn = prefs[FORGOT_REMINDED_ON]?.let(LocalDate::ofEpochDay),
        )
    }.distinctUntilChanged()

    suspend fun currentReminders(): ReminderSettings = reminders.first()

    suspend fun currentQuickLog(): QuickLogOptions = quickLog.first()

    suspend fun currentMarks(): ReminderMarks = marks.first()

    suspend fun setOn(reminder: Reminder, on: Boolean) = store.edit { it[reminder.key] = on }

    suspend fun setCheckInTime(time: LocalTime) = store.edit { it[CHECK_IN_MINUTES] = minutesOf(time) }

    suspend fun setQuietHours(quiet: QuietHours) = store.edit { prefs ->
        prefs[QUIET_ON] = quiet.enabled
        prefs[QUIET_START_MINUTES] = minutesOf(quiet.start)
        prefs[QUIET_END_MINUTES] = minutesOf(quiet.end)
    }

    suspend fun setTileOpensSheet(on: Boolean) = store.edit { it[TILE_OPENS_SHEET] = on }

    suspend fun setWidgetAsksForTags(on: Boolean) = store.edit { it[WIDGET_ASKS_FOR_TAGS] = on }

    suspend fun markPermissionAsked() = store.edit { it[PERMISSION_ASKED] = true }

    /** Keeps the newest of [upTo] and what's kept already, so notes never go backwards. */
    suspend fun markResultsNotified(upTo: Instant) = store.edit { prefs ->
        val kept = prefs[RESULTS_NOTIFIED_UP_TO]
        if (kept == null || upTo.toEpochMilli() > kept) prefs[RESULTS_NOTIFIED_UP_TO] = upTo.toEpochMilli()
    }

    suspend fun markCheckInSent(on: LocalDate) = store.edit { it[CHECK_IN_SENT_ON] = on.toEpochDay() }

    suspend fun markForgotReminded(on: LocalDate) = store.edit { it[FORGOT_REMINDED_ON] = on.toEpochDay() }

    companion object {
        private val Reminder.key: Preferences.Key<Boolean> get() = booleanPreferencesKey("reminder_${name.lowercase()}")
        private val CHECK_IN_MINUTES = intPreferencesKey("check_in_minutes")
        private val QUIET_ON = booleanPreferencesKey("quiet_on")
        private val QUIET_START_MINUTES = intPreferencesKey("quiet_start_minutes")
        private val QUIET_END_MINUTES = intPreferencesKey("quiet_end_minutes")
        private val TILE_OPENS_SHEET = booleanPreferencesKey("tile_opens_sheet")
        private val WIDGET_ASKS_FOR_TAGS = booleanPreferencesKey("widget_asks_for_tags")
        private val PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        private val RESULTS_NOTIFIED_UP_TO = longPreferencesKey("results_notified_up_to")
        private val CHECK_IN_SENT_ON = longPreferencesKey("check_in_sent_on")
        private val FORGOT_REMINDED_ON = longPreferencesKey("forgot_reminded_on")

        private fun minutesOf(time: LocalTime): Int = time.hour * 60 + time.minute

        private fun timeOf(minutes: Int): LocalTime = LocalTime.ofSecondOfDay(minutes.mod(24 * 60) * 60L)

        /** The file's path under the app's files, as the backup rules name it. */
        const val FILE = "datastore/reminders.preferences_pb"

        private const val NAME = "reminders"

        /** Where the store keeps its file: [FILE] under the app's files. */
        fun fileIn(context: Context): File = context.preferencesDataStoreFile(NAME)

        /** Only one DataStore may exist per file, so the app creates this once. */
        fun createDataStore(context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { fileIn(context) })
    }
}
