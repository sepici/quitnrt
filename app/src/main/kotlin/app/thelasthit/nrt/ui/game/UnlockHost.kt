package app.thelasthit.nrt.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.UnlockNews
import app.thelasthit.nrt.ui.components.pressable
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UnlockViewModel(private val graph: DataGraph) : ViewModel() {
    /** What's come in and not been shown yet, oldest first. */
    val news: StateFlow<List<UnlockNews>> = graph.unlocks.news

    fun shown(item: UnlockNews) = graph.unlocks.shown(item)

    /** Equips the new [gear] from its card, then closes the card. */
    fun equip(gear: Gear, item: UnlockNews) {
        viewModelScope.launch {
            graph.unlocks.equip(gear)
            graph.unlocks.shown(item)
        }
    }
}

/** How long a badge's toast stays before the next one, in milliseconds. */
const val BADGE_TOAST_MILLIS = 4_000L

/**
 * Shows unlocks as they come in, one at a time, sliding down from the top over whichever page is
 * open (PLAN 8.3): a toast for each badge, which goes by itself, the "new gear" card, and one
 * summary card when many badges come at once (ROADMAP P4-2). The cards wait to be closed.
 */
@Composable
fun UnlockHost(onOpenLair: () -> Unit, viewModel: UnlockViewModel = graphViewModel { UnlockViewModel(it) }) {
    val news by viewModel.news.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = news.firstOrNull(),
            transitionSpec = {
                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            contentAlignment = Alignment.TopCenter,
            label = "unlock",
        ) { item ->
            when (item) {
                null -> Unit
                is UnlockNews.Badge -> {
                    LaunchedEffect(item) {
                        delay(BADGE_TOAST_MILLIS)
                        viewModel.shown(item)
                    }
                    BadgeToast(item, onOpen = {
                        viewModel.shown(item)
                        onOpenLair()
                    })
                }
                is UnlockNews.NewGear -> GearCard(
                    item.gear,
                    onEquip = { viewModel.equip(item.gear, item) },
                    onClose = { viewModel.shown(item) },
                )
                is UnlockNews.Summary -> SummaryCard(
                    item,
                    onSee = {
                        viewModel.shown(item)
                        onOpenLair()
                    },
                    onClose = { viewModel.shown(item) },
                )
            }
        }
    }
}

/** The pixel toast: the badge on a marigold block, and its name. A tap opens the lair, where the shelf is. */
@Composable
fun BadgeToast(item: UnlockNews.Badge, onOpen: () -> Unit) {
    val name = stringResource(badgeName(item.achievement))
    UnlockCard(
        Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.unlock_open_lair), onClick = onOpen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GameBlock(56.dp) { PixelImage(GameIcons.badge(item.achievement), 40.dp) }
            Column(Modifier.weight(1f)) {
                Label(stringResource(R.string.unlock_badge_label))
                Text(name, style = CardTitle, color = LastHit.colors.text)
            }
        }
    }
}

/** The short "new gear" card, with Equip and Not now. */
@Composable
fun GearCard(gear: Gear, onEquip: () -> Unit, onClose: () -> Unit) {
    UnlockCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GameBlock(64.dp) { PixelImage(GameIcons.gear(gear), 48.dp) }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Label(stringResource(R.string.unlock_gear_label))
                Text(stringResource(gearName(gear)), style = CardTitle, color = LastHit.colors.text)
                Text(stringResource(gearFor(gear)), style = CardBody, color = LastHit.colors.text2)
            }
        }
        CardActions(
            main = stringResource(R.string.unlock_gear_equip),
            onMain = onEquip,
            other = stringResource(R.string.unlock_gear_not_now),
            onOther = onClose,
        )
    }
}

/**
 * Many badges at once, in one card: how many, a row of them, and what came with them. As when
 * phase 4 first looks at weeks of history (ROADMAP P4-2).
 */
@Composable
fun SummaryCard(item: UnlockNews.Summary, onSee: () -> Unit, onClose: () -> Unit) {
    val count = item.achievements.size
    UnlockCard {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                pluralStringResource(R.plurals.unlock_summary_title, count, count),
                style = CardTitle,
                color = LastHit.colors.text,
                modifier = Modifier.semantics { heading() },
            )
            // Balanced, so a wrapped line doesn't leave "lair." on its own.
            Text(
                if (item.gear.isEmpty()) {
                    stringResource(R.string.unlock_summary_body)
                } else {
                    pluralStringResource(R.plurals.unlock_summary_body_gear, item.gear.size, item.gear.size)
                },
                style = CardBody.copy(lineBreak = LineBreak.Heading),
                color = LastHit.colors.text2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        SummaryIcons(item, Modifier.padding(top = 12.dp))
        CardActions(
            main = stringResource(R.string.unlock_summary_see),
            onMain = onSee,
            other = stringResource(R.string.unlock_summary_close),
            onOther = onClose,
        )
    }
}

/** The badges, then the gear, in a row, with a count of any that don't fit. */
@Composable
private fun SummaryIcons(item: UnlockNews.Summary, modifier: Modifier = Modifier) {
    val icons = item.achievements.map { GameIcons.badge(it) } + item.gear.map { GameIcons.gear(it) }
    val shown = if (icons.size > MAX_SUMMARY_ICONS) icons.take(MAX_SUMMARY_ICONS - 1) else icons
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { PixelImage(it, 36.dp) }
        if (shown.size < icons.size) {
            Text(
                stringResource(R.string.unlock_summary_more, icons.size - shown.size),
                style = LastHit.type.pixel.copy(fontSize = 20.sp, lineHeight = 20.sp),
                color = LastHit.colors.text2,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private const val MAX_SUMMARY_ICONS = 8

/** The card the toast and both cards share: the surface, lifted off the page, with a hairline edge in dark mode. */
@Composable
private fun UnlockCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(colors.surface)
            .then(if (colors.isDark) Modifier.border(1.dp, colors.line, shape) else Modifier)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .then(modifier)
            .padding(14.dp),
        content = content,
    )
}

/** A square marigold block with a pressed lower edge, as the lair's banked hits sit in. */
@Composable
private fun GameBlock(size: Dp, content: @Composable BoxScope.() -> Unit) {
    val colors = LastHit.colors
    val edge = 4.dp
    Box(
        Modifier
            .size(size)
            .drawBehind {
                drawRect(colors.marigold)
                drawRect(colors.marigoldEdge, Offset(0f, this.size.height - edge.toPx()), Size(this.size.width, edge.toPx()))
            }
            .padding(bottom = edge),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A marigold button for the card's game action, and a plain one beside it to close the card. */
@Composable
private fun CardActions(main: String, onMain: () -> Unit, other: String, onOther: () -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .pressable(colors.marigold, RoundedCornerShape(26.dp), edge = colors.marigoldEdge, depth = 4.dp)
                .clickable(role = Role.Button, onClick = onMain)
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(main, style = ButtonText, color = colors.onMarigold)
        }
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .pressable(colors.sunk, RoundedCornerShape(26.dp), depth = 4.dp)
                .clickable(role = Role.Button, onClick = onOther)
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(other, style = ButtonText, color = colors.text)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold), color = LastHit.colors.text2)
}

private val CardTitle
    @Composable get() = LastHit.type.title.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.015).em)

private val CardBody
    @Composable get() = LastHit.type.label.copy(fontWeight = FontWeight.Normal)

private val ButtonText
    @Composable get() = LastHit.type.title.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
