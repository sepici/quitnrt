package app.thelasthit.nrt.ui.lair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.ui.game.CreatureScene
import app.thelasthit.nrt.ui.game.GameArt
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The demon picked in onboarding. Until the plan starts it has no HP and shows no colours. */
data class LairState(val name: String?, val creature: Creature)

class LairViewModel(graph: DataGraph) : ViewModel() {
    val state: StateFlow<LairState?> = graph.settings.settings
        .map { LairState(it.demonName, it.demonCreature) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The lair. For now the demon only lurks on its rock, a shadow with unknown HP, because the
 * battle needs a plan to start. The stage runs up behind the status bar.
 */
@Composable
fun LairScreen(viewModel: LairViewModel = graphViewModel { LairViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val colors = LastHit.colors
    val name = current.name ?: stringResource(R.string.demon_default_name)
    StageAbove(
        stage = { Stage(current.creature, stringResource(R.string.lair_stage_lurking, name)) },
        modifier = Modifier.background(colors.bg),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    name,
                    style = LastHit.type.display.copy(lineHeight = 48.sp),
                    color = colors.text,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline()
                        .semantics { heading() },
                )
                UnknownHp(Modifier.alignByBaseline())
            }
            Text(
                stringResource(R.string.lair_lurking, name),
                style = LastHit.type.flavour,
                color = colors.text2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Bank(
            count = 0,
            title = stringResource(R.string.lair_no_hits),
            rule = stringResource(R.string.lair_hits_rule),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 32.dp),
        )
    }
}

/**
 * [stage] over the rest of the page. The stage is at least the backdrop's own height, and on a
 * tall screen it also takes whatever room the page leaves, as more sky, so the page never ends
 * in a gap. On a short screen, or at a large font, the page scrolls instead.
 */
@Composable
private fun StageAbove(stage: @Composable () -> Unit, modifier: Modifier = Modifier, rest: @Composable ColumnScope.() -> Unit) {
    val backdrop = GameArt.stage
    BoxWithConstraints(modifier.fillMaxSize()) {
        val screen = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        Layout(
            content = {
                stage()
                Column(content = rest)
            },
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) { (top, below), constraints ->
            val width = constraints.maxWidth
            val page = below.measure(constraints.copy(minHeight = 0))
            val stageHeight = maxOf(width * backdrop.height / backdrop.width, screen - page.height)
            val art = top.measure(Constraints.fixed(width, stageHeight))
            layout(width, stageHeight + page.height) {
                art.place(0, 0)
                page.place(0, stageHeight)
            }
        }
    }
}

/** The backdrop on the ground, with plain sky above it for any extra height. */
@Composable
private fun Stage(creature: Creature, description: String) {
    val backdrop = GameArt.stage
    Box(
        Modifier
            .background(LastHit.colors.stageSky)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        CreatureScene(
            backdrop,
            creature,
            variant = null,
            Modifier
                .fillMaxWidth()
                .aspectRatio(backdrop.width.toFloat() / backdrop.height),
        )
    }
}

/** "?? HP" in the pixel font, until the demon shows itself. */
@Composable
private fun UnknownHp(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.lair_hp_unknown)
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.demon_hp_unknown),
            style = LastHit.type.pixelLarge,
            color = LastHit.colors.text,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            stringResource(R.string.demon_hp),
            style = LastHit.type.caption.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp),
            color = LastHit.colors.text2,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * The hits banked, as a block with the rule for earning them. The block is marigold only when
 * there's a hit to use. With none it's an empty outlined slot, like the mockup's empty pips.
 */
@Composable
private fun Bank(count: Int, title: String, rule: String, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val banked = count > 0
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .drawBehind {
                    if (banked) {
                        val edge = 4.dp.toPx()
                        drawRect(colors.marigold)
                        drawRect(colors.marigoldEdge, Offset(0f, size.height - edge), Size(size.width, edge))
                    } else {
                        val outline = 2.dp.toPx()
                        drawRect(
                            colors.lineStrong,
                            Offset(outline / 2, outline / 2),
                            Size(size.width - outline, size.height - outline),
                            style = Stroke(outline),
                        )
                    }
                }
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                count.toString(),
                style = LastHit.type.pixelLarge,
                color = if (banked) colors.onMarigold else colors.text2,
                // The digit's own spacing sits to its right, and a banked block's edge at the bottom.
                modifier = Modifier.padding(start = 4.dp, bottom = if (banked) 4.dp else 0.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = LastHit.type.title.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.015).em),
                color = colors.text,
            )
            Text(rule, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
        }
    }
}
