package app.thelasthit.nrt.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.theme.LastHit

// Form parts shared by onboarding, the sheets and Settings.

/** A small bold label above a field. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
        color = LastHit.colors.text2,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** A calm note: neutral, never green or marigold, as the colour rules ask. */
@Composable
fun NoteCard(title: String?, body: String, modifier: Modifier = Modifier, @DrawableRes icon: Int = R.drawable.ic_info) {
    val colors = LastHit.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.text2, modifier = Modifier.padding(top = 1.dp))
        Column {
            if (title != null) {
                Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
            }
            Text(body, style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal), color = colors.text)
        }
    }
}

/** One option in a list of radio rows, such as a country. */
data class RadioOption<T>(val value: T, val title: String, val detail: String?, val tag: String? = null)

@Composable
fun <T> RadioList(options: List<RadioOption<T>>, selected: T, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) HorizontalDivider(color = colors.line)
            val isSelected = option.value == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onPick(option.value) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(option.title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                        if (option.tag != null) {
                            Text(
                                option.tag,
                                style = LastHit.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = colors.text2,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.sunk)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (option.detail != null) {
                        Text(option.detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
                    }
                }
                RadioDot(isSelected)
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = LastHit.colors
    Box(
        Modifier
            .size(24.dp)
            .border(2.dp, if (selected) colors.chipSelected else colors.lineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(colors.chipSelected))
    }
}

/** A row with a switch, ruled above and below. */
@Composable
fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LastHit.colors
    Column {
        HorizontalDivider(color = colors.line)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .toggleable(checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.chipSelected,
                    checkedThumbColor = colors.onChipSelected,
                    uncheckedTrackColor = colors.bg,
                    uncheckedThumbColor = colors.lineStrong,
                    uncheckedBorderColor = colors.lineStrong,
                ),
            )
        }
        HorizontalDivider(color = colors.line)
    }
}
