package app.thelasthit.nrt.ui.plan

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.ExtraGoals
import app.thelasthit.nrt.ui.components.RoundButton
import app.thelasthit.nrt.ui.format.plural
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.Duration

/**
 * The extra goals (PLAN §6.3), each off to start: most units a day for each product, a gap
 * between doses, and a first dose that moves 15 minutes later each week. They show in Insights,
 * and never change whether a week is met.
 */
@Composable
fun GoalsScreen(onBack: () -> Unit, viewModel: GoalsViewModel = graphViewModel { GoalsViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize().background(LastHit.colors.bg))
        return
    }
    val resources = LocalResources.current
    val goals = current.goals
    val weekCaption = if (current.running) {
        stringResource(R.string.plan_goal_this_week, current.week.number)
    } else {
        stringResource(R.string.plan_goal_from_week, current.week.number)
    }
    PlanPage(title = stringResource(R.string.plan_extra_goals), onBack = onBack) {
        PageIntro(stringResource(R.string.plan_goals_intro))
        Column(Modifier.padding(top = 16.dp)) {
            HorizontalDivider(color = LastHit.colors.line)
            Goal(
                icon = R.drawable.ic_plan_count,
                title = stringResource(R.string.plan_goal_caps),
                detail = stringResource(R.string.plan_goal_caps_detail),
                on = goals.maxUnitsPerDay.isNotEmpty(),
                onSwitch = viewModel::switchCaps,
            ) {
                current.week.target.mix.items.forEach { item ->
                    val cap = goals.maxUnitsPerDay[item.product.id] ?: return@forEach
                    CapRow(
                        title = resources.productTitle(item.product),
                        value = pluralStringResource(item.product.unit.plural, cap, cap),
                        unit = stringResource(unitWord(item.product.unit)),
                        canFewer = cap > 1,
                        onStep = { viewModel.stepCap(item.product.id, it) },
                    )
                }
                Hint(
                    stringResource(R.string.plan_goal_caps_hint, stringResource(R.string.plan_mix_a_day, resources.mixText(current.week.target.mix))),
                    Modifier.padding(top = 10.dp),
                )
            }
            HorizontalDivider(color = LastHit.colors.line)
            Goal(
                icon = R.drawable.ic_plan_gap,
                title = stringResource(R.string.plan_goal_gap),
                detail = stringResource(R.string.plan_goal_gap_detail),
                on = goals.minGapMinutes != null,
                onSwitch = viewModel::switchGap,
            ) {
                val gap = goals.minGapMinutes ?: return@Goal
                GoalStepper(
                    value = durationText(Duration.ofMinutes(gap.toLong())),
                    caption = weekCaption,
                    fewer = stringResource(R.string.plan_goal_gap_shorter),
                    more = stringResource(R.string.plan_goal_gap_longer),
                    canFewer = gap > GoalsViewModel.GAP_STEP,
                    canMore = gap < GoalsViewModel.MAX_GAP,
                    onStep = viewModel::stepGap,
                )
                current.habits.averageGap?.let { average ->
                    Hint(stringResource(R.string.plan_goal_gap_average, durationText(average)), Modifier.padding(top = 10.dp))
                }
            }
            HorizontalDivider(color = LastHit.colors.line)
            Goal(
                icon = R.drawable.ic_plan_sunrise,
                title = stringResource(R.string.plan_goal_first),
                detail = stringResource(R.string.plan_goal_first_detail),
                on = goals.firstDoseNotBefore != null,
                onSwitch = viewModel::switchFirstDose,
            ) {
                val time = goals.firstDoseNotBefore ?: return@Goal
                val format = rememberTimeFormatter()
                GoalStepper(
                    value = format.format(time),
                    caption = weekCaption,
                    fewer = stringResource(R.string.plan_goal_earlier),
                    more = stringResource(R.string.plan_goal_later),
                    canFewer = time.hour * 60 + time.minute > 0,
                    canMore = time < ExtraGoals.LATEST_FIRST_DOSE,
                    onStep = viewModel::stepFirstDose,
                )
                val next = current.next?.let { stringResource(R.string.plan_goal_next_week, format.format(ExtraGoals.later(time, 1))) }
                val average = current.habits.firstDose?.let { stringResource(R.string.plan_goal_first_average, format.format(it)) }
                listOfNotNull(next, average).joinToString(" ").takeIf { it.isNotEmpty() }?.let { Hint(it, Modifier.padding(top = 10.dp)) }
                Hint(stringResource(R.string.plan_goal_dependence), Modifier.padding(top = 10.dp))
            }
            HorizontalDivider(color = LastHit.colors.line)
        }
    }
}

/** One goal: the whole top row is its switch, and what it's set to opens under it while it's on. */
@Composable
private fun Goal(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    on: Boolean,
    onSwitch: (Boolean) -> Unit,
    more: @Composable ColumnScope.() -> Unit,
) {
    val colors = LastHit.colors
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(on, role = Role.Switch, onValueChange = onSwitch),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (on) colors.iconTile else colors.sunk),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = if (on) colors.onIconTile else colors.text3, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
            Switch(
                checked = on,
                onCheckedChange = null,
                modifier = Modifier.padding(top = 2.dp),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.chipSelected,
                    checkedThumbColor = colors.onChipSelected,
                    uncheckedTrackColor = colors.bg,
                    uncheckedThumbColor = colors.lineStrong,
                    uncheckedBorderColor = colors.lineStrong,
                ),
            )
        }
        if (on) Column(Modifier.padding(start = 54.dp, end = 8.dp, top = 14.dp), content = more)
    }
}

/** A value in the sunk box, with what it's for under it and a round button either side of nothing: 15 minutes at a time. */
@Composable
private fun GoalStepper(
    value: String,
    caption: String,
    fewer: String,
    more: String,
    canFewer: Boolean,
    canMore: Boolean,
    onStep: (Int) -> Unit,
) {
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
        // Read out again after each step, so the new value is heard without leaving the button.
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(value, style = LastHit.type.title.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em), color = colors.text)
            Text(caption, style = LastHit.type.caption, color = colors.text2)
        }
        RoundButton(R.drawable.ic_minus, fewer, canFewer) { onStep(-1) }
        Box(Modifier.size(8.dp))
        RoundButton(R.drawable.ic_plus, more, canMore) { onStep(1) }
    }
}

/** A product's cap: its name and most units a day, with a stepper. */
@Composable
private fun CapRow(title: String, value: String, unit: String, canFewer: Boolean, onStep: (Int) -> Unit) {
    val colors = LastHit.colors
    val description = stringResource(R.string.plan_units_a_day_description, title, value)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
            Text(stringResource(R.string.plan_a_day, value), style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
        }
        StepButton(R.drawable.ic_minus, stringResource(R.string.plan_one_fewer, unit), enabled = canFewer, onClick = { onStep(-1) })
        StepButton(R.drawable.ic_plus, stringResource(R.string.plan_one_more, unit), enabled = true, onClick = { onStep(1) })
    }
}

/** "90 min", "1 h 40 min". */
@Composable
private fun durationText(duration: Duration): String {
    val hours = duration.toHours().toInt()
    val minutes = (duration.toMinutes() % 60).toInt()
    return when {
        hours == 0 -> stringResource(R.string.plan_minutes, minutes)
        minutes == 0 -> stringResource(R.string.plan_hours, hours)
        else -> stringResource(R.string.plan_hours_minutes, hours, minutes)
    }
}
