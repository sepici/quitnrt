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
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.game.ColourPicker
import app.thelasthit.nrt.ui.game.CreaturePicker
import app.thelasthit.nrt.ui.game.DemonPreview
import app.thelasthit.nrt.ui.theme.LastHit

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
        DemonPreview(name = draft.demonName.trim(), creature = draft.creature, variant = draft.variant, modifier = Modifier.padding(top = 24.dp))

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

        CreaturePicker(
            selected = draft.creature,
            variant = draft.variant,
            onPick = viewModel::pickCreature,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
        )
        ColourPicker(
            draft.creature,
            selected = draft.variant,
            onPick = viewModel::pickVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
        )
    }
}
