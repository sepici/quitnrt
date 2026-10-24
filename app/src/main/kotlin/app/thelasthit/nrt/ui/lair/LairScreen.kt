package app.thelasthit.nrt.ui.lair

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.domain.game.Attack
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.LairLine
import app.thelasthit.nrt.domain.game.Victory
import app.thelasthit.nrt.ui.components.pressable
import app.thelasthit.nrt.ui.format.DayMonthFormatter
import app.thelasthit.nrt.ui.format.DayMonthYearFormatter
import app.thelasthit.nrt.ui.format.creatureName
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.plural
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.game.CreatureScene
import app.thelasthit.nrt.ui.game.GameArt
import app.thelasthit.nrt.ui.game.GameIcons
import app.thelasthit.nrt.ui.game.GameSound
import app.thelasthit.nrt.ui.game.HitVoice
import app.thelasthit.nrt.ui.game.HoldUnlocksWhile
import app.thelasthit.nrt.ui.game.PixelImage
import app.thelasthit.nrt.ui.game.rememberGameSounds
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.onboarding.OnboardingDraft
import app.thelasthit.nrt.ui.plan.mixText
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.components.rememberAnimatorScale
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The lair (PLAN 7.3). Before the plan starts the demon lurks on its rock, a shadow with unknown
 * HP. Once it starts, this is the battle: the demon in its colours and condition, the HP bar, the
 * hits banked, Hit and the 5× attack, then the defeat and the victory. The stage runs up behind
 * the status bar. With [toShelf], it scrolls down to the gear and the trophy shelf once they're
 * in, then calls [onShelfShown].
 */
