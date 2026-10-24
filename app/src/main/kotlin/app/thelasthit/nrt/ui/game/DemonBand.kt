package app.thelasthit.nrt.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    BandScene(
        creature,
        variant = null,
        height = 132.dp,
        textStart = 138.dp,
        textTop = 14.dp,
        modifier = modifier
            .clickable(onClick = onOpenLair)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                displayName,
                style = LastHit.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em),
                color = colors.stageText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    style = LastHit.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = colors.stageText2,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/**
 * The band backdrop with the creature on its rock and [content] in the sky beside it. At [height]
 * the band shows at its own scale. When the text needs more room, at a large font say, the band
 * grows upwards as more sky, so text never runs into the hills.
 */
@Composable
fun BandScene(
    creature: Creature,
    variant: String?,
    height: Dp,
    textStart: Dp,
    textTop: Dp,
    modifier: Modifier = Modifier,
    frame: SpriteFrame = SpriteFrame.IDLE_1,
    content: @Composable ColumnScope.() -> Unit,
) {
    val band = GameArt.band
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // drawBackdrop's scale, so the gap matches the hills as drawn.
        val scale = maxOf(maxWidth / band.width, height / band.height)
        val land = scale * (band.height - band.horizon)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .background(LastHit.colors.stageSky),
        ) {
            CreatureScene(
                band,
                creature,
                variant,
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(height),
                frame,
            )
            Column(Modifier.padding(start = textStart, top = textTop, end = 16.dp, bottom = land + 4.dp), content = content)
        }
    }
}

/**
 * A backdrop with the creature on its rock: in [variant]'s colours, or with no variant as a
 * lurking dark shape watching with marigold eyes. In dark mode the creature gets the backdrop's
 * light rim, or it would vanish into the night sky. [frame] is its condition, idle by default.
 */
@Composable
fun CreatureScene(
    backdrop: Backdrop,
    creature: Creature,
    variant: String?,
    modifier: Modifier = Modifier,
    frame: SpriteFrame = SpriteFrame.IDLE_1,
) {
    val dark = LastHit.colors.isDark
    val image = remember(backdrop, dark) { GameArt.backdrop(backdrop, dark) }
    val sprite = rememberCreature(creature, variant, frame)
    val rim = rememberRim(creature, backdrop.rim(dark), frame)
    Canvas(modifier) {
        val (scale, origin) = drawBackdrop(backdrop, image)
        val at = origin + Offset(backdrop.spriteX * scale, backdrop.spriteY * scale)
        rim?.let { drawRim(it, at, scale, 1.dp.toPx()) }
        drawPixels(sprite, at, scale)
    }
}

/** The creature on its own, filling [modifier]'s square, as tiles and swatches show it. */
@Composable
fun CreatureImage(creature: Creature, variant: String?, modifier: Modifier = Modifier) {
    val dark = LastHit.colors.isDark
    val sprite = rememberCreature(creature, variant)
    val rim = rememberRim(creature, GameArt.band.rim(dark))
    Canvas(modifier) {
        val scale = minOf(size.width, size.height) / sprite.width
        val at = Offset((size.width - sprite.width * scale) / 2, (size.height - sprite.height * scale) / 2)
        rim?.let { drawRim(it, at, scale, 1.dp.toPx()) }
        drawPixels(sprite, at, scale)
    }
}

/** The creature as a flat shape in [colour], watching with marigold eyes, as on the welcome screen. */
@Composable
fun CreatureSilhouette(creature: Creature, colour: Color, modifier: Modifier = Modifier) {
    val eyes = LastHit.colors.marigold
    val sheet = remember(creature) { GameArt.sheet(creature) }
    val shape = remember(sheet, colour, eyes) { GameArt.silhouette(sheet, SpriteFrame.IDLE_1, colour, eyes) }
    Canvas(modifier) {
        val scale = minOf(size.width, size.height) / shape.width
        drawPixels(shape, Offset(size.width - shape.width * scale, size.height - shape.height * scale), scale)
    }
}

/** The creature's [frame] in [variant], or lurking when there's no variant. */
@Composable
private fun rememberCreature(creature: Creature, variant: String?, frame: SpriteFrame = SpriteFrame.IDLE_1): ImageBitmap {
    val colors = LastHit.colors
    val dark = colors.isDark
    val sheet = remember(creature) { GameArt.sheet(creature) }
    return remember(sheet, variant, dark, frame) {
        if (variant == null) {
            GameArt.silhouette(sheet, frame, if (dark) LurkDark else LurkLight, eyes = colors.marigold)
        } else {
            GameArt.sprite(sheet, frame, variant)
        }
    }
}

@Composable
private fun rememberRim(creature: Creature, rim: Int?, frame: SpriteFrame = SpriteFrame.IDLE_1): ImageBitmap? {
    val sheet = remember(creature) { GameArt.sheet(creature) }
    return remember(sheet, rim, frame) { rim?.let { GameArt.silhouette(sheet, frame, Color(it)) } }
}

private fun DrawScope.drawRim(rim: ImageBitmap, at: Offset, scale: Float, width: Float) {
    for (offset in listOf(Offset(width, 0f), Offset(-width, 0f), Offset(0f, width), Offset(0f, -width))) {
        drawPixels(rim, at + offset, scale)
    }
}

// The stage's own browns, as in the onboarding mockups.
private val LurkLight = Color(0xFF3F3829)
private val LurkDark = Color(0xFF504838)
