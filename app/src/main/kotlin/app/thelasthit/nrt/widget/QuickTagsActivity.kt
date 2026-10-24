package app.thelasthit.nrt.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.thelasthit.nrt.LastHitApp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.TagOrder
import app.thelasthit.nrt.ui.components.AppSheet
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.log.TagChips
import app.thelasthit.nrt.ui.settings.TextLink
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.LastHitTheme
import app.thelasthit.nrt.ui.components.DialogButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The quick tag picker the widget opens (PLAN §11): straight after a tap logs a dose when the
 * widget is set to ask, or from Tags on its "Logged" moment. It's a sheet over the home screen,
 * and closing it keeps the dose as it is.
 */
class QuickTagsActivity : ComponentActivity() {

    private val viewModel: QuickTagsViewModel by viewModels { QuickTagsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val product = intent.getStringExtra(WidgetKeys.PRODUCT.name)
            val entry = intent.getStringExtra(WidgetKeys.ENTRY.name)
            when {
                product != null -> viewModel.logThenPick(product)
                entry != null -> viewModel.pickFor(entry)
                else -> finish()
            }
        }
        setContent {
            LastHitTheme {
                QuickTagsSheet(viewModel, onDone = ::finish)
            }
        }
    }
}

data class QuickTagsState(
    /** The dose, once it's logged. */
    val entry: Entry? = null,
    val name: String = "",
    /** A dose logged within a minute from somewhere else, waiting on Keep both or Discard. */
    val duplicate: Entry? = null,
    val picked: Set<String> = emptySet(),
    /** The sheet has done its job and can close. */
    val done: Boolean = false,
)

class QuickTagsViewModel(private val graph: DataGraph) : ViewModel() {

    private val form = MutableStateFlow(QuickTagsState())
    private var request: LogRequest? = null

    private val groups = combine(graph.tags.all, graph.tags.observeUses(graph.clock.instant().minus(TagOrder.USE_WINDOW))) { tags, uses ->
        TagGroup.entries.mapNotNull { group ->
            val inGroup = tags.filter { it.group == group && !it.isHidden }
            if (inGroup.isEmpty()) null else TagChips(group, TagOrder.byUse(inGroup, uses) { it.key })
        }
    }

    val state: StateFlow<Pair<QuickTagsState, List<TagChips>>?> = combine(form, groups, ::Pair)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Logs one of [productId] from the widget, through the same path as every log. */
    fun logThenPick(productId: String) {
        val log = LogRequest(productId, source = EntrySource.WIDGET)
        request = log
        viewModelScope.launch { log(log) }
    }

    /** Opens on a dose the widget has just logged. */
    fun pickFor(entryId: String) {
        viewModelScope.launch {
            val entry = graph.entries.get(entryId)
            if (entry == null) form.update { it.copy(done = true) } else form.update { it.copy(entry = entry, name = nameOf(entry), picked = entry.tagKeys) }
        }
    }

    fun toggle(key: String) = form.update { it.copy(picked = if (key in it.picked) it.picked - key else it.picked + key) }

    fun keepBoth() {
        val log = request ?: return
        form.update { it.copy(duplicate = null) }
        viewModelScope.launch { log(log.copy(allowDuplicate = true)) }
    }

    fun discard() = form.update { it.copy(duplicate = null, done = true) }

    /** Saves the picked tags on the dose, changing nothing else about it. */
    fun save() {
        val entry = form.value.entry ?: return
        val tags = form.value.picked
        viewModelScope.launch {
            graph.entries.edit(
                entry.id,
                LogRequest(
                    productId = entry.productId,
                    quantity = entry.quantity,
                    at = entry.occurredAt,
                    kind = entry.kind,
                    cravingStrength = entry.cravingStrength,
                    tagKeys = tags,
                    note = entry.note,
                    source = entry.source,
                ),
            )
            form.update { it.copy(done = true) }
        }
    }

    /** Takes the dose back, as Undo does. */
    fun undo() {
        val entry = form.value.entry ?: return
        viewModelScope.launch {
            graph.entries.delete(entry.id)
            form.update { it.copy(done = true) }
        }
    }

    private suspend fun log(log: LogRequest) {
        when (val result = graph.entries.log(log)) {
            is LogResult.Saved -> form.update { it.copy(entry = result.entry, name = nameOf(result.entry)) }
            is LogResult.PossibleDuplicate -> form.update { it.copy(duplicate = result.existing) }
            LogResult.UnknownProduct, LogResult.NotFound, LogResult.TimeInFuture -> form.update { it.copy(done = true) }
        }
    }

    private suspend fun nameOf(entry: Entry): String = graph.products.shortNames.first()[entry.productId] ?: entry.productName

    companion object {
        /** As on the log sheet: each group folds after this many, behind "Show more". */
        const val FOLDED_TAGS = 4

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { QuickTagsViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LastHitApp).graph) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickTagsSheet(viewModel: QuickTagsViewModel, onDone: () -> Unit) {
    val current by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = LastHit.colors
    val (form, groups) = current ?: return

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDone() }
    }

    LaunchedEffect(form.done) { if (form.done) close() }

    form.duplicate?.let { existing ->
        val time = rememberTimeFormatter().formatAt(existing.occurredAt, existing.zone)
        AlertDialog(
            onDismissRequest = viewModel::discard,
            title = { Text(stringResource(R.string.log_duplicate_title)) },
            text = { Text(stringResource(R.string.log_duplicate_body, time)) },
            confirmButton = { DialogButton(stringResource(R.string.log_duplicate_keep), onClick = viewModel::keepBoth) },
            dismissButton = { DialogButton(stringResource(R.string.log_duplicate_discard), onClick = viewModel::discard) },
        )
    }

    val entry = form.entry ?: return
    val title = stringResource(R.string.quick_tags_title)
    AppSheet(onDismissRequest = onDone, sheetState = sheetState, title = title) {
        var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em),
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.quick_tags_logged, entry.total.mgText(), form.name, rememberTimeFormatter().formatAt(entry.occurredAt, entry.zone)),
                        style = LastHit.type.label,
                        color = colors.text2,
                    )
                }
                IconButton(onClick = ::close, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = colors.text)
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.quick_tags_none), style = LastHit.type.label, color = colors.text2)
                }
                groups.forEach { group ->
                    val isExpanded = group.group.name in expanded
                    val folds = group.tags.size > QuickTagsViewModel.FOLDED_TAGS
                    val shown = if (isExpanded || !folds) {
                        group.tags
                    } else {
                        group.tags.filterIndexed { index, tag -> index < QuickTagsViewModel.FOLDED_TAGS || tag.key in form.picked }
                    }
                    Column {
                        Text(
                            stringResource(group.group.text),
                            style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text2,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.forEach { tag ->
                                Chip(tagText(tag), selected = tag.key in form.picked, onClick = { viewModel.toggle(tag.key) })
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
            HorizontalDivider(color = colors.line)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth()) {
                    PrimaryButton(onClick = viewModel::save) { content ->
                        Text(
                            stringResource(R.string.quick_tags_save),
                            style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                            color = content,
                        )
                    }
                }
                TextLink(stringResource(R.string.quick_tags_undo), onClick = viewModel::undo)
            }
        }
    }
}
