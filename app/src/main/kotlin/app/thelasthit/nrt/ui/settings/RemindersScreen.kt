package app.thelasthit.nrt.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.settings.ReminderMarks
import app.thelasthit.nrt.domain.reminders.ForgotToLog
import app.thelasthit.nrt.domain.reminders.QuietHours
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.ReminderSettings
import app.thelasthit.nrt.reminders.NoticePoster
import app.thelasthit.nrt.ui.components.NoteCard
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

data class RemindersState(
    val settings: ReminderSettings,
    val marks: ReminderMarks,
    /** About when "Forgot to log" would come, from the last 14 days, or null before there's a usual time. */
    val forgotAt: LocalTime?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val store = graph.reminders

    private val forgotAt: Flow<LocalTime?> = graph.settings.settings.flatMapLatest { settings ->
        val boundary = settings.dayBoundary
        val today = boundary.logicalDate(graph.clock.instant(), zone())
        graph.entries.observeRange(today.minusDays(ForgotToLog.LOOK_BACK_DAYS.toLong()), today).map { entries ->
            ForgotToLog.usualFirstLog(entries, today, boundary.dayStart)?.plus(ForgotToLog.AFTER_USUAL)
        }
    }

    val state: StateFlow<RemindersState?> = combine(store.reminders, store.marks, forgotAt, ::RemindersState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun set(reminder: Reminder, on: Boolean) {
        viewModelScope.launch { store.setOn(reminder, on) }
    }

    fun setCheckInTime(time: LocalTime) {
        viewModelScope.launch { store.setCheckInTime(time) }
    }

    fun setQuietHours(quiet: QuietHours) {
        viewModelScope.launch { store.setQuietHours(quiet) }
    }

    fun markPermissionAsked() {
        viewModelScope.launch { store.markPermissionAsked() }
    }
}

/** Which time the picker is setting. */
private enum class TimeField(val title: Int) {
    CHECK_IN(R.string.reminders_pick_check_in),
    QUIET_START(R.string.reminders_pick_quiet_start),
    QUIET_END(R.string.reminders_pick_quiet_end),
}

/**
 * Settings, Reminders (PLAN §13), in the Settings mockups' style: a switch for each reminder, the
 * check-in time and quiet hours. On Android 13 and newer, switching one on is when the app asks
 * for notifications; if they're off, a note says so with a way to allow them.
 */
@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = graphViewModel { RemindersViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val timeFormat = rememberTimeFormatter()
    var canPost by remember { mutableStateOf(NoticePoster(context).canPost()) }
    var picking by rememberSaveable { mutableStateOf<TimeField?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = NoticePoster(context).canPost() }

    // Back from the phone's settings, or after the question, the note follows what was chosen.
    LifecycleResumeEffect(Unit) {
        canPost = NoticePoster(context).canPost()
        onPauseOrDispose {}
    }

    SettingsPage(stringResource(R.string.reminders_title), stringResource(R.string.settings_back), onBack) {
        val current = state ?: return@SettingsPage
        val settings = current.settings

        fun allow() {
            val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NoticePoster.hasPermission(context) && (
                !current.marks.permissionAsked ||
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                )
            if (canAsk) {
                viewModel.markPermissionAsked()
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        }

        fun switch(reminder: Reminder, on: Boolean) {
            viewModel.set(reminder, on)
            if (on && !canPost) allow()
        }

        Intro(stringResource(R.string.reminders_intro))
        if (!canPost) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                NoteCard(stringResource(R.string.reminders_blocked_title), stringResource(R.string.reminders_blocked_body))
                // The link's own padding, taken back, so its text lines up with the note's edge.
                Box(Modifier.offset(x = (-8).dp)) {
                    TextLink(stringResource(R.string.reminders_allow), onClick = ::allow)
                }
            }
        }

        SectionHeading(stringResource(R.string.reminders_after_log))
        RowList {
            ToggleRow(
                stringResource(R.string.reminders_allowance),
                stringResource(R.string.reminders_allowance_detail),
                settings.isOn(Reminder.ALLOWANCE),
                first = true,
            ) { switch(Reminder.ALLOWANCE, it) }
        }

        SectionHeading(stringResource(R.string.reminders_each_day))
        RowList {
            val checkInOn = settings.isOn(Reminder.CHECK_IN)
            ToggleRow(stringResource(R.string.reminders_check_in), stringResource(R.string.reminders_check_in_detail), checkInOn, first = true) {
                switch(Reminder.CHECK_IN, it)
            }
            SettingsRow(
                stringResource(R.string.reminders_check_in_time),
                detail = null,
                first = false,
                onClick = { picking = TimeField.CHECK_IN },
                muted = !checkInOn,
                value = timeFormat.format(settings.checkInTime),
                chevron = true,
            )
            ToggleRow(
                stringResource(R.string.reminders_forgot),
                current.forgotAt?.let { stringResource(R.string.reminders_forgot_detail_due, timeFormat.format(it)) }
                    ?: stringResource(R.string.reminders_forgot_detail),
                settings.isOn(Reminder.FORGOT_TO_LOG),
                first = false,
            ) { switch(Reminder.FORGOT_TO_LOG, it) }
        }
        if (settings.isOn(Reminder.CHECK_IN) && settings.checkInIsQuiet) {
            FootNote(stringResource(R.string.reminders_check_in_quiet, timeFormat.format(settings.checkInTime)))
        }

        SectionHeading(stringResource(R.string.reminders_plan_and_game))
        RowList {
            ToggleRow(
                stringResource(R.string.reminders_results),
                stringResource(R.string.reminders_results_detail),
                settings.isOn(Reminder.WEEKLY_RESULT),
                first = true,
            ) { switch(Reminder.WEEKLY_RESULT, it) }
            ToggleRow(
                stringResource(R.string.reminders_achievements),
                stringResource(R.string.reminders_achievements_detail),
                settings.isOn(Reminder.ACHIEVEMENTS),
                first = false,
            ) { switch(Reminder.ACHIEVEMENTS, it) }
        }

        SectionHeading(stringResource(R.string.reminders_quiet))
        RowList {
            val quiet = settings.quietHours
            ToggleRow(
                stringResource(R.string.reminders_quiet_switch),
                if (quiet.enabled) {
                    stringResource(R.string.reminders_quiet_detail, timeFormat.format(quiet.start), timeFormat.format(quiet.end))
                } else {
                    stringResource(R.string.reminders_quiet_off_detail)
                },
                quiet.enabled,
                first = true,
            ) { viewModel.setQuietHours(quiet.copy(enabled = it)) }
            SettingsRow(
                stringResource(R.string.reminders_quiet_start),
                detail = null,
                first = false,
                onClick = { picking = TimeField.QUIET_START },
                muted = !quiet.enabled,
                value = timeFormat.format(quiet.start),
                chevron = true,
            )
            SettingsRow(
                stringResource(R.string.reminders_quiet_end),
                detail = null,
                first = false,
                onClick = { picking = TimeField.QUIET_END },
                muted = !quiet.enabled,
                value = timeFormat.format(quiet.end),
                chevron = true,
            )
        }
        FootNote(stringResource(R.string.reminders_foot))

        picking?.let { field ->
            val initial = when (field) {
                TimeField.CHECK_IN -> settings.checkInTime
                TimeField.QUIET_START -> settings.quietHours.start
                TimeField.QUIET_END -> settings.quietHours.end
            }
            TimeDialog(stringResource(field.title), initial, onDismiss = { picking = null }) { time ->
                picking = null
                when (field) {
                    TimeField.CHECK_IN -> viewModel.setCheckInTime(time)
                    TimeField.QUIET_START -> viewModel.setQuietHours(settings.quietHours.copy(start = time))
                    TimeField.QUIET_END -> viewModel.setQuietHours(settings.quietHours.copy(end = time))
                }
            }
        }
    }
}

/** A Settings row with a switch, ruled like [SettingsRow], so a list of them reads as one. */
@Composable
internal fun ToggleRow(title: String, detail: String?, checked: Boolean, first: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LastHit.colors
    if (!first) HorizontalDivider(color = colors.line)
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
            if (detail != null) {
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.chipSelected,
                checkedThumbColor = colors.onChipSelected,
                checkedBorderColor = colors.chipSelected,
                uncheckedTrackColor = colors.bg,
                uncheckedThumbColor = colors.lineStrong,
                uncheckedBorderColor = colors.lineStrong,
            ),
        )
    }
}

/** A Settings row that's one of a choice, with the radio dot of the app's other choices. */
@Composable
internal fun ChoiceRow(title: String, detail: String?, selected: Boolean, first: Boolean, onClick: () -> Unit) {
    val colors = LastHit.colors
    if (!first) HorizontalDivider(color = colors.line)
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
            if (detail != null) {
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
        }
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, if (selected) colors.chipSelected else colors.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(colors.chipSelected))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.reminders_set_time)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The app's page in the phone's notification settings, where a denied permission can be changed. */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
