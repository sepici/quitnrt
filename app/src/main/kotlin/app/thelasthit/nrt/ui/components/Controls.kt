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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
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
            style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = content,
        )
    }
}

/**
 * A row of equal options in a sunk track, one of which can be chosen. [numbers], like the craving
 * scale, are set a size up and heavier, as the mockups have them.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    gap: Int = 0,
    numbers: Boolean = false,
    describe: (@Composable (T) -> String)? = null,
) {
    val colors = LastHit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.sunk)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val description = describe?.invoke(option)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .trackOption(
                        selected = isSelected,
                        onClick = { onPick(option) },
                        inset = trackInset(index, options.size, gap = gap.dp),
                        fill = if (isSelected) colors.chipSelected else colors.sunk,
                    )
                    .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = LastHit.type.label.copy(
                        fontSize = if (numbers) 16.sp else 15.sp,
                        fontWeight = if (numbers) FontWeight.ExtraBold else FontWeight.Bold,
                    ),
                    color = if (isSelected) colors.onChipSelected else colors.text2,
                )
            }
        }
    }
}

/** How far a track's options are drawn in from its edge. */
val TRACK_INSET = 4.dp

/**
 * Where an option's pill is drawn inside its share of a sunk track: in from the track's outer
 * edges by [TRACK_INSET], and half of [gap] from the options either side.
 */
fun trackInset(index: Int, count: Int, gap: Dp = 0.dp, row: Int = 0, rows: Int = 1): PaddingValues = PaddingValues(
    start = if (index == 0) TRACK_INSET else gap / 2,
    end = if (index == count - 1) TRACK_INSET else gap / 2,
    top = if (row == 0) TRACK_INSET else 0.dp,
    bottom = if (row == rows - 1) TRACK_INSET else 0.dp,
)

/**
 * One option of a sunk track. The whole of its share of the track takes the tap, so it's at
 * least 48 dp tall, while its pill, and the ripple, are drawn [inset] as the mockups have them.
 * [onClick] null leaves it unpickable, as a locked range is.
 */
@Composable
fun Modifier.trackOption(
    selected: Boolean,
    onClick: (() -> Unit)?,
    inset: PaddingValues,
    fill: Color,
    shape: Shape = RoundedCornerShape(22.dp),
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .then(
            if (onClick != null) {
                Modifier.selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            } else {
                Modifier
            },
        )
        .padding(inset)
        .clip(shape)
        .background(fill)
        .indication(interaction, ripple())
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
