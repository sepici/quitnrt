package app.thelasthit.nrt.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * One onboarding step: the progress bar, a scrolling body, and a footer pinned to the bottom
 * with the step's main button. [onBack] and [onSkip] show their buttons when given.
 */
@Composable
fun StepScaffold(
    step: OnboardingStep,
    onBack: (() -> Unit)?,
    footer: @Composable ColumnScope.() -> Unit,
    onSkip: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LastHit.colors.bg)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        StepsBar(step, onBack, onSkip)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            footer()
        }
    }
}

@Composable
private fun StepsBar(step: OnboardingStep, onBack: (() -> Unit)?, onSkip: (() -> Unit)?) {
    val colors = LastHit.colors
    val total = OnboardingStep.entries.size
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = if (onBack == null) 20.dp else 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.onboarding_back), tint = colors.text)
            }
        }
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = if (onBack == null) 0.dp else 6.dp)
                // The "2 of 7" next to it says the same to screen readers.
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OnboardingStep.entries.forEach { each ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (each.ordinal <= step.ordinal) colors.text else colors.track),
                )
            }
        }
        Text(
            stringResource(R.string.onboarding_step_count, step.ordinal + 1, total),
            style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.text2,
            modifier = Modifier.padding(start = 8.dp, end = if (onSkip == null) 12.dp else 4.dp),
        )
        if (onSkip != null) {
            Text(
                stringResource(R.string.onboarding_skip),
                style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
                color = colors.link,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
    }
}

/** The step's title and the line under it. */
@Composable
fun StepHeader(title: String, body: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Text(
            title,
            style = LastHit.type.headline.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.035).em),
            color = LastHit.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(
                body,
                style = LastHit.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                color = LastHit.colors.text2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** A small bold label above a field. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.ExtraBold),
        color = LastHit.colors.text2,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** The step's main button, with [label] naming what comes next. */
@Composable
fun NextButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    PrimaryButton(onClick = onClick, enabled = enabled) { content ->
        Text(
            label,
            style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
            color = content,
            textAlign = TextAlign.Center,
        )
    }
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
                Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold), color = colors.text)
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
                        Text(option.title, style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold), color = colors.text)
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
                Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold), color = colors.text)
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

/** A label on the left and a value on the right, for the summary at the end. */
@Composable
fun SummaryRow(label: String, value: String, first: Boolean) {
    val colors = LastHit.colors
    Column {
        if (!first) HorizontalDivider(color = colors.line)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(label, style = LastHit.type.body, color = colors.text2)
            Text(
                value,
                style = LastHit.type.body.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.text,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
