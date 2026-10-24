package app.thelasthit.nrt.ui.lair

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.Loadout
import app.thelasthit.nrt.domain.game.TrophyShelf
import app.thelasthit.nrt.ui.components.sheetEdge
import app.thelasthit.nrt.ui.components.sheetScrim
import app.thelasthit.nrt.ui.format.DayMonthFormatter
import app.thelasthit.nrt.ui.format.DayMonthYearFormatter
import app.thelasthit.nrt.ui.game.GameIcons
import app.thelasthit.nrt.ui.game.PixelImage
import app.thelasthit.nrt.ui.game.badgeFor
import app.thelasthit.nrt.ui.game.badgeGroupName
import app.thelasthit.nrt.ui.game.badgeName
import app.thelasthit.nrt.ui.game.gearFor
import app.thelasthit.nrt.ui.game.gearName
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** The badges on the shelf and the gear, unlocked and equipped. [year] is this year's, for dates. */
data class TrophyState(val shelf: TrophyShelf, val loadout: Loadout, val year: Int)

class TrophyViewModel(private val graph: DataGraph) : ViewModel() {
    val state: StateFlow<TrophyState?> = combine(graph.unlocks.shelf, graph.unlocks.loadout) { shelf, loadout ->
        TrophyState(shelf, loadout, graph.clock.instant().atZone(ZoneId.systemDefault()).year)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Equips [gear], through the loadout, so only unlocked gear can be. */
    fun equip(gear: Gear) {
        viewModelScope.launch { graph.unlocks.equip(gear) }
    }
}

/**
 * The lair's gear row, where the piece equipped can be changed, then the trophy shelf: every
 * badge earned, and the rest as plain outlines (PLAN 8.2 and 8.3).
 */
@Composable
fun LairTrophies(modifier: Modifier = Modifier, viewModel: TrophyViewModel = graphViewModel { TrophyViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        GearRow(current.loadout.equipped, onChange = { choosing = true })
        Shelf(current.shelf, current.year)
    }
    if (choosing) {
        GearSheet(current.loadout, onEquip = viewModel::equip, onDismiss = { choosing = false })
    }
}

// The gear row.

/** The piece equipped, as lair.html has it: its icon on a stone tile, its name, and Change. */
@Composable
private fun GearRow(equipped: Gear, onChange: () -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .topRule(colors.line)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.gear_change_spoken), onClick = onChange)
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GearTile(gear = equipped, unlocked = true)
        Column(Modifier.weight(1f)) {
            Text(stringResource(gearName(equipped)), style = RowTitle, color = colors.text)
            Text(stringResource(R.string.gear_equipped), style = RowSub, color = colors.text2)
        }
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.gear_change),
                style = LastHit.type.title.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                color = colors.link,
            )
        }
    }
}

/** A piece of gear on a rounded stone tile, or, still to come, its outline with no tile. */
@Composable
private fun GearTile(gear: Gear, unlocked: Boolean) {
    val colors = LastHit.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (unlocked) Modifier.background(colors.sunk) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PixelImage(if (unlocked) GameIcons.gear(gear) else GameIcons.gearOutline(gear, colors.lineStrong), 32.dp)
    }
}

/** Every piece of gear, in unlock order. A tap on one that's unlocked equips it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GearSheet(loadout: Loadout, onEquip: (Gear) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = LastHit.colors

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        scrimColor = sheetScrim,
        modifier = Modifier.sheetEdge(colors),
        contentColor = colors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.lineStrong, width = 36.dp, height = 4.dp) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            SheetHeader(onClose = ::close)
            Spacer(Modifier.height(8.dp))
            for (gear in Gear.entries) {
                GearOption(
                    gear,
                    unlocked = loadout.canEquip(gear),
                    equipped = loadout.equipped == gear,
                    onEquip = {
                        onEquip(gear)
                        close()
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    val titleStyle = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em)
    val titleLine = with(LocalDensity.current) { titleStyle.lineHeight.toDp() }
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.gear_sheet_title),
                style = titleStyle,
                color = LastHit.colors.text,
                modifier = Modifier.padding(end = 48.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.gear_sheet_intro),
                style = LastHit.type.label.copy(lineBreak = LineBreak.Heading),
                color = LastHit.colors.text2,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (titleLine - 48.dp) / 2)
                .size(48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = LastHit.colors.text)
        }
    }
}

/** One piece in the sheet: equipped, ready to equip, or still to come with what brings it. */
@Composable
private fun GearOption(gear: Gear, unlocked: Boolean, equipped: Boolean, onEquip: () -> Unit) {
    val colors = LastHit.colors
    val name = stringResource(gearName(gear))
    val what = stringResource(gearFor(gear))
    val locked = stringResource(R.string.gear_still_to_come, name, what)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (unlocked) {
                    Modifier
                        .semantics { selected = equipped }
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.unlock_gear_equip), onClick = onEquip)
                } else {
                    Modifier.clearAndSetSemantics { contentDescription = locked }
                },
            )
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GearTile(gear, unlocked)
        Column(Modifier.weight(1f)) {
            Text(name, style = RowTitle, color = if (unlocked) colors.text else colors.text2)
            Text(if (equipped) stringResource(R.string.gear_equipped) else what, style = RowSub, color = colors.text2)
        }
        if (equipped) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        }
    }
}

