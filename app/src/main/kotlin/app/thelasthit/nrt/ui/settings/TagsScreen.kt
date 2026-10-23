package app.thelasthit.nrt.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.components.SwitchRow
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(private val graph: DataGraph) : ViewModel() {

    /** Every tag, hidden ones too, by group in the user's order. */
    val groups: StateFlow<Map<TagGroup, List<TagEntity>>?> = graph.tags.all
        .map { tags -> TagGroup.entries.associateWith { group -> tags.filter { it.group == group } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(name: String, group: TagGroup) {
        viewModelScope.launch { graph.tags.addCustom(name, group) }
    }

    fun save(tag: TagEntity, name: String, group: TagGroup, hidden: Boolean) {
        viewModelScope.launch { graph.tags.edit(tag, name, group, hidden) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(onBack: () -> Unit, viewModel: TagsViewModel = graphViewModel { TagsViewModel(it) }) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = LastHit.colors

    SettingsPage(stringResource(R.string.tags_title), stringResource(R.string.settings_back), onBack) {
        val all = groups ?: return@SettingsPage
        Intro(stringResource(R.string.tags_intro))
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(
                text = stringResource(R.string.tags_add),
                selected = false,
                kind = ChipKind.Action,
                icon = R.drawable.ic_plus,
                onClick = { editing = NEW_TAG },
            )
        }
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // A tiny chip in the style of the user's own tags, so it doesn't read as a checkbox.
                Box(
                    Modifier
                        .size(width = 22.dp, height = 14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.sunk)
                        .border(1.5.dp, colors.lineStrong, RoundedCornerShape(7.dp)),
                )
                Text(stringResource(R.string.tags_key_own), style = LastHit.type.caption, color = colors.text2)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_hidden), contentDescription = null, tint = colors.text2, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.tags_key_hidden), style = LastHit.type.caption, color = colors.text2)
            }
        }
        all.forEach { (group, tags) ->
            if (tags.isEmpty()) return@forEach
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                val own = tags.count { !it.isBuiltIn }
                val hidden = tags.count { it.isHidden }
                val counts = buildList {
                    add(pluralStringResource(R.plurals.tags_count, tags.size, tags.size))
                    if (own > 0) add(pluralStringResource(R.plurals.tags_yours, own, own))
                    if (hidden > 0) add(pluralStringResource(R.plurals.settings_hidden_count, hidden, hidden))
                }
                Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(group.text), style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold), color = colors.text)
                    Text(counts.joinToString(", "), style = LastHit.type.label, color = colors.text2)
                }
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag -> TagChip(tag, onClick = { editing = tag.key }) }
                }
            }
        }

        editing?.let { key ->
            val tag = all.values.flatten().firstOrNull { it.key == key }
            if (key == NEW_TAG || tag != null) {
                TagSheet(
                    tag = tag,
                    onDismiss = { editing = null },
                    onAdd = viewModel::add,
                    onSave = viewModel::save,
                )
            }
        }
    }
}

private const val NEW_TAG = "new"

/** A tag to tap and edit. Your own are outlined; hidden ones are struck through with an eye. */
@Composable
private fun TagChip(tag: TagEntity, onClick: () -> Unit) {
    val colors = LastHit.colors
    val name = tagText(tag)
    val description = when {
        tag.isHidden -> stringResource(R.string.tags_hidden_description, name)
        !tag.isBuiltIn -> stringResource(R.string.tags_own_description, name)
        else -> name
    }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(
                when {
                    tag.isHidden -> Modifier.border(1.5.dp, colors.line, shape)
                    !tag.isBuiltIn -> Modifier.background(colors.sunk).border(1.5.dp, colors.lineStrong, shape)
                    else -> Modifier.background(colors.sunk)
                },
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tag.isHidden) Icon(painterResource(R.drawable.ic_hidden), contentDescription = null, tint = colors.text3, modifier = Modifier.size(18.dp))
        Text(
            name,
            style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = if (tag.isHidden) colors.text3 else colors.text,
        )
    }
}

/** Renames, moves or hides a tag, or with no [tag], adds one of your own. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagSheet(
    tag: TagEntity?,
    onDismiss: () -> Unit,
    onAdd: (String, TagGroup) -> Unit,
    onSave: (TagEntity, String, TagGroup, Boolean) -> Unit,
) {
    val colors = LastHit.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val builtInName = tag?.takeIf { it.isBuiltIn }?.let { tagText(it.key) }
    var name by rememberSaveable { mutableStateOf(tag?.let { it.customName ?: builtInName }.orEmpty()) }
    var group by rememberSaveable { mutableStateOf(tag?.group ?: TagGroup.FEELINGS) }
    var hidden by rememberSaveable { mutableStateOf(tag?.isHidden ?: false) }
    val canSave = name.isNotBlank() || tag?.isBuiltIn == true

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.lineStrong, width = 36.dp, height = 4.dp) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (tag == null) R.string.tag_add_title else R.string.tag_edit_title),
                            style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp),
                            color = colors.text,
                        )
                        Text(
                            when {
                                builtInName != null -> stringResource(R.string.tag_built_in_as, builtInName)
                                else -> stringResource(R.string.tag_new_detail)
                            },
                            style = LastHit.type.label,
                            color = colors.text2,
                        )
                    }
                    IconButton(onClick = ::close) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = colors.text)
                    }
                }
                SheetField(stringResource(R.string.tag_name)) {
                    SheetTextField(
                        value = name,
                        onChange = { name = it.take(40) },
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        trailing = if (name.isEmpty()) {
                            null
                        } else {
                            {
                                IconButton(onClick = { name = "" }) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.tag_clear), tint = colors.text2)
                                }
                            }
                        },
                    )
                    if (tag != null) {
                        Text(stringResource(R.string.tag_name_help), style = LastHit.type.caption, color = colors.text2, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                SheetField(stringResource(R.string.tag_group)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagGroup.entries.forEach { each ->
                            Chip(stringResource(each.text), selected = each == group, kind = ChipKind.Choice, onClick = { group = each })
                        }
                    }
                }
                if (tag != null) {
                    Box(Modifier.padding(top = 24.dp)) {
                        SwitchRow(
                            title = stringResource(R.string.tag_hide),
                            detail = stringResource(R.string.tag_hide_detail),
                            checked = hidden,
                            onChange = { hidden = it },
                        )
                    }
                }
            }
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    onClick = {
                        if (tag == null) onAdd(name, group) else onSave(tag, name.takeIf { it != builtInName }.orEmpty(), group, hidden)
                        close()
                    },
                    enabled = canSave,
                ) { content ->
                    Text(
                        stringResource(if (tag == null) R.string.tag_add_button else R.string.tag_save),
                        style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                        color = content,
                    )
                }
                if (builtInName != null && tag.customName != null) {
                    TextLink(stringResource(R.string.tag_reset, builtInName), onClick = { name = builtInName })
                }
            }
        }
    }
}
