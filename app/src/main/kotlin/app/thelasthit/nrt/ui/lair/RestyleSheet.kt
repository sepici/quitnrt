package app.thelasthit.nrt.ui.lair

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.ui.components.AppSheet
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.game.ColourPicker
import app.thelasthit.nrt.ui.game.CreaturePicker
import app.thelasthit.nrt.ui.onboarding.OnboardingDraft
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch

/**
 * Renames or restyles the demon (PLAN §15): its name, creature and colours, as onboarding picks
 * them. In the middle of a plan, [fighting] says so: the HP and hits stay as they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestyleSheet(
    name: String,
    creature: Creature,
    variant: String,
    fighting: Boolean,
    onSave: (name: String, creature: Creature, variant: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LastHit.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var newName by rememberSaveable { mutableStateOf(name) }
    // Only a drawn creature can be picked, so one that isn't any more starts as the dragon.
    var newCreature by rememberSaveable { mutableStateOf(creature.takeIf { it.isDrawn } ?: Creature.DRAGON) }
    var newVariant by rememberSaveable { mutableStateOf(variant) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    AppSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Header(fighting, onClose = ::close)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                val nameLabel = stringResource(R.string.onboarding_name)
                // The field carries the name, which stays once the placeholder has gone, so the label isn't read twice.
                FieldLabel(nameLabel, Modifier.clearAndSetSemantics {})
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(OnboardingDraft.MAX_NAME) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameLabel },
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
            }
            CreaturePicker(
                selected = newCreature,
                variant = newVariant,
                onPick = { newCreature = it },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
            )
            ColourPicker(
                newCreature,
                selected = newVariant,
                onPick = { newVariant = it },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            )
        }
        HorizontalDivider(color = colors.line)
        Box(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        ) {
            PrimaryButton(
                onClick = {
                    onSave(newName, newCreature, newVariant)
                    close()
                },
            ) { content ->
                Text(
                    stringResource(R.string.lair_restyle_save),
                    style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = content,
                )
            }
        }
    }
}

@Composable
private fun Header(fighting: Boolean, onClose: () -> Unit) {
    val titleStyle = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em)
    val titleLine = with(LocalDensity.current) { titleStyle.lineHeight.toDp() }
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.lair_restyle_title),
                style = titleStyle,
                color = LastHit.colors.text,
                modifier = Modifier.padding(end = 48.dp).semantics { heading() },
            )
            if (fighting) {
                Text(stringResource(R.string.lair_restyle_note), style = LastHit.type.label, color = LastHit.colors.text2)
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (titleLine - 48.dp) / 2)
                .size(48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = LastHit.colors.text)
        }
    }
}
