package app.thelasthit.nrt.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.ui.components.IconTile
import app.thelasthit.nrt.ui.components.pressable
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.pillLabelText
import app.thelasthit.nrt.ui.format.productTitle
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuickLogState(
    val onQuickLog: List<UserProduct>,
    val labels: Map<String, PillLabel>,
    /** Products in use but not on the quick log. Hidden ones are left out. */
    val others: List<UserProduct>,
)

class QuickLogViewModel(private val graph: DataGraph) : ViewModel() {

    val state: StateFlow<QuickLogState?> = combine(graph.products.quickLog, graph.products.all) { quick, all ->
        val ids = quick.map { it.id }.toSet()
        QuickLogState(quick, PillLabel.of(quick), all.filter { it.id !in ids })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun move(from: Int, to: Int) {
        val current = state.value?.onQuickLog?.map { it.id } ?: return
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { graph.products.setQuickLog(reordered) }
    }

    fun remove(product: UserProduct) {
        viewModelScope.launch { graph.products.removeFromQuickLog(product.id) }
    }

    fun add(product: UserProduct) {
        viewModelScope.launch { graph.products.appendToQuickLog(product.id) }
    }
}

@Composable
fun QuickLogScreen(onBack: () -> Unit, viewModel: QuickLogViewModel = graphViewModel { QuickLogViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsPage(stringResource(R.string.quick_title), stringResource(R.string.settings_back), onBack) {
        val current = state ?: return@SettingsPage
        Intro(stringResource(R.string.quick_intro))
        Preview(current)
        SectionHeading(stringResource(R.string.quick_in_order))
        if (current.onQuickLog.isEmpty()) {
            FootNote(stringResource(R.string.quick_empty))
        } else {
            ReorderableList(current, viewModel)
        }
        SectionHeading(stringResource(R.string.quick_not_on))
        if (current.others.isEmpty()) {
            FootNote(stringResource(R.string.quick_all_on))
        } else {
            RowList {
                current.others.forEachIndexed { index, product ->
                    SettingsRow(
                        title = productTitle(product),
                        detail = null,
                        first = index == 0,
                        onClick = null,
                        icon = product.form.icon,
                        trailing = {
                            TextLink(
                                stringResource(R.string.quick_add),
                                onClick = { viewModel.add(product) },
                                description = stringResource(R.string.quick_add_description, productTitle(product)),
                            )
                        },
                    )
                }
            }
        }
        FootNote(stringResource(R.string.quick_foot))
    }
}

/** The quick log as Today shows it, so the order can be checked at a glance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Preview(state: QuickLogState) {
    val colors = LastHit.colors
    Column(
        Modifier
            .padding(start = 8.dp, end = 8.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.hero)
            .padding(16.dp)
            .clearAndSetSemantics {},
    ) {
        Text(
            stringResource(R.string.quick_on_today),
            style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onHero2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.height(48.dp).pressable(colors.onHero, RoundedCornerShape(24.dp), depth = 4.dp).padding(start = 14.dp, end = 19.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.hero, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.today_log), style = LastHit.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = colors.hero)
            }
            state.onQuickLog.forEach { product ->
                Row(
                    Modifier.height(48.dp).pressable(colors.heroChip, RoundedCornerShape(24.dp)).padding(start = 11.dp, end = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(product.form.icon), contentDescription = null, tint = colors.onHero, modifier = Modifier.size(22.dp))
                    state.labels[product.id]?.let { label ->
                        Text(pillLabelText(label), style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.onHero)
                    }
                }
            }
        }
    }
}

/**
 * The quick log in order. Hold a row's handle and drag it to move it; the rows it passes make
 * room. Screen readers get "Move up" and "Move down" instead.
 */
@Composable
private fun ReorderableList(state: QuickLogState, viewModel: QuickLogViewModel) {
    val colors = LastHit.colors
    val products = state.onQuickLog
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableIntStateOf(-1) }
    val moveUp = stringResource(R.string.quick_move_up)
    val moveDown = stringResource(R.string.quick_move_down)

    fun targetFor(from: Int, by: Float): Int {
        var index = from
        var travelled = by
        while (travelled > 0 && index < products.lastIndex) {
            val next = heights[index + 1] ?: break
            if (travelled < next / 2f) break
            travelled -= next
            index++
        }
        while (travelled < 0 && index > 0) {
            val previous = heights[index - 1] ?: break
            if (-travelled < previous / 2f) break
            travelled += previous
            index--
        }
        return index
    }

    RowList {
        products.forEachIndexed { index, product ->
            val isDragged = dragging == index
            val from = dragging
            // Rows between where the dragged one started and where it's heading make room for it.
            val shift = when {
                from == null || isDragged -> 0f
                index in (from + 1)..target -> -(heights[from] ?: 0).toFloat()
                index in target until from -> (heights[from] ?: 0).toFloat()
                else -> 0f
            }
            Box(
                Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) offset else shift }
                    .onSizeChanged { heights[index] = it.height },
            ) {
                Column(
                    Modifier
                        .then(if (isDragged) Modifier.shadow(8.dp, RoundedCornerShape(16.dp)) else Modifier)
                        .background(if (isDragged) colors.surface else colors.bg, RoundedCornerShape(if (isDragged) 16.dp else 0.dp)),
                ) {
                    if (index > 0 && !isDragged) HorizontalDivider(color = colors.line)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .semantics(mergeDescendants = true) {
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction(moveUp) { viewModel.move(index, index - 1); true })
                                    if (index < products.lastIndex) add(CustomAccessibilityAction(moveDown) { viewModel.move(index, index + 1); true })
                                }
                            }
                            .padding(end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // A full 48 dp to grab, from the row's edge, with the dots where they were.
                        Box(
                            Modifier
                                .size(48.dp)
                                .pointerInput(index, products.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragging = index
                                            offset = 0f
                                            target = index
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            offset += amount.y
                                            target = targetFor(index, offset)
                                        },
                                        onDragEnd = {
                                            val to = target
                                            dragging = null
                                            offset = 0f
                                            viewModel.move(index, to)
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            offset = 0f
                                        },
                                    )
                                }
                                .clearAndSetSemantics {},
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_grip), contentDescription = stringResource(R.string.quick_drag), tint = colors.text2)
                        }
                        IconTile(product.form.icon, size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            state.labels[product.id]?.let { label ->
                                Text(pillLabelText(label), style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                            }
                            Text(product.shortName, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
                        }
                        val label = state.labels[product.id]?.let { pillLabelText(it) } ?: product.name
                        IconButton(onClick = { viewModel.remove(product) }) {
                            Icon(
                                painterResource(R.drawable.ic_remove),
                                contentDescription = stringResource(R.string.quick_remove, label),
                                tint = colors.text2,
                            )
                        }
                    }
                }
            }
        }
    }
}
