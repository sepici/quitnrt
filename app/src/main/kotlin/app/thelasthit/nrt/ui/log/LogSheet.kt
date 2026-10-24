package app.thelasthit.nrt.ui.log

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.QuickTime
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.ui.components.AppSheet
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.components.Segmented
import app.thelasthit.nrt.ui.format.ShortDateFormatter
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.plural
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** How many tags each group shows before "More". Selected tags always show. */
private const val FOLDED_TAGS = 4

/**
 * The log sheet: product, amount, time, planned or craving, strength, tags and a note, with Save
 * pinned to the bottom. Call [LogSheetViewModel.openNew], [LogSheetViewModel.openFromLink] or
 * [LogSheetViewModel.openEdit] first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    viewModel: LogSheetViewModel,
    onDismiss: () -> Unit,
    onSaved: (SavedDose) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = LastHit.colors

    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    AppSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val current = state ?: return@AppSheet
        val save = {
            viewModel.save { saved -> close { onSaved(saved) } }
        }
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Header(current, onClose = { close(onDismiss) })
                ProductField(current, viewModel)
                AmountField(current, viewModel)
                TimeField(current, viewModel)
                KindField(current, viewModel)
                if (current.form.kind != EntryKind.PLANNED) StrengthField(current, viewModel)
                TagsField(current, viewModel)
                NoteField(current, viewModel)
                Spacer(Modifier.height(28.dp))
            }
            SaveBar(current, viewModel, onSave = save)
        }

        current.form.duplicate?.let { existing ->
            val time = rememberTimeFormatter().formatAt(existing.occurredAt, existing.zone)
            AlertDialog(
                onDismissRequest = viewModel::dismissDuplicate,
                title = { Text(stringResource(R.string.log_duplicate_title)) },
                text = { Text(stringResource(R.string.log_duplicate_body, time)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.save(allowDuplicate = true) { saved -> close { onSaved(saved) } } }) {
                        Text(stringResource(R.string.log_duplicate_keep))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDuplicate(); close(onDismiss) }) {
                        Text(stringResource(R.string.log_duplicate_discard))
                    }
                },
            )
        }
    }
}

@Composable
private fun Header(state: LogSheetState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (state.form.editing != null) R.string.log_title_edit else R.string.log_title),
                style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em),
                color = LastHit.colors.text,
            )
            if (state.form.editing == null) {
                Text(
                    stringResource(R.string.log_so_far, state.todayTotal.mgText()),
                    style = LastHit.type.label,
                    color = LastHit.colors.text2,
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = LastHit.colors.text)
        }
    }
}

/** A titled part of the sheet. With [bleed], the content runs to the sheet's edges and pads itself. */
@Composable
private fun Field(title: String, bleed: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = if (bleed) 0.dp else 20.dp, end = if (bleed) 0.dp else 20.dp, top = 20.dp)) {
        Text(
            title,
            style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
            color = LastHit.colors.text2,
            modifier = Modifier.padding(start = if (bleed) 20.dp else 0.dp, bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun ProductField(state: LogSheetState, viewModel: LogSheetViewModel) {
    Field(stringResource(R.string.log_product), bleed = true) {
        if (state.products.isEmpty()) {
            Text(
                stringResource(R.string.log_no_products),
                style = LastHit.type.body,
                color = LastHit.colors.text2,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            return@Field
        }
        if (state.isChoosing) {
            Text(
                stringResource(R.string.log_choose_product),
                style = LastHit.type.body,
                color = LastHit.colors.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
        }
        val others = state.products.size > state.leadingCount && state.leadingCount > 0
        val shown = if (state.form.showOtherProducts || !others) state.products else state.products.take(state.leadingCount)
        // Tiles in a row share the tallest one's height, so a long name doesn't leave a ragged edge.
        // The row scrolls under the sheet's edges, with its padding inside the scroll.
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .height(IntrinsicSize.Max)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { product ->
                ProductTile(product, selected = product.id == state.form.productId, onClick = { viewModel.pickProduct(product.id) })
            }
            if (others && !state.form.showOtherProducts) {
                Tile(
                    icon = R.drawable.ic_search,
                    big = stringResource(R.string.log_more),
                    small = stringResource(R.string.log_more_detail),
                    selected = false,
                    role = Role.Button,
                    onClick = viewModel::showOtherProducts,
                )
            }
        }
    }
}

@Composable
private fun ProductTile(product: UserProduct, selected: Boolean, onClick: () -> Unit) {
    Tile(
        icon = product.form.icon,
        big = stringResource(R.string.dose_mg, product.perUnit.mgText()),
        small = product.shortName,
        selected = selected,
        role = Role.RadioButton,
        onClick = onClick,
    )
}

@Composable
private fun Tile(icon: Int, big: String, small: String, selected: Boolean, role: Role, onClick: () -> Unit) {
    val colors = LastHit.colors
    val content = if (selected) colors.onChipSelected else colors.text
    Column(
        Modifier
            .width(112.dp)
            .fillMaxHeight()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.chipSelected else colors.sunk)
            .then(
                if (role == Role.Button) {
                    Modifier.clickable(role = role, onClick = onClick)
                } else {
                    Modifier.selectable(selected, role = role, onClick = onClick)
                },
            )
            .padding(12.dp),
    ) {
        // Top-aligned, so amounts line up across tiles when one name wraps.
        Icon(painterResource(icon), contentDescription = null, tint = content)
        Spacer(Modifier.height(10.dp))
        Column {
            Text(big, style = LastHit.type.title.copy(fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold), color = content)
            Text(small, style = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = content.copy(alpha = 0.85f), maxLines = 2)
        }
    }
}

@Composable
private fun AmountField(state: LogSheetState, viewModel: LogSheetViewModel) {
    val product = state.product ?: return
    val colors = LastHit.colors
    Field(stringResource(R.string.log_amount)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.sunk)
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(product.unit.plural, state.form.quantity, state.form.quantity),
                    style = LastHit.type.title.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                )
                val absorbed = state.absorbedDose
                Text(
                    if (absorbed == null) {
                        stringResource(R.string.log_amount_dose, state.dose.mgText())
                    } else {
                        stringResource(R.string.absorbed_log_amount, state.dose.mgText(), absorbed.mgText())
                    },
                    style = LastHit.type.caption,
                    color = colors.text2,
                )
            }
            StepButton(R.drawable.ic_minus, stringResource(R.string.log_one_fewer), enabled = state.form.quantity > 1) { viewModel.changeQuantity(-1) }
            Spacer(Modifier.width(8.dp))
            StepButton(R.drawable.ic_plus, stringResource(R.string.log_one_more), enabled = true) { viewModel.changeQuantity(1) }
        }
    }
}

