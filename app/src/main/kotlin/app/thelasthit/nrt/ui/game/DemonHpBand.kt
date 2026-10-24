package app.thelasthit.nrt.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.repo.DemonState
import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.domain.programme.HP_PER_HIT
import app.thelasthit.nrt.ui.onboarding.OnboardingDraft
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * The demon's strip on Today once the plan has started (B's `today.html`): the creature in its
 * colours, roughed up as its HP falls, with its name, the hits banked for it ("1 hit ready", in
 * the game's marigold), its HP bar in blocks of 10 and the HP left. Everything here comes from
 * the hit ledger. Tapping it opens the lair.
 */
@Composable
fun DemonHpBand(state: DemonState, onOpenLair: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val demon = state.demon
    val balance = state.balance
    val name = demon.name ?: stringResource(R.string.demon_default_name)
    val hp = balance.hp
    val banked = balance.banked
    val ready = if (banked > 0) pluralStringResource(R.plurals.demon_band_hits_ready, banked, banked) else null
    val description = if (ready == null) {
        stringResource(R.string.demon_band_hp_description, name, hp, balance.maxHp)
    } else {
        stringResource(R.string.demon_band_hp_ready_description, name, hp, balance.maxHp, ready)
    }
    val frame = if (balance.maxHp > 0) SpriteFrame.forHealth(hp, balance.maxHp) else SpriteFrame.IDLE_1
    BandScene(
        demon.creature,
        variant = demon.variant ?: OnboardingDraft.DEFAULT_VARIANT,
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
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = LastHit.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em),
                color = colors.stageText,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (ready != null) ReadyChip(ready)
        }
        HpBar(hp = hp, maxHp = balance.maxHp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.demon_band_hp_value, hp, balance.maxHp), style = LastHit.type.pixel, color = colors.stageText)
            Text(
                stringResource(R.string.demon_hp),
                style = LastHit.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                color = colors.stageText2,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** Banked hits, as a marigold chip with a darker edge along its bottom, like a pixel button. */
@Composable
private fun ReadyChip(text: String) {
    val colors = LastHit.colors
    Box(
        Modifier
            .padding(start = 8.dp)
            .heightIn(min = 32.dp)
            .drawBehind {
                drawRect(colors.marigold)
                val edge = 3.dp.toPx()
                drawRect(colors.marigoldEdge, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LastHit.type.label.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold), color = colors.onMarigold, maxLines = 1)
    }
}

/**
 * The demon's HP in blocks of 10, full ones with a lighter top edge, as the lair's bar. A 12-week
 * programme is 12 blocks; a repeat adds one.
 */
@Composable
fun HpBar(hp: Int, maxHp: Int, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val blocks = (maxHp / HP_PER_HIT).coerceAtLeast(1)
    val full = (hp / HP_PER_HIT).coerceIn(0, blocks)
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val gap = (if (blocks > 24) 2.dp else 3.dp).toPx()
        val width = (size.width - gap * (blocks - 1)) / blocks
        val shine = 3.dp.toPx()
        for (index in 0 until blocks) {
            val left = index * (width + gap)
            if (index < full) {
                drawRect(colors.hpFull, Offset(left, 0f), Size(width, size.height))
                drawRect(colors.hpHigh, Offset(left, 0f), Size(width, shine))
            } else {
                drawRect(colors.hpEmpty, Offset(left, 0f), Size(width, size.height))
            }
        }
    }
}
