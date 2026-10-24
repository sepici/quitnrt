package app.thelasthit.nrt.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.settings.QuickLogOptions
import app.thelasthit.nrt.data.settings.ReminderMarks
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.ReminderSettings
import app.thelasthit.nrt.reminders.NoticePoster
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.graphViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The widget and tile options, with the label of the product the tile logs. */
data class WidgetTileState(val options: QuickLogOptions, val first: UserProduct?) {
    companion object {
        fun of(options: QuickLogOptions, quickLog: List<UserProduct>) = WidgetTileState(options, quickLog.firstOrNull())
    }
}

class WidgetTileViewModel(private val graph: DataGraph) : ViewModel() {

    val state: StateFlow<WidgetTileState?> = combine(graph.reminders.quickLog, graph.products.quickLog, WidgetTileState::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTileOpensSheet(on: Boolean) {
        viewModelScope.launch { graph.reminders.setTileOpensSheet(on) }
    }

    fun setWidgetAsksForTags(on: Boolean) {
        viewModelScope.launch { graph.reminders.setWidgetAsksForTags(on) }
    }
}

/**
 * Settings, Widget and tile: whether the Quick Settings tile logs the first product on the quick
 * log or opens the log sheet, and whether the widget opens the tag picker after it logs.
 */
@Composable
fun WidgetTileScreen(onBack: () -> Unit, viewModel: WidgetTileViewModel = graphViewModel { WidgetTileViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsPage(stringResource(R.string.widget_tile_title), stringResource(R.string.settings_back), onBack) {
        val current = state ?: return@SettingsPage
        Intro(stringResource(R.string.widget_tile_intro))

        SectionHeading(stringResource(R.string.widget_tile_tile))
        RowList {
            Column(Modifier.selectableGroup()) {
                ChoiceRow(
                    stringResource(R.string.widget_tile_tile_log),
                    current.first?.let { stringResource(R.string.widget_tile_tile_log_detail, doseName(it)) }
                        ?: stringResource(R.string.widget_tile_tile_log_first),
                    selected = !current.options.tileOpensSheet,
                    first = true,
                ) { viewModel.setTileOpensSheet(false) }
                ChoiceRow(
                    stringResource(R.string.widget_tile_tile_sheet),
                    stringResource(R.string.widget_tile_tile_sheet_detail),
                    selected = current.options.tileOpensSheet,
                    first = false,
                ) { viewModel.setTileOpensSheet(true) }
            }
        }

        SectionHeading(stringResource(R.string.widget_tile_widget))
        RowList {
            ToggleRow(
                stringResource(R.string.widget_tile_tags),
                stringResource(R.string.widget_tile_tags_detail),
                current.options.widgetAsksForTags,
                first = true,
                onChange = viewModel::setWidgetAsksForTags,
            )
        }
        FootNote(stringResource(R.string.widget_tile_foot))
    }
}

/** What the two Settings rows for phase 6 say: reminders on, and what the tile does. */
data class PhoneRowsState(val reminders: ReminderSettings, val marks: ReminderMarks, val tile: WidgetTileState)

class PhoneRowsViewModel(graph: DataGraph) : ViewModel() {
    val state: StateFlow<PhoneRowsState?> = combine(
        graph.reminders.reminders,
        graph.reminders.marks,
        graph.reminders.quickLog,
        graph.products.quickLog,
    ) { reminders, marks, options, quickLog ->
        PhoneRowsState(reminders, marks, WidgetTileState.of(options, quickLog))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The Reminders row's detail: how many are on, and quiet hours, or that notifications are off.
 * On Android 13 and newer, until the app has asked for notifications, it says to allow them
 * rather than that they're off, since nobody turned them off.
 */
@Composable
fun remindersSummary(settings: ReminderSettings, marks: ReminderMarks): String {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(NoticePoster(context).canPost()) }
    // Back from the phone's settings, the row follows what was chosen there.
    LifecycleResumeEffect(Unit) {
        canPost = NoticePoster(context).canPost()
        onPauseOrDispose {}
    }
    val timeFormat = rememberTimeFormatter()
    val on = settings.on.size
    val all = Reminder.entries.size
    val quiet = settings.quietHours
    return when {
        on > 0 && !canPost && !marks.permissionAsked && !NoticePoster.hasPermission(context) -> stringResource(R.string.settings_reminders_ask)
        on > 0 && !canPost -> stringResource(R.string.settings_reminders_blocked)
        on == 0 -> stringResource(R.string.settings_reminders_off)
        quiet.enabled -> stringResource(R.string.settings_reminders_quiet, on, all, timeFormat.format(quiet.start), timeFormat.format(quiet.end))
        else -> stringResource(R.string.settings_reminders_some, on, all)
    }
}

/** The Widget and tile row's detail: what a tap on the tile does. */
@Composable
fun widgetTileSummary(state: WidgetTileState): String = when {
    state.options.tileOpensSheet -> stringResource(R.string.settings_widget_tile_sheet)
    state.first != null -> stringResource(R.string.settings_widget_tile_logs, doseName(state.first))
    else -> stringResource(R.string.settings_widget_tile_logs_first)
}

/** "Nicorette gum 2 mg": the product the tile logs, as the shortcuts name it. */
@Composable
private fun doseName(product: UserProduct): String = stringResource(R.string.dose_named, product.shortName, product.perUnit.mgText())
