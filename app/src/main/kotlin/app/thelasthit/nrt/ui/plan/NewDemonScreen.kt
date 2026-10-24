package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.game.GameArt
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.onboarding.CreatureTile
import app.thelasthit.nrt.ui.onboarding.Preview
import app.thelasthit.nrt.ui.onboarding.Tiles
import app.thelasthit.nrt.ui.onboarding.VariantSwatch
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A new demon for the rematch: its name and colours, as onboarding picks them. */
data class NewDemonDraft(val name: String = "", val variant: String = DEFAULT_VARIANT) {
    val canStart: Boolean get() = name.isNotBlank()
}

class NewDemonViewModel(private val graph: DataGraph) : ViewModel() {
    private val _draft = MutableStateFlow(NewDemonDraft())
    val draft: StateFlow<NewDemonDraft> = _draft.asStateFlow()

    fun name(name: String) = _draft.update { it.copy(name = name.take(MAX_NAME)) }

    fun pickVariant(variant: String) = _draft.update { it.copy(variant = variant) }

    /** Set from the tap that starts the new programme, so a quick second tap does nothing while it saves. */
    private var starting = false

    /** Closes the programme done and starts a new one from current use, with this demon waiting in it. */
    fun start(then: () -> Unit) {
        val chosen = _draft.value
        if (!chosen.canStart || starting) return
        starting = true
        viewModelScope.launch {
            runCatching { graph.programmes.startNew() }
                .onSuccess {
                    graph.game.restyleDemon(chosen.name, Creature.DRAGON, chosen.variant)
                    then()
                }
                .onFailure { starting = false }
        }
    }

    private companion object {
        const val MAX_NAME = 24
    }
}

/** "A new demon": name it and pick its colours, then a new programme starts with it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewDemonScreen(onBack: () -> Unit, onStarted: () -> Unit, viewModel: NewDemonViewModel = graphViewModel { NewDemonViewModel(it) }) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val colors = LastHit.colors
    PlanPage(
        title = stringResource(R.string.plan_new_demon),
        onBack = onBack,
        footer = {
            MainButton(stringResource(R.string.plan_new_programme), onClick = { viewModel.start(onStarted) }, enabled = draft.canStart)
            FootNote(stringResource(R.string.plan_new_demon_note))
        },
    ) {
        PageIntro(stringResource(R.string.plan_new_demon_intro))
        Preview(name = draft.name.trim(), variant = draft.variant)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldLabel(stringResource(R.string.onboarding_name))
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::name,
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
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.onboarding_name_ideas).forEach { idea ->
                    Chip(idea, selected = draft.name.trim() == idea, kind = ChipKind.Choice, onClick = { viewModel.name(idea) })
                }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldLabel(stringResource(R.string.onboarding_creature))
            Tiles(Creature.entries, Modifier.selectableGroup()) { creature ->
                CreatureTile(creature, selected = creature == Creature.DRAGON, variant = draft.variant)
            }
            Text(stringResource(R.string.onboarding_creature_hint), style = LastHit.type.caption, color = colors.text2, modifier = Modifier.padding(top = 10.dp))
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
