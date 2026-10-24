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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The back button's place is kept on every step, so the bar doesn't jump between steps.
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.onboarding_back), tint = colors.text)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                // "Step 2 of 7" next to it says the same to screen readers.
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
        val stepDescription = stringResource(R.string.onboarding_step_description, step.ordinal + 1, total)
        Text(
            stringResource(R.string.onboarding_step_count, step.ordinal + 1, total),
            style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.text2,
            modifier = Modifier
                .padding(start = 8.dp, end = if (onSkip == null) 12.dp else 4.dp)
                .semantics { contentDescription = stepDescription },
        )
        if (onSkip != null) {
            Text(
                stringResource(R.string.onboarding_skip),
                // Underlined, as the app's other links are.
                style = LastHit.type.title.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, textDecoration = TextDecoration.Underline),
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
                style = LastHit.type.body.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
