package app.thelasthit.nrt.ui.plan

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.GoalProgress
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.ui.components.TickDot
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * The week's extra goals (PLAN §6.3) under its columns, a short line each: "At most 5 pieces of
 * 2 mg gum a day: kept on 4 of 4 days so far". A day counts once it's over. A goal kept every
 * time so far gets the guide's green tick, as at or under; the rest stays neutral, since goals
 * never change whether the week is met.
 */
@Composable
internal fun WeekGoalLines(state: PlanState.Running, week: ProgrammeWeek, modifier: Modifier = Modifier) {
    if (week.goals.onCount == 0) return
    val progress = remember(week, state.entries, state.today, state.clock.boundary.dayStart) {
        GoalProgress.of(week, state.entries, state.today, state.clock.boundary.dayStart)
    }
    val resources = LocalResources.current
    val time = rememberTimeFormatter()
    Column(modifier.fillMaxWidth()) {
        Kicker(stringResource(R.string.goals_plan_title))
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            progress.counts.forEach { count ->
                val label = resources.goalLabel(count, time)
                val kept = resources.keptInLine(count)
                val text = when {
                    kept != null -> stringResource(R.string.goals_plan_line, label, kept)
                    count is GoalProgress.Gap && progress.days > 0 -> stringResource(R.string.goals_plan_no_gaps, label)
                    else -> stringResource(R.string.goals_plan_waiting, label)
                }
                GoalLine(count.icon, text, count.isAllKept)
            }
        }
    }
}

/** One goal's line: its icon from the goals page, the words, and a green tick when it's been kept every time. */
@Composable
private fun GoalLine(@DrawableRes icon: Int, text: String, allKept: Boolean) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.text2, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(text, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text, modifier = Modifier.weight(1f))
        if (allKept) TickDot(16.dp, fill = colors.success, ink = colors.bg, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The goals page's icon for a goal. */
@get:DrawableRes
internal val GoalProgress.Count.icon: Int
    get() = when (this) {
        is GoalProgress.Cap -> R.drawable.ic_plan_count
        is GoalProgress.Gap -> R.drawable.ic_plan_gap
        is GoalProgress.FirstDose -> R.drawable.ic_plan_sunrise
    }
