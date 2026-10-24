package app.thelasthit.nrt.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.format.creatureName
import app.thelasthit.nrt.ui.format.variantName
import app.thelasthit.nrt.ui.theme.LastHit
import java.util.Locale

/** The demon on the band backdrop, as Today will show it once the plan starts. */
@Composable
fun DemonPreview(name: String, creature: Creature, variant: String, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val kind = stringResource(
        R.string.onboarding_demon_kind,
        stringResource(variantName(variant)),
        stringResource(creatureName(creature)).lowercase(Locale.UK),
    )
    BandScene(creature, variant, height = 168.dp, textStart = 172.dp, textTop = 30.dp, modifier = modifier) {
        Column {
            if (name.isNotEmpty()) {
                Text(
                    name,
                    style = LastHit.type.headline.copy(fontSize = 30.sp, lineHeight = 34.sp),
                    color = colors.stageText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(kind, style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.stageText2, modifier = Modifier.padding(top = 2.dp))
            if (name.isNotEmpty()) {
                Text(
                    stringResource(R.string.onboarding_demon_flavour, name),
                    style = LastHit.type.flavour.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = colors.stageText2,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/**
 * The demon's creature, as onboarding, the Plan tab's rematch and the lair's rename sheet pick it,
 * under its label: every creature four to a row. The drawn ones ([Creature.isDrawn])
 * can be picked, in [variant]'s colours; the rest are empty dotted frames until their sprites are
 * signed off, and a line under the tiles says so.
 */
@Composable
fun CreaturePicker(selected: Creature, variant: String, onPick: (Creature) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        FieldLabel(stringResource(R.string.onboarding_creature))
        Tiles(Creature.entries, Modifier.selectableGroup()) { creature ->
            CreatureTile(creature, selected = creature == selected, variant = variant, onPick = { onPick(creature) })
        }
        if (Creature.someUndrawn) {
            Text(
                stringResource(R.string.onboarding_creature_hint),
                style = LastHit.type.caption,
                color = LastHit.colors.text2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** The colours, under their label: [creature] in each of its variants on the stage's sky. */
@Composable
fun ColourPicker(creature: Creature, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        FieldLabel(stringResource(R.string.onboarding_colour))
        VariantTiles(creature, selected, onPick)
    }
}

@Composable
private fun VariantTiles(creature: Creature, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val variants = remember(creature) { GameArt.sheet(creature).variantNames }
    Tiles(variants, modifier.selectableGroup()) { variant ->
        VariantSwatch(creature, variant, selected = variant == selected, onPick = { onPick(variant) })
    }
}

/**
 * A tile's name on one line: at the largest fonts it steps down in size to fit, rather than
 * breaking "Dragon" across two lines.
 */
@Composable
private fun TileName(text: String, color: Color) {
    val style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold)
    BasicText(
        text,
        style = style,
        color = { color },
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * Items four to a row, each the same width, and as tall as the tallest in its row: at least
 * 118 dp, and more when a large font wraps a name, so the name never pushes the sprite out.
 */
@Composable
private fun <T> Tiles(items: List<T>, modifier: Modifier = Modifier, tile: @Composable BoxScope.(T) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> Box(Modifier.weight(1f).fillMaxHeight()) { tile(item) } }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BoxScope.CreatureTile(creature: Creature, selected: Boolean, variant: String, onPick: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    val name = stringResource(creatureName(creature))
    if (creature.isDrawn) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = 118.dp)
                .clip(shape)
                .background(if (selected) colors.stageSky else colors.bg)
                .border(if (selected) 2.dp else 1.5.dp, if (selected) colors.text else colors.line, shape)
                .selectable(selected, role = Role.RadioButton, onClick = onPick)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            // 64 dp is twice the sprite's size, with room between its feet and the name.
            CreatureImage(creature, variant, Modifier.padding(bottom = 8.dp).size(64.dp))
            TileName(name, if (selected) colors.stageText else colors.text)
        }
        if (selected) Tick()
    } else {
        val description = stringResource(R.string.onboarding_creature_locked, name)
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = 118.dp)
                .clip(shape)
                .border(1.5.dp, colors.line, shape)
                .clearAndSetSemantics {
                    contentDescription = description
                    disabled()
                }
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            // An empty dotted frame: not drawn yet, and nothing to earn or pay for.
            val frame = colors.lineStrong.copy(alpha = 0.5f)
            Canvas(Modifier.padding(bottom = 18.dp).size(42.dp)) {
                val dash = 4.dp.toPx()
                drawRoundRect(
                    frame,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            }
            TileName(name, colors.text3)
        }
    }
}

@Composable
private fun BoxScope.VariantSwatch(creature: Creature, variant: String, selected: Boolean, onPick: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(colors.stageSky)
            .then(if (selected) Modifier.border(2.dp, colors.text, shape) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onPick)
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        CreatureImage(creature, variant, Modifier.padding(bottom = 8.dp).size(64.dp))
        TileName(stringResource(variantName(variant)), colors.stageText)
    }
    if (selected) Tick()
}

@Composable
private fun BoxScope.Tick() {
    val colors = LastHit.colors
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(colors.chipSelected),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onChipSelected, modifier = Modifier.size(14.dp))
    }
}
