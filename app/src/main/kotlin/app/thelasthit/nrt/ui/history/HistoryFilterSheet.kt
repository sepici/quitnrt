package app.thelasthit.nrt.ui.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.log.HistoryFilter
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.pillLabelText
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch

/** How many tags each group shows before "More", as on the log sheet. The picked tag always shows. */
private const val FOLDED_TAGS = 4

/**
 * Picks one product or one tag for History, from the products and tags that have doses, most used
 * first. The button says how many doses the pick shows. Tapping the picked chip again unpicks
 * it, and with nothing picked the button shows every dose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryFilterSheet(
    options: FilterOptions,
    current: HistoryFilter?,
    onShow: (HistoryFilter?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = LastHit.colors
    var picked by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(current) }

    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    fun pick(filter: HistoryFilter) {
        picked = if (picked == filter) null else filter
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.lineStrong, width = 36.dp, height = 4.dp) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Header(onClose = { close(onDismiss) })
                if (options.products.isNotEmpty()) ProductField(options, picked, ::pick)
                if (options.tagGroups.isNotEmpty()) TagField(options, picked, ::pick)
                Spacer(Modifier.height(28.dp))
            }
            ShowBar(
                count = options.count(picked),
                onShow = {
                    val chosen = picked
                    close { onShow(chosen) }
                },
            )
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.history_filter_title),
                style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em),
                color = LastHit.colors.text,
            )
            // Balanced, so a large font doesn't leave "tag" on a line of its own.
            Text(
                stringResource(R.string.history_filter_intro),
                style = LastHit.type.label.copy(lineBreak = LineBreak.Heading),
                color = LastHit.colors.text2,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = LastHit.colors.text)
        }
    }
}

/** A field's title, as the log sheet sets them. */
@Composable
private fun FieldTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
        color = LastHit.colors.text2,
        modifier = modifier,
    )
}

/** One scrolling row that runs under the sheet's edges, with its padding inside the scroll. */
@Composable
private fun ProductField(options: FilterOptions, picked: HistoryFilter?, onPick: (HistoryFilter) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        FieldTitle(stringResource(R.string.history_filter_product), Modifier.padding(start = 20.dp, bottom = 10.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.products.forEach { choice ->
                val filter = HistoryFilter.Product(choice.product.id)
                Chip(
                    text = pillLabelText(choice.label),
                    selected = picked == filter,
                    kind = ChipKind.Choice,
                    icon = choice.product.form.icon,
                    onClick = { onPick(filter) },
                )
            }
        }
    }
}

/**
 * The used tags by group, most used first, each group folded after [FOLDED_TAGS] behind "More".
 * Groups flow side by side when they fit, as Body and Habit do in the mockup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagField(options: FilterOptions, picked: HistoryFilter?, onPick: (HistoryFilter) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            FieldTitle(stringResource(R.string.history_filter_tag), Modifier.alignByBaseline())
            Text(
                stringResource(R.string.history_filter_tag_note),
                style = LastHit.type.caption,
                color = LastHit.colors.text3,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                    .alignByBaseline(),
            )
        }
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            options.tagGroups.forEach { choices ->
                val groupName = stringResource(choices.group.text)
                val isExpanded = choices.group.name in expanded
                val shown = if (isExpanded) {
                    choices.tags
                } else {
                    choices.tags.filterIndexed { index, tag -> index < FOLDED_TAGS || picked == HistoryFilter.Tag(tag.key) }
                }
                Column {
                    Text(
                        groupName,
                        style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = LastHit.colors.text2,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { tag ->
                            val filter = HistoryFilter.Tag(tag.key)
                            Chip(tagText(tag), selected = picked == filter, kind = ChipKind.Choice, onClick = { onPick(filter) })
                        }
                        val hidden = choices.tags.size - shown.size
                        if (hidden > 0) {
                            MoreChip(hidden, groupName, onClick = { expanded = expanded + choices.group.name })
                        }
                    }
                }
            }
        }
    }
}

/** "More", outlined, for the rest of a group's tags. Screen readers hear "4 more Routine tags". */
@Composable
private fun MoreChip(hidden: Int, group: String, onClick: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(24.dp)
    val description = pluralStringResource(R.plurals.history_filter_more_description, hidden, hidden, group)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.5.dp, colors.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.history_filter_more),
            style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text2,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun ShowBar(count: Int, onShow: () -> Unit) {
    HorizontalDivider(color = LastHit.colors.line)
    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
        PrimaryButton(onClick = onShow) { content ->
            Text(
                pluralStringResource(R.plurals.history_filter_show, count, count),
                style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                color = content,
            )
        }
    }
}

/** Keeps the pick through a rotation: "product:<id>", "tag:<key>", or empty for none. */
private val FilterSaver = Saver<HistoryFilter?, String>(
    save = { filter ->
        when (filter) {
            null -> ""
            is HistoryFilter.Product -> PRODUCT + filter.productId
            is HistoryFilter.Tag -> TAG + filter.tagKey
        }
    },
    restore = { saved ->
        when {
            saved.startsWith(PRODUCT) -> HistoryFilter.Product(saved.removePrefix(PRODUCT))
            saved.startsWith(TAG) -> HistoryFilter.Tag(saved.removePrefix(TAG))
            else -> null
        }
    },
)

private const val PRODUCT = "product:"
private const val TAG = "tag:"
