package app.thelasthit.nrt.ui.components

import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * A dialog's button, in the link colour. Material's own takes the primary colour, which in dark
 * mode is the hero pills' green and too dim for text on a dialog (3:1).
 */
@Composable
fun DialogButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = LastHit.colors.link)) {
        Text(text)
    }
}

/** A date picker in the app's colours, as the onboarding stop date has it, for a dialog or a page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun lastHitDatePickerColors(): DatePickerColors {
    val colors = LastHit.colors
    return DatePickerDefaults.colors(
        containerColor = colors.surface,
        titleContentColor = colors.text2,
        headlineContentColor = colors.text,
        selectedDayContainerColor = colors.chipSelected,
        selectedDayContentColor = colors.onChipSelected,
        todayDateBorderColor = colors.lineStrong,
        todayContentColor = colors.text,
        dayContentColor = colors.text,
        weekdayContentColor = colors.text2,
        navigationContentColor = colors.text,
        subheadContentColor = colors.text2,
        yearContentColor = colors.text,
        currentYearContentColor = colors.text,
        selectedYearContainerColor = colors.chipSelected,
        selectedYearContentColor = colors.onChipSelected,
    )
}

/**
 * Picks a time: Material's clock dial, or its typed fields at a large font or with a screen
 * reader's touch exploration on, where the dial's fixed boxes crowd and dragging round it is hard.
 * The dial's hour and minute are set in the size Material made their boxes for, not the app's
 * 112 sp hero figure, which its display style otherwise lends them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeChooser(state: TimePickerState) {
    if (prefersTypedTime()) {
        TimeInput(state)
    } else {
        val typography = MaterialTheme.typography
        MaterialTheme(typography = typography.copy(displayLarge = typography.displayLarge.copy(fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp))) {
            TimePicker(state)
        }
    }
}

/** Above this font scale, a time is typed rather than picked on the dial, whose boxes don't grow with the text. */
const val TYPED_TIME_FONT_SCALE = 1.15f

@Composable
private fun prefersTypedTime(): Boolean {
    val context = LocalContext.current
    val exploring = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    return exploring || LocalDensity.current.fontScale > TYPED_TIME_FONT_SCALE
}
