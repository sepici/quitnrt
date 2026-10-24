package app.thelasthit.nrt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.theme.LastHit
import kotlin.math.roundToInt

// The day start setting, shared by onboarding and Settings.

@Composable
fun DayStartStepper(time: String, isDefault: Boolean, canEarlier: Boolean, canLater: Boolean, onMove: (Int) -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(time, style = LastHit.type.title.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold), color = colors.text)
            if (isDefault) Text(stringResource(R.string.onboarding_day_default), style = LastHit.type.caption, color = colors.text2)
        }
        RoundButton(R.drawable.ic_minus, stringResource(R.string.onboarding_day_earlier), canEarlier) { onMove(-1) }
        Box(Modifier.size(8.dp))
        RoundButton(R.drawable.ic_plus, stringResource(R.string.onboarding_day_later), canLater) { onMove(1) }
    }
}

/** A round stepper button. At the end of its range it's disabled, and the whole button fades, as the mockups' does. */
@Composable
fun RoundButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LastHit.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.surface,
        contentColor = colors.text,
        shadowElevation = if (enabled) 1.dp else 0.dp,
        modifier = Modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null) }
    }
}

/** How much of a disabled round button shows, from the mockups' opacity. */
private const val DISABLED_ALPHA = 0.38f

/**
 * Thursday as the app counts it: from the day start to the next one, with midnight marked and a
 * late dose that still counts toward Thursday.
 */
@Composable
fun DayLine(start: String, example: String, startSecond: Int, exampleSecond: Int) {
    val colors = LastHit.colors
    val day = 24 * 3600f
    val midnight = (day - startSecond) / day
    val dose = (day - startSecond + exampleSecond) / day
    val description = stringResource(R.string.onboarding_day_diagram, start, example)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.onboarding_day_caption)) }
                append(" ")
                append(stringResource(R.string.onboarding_day_caption_note))
            },
            style = LastHit.type.label,
            color = colors.text2,
        )
        Text(
            stringResource(R.string.onboarding_day_midnight),
            style = LastHit.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text2,
            modifier = Modifier.padding(top = 10.dp).centredAt(midnight),
        )
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val barTop = 8.dp.toPx()
            val barHeight = 12.dp.toPx()
            drawRoundRect(colors.track, Offset(0f, barTop), Size(size.width, barHeight), CornerRadius(barHeight / 2))
            val splitX = size.width * midnight
            drawRect(colors.lineStrong, Offset(splitX - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            val doseX = size.width * dose
            drawCircle(colors.surface, radius = 13.dp.toPx(), center = Offset(doseX, barTop + barHeight / 2))
            drawCircle(colors.used, radius = 10.dp.toPx(), center = Offset(doseX, barTop + barHeight / 2))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(stringResource(R.string.onboarding_day_start_thursday, start), style = LastHit.type.caption.copy(fontSize = 12.sp), color = colors.text2)
            Box(Modifier.weight(1f))
            Text(stringResource(R.string.onboarding_day_start_friday, start), style = LastHit.type.caption.copy(fontSize = 12.sp), color = colors.text2)
        }
        Text(
            stringResource(R.string.onboarding_day_example, example),
            style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
            color = colors.text2,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Places the element so its middle sits at [fraction] of the available width, kept in bounds. */
private fun Modifier.centredAt(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val width = constraints.maxWidth
    layout(width, placeable.height) {
        val x = (width * fraction - placeable.width / 2f).roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
        placeable.place(x, 0)
    }
}