@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LastHit.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.surface,
        contentColor = if (enabled) colors.text else colors.disabledText,
        shadowElevation = 1.dp,
        modifier = Modifier.size(52.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeField(state: LogSheetState, viewModel: LogSheetViewModel) {
    val timeFormat = rememberTimeFormatter()
    var picking by rememberSaveable { mutableStateOf(false) }
    val choice = state.form.time
    Field(stringResource(R.string.log_time)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val quick = listOf(
                QuickTime.NOW to stringResource(R.string.log_time_now, timeFormat.formatAt(state.now)),
                QuickTime.MINUTES_15_AGO to stringResource(R.string.log_time_15),
                QuickTime.MINUTES_30_AGO to stringResource(R.string.log_time_30),
                QuickTime.HOUR_AGO to stringResource(R.string.log_time_1h),
                QuickTime.TWO_HOURS_AGO to stringResource(R.string.log_time_2h),
                QuickTime.YESTERDAY to stringResource(R.string.log_time_yesterday),
            )
            quick.forEach { (time, label) ->
                Chip(label, selected = choice == TimeChoice.Quick(time), kind = ChipKind.Choice, onClick = { viewModel.pickTime(TimeChoice.Quick(time)) })
            }
            val picked = choice as? TimeChoice.At
            Chip(
                text = picked?.let {
                    stringResource(R.string.log_time_picked, ShortDateFormatter.format(it.instant.atZone(ZoneId.systemDefault())), timeFormat.formatAt(it.instant))
                } ?: stringResource(R.string.log_time_pick),
                selected = picked != null,
                kind = ChipKind.Choice,
                icon = R.drawable.ic_clock,
                onClick = { picking = true },
            )
        }
        if (state.form.timeInFuture) {
            Text(
                stringResource(R.string.log_future),
                style = LastHit.type.label,
                color = LastHit.colors.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (picking) {
        DateTimePicker(
            initial = viewModel.resolvedTime(choice),
            onPicked = { viewModel.pickTime(TimeChoice.At(it)); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** A date, then a time. Days after today can't be picked, so only a few minutes of future is possible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePicker(initial: Instant, onPicked: (Instant) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = initial.atZone(zone)
    val today = LocalDate.now(zone)
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val pickedDate = date
    if (pickedDate == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today

                    override fun isSelectableYear(year: Int): Boolean = year <= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    date = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text(stringResource(R.string.log_time_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    onPicked(pickedDate.atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant())
                }) { Text(stringResource(R.string.log_time_set)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(timeState) },
        )
    }
}

@Composable
private fun KindField(state: LogSheetState, viewModel: LogSheetViewModel) {
    Field(stringResource(R.string.log_kind)) {
        Segmented(
            options = listOf(EntryKind.PLANNED, EntryKind.CRAVING),
            selected = state.form.kind.takeIf { it != EntryKind.UNSPECIFIED },
            label = { stringResource(if (it == EntryKind.PLANNED) R.string.log_planned else R.string.log_craving) },
            onPick = viewModel::pickKind,
        )
    }
}

@Composable
private fun StrengthField(state: LogSheetState, viewModel: LogSheetViewModel) {
    Field(stringResource(R.string.log_strength)) {
        Segmented(
            options = (1..5).toList(),
            selected = state.form.strength,
            label = { it.toString() },
            onPick = viewModel::pickStrength,
            gap = 6,
            numbers = true,
            describe = { stringResource(R.string.log_strength_value, it) },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.log_strength_mild), style = LastHit.type.caption, color = LastHit.colors.text2)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.log_strength_strong), style = LastHit.type.caption, color = LastHit.colors.text2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsField(state: LogSheetState, viewModel: LogSheetViewModel) {
    if (state.tagGroups.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    Field(stringResource(R.string.log_tags)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.tagGroups.forEach { group ->
                val isExpanded = group.group.name in expanded
                val folds = group.tags.size > FOLDED_TAGS
                val shown = if (isExpanded || !folds) {
                    group.tags
                } else {
                    group.tags.filterIndexed { index, tag -> index < FOLDED_TAGS || tag.key in state.form.tags }
                }
                Column {
                    Text(
                        stringResource(group.group.text),
                        style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = LastHit.colors.text2,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { tag ->
                            Chip(tagText(tag), selected = tag.key in state.form.tags, onClick = { viewModel.toggleTag(tag.key) })
                        }
                        if (folds) {
                            Chip(
                                text = stringResource(if (isExpanded) R.string.log_tags_fewer else R.string.log_tags_more),
                                selected = false,
                                kind = ChipKind.Action,
                                icon = if (isExpanded) R.drawable.ic_minus else R.drawable.ic_plus,
                                onClick = { expanded = if (isExpanded) expanded - group.group.name else expanded + group.group.name },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteField(state: LogSheetState, viewModel: LogSheetViewModel) {
    val colors = LastHit.colors
    Field(stringResource(R.string.log_note)) {
        TextField(
            value = state.form.note,
            onValueChange = viewModel::changeNote,
            placeholder = { Text(stringResource(R.string.log_note_hint)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            textStyle = LastHit.type.body,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.sunk,
                unfocusedContainerColor = colors.sunk,
                focusedIndicatorColor = colors.sunk,
                unfocusedIndicatorColor = colors.sunk,
                focusedPlaceholderColor = colors.text3,
                unfocusedPlaceholderColor = colors.text3,
                cursorColor = colors.text,
            ),
        )
    }
}

@Composable
private fun SaveBar(state: LogSheetState, viewModel: LogSheetViewModel, onSave: () -> Unit) {
    val time = rememberTimeFormatter().formatAt(viewModel.resolvedTime(state.form.time))
    HorizontalDivider(color = LastHit.colors.line)
    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
        PrimaryButton(onClick = onSave, enabled = state.canSave && !state.form.timeInFuture) { content ->
            val editing = state.form.editing != null
            Text(
                stringResource(if (editing) R.string.log_save_changes else R.string.log_save),
                style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                color = content,
            )
            // Nothing to sum until a product is picked, as when a link's product could be several.
            if (!editing && state.product != null) {
                Text(
                    stringResource(R.string.log_save_detail, state.dose.mgText(), time),
                    style = LastHit.type.label.copy(fontSize = 15.sp),
                    color = content.copy(alpha = 0.82f),
                )
            }
        }
    }
}
