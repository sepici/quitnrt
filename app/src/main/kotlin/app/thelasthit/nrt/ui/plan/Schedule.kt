package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.domain.programme.caughtUpIn
import app.thelasthit.nrt.domain.programme.hitsEarnedBy
import app.thelasthit.nrt.domain.programme.overtimeWeeks
import app.thelasthit.nrt.domain.programme.paidBackBy
import app.thelasthit.nrt.domain.programme.plannedWeeks
import app.thelasthit.nrt.ui.components.bleed
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.settings.TextLink
import app.thelasthit.nrt.ui.theme.LastHit

/** How many weeks the schedule shows in overtime before "Show all": the three before this one, and this one. */
private const val OVERTIME_ROWS = 4

/**
 * Every week of the plan, with its result: met in green, missed and caught up in neutral marks,
 * the hits each result brought as marigold chips, this week outlined, and the weeks to come
 * with their allowance. A taper week that hasn't started opens the week editor.
 */
@Composable
internal fun Schedule(state: PlanState.Running, onEdit: (Int) -> Unit) {
    val plan = state.plan
    var showAll by rememberSaveable { mutableStateOf(false) }
    val overtime = plan.overtimeWeeks > 0
    val focusIndex = state.focus?.let { plan.weeks.indexOf(it) } ?: -1
    val weeks = if (overtime && !showAll && focusIndex >= OVERTIME_ROWS - 1) {
        plan.weeks.subList(focusIndex - (OVERTIME_ROWS - 1), plan.weeks.size)
    } else {
        plan.weeks
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
        SectionTitle(stringResource(R.string.plan_schedule))
        val start = LongDate.format(plan.startDate)
        Meta(
            if (overtime) {
                stringResource(R.string.plan_schedule_overtime, plan.plannedWeeks, start)
            } else {
                stringResource(R.string.plan_schedule_span, plan.plannedWeeks, start, LongDate.format(plan.weeks.last().endDate))
            },
            Modifier.padding(top = 2.dp),
        )
        Column(Modifier.padding(top = 14.dp)) {
            weeks.forEachIndexed { index, week ->
                val isNow = week == state.focus && state.today <= week.endDate
                val previousNow = index > 0 && weeks[index - 1] == state.focus && state.today <= weeks[index - 1].endDate
                if (index > 0 && !isNow && !previousNow) HorizontalDivider(color = LastHit.colors.line)
                ScheduleRow(state, week, isNow, onEdit)
            }
        }
        if (overtime && weeks.size < plan.weeks.size) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                TextLink(stringResource(R.string.plan_show_all, plan.weeks.size), onClick = { showAll = true })
            }
        }
        val name = state.demonName ?: stringResource(R.string.demon_default_name)
        PlanKey(
            buildList {
                add(KeyItem.Met(stringResource(R.string.plan_key_met)))
                add(KeyItem.Missed(stringResource(R.string.plan_key_missed)))
                add(KeyItem.CaughtUp(stringResource(R.string.plan_key_caught_up)))
                if (weeks.any { it.result == WeekResult.PENDING && state.clock.hasEnded(it) }) add(KeyItem.Waiting(stringResource(R.string.plan_key_waiting)))
                add(KeyItem.Hit(stringResource(R.string.plan_key_hit, name)))
            },
            Modifier.padding(top = 16.dp),
        )
    }
}

