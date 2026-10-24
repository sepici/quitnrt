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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Holds new unlocks back while a strike plays in the lair, so a badge's toast or a gear card never
 * lands on top of a hit or the defeat. They come in once it's over.
 */
private val strikePlaying = MutableStateFlow(false)

/** Holds unlocks back while [playing], and lets them go when it isn't or this leaves the screen. */
@Composable
fun HoldUnlocksWhile(playing: Boolean) {
    DisposableEffect(playing) {
        strikePlaying.value = playing
        onDispose { strikePlaying.value = false }
    }
}

/**
 * How long a badge's toast stays before the next one, in milliseconds, unless the phone's
 * accessibility settings ask for longer.
 */
const val BADGE_TOAST_MILLIS = 4_000L

/**
 * Shows unlocks as they come in, one at a time, sliding down from the top over whichever page is
 * open (PLAN 8.3): a toast for each badge, which goes by itself, the "new gear" card, and one
 * summary card when many badges come at once (ROADMAP P4-2). The cards wait to be closed. A tap
 * on a toast, or "See the shelf", calls [onOpenShelf] to open the lair at its trophy shelf.
 */
@Composable
fun UnlockHost(onOpenShelf: () -> Unit, viewModel: UnlockViewModel = graphViewModel { UnlockViewModel(it) }) {
    val news by viewModel.news.collectAsStateWithLifecycle()
    val held by strikePlaying.collectAsStateWithLifecycle()
    // As Material's snackbar does: the toast can be tapped, so it stays as long as the phone's
    // accessibility settings ask, for anyone who needs longer to reach it.
    val accessibility = LocalAccessibilityManager.current
    val toastMillis = accessibility?.calculateRecommendedTimeoutMillis(
        BADGE_TOAST_MILLIS,
        containsIcons = true,
        containsText = true,
        containsControls = true,
    ) ?: BADGE_TOAST_MILLIS
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = if (held) null else news.firstOrNull(),
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
                        delay(toastMillis)
                        viewModel.shown(item)
                    }
                    BadgeToast(item, onOpen = {
                        viewModel.shown(item)
                        onOpenShelf()
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
                        onOpenShelf()
                    },
                    onClose = { viewModel.shown(item) },
                )
            }
        }
    }
}

/** The pixel toast: the badge on a marigold block, and its name. A tap opens the lair at the shelf. */
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

/**
 * The badges, then the gear, in a row: as many as fit the card's width, up to
 * [MAX_SUMMARY_ICONS], with a count of the rest.
 */
@Composable
private fun SummaryIcons(item: UnlockNews.Summary, modifier: Modifier = Modifier) {
    val icons = item.achievements.map { GameIcons.badge(it) } + item.gear.map { GameIcons.gear(it) }
    val countStyle = LastHit.type.pixel.copy(fontSize = 20.sp, lineHeight = 20.sp)
    // Room for the count at its widest, as if no icon were shown, with its gap.
    val measurer = rememberTextMeasurer()
    val widest = stringResource(R.string.unlock_summary_more, icons.size)
    val countRoom = with(LocalDensity.current) {
        measurer.measure(widest, countStyle, maxLines = 1, softWrap = false).size.width.toDp()
    } + COUNT_PADDING + ICON_GAP
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics {}) {
        fun fitting(width: Dp) = ((width + ICON_GAP) / (SUMMARY_ICON + ICON_GAP)).toInt()
        val all = icons.size <= minOf(MAX_SUMMARY_ICONS, fitting(maxWidth))
        val shown = if (all) icons else icons.take(minOf(MAX_SUMMARY_ICONS - 1, fitting(maxWidth - countRoom)).coerceAtLeast(1))
        Row(horizontalArrangement = Arrangement.spacedBy(ICON_GAP), verticalAlignment = Alignment.CenterVertically) {
            shown.forEach { PixelImage(it, SUMMARY_ICON) }
            if (shown.size < icons.size) {
                Text(
                    stringResource(R.string.unlock_summary_more, icons.size - shown.size),
                    style = countStyle,
                    color = LastHit.colors.text2,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = COUNT_PADDING),
                )
            }
        }
    }
}

private const val MAX_SUMMARY_ICONS = 8
private val SUMMARY_ICON = 36.dp
private val ICON_GAP = 6.dp
private val COUNT_PADDING = 4.dp

/**
 * The card the toast and both cards share: the surface, lifted off the page, with a hairline edge
 * as B's floating tip has it: line in light, and lineStrong in dark, where a shadow can't show.
 */
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
            .border(1.dp, if (colors.isDark) colors.lineStrong else colors.line, shape)
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

/**
 * A marigold button for the card's game action, pressed as the game's buttons are, and B's line
 * button beside it to close the card. They're side by side when both labels fit on one line,
 * and stacked full width when either doesn't, as at a large font on a narrow phone.
 */
@Composable
private fun CardActions(main: String, onMain: () -> Unit, other: String, onOther: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(26.dp)
    Layout(
        content = {
            Box(
                Modifier
                    .heightIn(min = 52.dp)
                    .pressable(colors.marigold, shape, edge = colors.marigoldEdge, depth = 4.dp)
                    .clickable(role = Role.Button, onClick = onMain)
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(main, style = ButtonText, color = colors.onMarigold, textAlign = TextAlign.Center)
            }
            Box(
                Modifier
                    .heightIn(min = 52.dp)
                    .border(1.5.dp, colors.lineStrong, shape)
                    .clip(shape)
                    .clickable(role = Role.Button, onClick = onOther)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(other, style = LineButtonText, color = colors.text, textAlign = TextAlign.Center)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) { buttons, constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val half = (width - gap) / 2
        if (buttons.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half }) {
            val height = buttons.maxOf { it.minIntrinsicHeight(half) }
            val placed = buttons.map { it.measure(Constraints.fixed(half, height)) }
            layout(width, height) {
                placed[0].place(0, 0)
                placed[1].place(width - half, 0)
            }
        } else {
            val placed = buttons.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            layout(width, placed.sumOf { it.height } + gap) {
                placed[0].place(0, 0)
                placed[1].place(0, placed[0].height + gap)
            }
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

/** B's line button has its label a weight lighter, at 700. */
private val LineButtonText
    @Composable get() = ButtonText.copy(fontWeight = FontWeight.Bold)
