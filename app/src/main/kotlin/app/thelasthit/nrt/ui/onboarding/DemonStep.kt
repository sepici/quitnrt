package app.thelasthit.nrt.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
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
import app.thelasthit.nrt.ui.game.CreatureTiles
import app.thelasthit.nrt.ui.game.VariantTiles
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
        Preview(name = draft.demonName.trim(), creature = draft.creature, variant = draft.variant)

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
            CreatureTiles(selected = draft.creature, variant = draft.variant, onPick = viewModel::pickCreature)
            if (Creature.someUndrawn) {
                Text(
                    stringResource(R.string.onboarding_creature_hint),
                    style = LastHit.type.caption,
                    color = colors.text2,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldLabel(stringResource(R.string.onboarding_colour))
            VariantTiles(draft.creature, selected = draft.variant, onPick = viewModel::pickVariant)
        }
    }
}

/** The demon on the band backdrop, as Today will show it once the plan starts. */
@Composable
private fun Preview(name: String, creature: Creature, variant: String) {
    val colors = LastHit.colors
    val kind = stringResource(
        R.string.onboarding_demon_kind,
        stringResource(variantName(variant)),
        stringResource(creatureName(creature)).lowercase(Locale.UK),
    )
    BandScene(creature, variant, height = 168.dp, textStart = 172.dp, textTop = 30.dp, modifier = Modifier.padding(top = 24.dp)) {
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
