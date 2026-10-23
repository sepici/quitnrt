package app.thelasthit.nrt.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.format.creatureName
import app.thelasthit.nrt.ui.format.variantName
import app.thelasthit.nrt.ui.game.BandScene
import app.thelasthit.nrt.ui.game.CreatureImage
import app.thelasthit.nrt.ui.game.GameArt
import app.thelasthit.nrt.ui.theme.LastHit
import java.util.Locale

/** Step 5: the demon's name, creature and colour, with a preview of it on the band. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemonStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val colors = LastHit.colors
    val draft = state.draft
    StepScaffold(
        step = OnboardingStep.DEMON,
        onBack = viewModel::back,
        footer = { NextButton(stringResource(R.string.onboarding_next_day), viewModel::next, enabled = state.canName) },
    ) {
        StepHeader(stringResource(R.string.onboarding_demon_title), stringResource(R.string.onboarding_demon_body))
        Preview(name = draft.demonName.trim(), variant = draft.variant)

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldLabel(stringResource(R.string.onboarding_name))
            OutlinedTextField(
                value = draft.demonName,
                onValueChange = viewModel::nameDemon,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.onboarding_name_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = LastHit.type.body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.lineStrong,
                    focusedPlaceholderColor = colors.text3,
                    unfocusedPlaceholderColor = colors.text3,
                    cursorColor = colors.text,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                ),
            )
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stringArrayResource(R.array.onboarding_name_ideas).forEach { idea ->
                    Chip(idea, selected = draft.demonName.trim() == idea, kind = ChipKind.Choice, onClick = { viewModel.nameDemon(idea) })
                }
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldLabel(stringResource(R.string.onboarding_creature))
            Tiles(Creature.entries, Modifier.selectableGroup()) { creature ->
                CreatureTile(creature, selected = creature == Creature.DRAGON, variant = draft.variant)
            }
            Text(
                stringResource(R.string.onboarding_creature_hint),
                style = LastHit.type.caption,
                color = colors.text2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldLabel(stringResource(R.string.onboarding_colour))
            val variants = remember { GameArt.sheet(Creature.DRAGON).variantNames }
            Tiles(variants, Modifier.selectableGroup()) { variant ->
                VariantSwatch(variant, selected = variant == draft.variant, onPick = { viewModel.pickVariant(variant) })
            }
        }
    }
}

/** The demon on the band backdrop, as Today will show it once the plan starts. */
@Composable
private fun Preview(name: String, variant: String) {
    val colors = LastHit.colors
    val kind = stringResource(
        R.string.onboarding_demon_kind,
        stringResource(variantName(variant)),
        stringResource(R.string.creature_dragon).lowercase(Locale.UK),
    )
    BandScene(Creature.DRAGON, variant, height = 168.dp, textStart = 172.dp, textTop = 30.dp, modifier = Modifier.padding(top = 24.dp)) {
        Column {
            if (name.isNotEmpty()) {
                Text(name, style = LastHit.type.headline.copy(fontSize = 30.sp, lineHeight = 34.sp), color = colors.stageText, maxLines = 1)
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

/** Items four to a row, each the same width. */
@Composable
private fun <T> Tiles(items: List<T>, modifier: Modifier = Modifier, tile: @Composable BoxScope.(T) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { tile(item) } }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BoxScope.CreatureTile(creature: Creature, selected: Boolean, variant: String) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    val name = stringResource(creatureName(creature))
    if (creature.isDrawn) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(shape)
                .background(if (selected) colors.stageSky else colors.bg)
                .border(if (selected) 2.dp else 1.5.dp, if (selected) colors.text else colors.line, shape)
                .selectable(selected, role = Role.RadioButton, onClick = {})
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            // 64 dp is twice the sprite's size, with room between its feet and the name.
            CreatureImage(creature, variant, Modifier.padding(bottom = 8.dp).size(64.dp))
            Text(name, style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold), color = if (selected) colors.stageText else colors.text)
        }
        if (selected) Tick()
    } else {
        val description = stringResource(R.string.onboarding_creature_locked, name)
        Column(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
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
            Text(name, style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.text3)
        }
    }
}

@Composable
private fun BoxScope.VariantSwatch(variant: String, selected: Boolean, onPick: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(shape)
            .background(colors.stageSky)
            .then(if (selected) Modifier.border(2.dp, colors.text, shape) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onPick)
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        CreatureImage(Creature.DRAGON, variant, Modifier.padding(bottom = 8.dp).size(64.dp))
        Text(stringResource(variantName(variant)), style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.stageText)
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