@Composable
fun LairScreen(
    toShelf: Boolean = false,
    onShelfShown: () -> Unit = {},
    viewModel: LairViewModel = graphViewModel { LairViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strike by viewModel.strike.collectAsStateWithLifecycle()
    val lastAttack by viewModel.lastAttack.collectAsStateWithLifecycle()
    val soundOn by viewModel.soundOn.collectAsStateWithLifecycle()
    val equipped by viewModel.equipped.collectAsStateWithLifecycle()
    // Leaving mid-strike ends it, so it isn't played again from the tap when the lair comes back.
    DisposableEffect(viewModel) { onDispose(viewModel::leave) }
    val current = state ?: return
    val colors = LastHit.colors
    val name = current.name ?: stringResource(R.string.demon_default_name)
    var restyling by rememberSaveable { mutableStateOf(false) }

    // Motion: the phone's animation speed, where the gear lands, and the strike playing now.
    val animatorScale = rememberAnimatorScale()
    val reduced = animatorScale <= 0f
    val sheet = remember(current.creature) { GameArt.sheet(current.creature) }
    val impact = remember(sheet) { impactPoint(sheet) }
    val sounds = rememberGameSounds()
    val haptics = LocalHapticFeedback.current
    val voice = HitVoice.of(equipped)
    val pose = rememberStrikePose(
        strike,
        reduced = reduced,
        animatorScale = animatorScale,
        impact = impact,
        onCue = { sound ->
            if (soundOn) sounds.play(sound, voice)
            hapticFor(sound)?.let(haptics::performHapticFeedback)
        },
        onDone = viewModel::strikeDone,
    )
    // New badges wait while a strike plays, so a toast never lands on the defeat.
    HoldUnlocksWhile(strike != null)

    val battle = current as? LairState.Battle
    val shownHp = pose?.hp ?: battle?.hp ?: 0
    val idling = battle != null && strike == null && !reduced && !battle.isDefeated
    var idleTwo by remember { mutableStateOf(false) }
    LaunchedEffect(idling, animatorScale) {
        idleTwo = false
        if (!idling) return@LaunchedEffect
        // A slow breath between the two idle frames, at the phone's animation speed.
        while (true) {
            delay((IDLE_BEAT_MS * animatorScale).toLong().coerceAtLeast(1))
            idleTwo = !idleTwo
        }
    }

    StageAbove(
        stage = {
            StageBox(
                description = stageDescription(current, name, pose?.frame ?: battle?.let { restingFrame(shownHp, it.maxHp, idleTwo = false) }),
                bar = {
                    StageBar(
                        label = battle?.let { weekText(it.week) },
                        restyle = if (battle?.isDefeated == true) null else stringResource(R.string.lair_restyle_spoken, name),
                        onRestyle = { restyling = true },
                    )
                },
            ) { modifier ->
                if (battle == null) {
                    CreatureScene(GameArt.stage, current.creature, variant = null, modifier)
                } else {
                    BattleScene(
                        battle.creature,
                        battle.variant,
                        frame = pose?.frame ?: restingFrame(shownHp, battle.maxHp, idleTwo),
                        pose = pose,
                        equipped = equipped,
                        modifier = modifier,
                    )
                }
            }
        },
        modifier = Modifier.background(colors.bg),
    ) {
        if (battle == null) {
            Lurking(name)
        } else {
            val line = LairLine.of(shownHp, battle.maxHp, battle.banked, battle.lastWeekMissed, lastAttack?.answeredIn(battle))
            // The demon answers once the strike is over: until then its line stays as it was at the tap.
            val heldLine = remember(strike?.id) { line }
            BattleHud(name, shownHp, battle.maxHp, lineText(if (strike != null) heldLine else line, name))
            val won = battle.isDefeated && strike == null
            if (!won) {
                Bank(
                    count = battle.banked,
                    title = stringResource(
                        when (battle.banked) {
                            0 -> R.string.lair_bank_none
                            1 -> R.string.lair_bank_one
                            else -> R.string.lair_bank_many
                        },
                    ),
                    rule = stringResource(R.string.lair_hits_rule),
                    // With none, the title says it: "No hits banked".
                    spoken = if (battle.banked > 0) pluralStringResource(R.plurals.lair_bank_spoken, battle.banked, battle.banked) else null,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
                )
                Attacks(
                    banked = battle.banked,
                    hp = battle.hp,
                    busy = strike != null,
                    equipped = equipped,
                    onAttack = viewModel::attack,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                )
            }
            AnimatedVisibility(visible = won, enter = fadeIn() + expandVertically(expandFrom = Alignment.Top)) {
                battle.victory?.let { VictorySection(it, name, battle) }
            }
        }
        LairTrophies(toShelf = toShelf, onShelfShown = onShelfShown)
    }

    if (restyling) {
        RestyleSheet(
            name = current.name.orEmpty(),
            creature = current.creature,
            variant = current.variant ?: OnboardingDraft.DEFAULT_VARIANT,
            fighting = battle != null,
            onSave = viewModel::restyle,
            onDismiss = { restyling = false },
        )
    }
}

/**
 * The strike playing now as a [Pose], moved on every frame from the time since the tap, at the
 * phone's animation speed. Each sound's moment calls [onCue] once, and the end calls [onDone].
 * A strike cut short by the lair leaving the screen shows as it ends, with no sound. Null when no
 * strike is playing.
 */
@Composable
private fun rememberStrikePose(
    strike: Strike?,
    reduced: Boolean,
    animatorScale: Float,
    impact: Px,
    onCue: (GameSound) -> Unit,
    onDone: (Strike) -> Unit,
): Pose? {
    val motion = remember(strike?.id, reduced) { strike?.let { BattleMotion(it, reduced, impact) } }
    var elapsed by remember(motion) { mutableLongStateOf(0L) }
    val cue by rememberUpdatedState(onCue)
    val done by rememberUpdatedState(onDone)
    val cutShort = strike?.cutShort == true
    LaunchedEffect(motion, cutShort) {
        val playing = motion?.takeUnless { cutShort } ?: return@LaunchedEffect
        var start = -1L
        var last = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0) start = now
            val real = (now - start) / 1_000_000
            // Reduced motion is already as short as it gets; any other speed stretches or squeezes time.
            val ms = if (reduced) real else (real / animatorScale).toLong()
            playing.cuesBetween(last, ms).forEach { cue(it) }
            last = ms
            elapsed = ms
            if (ms >= playing.length) break
        }
        done(playing.strike)
    }
    return motion?.at(if (cutShort) motion.length else elapsed)
}

/** The haptic for each sound: a tick for each hit, a heavier one for the finisher and the knockout. */
private fun hapticFor(sound: GameSound): HapticFeedbackType? = when (sound) {
    GameSound.IMPACT -> HapticFeedbackType.ContextClick
    GameSound.STRIKE -> HapticFeedbackType.SegmentTick
    GameSound.FINISHER, GameSound.KNOCKOUT -> HapticFeedbackType.LongPress
    GameSound.VICTORY -> HapticFeedbackType.Confirm
    GameSound.SWING, GameSound.BREAK -> null
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

/** The backdrop on the ground, with plain sky above it for any extra height, and the bar over its top. */
@Composable
private fun StageBox(description: String, bar: @Composable () -> Unit, scene: @Composable (Modifier) -> Unit) {
    val backdrop = GameArt.stage
    Box(Modifier.background(LastHit.colors.stageSky)) {
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = description },
            contentAlignment = Alignment.BottomCenter,
        ) {
            scene(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(backdrop.width.toFloat() / backdrop.height),
            )
        }
        bar()
    }
}