// The trophy shelf.

/**
 * Every badge in PLAN's groups, the ones earned on stone tiles and the rest as plain outlines.
 * A tap on a badge says what it's for, and when it was earned, under its group.
 */
@Composable
private fun Shelf(shelf: TrophyShelf, year: Int) {
    val colors = LastHit.colors
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = Achievement.fromKey(selectedKey)
    val spokenCount = pluralStringResource(R.plurals.shelf_count_spoken, shelf.total, shelf.earned, shelf.total)
    Column(
        Modifier
            .fillMaxWidth()
            .topRule(colors.line)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.shelf_title),
                style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
                color = colors.text,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.shelf_count, shelf.earned, shelf.total),
                style = LastHit.type.label,
                color = colors.text2,
                modifier = Modifier
                    .alignByBaseline()
                    .semantics { contentDescription = spokenCount },
            )
        }
        Text(stringResource(R.string.shelf_hint), style = RowSub, color = colors.text2, modifier = Modifier.padding(top = 2.dp))
        for (group in shelf.groups) {
            Text(
                stringResource(badgeGroupName(group.group)),
                style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text2,
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
            BadgeGrid(group.badges, selected, year, onSelect = { selectedKey = if (selectedKey == it.key) null else it.key })
            group.badges.firstOrNull { it.achievement == selected }?.let { BadgeDetail(it, year) }
        }
    }
}

/** A group's badges in rows of square tiles, as many to a row as fit. */
@Composable
private fun BadgeGrid(badges: List<TrophyShelf.Badge>, selected: Achievement?, year: Int, onSelect: (Achievement) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val columns = ((maxWidth + gap) / (TILE + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in badges.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (badge in row) {
                        BadgeTile(badge, badge.achievement == selected, year, onSelect, Modifier.weight(1f))
                    }
                    // Keeps a short last row's tiles the same size as the rest.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: TrophyShelf.Badge, selected: Boolean, year: Int, onSelect: (Achievement) -> Unit, modifier: Modifier) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(14.dp)
    val name = stringResource(badgeName(badge.achievement))
    val earnedAt = badge.earnedAt
    val description = if (earnedAt != null) {
        stringResource(R.string.shelf_badge_earned, name, dateText(earnedAt, year))
    } else {
        stringResource(R.string.shelf_badge_locked, name)
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(if (badge.isEarned) Modifier.background(colors.sunk) else Modifier)
            .then(if (selected) Modifier.border(2.dp, colors.text, shape) else Modifier)
            .clickable(role = Role.Button) { onSelect(badge.achievement) }
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = if (badge.isEarned) GameIcons.badge(badge.achievement) else GameIcons.badgeOutline(badge.achievement, colors.lineStrong)
        PixelImage(image, 40.dp)
    }
}

/** What the picked badge is for, and when it was earned, on a sunk panel under its group. */
@Composable
private fun BadgeDetail(badge: TrophyShelf.Badge, year: Int) {
    val colors = LastHit.colors
    val earnedAt = badge.earnedAt
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(badgeName(badge.achievement)), style = RowTitle, color = colors.text)
        Text(stringResource(badgeFor(badge.achievement)), style = RowSub, color = colors.text2)
        Text(
            if (earnedAt != null) stringResource(R.string.shelf_earned_on, dateText(earnedAt, year)) else stringResource(R.string.shelf_not_yet),
            style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.text,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "23 October", with the year when it isn't this one. */
private fun dateText(at: Instant, year: Int): String {
    val date = at.atZone(ZoneId.systemDefault())
    return (if (date.year == year) DayMonthFormatter else DayMonthYearFormatter).format(date)
}

/** A hairline across the top, as lair.html draws above the gear row. */
private fun Modifier.topRule(colour: Color): Modifier = drawBehind {
    drawLine(colour, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}

private val TILE = 52.dp

private val RowTitle
    @Composable get() = LastHit.type.title.copy(fontWeight = FontWeight.Bold)

private val RowSub
    @Composable get() = LastHit.type.label.copy(fontWeight = FontWeight.Normal)