/** One week of the schedule. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleRow(state: PlanState.Running, week: ProgrammeWeek, isNow: Boolean, onEdit: (Int) -> Unit) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val plan = state.plan
    val ended = state.clock.hasEnded(week)
    val editable = plan.editableSteps(week.number, state.today) != null
    val total = state.totalOf(week)
    val allowance = week.target.weeklyAllowance
    val mark = when {
        week.result == WeekResult.MET -> Mark.MET
        week.result == WeekResult.MISSED -> Mark.MISSED
        week.result == WeekResult.CAUGHT_UP -> Mark.CAUGHT_UP
        isNow -> Mark.NOW
        ended -> Mark.WAITING
        else -> Mark.LATER
    }
    val later = mark == Mark.LATER
    val title = when {
        week.kind != WeekKind.OVERTIME -> stringResource(R.string.plan_week_n, week.number)
        else -> {
            val nth = week.number - plan.plannedWeeks
            if (nth == 1) stringResource(R.string.plan_overtime) else stringResource(R.string.plan_overtime_nth, nth)
        }
    }
    val range = resources.shortRange(week.startDate, week.endDate)
    val dates = if (week.kind == WeekKind.REPEAT) stringResource(R.string.plan_repeating, range, week.number - 1) else range
    val amount = if (later) resources.mg(allowance) else stringResource(R.string.plan_amount_of, total.mgText(), allowance.mgText())
    val status = when (week.result) {
        WeekResult.MET -> {
            val paid = plan.paidBackBy(week.number)
            when {
                paid.isNotEmpty() -> stringResource(R.string.plan_status_paid_back, resources.weekRef(paid))
                week.isNrtFree -> stringResource(R.string.plan_status_met_free)
                else -> stringResource(R.string.plan_key_met)
            }
        }
        WeekResult.MISSED -> {
            val next = plan.weeks.firstOrNull { it.number > week.number && it.result == WeekResult.PENDING }
            when {
                week.isNrtFree -> stringResource(R.string.plan_status_missed_free)
                next != null && week.earnsHit -> stringResource(R.string.plan_status_missed_back, next.number)
                else -> stringResource(R.string.plan_key_missed)
            }
        }
        WeekResult.CAUGHT_UP -> plan.caughtUpIn(week.number)?.let { stringResource(R.string.plan_status_caught_up, it) }
            ?: stringResource(R.string.plan_key_caught_up)
        WeekResult.PENDING -> when {
            isNow -> stringResource(R.string.plan_status_now)
            ended -> stringResource(R.string.plan_status_waiting)
            week.isNrtFree -> stringResource(R.string.plan_nrt_free)
            else -> stringResource(R.string.plan_mg_a_day_value, week.target.dailyGuide.mgText())
        }
    }
    val mix = if (week.isNrtFree) null else mixText(week.target.mix)
    val hits = plan.hitsEarnedBy(week.number)
    val name = state.demonName ?: stringResource(R.string.demon_default_name)
    val description = buildString {
        append(title).append(", ").append(dates).append(": ")
        if (mix != null) append(mix).append(". ")
        append(amount).append(", ").append(status)
        if (hits > 0) append(". ").append(pluralStringResource(R.plurals.plan_hits_for, hits, hits, name))
        if (editable) append(". ").append(stringResource(R.string.plan_change_it))
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .then(
                if (isNow) {
                    Modifier
                        .padding(vertical = 4.dp)
                        .bleed(12.dp)
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.5.dp, colors.lineStrong, shape)
                } else {
                    Modifier
                },
            )
            .then(if (editable) Modifier.clickable(role = Role.Button) { onEdit(week.number) } else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (editable) role = Role.Button
            }
            .heightIn(min = 64.dp)
            .padding(horizontal = if (isNow) 12.dp else 0.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeekMark(mark)
            Column(Modifier.weight(1f)) {
                Text(title, style = LastHit.type.title.copy(fontWeight = if (later) FontWeight.SemiBold else FontWeight.Bold), color = colors.text)
                Text(dates, style = LastHit.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
            // At most half the row, so a long status wraps rather than squeezing the dates.
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.End) {
                // The hits sit before the amount, or above it when a large font leaves no room.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hits > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(hits) { HitChip() } }
                    }
                    Text(
                        amount,
                        style = LastHit.type.title.copy(fontWeight = if (later) FontWeight.SemiBold else FontWeight.ExtraBold),
                        color = colors.text,
                        textAlign = TextAlign.End,
                    )
                }
                Text(status, style = LastHit.type.caption.copy(fontWeight = FontWeight.Medium), color = colors.text2, textAlign = TextAlign.End)
            }
            if (editable) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (mix != null) {
            Text(
                mix,
                style = LastHit.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = colors.text,
                modifier = Modifier.padding(start = 40.dp, end = if (editable) 36.dp else 0.dp),
            )
        }
    }
}