/** Under the status bar: the programme's week, and the button that renames or restyles the demon. */
@Composable
private fun StageBar(label: String?, restyle: String?, onRestyle: () -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 52.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.orEmpty(),
            style = LastHit.type.title.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            color = colors.stageText2,
            modifier = Modifier.weight(1f),
        )
        if (restyle != null) {
            IconButton(onClick = onRestyle, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_tune), contentDescription = restyle, tint = colors.stageText)
            }
        }
    }
}

@Composable
private fun weekText(week: WeekLabel): String = when (week) {
    is WeekLabel.Starts -> stringResource(R.string.lair_week_starts, DayMonthFormatter.format(week.on))
    is WeekLabel.Week -> stringResource(R.string.lair_week, week.number, week.of)
    is WeekLabel.Overtime -> stringResource(R.string.lair_week_overtime, week.number)
    WeekLabel.Done -> stringResource(R.string.lair_programme_done)
}

/** What the stage shows, for a screen reader: lurking, or the demon in its condition, or the empty rock. */
@Composable
private fun stageDescription(state: LairState, name: String, frame: SpriteFrame?): String {
    if (state is LairState.Lurking || frame == null) return stringResource(R.string.lair_stage_lurking, name)
    val kind = stringResource(creatureName(state.creature)).lowercase(Locale.UK)
    return when {
        frame in SpriteFrame.DEFEAT -> stringResource(R.string.lair_stage_gone, name)
        frame == SpriteFrame.BARELY -> stringResource(R.string.lair_stage_barely, name, kind)
        frame == SpriteFrame.ROUGH -> stringResource(R.string.lair_stage_rough, name, kind)
        else -> stringResource(R.string.lair_stage_fresh, name, kind)
    }
}

@Composable
private fun lineText(line: LairLine, name: String): String = stringResource(
    when (line) {
        LairLine.FRESH -> R.string.lair_line_fresh
        LairLine.ROUGH -> R.string.lair_line_rough
        LairLine.BARELY -> R.string.lair_line_barely
        LairLine.LAST_HIT -> R.string.lair_line_last_hit
        LairLine.SMUG -> R.string.lair_line_smug
        LairLine.STAGGERS -> R.string.lair_line_staggers
        LairLine.REELS -> R.string.lair_line_reels
        LairLine.GONE -> R.string.lair_line_gone
    },
    name,
)

// Lurking.

@Composable
private fun Lurking(name: String) {
    val colors = LastHit.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NameHeading(name, Modifier.weight(1f).alignByBaseline())
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
        spoken = null,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 32.dp),
    )
}

@Composable
private fun NameHeading(name: String, modifier: Modifier = Modifier) {
    Text(
        name,
        style = LastHit.type.display.copy(lineHeight = 48.sp),
        color = LastHit.colors.text,
        modifier = modifier.semantics { heading() },
    )
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
        HpUnit(Modifier.alignByBaseline())
    }
}

@Composable
private fun HpUnit(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.demon_hp),
        style = LastHit.type.caption.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp),
        color = LastHit.colors.text2,
        modifier = modifier,
    )
}

// The battle.

/** The name, the HP in the pixel font, the demon's line and the HP bar in blocks of 10. */
@Composable
private fun BattleHud(name: String, hp: Int, maxHp: Int, line: String) {
    val colors = LastHit.colors
    // At 0 it says so, as the hit that does it lands, before the victory is drawn.
    val spokenHp = if (hp == 0) stringResource(R.string.lair_hp_spoken_defeated, maxHp, name) else stringResource(R.string.lair_hp_spoken, hp, maxHp)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NameHeading(name, Modifier.weight(1f).alignByBaseline())
            Row(
                Modifier
                    .alignByBaseline()
                    .clearAndSetSemantics {
                        contentDescription = spokenHp
                        // Read out as a hit lands.
                        liveRegion = LiveRegionMode.Polite
                    },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.lair_hp, hp, maxHp),
                    style = LastHit.type.pixelLarge,
                    color = colors.text,
                    modifier = Modifier.alignByBaseline(),
                )
                HpUnit(Modifier.alignByBaseline())
            }
        }
        // Held through a strike, so it changes once, as the demon answers, and is read out then.
        Text(
            line,
            style = LastHit.type.flavour,
            color = colors.text2,
            modifier = Modifier
                .padding(top = 2.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        HpBar(hp, maxHp, Modifier.padding(top = 16.dp))
    }
}

