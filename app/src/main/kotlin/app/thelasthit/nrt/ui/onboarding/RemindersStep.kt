package app.thelasthit.nrt.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.SetUpReminders
import app.thelasthit.nrt.reminders.NoticePoster
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * The reminders step (onboarding-6b-reminders, PLAN §15), between the day start and done: the
 * reminders on by default, each with a switch, and the phone's notification question.
 *
 * On Android 13 and newer, while notifications aren't allowed and a reminder is on, the button
 * asks, then finishes set-up whatever the answer. The question then counts as asked, so Settings
 * says notifications are off rather than inviting it. With nothing to ask (Android 12 and older,
 * notifications already allowed, or every switch off) the button just finishes. Skip finishes
 * with the switches as they are and asks nothing; it goes when there's never a question to skip.
 */
@Composable
internal fun RemindersStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val colors = LastHit.colors
    val context = LocalContext.current
    val timeFormat = rememberTimeFormatter()
    val chosen = state.draft.reminders
    val settings = state.reminders
    // Below Android 13 the permission counts as granted, so there's never a question there. Kept
    // from the step's start, so the footer doesn't change while set-up finishes after an answer.
    val canAsk = remember { !NoticePoster.hasPermission(context) }
    val asks = canAsk && SetUpReminders.needsNotifications(chosen)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.finish() }

    fun allowAndFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.markPermissionAsked()
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.finish()
        }
    }

    StepScaffold(
        step = OnboardingStep.REMINDERS,
        onBack = viewModel::back,
        onSkip = if (canAsk) viewModel::finish else null,
        footer = {
            if (asks) {
                NextButton(stringResource(R.string.onboarding_reminders_allow), ::allowAndFinish)
                // Balanced, so at a large font the second line isn't one word on its own.
                Text(
                    stringResource(R.string.onboarding_reminders_allow_note),
                    style = LastHit.type.caption.copy(lineBreak = LineBreak.Heading),
                    color = colors.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                NextButton(stringResource(R.string.onboarding_finish), viewModel::finish)
            }
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_reminders_title), stringResource(R.string.onboarding_reminders_body))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.line, shape),
            ) {
                SetUpReminders.offered.forEachIndexed { index, reminder ->
                    if (index > 0) HorizontalDivider(color = colors.line)
                    ReminderSwitch(
                        title = stringResource(titleOf(reminder)),
                        detail = detailOf(reminder),
                        checked = reminder in chosen,
                        onChange = { viewModel.switchReminder(reminder, it) },
                    )
                }
            }
            val quiet = settings.quietHours
            Text(
                if (quiet.enabled) {
                    stringResource(R.string.onboarding_reminders_hint_quiet, timeFormat.format(quiet.start), timeFormat.format(quiet.end))
                } else {
                    stringResource(R.string.onboarding_reminders_hint)
                },
                style = LastHit.type.caption,
                color = colors.text2,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** A switch row in the country step's card, with the Reminders page's switch. */
@Composable
private fun ReminderSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                checkedBorderColor = colors.chipSelected,
                // The card shows through, as in the mockup.
                uncheckedTrackColor = Color.Transparent,
                uncheckedThumbColor = colors.lineStrong,
                uncheckedBorderColor = colors.lineStrong,
            ),
        )
    }
}

/** The Reminders page's own name for [reminder], so set-up and Settings call it the same. */
@StringRes
private fun titleOf(reminder: Reminder): Int = when (reminder) {
    Reminder.ALLOWANCE -> R.string.reminders_allowance
    Reminder.CHECK_IN -> R.string.reminders_check_in
    Reminder.WEEKLY_RESULT -> R.string.reminders_results
    Reminder.ACHIEVEMENTS -> R.string.reminders_achievements
    Reminder.FORGOT_TO_LOG -> R.string.reminders_forgot
}

/** What [reminder] brings, in the Reminders page's own words, so it reads the same in both places. */
@Composable
private fun detailOf(reminder: Reminder): String = when (reminder) {
    Reminder.ALLOWANCE -> stringResource(R.string.reminders_allowance_detail)
    Reminder.CHECK_IN -> stringResource(R.string.reminders_check_in_detail)
    Reminder.WEEKLY_RESULT -> stringResource(R.string.reminders_results_detail)
    Reminder.ACHIEVEMENTS -> stringResource(R.string.reminders_achievements_detail)
    // Set-up doesn't offer it, since it goes by a usual first dose.
    Reminder.FORGOT_TO_LOG -> stringResource(R.string.reminders_forgot_detail)
}
