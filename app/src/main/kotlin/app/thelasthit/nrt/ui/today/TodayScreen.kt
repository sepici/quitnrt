package app.thelasthit.nrt.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.log.Elapsed
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.ui.components.EntryRow
import app.thelasthit.nrt.ui.components.IconTile
import app.thelasthit.nrt.ui.components.pressable
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.elapsedSpoken
import app.thelasthit.nrt.ui.format.elapsedText
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.labelNoteText
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.pillLabelText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.game.DemonBand
import app.thelasthit.nrt.ui.game.DemonHpBand
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.log.LogSheet
import app.thelasthit.nrt.ui.log.LogSheetViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch
import java.time.Duration

@Composable
fun TodayScreen(
    snackbars: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onOpenLair: () -> Unit,
    onEditQuickLog: () -> Unit = onOpenSettings,
    viewModel: TodayViewModel = graphViewModel { TodayViewModel(it) },
    sheetViewModel: LogSheetViewModel = graphViewModel(key = "log-sheet") { LogSheetViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.logged.collect { logged ->
            launch {
                val result = snackbars.showSnackbar(
                    message = resources.getString(R.string.today_logged, logged.entry.total.mgText(), logged.name),
                    actionLabel = resources.getString(R.string.action_undo),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undo(logged.entry)
            }
        }
    }

    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar(current, onOpenSettings) }
        item {
            Hero(
                state = current,
                onLog = {
                    sheetViewModel.openNew()
                    sheetOpen = true
                },
                onQuickLog = { viewModel.quickLog(it.product) },
                onLongPress = {
                    sheetViewModel.openNew(it.product.id)
                    sheetOpen = true
                },
                onEditQuickLog = onEditQuickLog,
            )
        }
        current.labelNote?.let { note -> item { LabelNote(note, onDismiss = viewModel::dismissLabelNote) } }
        current.day.lastDose?.let { last ->
            item {
                SinceRow(
                    elapsed = Elapsed.of(Duration.between(last.occurredAt, current.now)),
                    // "4 mg of Nicorette gum", so two 2 mg pieces don't read like the 4 mg product.
                    detail = stringResource(
                        R.string.today_last_dose,
                        rememberTimeFormatter().formatAt(last.occurredAt, last.zone),
                        last.total.mgText(),
                        current.nameOf(last),
                    ),
                )
            }
        }
        item {
            val plan = current.plan
            if (plan != null) PlanWeekSection(plan, current.week) else WeekSection(current)
        }
        item {
            val demon = current.demon
            // The demon row is written when the plan is confirmed, but Gary lurks until its first
            // day (PLAN §7.1), as the lair says. Once it has started, the band stays out until the end.
            if (demon != null && current.planStartsOn == null) {
                DemonHpBand(demon, onOpenLair = onOpenLair, modifier = Modifier.padding(top = 28.dp))
            } else {
                DemonBand(
                    name = demon?.demon?.name ?: current.demonName,
                    creature = demon?.demon?.creature ?: current.creature,
                    onOpenLair = onOpenLair,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        }
        item { EntriesHeader(current) }
        if (current.day.entries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.today_empty),
                    style = LastHit.type.body,
                    color = LastHit.colors.text2,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        items(current.day.entries, key = { it.id }) { entry ->
            Column {
                if (entry != current.day.entries.first()) {
                    HorizontalDivider(Modifier.padding(start = 78.dp, end = 20.dp), color = LastHit.colors.line)
                }
                EntryRow(entry, name = current.nameOf(entry), tags = current.tags)
            }
        }
    }

    if (sheetOpen) {
        LogSheet(
            viewModel = sheetViewModel,
            onDismiss = { sheetOpen = false },
            onSaved = { saved ->
                sheetOpen = false
                viewModel.onSaved(saved.entry, saved.warning)
            },
        )
    }

    duplicate?.let { pending ->
        val time = rememberTimeFormatter().formatAt(pending.existing.occurredAt, pending.existing.zone)
        AlertDialog(
            onDismissRequest = viewModel::discardDuplicate,
            title = { Text(stringResource(R.string.log_duplicate_title)) },
            text = { Text(stringResource(R.string.log_duplicate_body, time)) },
            confirmButton = { TextButton(onClick = viewModel::keepBoth) { Text(stringResource(R.string.log_duplicate_keep)) } },
            dismissButton = { TextButton(onClick = viewModel::discardDuplicate) { Text(stringResource(R.string.log_duplicate_discard)) } },
        )
    }
}

@Composable
private fun TopBar(state: TodayState, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.today_title),
                style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
                color = LastHit.colors.text,
                modifier = Modifier.alignByBaseline().semantics { heading() },
            )
            Text(
                DayTitleFormatter.format(state.date),
                style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = LastHit.colors.text2,
                modifier = Modifier.padding(start = 10.dp).alignByBaseline(),
            )
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings_title),
                tint = LastHit.colors.text,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun Hero(
    state: TodayState,
    onLog: () -> Unit,
    onQuickLog: (QuickLogPill) -> Unit,
    onLongPress: (QuickLogPill) -> Unit,
    onEditQuickLog: () -> Unit,
) {
    val colors = LastHit.colors
    val total = state.day.total.mgText()
    val doses = state.day.entries.size
    Column(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.hero)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 4.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                stringResource(R.string.today_youve_had),
                style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onHero2,
            )
            val amountDescription = stringResource(R.string.today_amount_description, total)
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .clearAndSetSemantics { contentDescription = amountDescription },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(total, style = LastHit.type.hero, color = colors.onHero, modifier = Modifier.alignByBaseline())
                Text(
                    stringResource(R.string.unit_mg),
                    style = LastHit.type.hero.copy(fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.025).em),
                    color = colors.onHero,
                    modifier = Modifier.padding(start = 10.dp).alignByBaseline(),
                )
            }
            val plan = state.plan
            if (plan != null) {
                GuideMeter(plan, state.day.total, Modifier.padding(top = 22.dp))
            } else {
                Text(
                    if (doses == 0) stringResource(R.string.today_nothing_yet) else pluralStringResource(R.plurals.today_doses_so_far, doses, doses),
                    style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.onHero2,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        FlowRow(
            Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val logDescription = stringResource(R.string.today_log_description)
            Row(
                Modifier
                    .height(48.dp)
                    .pressable(colors.onHero, RoundedCornerShape(24.dp), depth = 4.dp)
                    .combinedClickable(role = Role.Button, onClick = onLog)
                    .clearAndSetSemantics {
                        contentDescription = logDescription
                        role = Role.Button
                    }
                    .padding(start = 14.dp, end = 19.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.hero, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.today_log), style = LastHit.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = colors.hero)
            }
            state.pills.forEach { pill ->
                val label = pillLabelText(pill.label)
                val description = stringResource(
                    R.string.today_quick_log_description,
                    stringResource(R.string.dose_named, pill.product.shortName, pill.product.perUnit.mgText()),
                )
                Row(
                    Modifier
                        .height(48.dp)
                        .pressable(colors.heroChip, RoundedCornerShape(24.dp))
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onQuickLog(pill) },
                            onLongClick = { onLongPress(pill) },
                        )
                        .clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                        }
                        .padding(start = 11.dp, end = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(pill.product.form.icon), contentDescription = null, tint = colors.onHero, modifier = Modifier.size(22.dp))
                    Text(label, style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.onHero)
                }
            }
            IconButton(
                onClick = onEditQuickLog,
                modifier = Modifier
                    .size(48.dp)
                    .border(1.5.dp, colors.onHero2, CircleShape),
            ) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.today_edit_quick_log),
                    tint = colors.onHero,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LabelNote(note: LabelWarning, onDismiss: () -> Unit) {
    val colors = LastHit.colors
    val text = LocalResources.current.labelNoteText(note)
    Row(
        Modifier
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.text2)
        Text(text, style = LastHit.type.label, color = colors.text, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_dismiss), tint = colors.text2)
        }
    }
}

