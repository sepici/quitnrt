package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.ui.game.BandScene
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.game.NameAndChip

/** The demon as the Plan tab shows it. */
sealed interface BandLook {
    val name: String?
    val creature: Creature

    /** Before the plan starts: a silhouette with unknown HP. */
    data class Lurking(override val name: String?, override val creature: Creature) : BandLook

    /** Stepped out with its HP, in its colours, roughed up as the HP falls. */
    data class Out(
        override val name: String?,
        override val creature: Creature,
        val variant: String?,
        val hp: Int,
        val maxHp: Int,
        val banked: Int,
    ) : BandLook

    /** At 0 HP: gone from its rock, scattered into pixels. */
    data class Defeated(override val name: String?, override val creature: Creature, val variant: String?, val maxHp: Int) : BandLook
}

/**
 * The demon band on the Plan tab, as on Today: lurking while the baseline runs, then out in
 * colour with its HP once the plan starts. Tapping it opens the lair.
 */
@Composable
internal fun PlanBand(look: BandLook, onOpenLair: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val name = look.name ?: stringResource(R.string.demon_default_name)
    val variant = when (look) {
        is BandLook.Lurking -> null
        is BandLook.Out -> look.variant ?: DEFAULT_VARIANT
        is BandLook.Defeated -> look.variant ?: DEFAULT_VARIANT
    }
    val frame = when (look) {
        is BandLook.Lurking -> SpriteFrame.IDLE_1
        is BandLook.Out -> if (look.maxHp > 0) SpriteFrame.forHealth(look.hp, look.maxHp) else SpriteFrame.IDLE_1
        is BandLook.Defeated -> SpriteFrame.DEFEAT_4
    }
    val ready = (look as? BandLook.Out)?.banked?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plan_hits_ready, it, it) }
    val description = when (look) {
        is BandLook.Lurking -> stringResource(R.string.demon_band_description, name)
        is BandLook.Out -> if (ready != null) {
            stringResource(R.string.plan_band_ready_description, name, look.hp, look.maxHp, ready)
        } else {
            stringResource(R.string.plan_band_description, name, look.hp, look.maxHp)
        }
        is BandLook.Defeated -> stringResource(R.string.plan_band_defeated_description, name, look.maxHp)
    }
    BandScene(
        look.creature,
        variant,
        height = 132.dp,
        textStart = 138.dp,
        textTop = 12.dp,
        frame = frame,
        modifier = modifier
            .clickable(onClick = onOpenLair)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        // As Today's band has them: the chip drops under a name it won't fit beside, and a name too
        // long for two lines ends in an ellipsis rather than being cut through a letter.
        val chip = when {
            look is BandLook.Defeated -> stringResource(R.string.plan_defeated)
            else -> ready
        }
        NameAndChip(
            name = {
                Text(
                    name,
                    style = LastHit.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em, lineBreak = LineBreak.Heading),
                    color = colors.stageText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            chip = chip?.let { { Chip(it) } },
        )
        when (look) {
            is BandLook.Lurking -> {
                Text(stringResource(R.string.demon_lurking), style = LastHit.type.label, color = colors.stageText2, modifier = Modifier.padding(top = 2.dp))
                Hp(stringResource(R.string.demon_hp_unknown), zero = false, top = 6.dp)
            }
            is BandLook.Out -> {
                HpBlocks(look.maxHp / 10, look.hp / 10, Modifier.padding(top = 10.dp))
                Hp(stringResource(R.string.plan_hp_value, look.hp, look.maxHp), zero = false, top = 8.dp)
            }
            is BandLook.Defeated -> {
                HpBlocks(look.maxHp / 10, 0, Modifier.padding(top = 10.dp))
                Hp(stringResource(R.string.plan_hp_value, 0, look.maxHp), zero = true, top = 8.dp)
            }
        }
    }
}

/** "110/120 HP" in the pixel font. */
@Composable
private fun Hp(value: String, zero: Boolean, top: androidx.compose.ui.unit.Dp) {
    val colors = LastHit.colors
    Row(Modifier.padding(top = top), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = LastHit.type.pixel, color = if (zero) colors.stageText2 else colors.stageText, modifier = Modifier.alignByBaseline())
        Text(
            stringResource(R.string.demon_hp),
            style = LastHit.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.stageText2,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** The game layer's marigold chip: "1 hit ready", "Defeated". */
@Composable
private fun Chip(text: String) {
    val colors = LastHit.colors
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .background(colors.marigold)
            .drawBehind {
                val edge = 3.dp.toPx()
                drawRect(colors.marigoldEdge, Offset(0f, size.height - edge), Size(size.width, edge))
            }
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LastHit.type.label.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 18.sp), color = colors.onMarigold, maxLines = 1)
    }
}

/** The palette a demon with none picked shows: the app's own, forest. */
internal const val DEFAULT_VARIANT = "forest"
