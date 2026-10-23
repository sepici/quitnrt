package app.thelasthit.nrt.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.log.LoggedDay
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.EntryRow
import app.thelasthit.nrt.ui.components.IconTile
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.format.DayMonthFormatter
import app.thelasthit.nrt.ui.format.DayMonthYearFormatter
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.log.LogSheet
import app.thelasthit.nrt.ui.log.LogSheetViewModel
import app.thelasthit.nrt.ui.log.SavedDose
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every dose, grouped by logical day with each day's total. Tap one to edit it in the log sheet,
 * or swipe it away to delete it, with Undo.
 */
@Composable
fun HistoryScreen(
    snackbars: SnackbarHostState,
    viewModel: HistoryViewModel = graphViewModel { HistoryViewModel(it) },
    sheetViewModel: LogSheetViewModel = graphViewModel(key = "history-sheet") { LogSheetViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    val timeFormat = rememberTimeFormatter()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect { deleted ->
            launch {
                val result = snackbars.showSnackbar(
                    message = resources.getString(
                        R.string.history_deleted,
                        deleted.entry.total.mgText(),
                        deleted.name,
                        timeFormat.formatAt(deleted.entry.occurredAt, deleted.entry.zone),
                    ),
                    actionLabel = resources.getString(R.string.action_undo),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(deleted.entry)
            }
        }
    }

    fun onSaved(saved: SavedDose) {
        sheetOpen = false
        scope.launch {
            if (saved.wasEdit) {
                snackbars.showSnackbar(resources.getString(R.string.history_saved))
            } else {
                val name = state?.nameOf(saved.entry) ?: saved.entry.productName
                val result = snackbars.showSnackbar(
                    message = resources.getString(R.string.today_logged, saved.entry.total.mgText(), name),
                    actionLabel = resources.getString(R.string.action_undo),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoLog(saved.entry)
            }
        }
    }

    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    if (current.days.isEmpty() && !current.hasEarlier) {
        EmptyHistory(onLog = {
            sheetViewModel.openNew()
            sheetOpen = true
        })
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { TopBar() }
            item {
                Text(
                    stringResource(R.string.history_day_start, timeFormat.format(current.dayStart)),
                    style = LastHit.type.label,
                    color = LastHit.colors.text2,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            current.days.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(day, today = current.today) }
                items(day.entries, key = { it.id }) { entry ->
                    Column {
                        if (entry != day.entries.first()) {
                            HorizontalDivider(Modifier.padding(start = 78.dp, end = 20.dp), color = LastHit.colors.line)
                        }
                        SwipeToDelete(onDelete = { viewModel.delete(entry) }) { deleteAction ->
                            EntryRow(
                                entry = entry,
                                name = current.nameOf(entry),
                                tags = current.tags,
                                modifier = deleteAction,
                                onClick = {
                                    sheetViewModel.openEdit(entry)
                                    sheetOpen = true
                                },
                            )
                        }
                    }
                }
            }
            if (current.hasEarlier) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        Chip(
                            text = stringResource(R.string.history_show_earlier),
                            selected = false,
                            kind = ChipKind.Action,
                            onClick = viewModel::showEarlier,
                        )
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        LogSheet(viewModel = sheetViewModel, onDismiss = { sheetOpen = false }, onSaved = ::onSaved)
    }
}

@Composable
private fun TopBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.history_title),
            style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
            color = LastHit.colors.text,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun DayHeader(day: LoggedDay, today: LocalDate) {
    val colors = LastHit.colors
    val isToday = day.date == today
    val title = if (isToday) {
        stringResource(R.string.history_today)
    } else {
        day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)
    }
    val date = when {
        isToday -> DayTitleFormatter.format(day.date)
        day.date.year == today.year -> DayMonthFormatter.format(day.date)
        else -> DayMonthYearFormatter.format(day.date)
    }
    val count = day.entries.size
    val total = day.total.mgText()
    val description = if (isToday) {
        stringResource(R.string.history_today_description, total)
    } else {
        stringResource(R.string.history_day_description, "$title $date", total)
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = description
                },
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
                    color = colors.text,
                )
                Text(
                    pluralStringResource(R.plurals.history_day_meta, count, date, count),
                    style = LastHit.type.label,
                    color = colors.text2,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (isToday) {
                    Text(
                        stringResource(R.string.history_so_far),
                        style = LastHit.type.caption,
                        color = colors.text2,
                        modifier = Modifier.padding(end = 6.dp, bottom = 2.dp),
                    )
                }
                Text(
                    stringResource(R.string.dose_mg, total),
                    style = LastHit.type.title.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
                    color = colors.text,
                )
            }
        }
        HorizontalDivider(color = colors.line)
    }
}

/**
 * Swiping a row to the left deletes it. Screen readers get Delete as an action instead, through
 * the modifier [content] puts on the row, so it sits on the same node as the row's tap.
 */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable (deleteAction: Modifier) -> Unit) {
    val colors = LastHit.colors
    // Not rememberSwipeToDismissBoxState: that's saved with the row, so a row brought back by Undo
    // would come back still swiped away.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val deleteLabel = stringResource(R.string.history_delete)
    val deleteAction = Modifier.semantics {
        customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(colors.error)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_trash), contentDescription = null, tint = colors.onError)
                Text(deleteLabel, style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold), color = colors.onError)
            }
        },
    ) {
        Box(Modifier.background(colors.bg)) { content(deleteAction) }
    }
}

@Composable
private fun EmptyHistory(onLog: () -> Unit) {
    val colors = LastHit.colors
    Column(Modifier.fillMaxSize()) {
        TopBar()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(R.drawable.ic_nav_history, size = 64.dp)
            Text(
                stringResource(R.string.history_empty_title),
                style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.history_empty_body),
                style = LastHit.type.body,
                color = colors.text2,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(onClick = onLog, modifier = Modifier.padding(top = 12.dp)) { content ->
                Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(R.string.log_title),
                    style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = content,
                )
            }
        }
    }
}
