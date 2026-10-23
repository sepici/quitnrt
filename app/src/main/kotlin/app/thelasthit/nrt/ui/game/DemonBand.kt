package app.thelasthit.nrt.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import app.thelasthit.nrt.domain.creature.Backdrop
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * The demon's strip on Today. Before the plan starts it lurks: a silhouette with unknown HP.
 * Tapping it opens the lair.
 */
@Composable
fun DemonBand(
    name: String?,
    creature: Creature,
    onOpenLair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LastHit.colors
    val displayName = name ?: stringResource(R.string.demon_default_name)
    val description = stringResource(R.string.demon_band_description, displayName)
    Box(
        modifier
            .fillMaxWidth()
            .height(132.dp)
            .clickable(onClick = onOpenLair)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        LurkingScene(GameArt.band, creature, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 138.dp, top = 14.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                displayName,
                style = LastHit.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em),
                color = colors.stageText,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.demon_lurking),
                style = LastHit.type.caption,
                color = colors.stageText2,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.demon_hp_unknown), style = LastHit.type.pixel, color = colors.stageText)
                Text(
                    stringResource(R.string.demon_hp),
                    style = LastHit.type.caption.copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp),
                    color = colors.stageText2,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/**
 * A backdrop with the creature as a dark shape on its rock, watching with marigold eyes. In dark
 * mode the shape gets the backdrop's light rim, or it would vanish into the night sky.
 */
@Composable
fun LurkingScene(backdrop: Backdrop, creature: Creature, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val dark = colors.isDark
    val image = remember(backdrop, dark) { GameArt.backdrop(backdrop, dark) }
    val sheet = remember(creature) { GameArt.sheet(creature) }
    val shape = remember(sheet, dark) {
        GameArt.silhouette(sheet, SpriteFrame.IDLE_1, if (dark) LurkDark else LurkLight, eyes = colors.marigold)
    }
    val rim = remember(sheet, dark) {
        backdrop.rim(dark)?.let { GameArt.silhouette(sheet, SpriteFrame.IDLE_1, Color(it)) }
    }
    Canvas(modifier) {
        val (scale, origin) = drawBackdrop(backdrop, image)
        val at = origin + Offset(backdrop.spriteX * scale, backdrop.spriteY * scale)
        rim?.let { drawRim(it, at, scale, 1.dp.toPx()) }
        drawPixels(shape, at, scale)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRim(rim: ImageBitmap, at: Offset, scale: Float, width: Float) {
    for (offset in listOf(Offset(width, 0f), Offset(-width, 0f), Offset(0f, width), Offset(0f, -width))) {
        drawPixels(rim, at + offset, scale)
    }
}

// The stage's own browns, as in the onboarding mockups.
private val LurkLight = Color(0xFF3F3829)
private val LurkDark = Color(0xFF504838)