/** One block for every 10 HP, full or empty, each full one with its lighter top edge. */
@Composable
private fun HpBar(hp: Int, maxHp: Int, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val blocks = maxHp / HP_BLOCK
    val full = hp / HP_BLOCK
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(if (blocks > MANY_BLOCKS) 3.dp else 4.dp),
    ) {
        repeat(blocks) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(22.dp)
                    .drawBehind {
                        if (index < full) {
                            drawRect(colors.hpFull)
                            drawRect(colors.hpHigh, size = Size(size.width, 4.dp.toPx()))
                        } else {
                            drawRect(colors.hpEmpty)
                        }
                    },
            )
        }
    }
}

/**
 * The hits banked, as a block with the rule for earning them. The block is marigold only when
 * there's a hit to use. With none it's an empty slot in the HP bar's empty colour. [spoken] is
 * the count for a screen reader, or null to leave it to the title.
 */
@Composable
private fun Bank(count: Int, title: String, rule: String, spoken: String?, modifier: Modifier = Modifier) {
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
                        drawRect(colors.hpEmpty)
                    }
                }
                .clearAndSetSemantics { if (spoken != null) contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                count.toString(),
                style = LastHit.type.pixelLarge,
                color = if (banked) colors.onMarigold else colors.stageText2,
                // The digit's own spacing sits to its right, and a banked block's edge at the bottom.
                modifier = Modifier.padding(start = 4.dp, bottom = if (banked) 4.dp else 0.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = LastHit.type.title.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.015).em),
                color = colors.text,
                modifier = if (spoken != null) Modifier.clearAndSetSemantics {} else Modifier,
            )
            Text(rule, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
        }
    }
}

/**
 * Hit, with the gear in hand, and the 5× attack with a pip for each hit towards it. While a strike
 * plays they're [busy]: held down and taking no taps until it's over.
 */
@Composable
private fun Attacks(banked: Int, hp: Int, busy: Boolean, equipped: Gear, onAttack: (Attack) -> Unit, modifier: Modifier = Modifier) {
    val canHit = Attack.HIT.canUse(banked, hp)
    val canCombo = Attack.COMBO.canUse(banked, hp)
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AttackButton(
            title = stringResource(R.string.lair_hit),
            sub = stringResource(if (canHit) R.string.lair_hit_uses else R.string.lair_hit_needs),
            enabled = canHit,
            busy = busy,
            onClick = { onAttack(Attack.HIT) },
            modifier = Modifier.weight(1.25f).fillMaxHeight(),
            icon = { PixelImage(GameIcons.gear(equipped), 32.dp) },
        )
        AttackButton(
            title = stringResource(R.string.lair_combo),
            sub = stringResource(if (canCombo) R.string.lair_combo_uses else R.string.lair_combo_needs),
            enabled = canCombo,
            busy = busy,
            onClick = { onAttack(Attack.COMBO) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            pips = minOf(banked, Attack.COMBO.hits),
        )
    }
}

/**
 * One of the two attack buttons: marigold with its pressed edge when it can be used, the quiet
 * disabled tone when not. While a strike plays, one that can be used is [busy]: held down onto its
 * edge, with no ripple, until the strike is over.
 */
@Composable
private fun AttackButton(
    title: String,
    sub: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    pips: Int? = null,
) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(24.dp)
    val content = if (enabled) colors.onMarigold else colors.disabledText
    val ready = enabled && !busy
    Column(
        modifier
            .heightIn(min = 104.dp)
            .then(
                when {
                    ready -> Modifier.pressable(colors.marigold, shape, edge = colors.marigoldEdge, depth = PRESS_DEPTH)
                    // Held down: the face sits on its edge, lower by the edge's depth.
                    enabled -> Modifier.padding(top = PRESS_DEPTH).clip(shape).background(colors.marigold)
                    else -> Modifier.clip(shape).background(colors.disabledBg)
                },
            )
            .clickable(enabled = ready, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (ready) 14.dp + PRESS_DEPTH else 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = LastHit.type.title.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.023).em),
                color = content,
                modifier = Modifier.weight(1f),
            )
            icon?.invoke()
        }
        Column(Modifier.padding(top = 10.dp)) {
            if (pips != null) Pips(pips, onMarigold = enabled, outline = content)
            Text(
                sub,
                style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
                color = content,
                modifier = Modifier.padding(top = if (pips != null) 6.dp else 0.dp),
            )
        }
    }
}