@Composable
private fun SinceRow(elapsed: Elapsed, detail: String) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(R.drawable.ic_clock, size = 48.dp)
            Column(Modifier.weight(1f)) {
                val spoken = elapsedSpoken(elapsed)
                Text(
                    elapsedText(elapsed),
                    style = LastHit.type.headline.copy(letterSpacing = (-0.03).em),
                    color = LastHit.colors.text,
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = LastHit.colors.text2)
            }
        }
        HorizontalDivider(Modifier.padding(start = 78.dp, end = 20.dp), color = LastHit.colors.line)
    }
}

@Composable
private fun WeekSection(state: TodayState) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp)) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(state.week.total.mgText(), style = LastHit.type.display, color = LastHit.colors.text, modifier = Modifier.alignByBaseline())
            Text(
                stringResource(R.string.today_week_total),
                style = LastHit.type.title.copy(fontSize = 17.sp, lineHeight = 24.sp),
                color = LastHit.colors.text2,
                modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
            )
        }
        state.planStartsOn?.let { PlanStartsLine(it, state.date) }
        Spacer(Modifier.height(12.dp))
        WeekColumns(state.week)
    }
}

@Composable
private fun EntriesHeader(state: TodayState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(R.string.today_logged_today),
            style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
            color = LastHit.colors.text,
            modifier = Modifier.alignByBaseline(),
        )
        if (state.day.entries.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.today_logged_summary, state.day.entries.size, state.day.entries.size, state.day.total.mgText()),
                style = LastHit.type.label,
                color = LastHit.colors.text2,
                modifier = Modifier.padding(start = 10.dp).alignByBaseline(),
            )
        }
    }
}
