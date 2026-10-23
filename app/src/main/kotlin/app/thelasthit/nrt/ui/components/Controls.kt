package app.thelasthit.nrt.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.theme.LastHit

/** How a chip behaves for accessibility: one of a set, or on and off on its own. */
enum class ChipKind { Choice, Toggle, Action }

/** A rounded chip, as the log sheet uses for times and tags. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ChipKind = ChipKind.Toggle,
    @DrawableRes icon: Int? = null,
) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(24.dp)
    val interaction = when (kind) {
        ChipKind.Choice -> Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
        ChipKind.Toggle -> Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() })
        ChipKind.Action -> Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(
                if (kind == ChipKind.Action) {
                    Modifier.border(1.5.dp, colors.lineStrong, shape)
                } else {
                    Modifier.background(if (selected) colors.chipSelected else colors.sunk)
                },
            )
            .then(interaction)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val content = if (selected) colors.onChipSelected else colors.text
        val leading = if (selected && kind != ChipKind.Action) R.drawable.ic_check else icon
        if (leading != null) Icon(painterResource(leading), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = content,
        )
    }
}

/** A row of equal options in a sunk track, one of which can be chosen. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    gap: Int = 0,
    describe: (@Composable (T) -> String)? = null,
) {
    val colors = LastHit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.sunk)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val description = describe?.invoke(option)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isSelected) colors.chipSelected else colors.sunk)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onPick(option) })
                    .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (isSelected) colors.onChipSelected else colors.text2,
                )
            }
        }
    }
}

/** The one main action on a sheet or screen: a full-width pill with a pressed lower edge. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (contentColour: Color) -> Unit,
) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(
                if (enabled) {
                    Modifier.pressable(colors.primary, shape, edge = colors.primaryEdge, depth = 4.dp)
                } else {
                    Modifier.clip(shape).background(colors.disabledBg)
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(if (enabled) colors.onPrimary else colors.disabledText)
    }
}