/**
 * Five squares, one filled for each hit banked towards the 5× attack: marigold on the quiet
 * button, and the button's dark ink once it's marigold itself, with all five in.
 */
@Composable
private fun Pips(filled: Int, onMarigold: Boolean, outline: Color) {
    val colors = LastHit.colors
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(Attack.COMBO.hits) { index ->
            val full = index < filled
            Box(
                Modifier
                    .size(10.dp)
                    .then(
                        when {
                            !full -> Modifier.border(1.5.dp, outline)
                            onMarigold -> Modifier.background(colors.onMarigold)
                            else -> Modifier.background(colors.marigold).border(1.5.dp, colors.marigoldEdge)
                        },
                    ),
            )
        }
    }
}

// The victory.

/**
 * What the run took, once the demon falls, in plan-done.html's rows: weeks, the usual day down to
 * none, the hits and the triggers.
 */
@Composable
private fun VictorySection(victory: Victory, name: String, battle: LairState.Battle) {
    val colors = LastHit.colors
    val weeks = if (victory.overtime > 0) {
        stringResource(R.string.lair_victory_weeks_overtime, victory.weeks, victory.overtime)
    } else {
        victory.weeks.toString()
    }
    val hits = if (victory.lastHitOn == battle.today) {
        stringResource(R.string.lair_victory_hits_today, victory.hits)
    } else {
        val day = (if (victory.lastHitOn.year == battle.today.year) DayMonthFormatter else DayMonthYearFormatter).format(victory.lastHitOn)
        stringResource(R.string.lair_victory_hits_on, victory.hits, day)
    }
    val triggers = victory.triggers.map { key -> battle.tags[key]?.let { tagText(it) } ?: tagText(key) }
    // As the Plan tab sums up the same run: the usual day product by product, or its mg if a product in it has gone.
    val daily = battle.usualDay?.let { stringResource(R.string.lair_victory_daily_value, mixText(it)) }
        ?: stringResource(R.string.lair_victory_daily_mg, victory.usualDaily.mgText())
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp)) {
        Text(
            stringResource(R.string.lair_victory_title),
            style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
            color = colors.text,
            // It comes in as the demon falls, so it's read out then, as well as being a heading.
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Column(Modifier.padding(top = 4.dp)) {
            VictoryRow(stringResource(R.string.lair_victory_weeks), weeks, first = true)
            VictoryRow(stringResource(R.string.lair_victory_daily), daily, first = false)
            VictoryRow(stringResource(R.string.lair_victory_hits, name), hits, first = false)
            if (triggers.isNotEmpty()) {
                VictoryRow(stringResource(R.string.lair_victory_triggers), triggers.joinToString(", "), first = false)
            }
        }
        Text(
            stringResource(R.string.lair_victory_foot),
            style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
            color = colors.text2,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * One row of the victory: the label on the left and the value on the right, as plan-done.html has
 * them, with the label on one line. A value that would take more than two lines beside it, as a
 * long label leaves it at a large font, goes under the label instead, across the full width.
 */
@Composable
private fun VictoryRow(label: String, value: String, first: Boolean) {
    val colors = LastHit.colors
    val labelStyle = LastHit.type.body
    val valueStyle = LastHit.type.body.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column {
        if (!first) HorizontalDivider(color = colors.line)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            val gap = 16.dp
            val beside = constraints.maxWidth - measurer.measure(label, labelStyle).size.width - with(density) { gap.roundToPx() }
            val stacked = beside <= 0 || measurer.measure(value, valueStyle, constraints = Constraints(maxWidth = beside)).lineCount > 2
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = labelStyle, color = colors.text2)
                    Text(value, style = valueStyle, color = colors.text)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Text(label, style = labelStyle, color = colors.text2, maxLines = 1)
                    Text(value, style = valueStyle, color = colors.text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** How deep an attack button's edge is, and how far it goes down while it's held. */
private val PRESS_DEPTH = 5.dp

/** HP comes in blocks of 10. */
private const val HP_BLOCK = 10

/** Past this many blocks, as in a 24-week plan, they sit a little closer. */
private const val MANY_BLOCKS = 16

/** The time between the two idle frames, at normal animation speed. */
private const val IDLE_BEAT_MS = 650f
