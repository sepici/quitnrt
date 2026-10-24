package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.awaitingResult
import app.thelasthit.nrt.domain.programme.dayOf
import app.thelasthit.nrt.domain.programme.owed
import app.thelasthit.nrt.domain.programme.plannedWeeks
import app.thelasthit.nrt.domain.programme.unpaid
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.settings.TextLink
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * A plan running: this week against its allowance, the demon, the schedule and the extra goals.
 * The choice after a missed week comes up as a sheet when its result comes in.
 */
@Composable
internal fun RunningPage(state: PlanState.Running, viewModel: PlanViewModel, onGoals: () -> Unit, onOpenLair: () -> Unit) {
    val focus = state.focus
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var repeatFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val title = when {
        focus == null -> if (state.plan.startDate == state.today.plusDays(1)) {
            stringResource(R.string.plan_starts_tomorrow)
        } else {
            stringResource(R.string.plan_starts_on, LongDate.format(state.plan.startDate))
        }
        focus.kind == WeekKind.OVERTIME -> stringResource(R.string.plan_overtime)
        else -> stringResource(R.string.plan_week_of, focus.number, state.plan.plannedWeeks)
    }
    // A week running against its allowance, which is the only kind with extra goals.
    val taperWeek = focus?.takeIf { state.today <= it.endDate && !it.isNrtFree }
    TabPage(date = title) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            when {
                focus == null -> BeforeStart(state)
                taperWeek != null -> TaperWeek(state, taperWeek)
                state.today > focus.endDate -> AfterTheLast(state, focus)
                else -> FreeWeek(state, focus)
            }
            // The choice still open today stays with the week's figures, above the goals.
            state.canStillRepeat?.let { missed ->
                if (state.awaiting == null) {
                    Meta(stringResource(R.string.plan_carried_on, missed.number), Modifier.padding(top = 18.dp))
                    TextLink(stringResource(R.string.plan_repeat_instead, missed.number), onClick = { repeatFor = missed.number })
                }
            }
            taperWeek?.let { WeekGoalLines(state, it, Modifier.padding(top = 24.dp)) }
        }
        PlanBand(state.band, onOpenLair, Modifier.padding(top = 28.dp))
        Schedule(state, onEdit = { editing = it })
        state.goalsOn?.let { on ->
            PlanRows(Modifier.padding(top = 24.dp)) {
                PlanRow(
                    R.drawable.ic_plan_flag,
                    stringResource(R.string.plan_extra_goals),
                    if (on == 0) stringResource(R.string.plan_goals_all_off) else pluralStringResource(R.plurals.plan_goals_on, on, on),
                    first = true,
                    onClick = onGoals,
                )
            }
        }
    }

    editing?.let { number ->
        EditWeekSheet(
            plan = state.plan,
            number = number,
            today = state.today,
            onSave = { step ->
                viewModel.editWeek(number, step)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    val awaiting = state.awaiting
    val sheetWeek = awaiting ?: repeatFor?.let { number -> state.canStillRepeat?.takeIf { it.number == number } }
    if (sheetWeek != null) {
        MissedWeekSheet(
            plan = state.plan,
            missed = sheetWeek,
            total = state.totalOf(sheetWeek),
            hp = state.hp,
            demonName = state.demonName,
            clock = state.clock,
            initial = if (awaiting == null) MissedWeekChoice.REPEAT else MissedWeekChoice.CARRY_ON,
            onChoose = { choice ->
                viewModel.choose(sheetWeek.number, choice)
                repeatFor = null
            },
        )
    }
}

/** The week against its allowance: the meter, the mix, the day and the days as columns. */
@Composable
private fun TaperWeek(state: PlanState.Running, week: ProgrammeWeek) {
    val resources = LocalResources.current
    val used = state.totalOf(week)
    val allowance = week.target.weeklyAllowance
    val under = used <= allowance
    val left = if (under) {
        stringResource(R.string.plan_left, allowance.apartFrom(used).mgText())
    } else {
        stringResource(R.string.plan_over, used.apartFrom(allowance).mgText())
    }
    Fig(
        used.mgText(),
        stringResource(R.string.plan_of_this_week, allowance.mgText()),
        // As the meter says it, not "60 of 105", which sounds like progress towards a target.
        description = stringResource(R.string.today_plan_week_description, used.mgText(), allowance.mgText()),
    )
    Meter(
        used = used,
        allowance = allowance,
        left = left,
        isUnder = under,
        markLabel = stringResource(R.string.plan_allowance_mark, allowance.mgText()),
        description = stringResource(R.string.plan_meter_description, used.mgText(), allowance.mgText(), left),
    )
    val mix = stringResource(R.string.plan_mix_a_day, mixText(week.target.mix))
    Text(
        boldIn(stringResource(R.string.plan_mix_now, mix, week.target.dailyGuide.mgText()), mix),
        style = LastHit.type.body,
        color = LastHit.colors.text,
        modifier = Modifier.padding(top = 14.dp),
    )
    Meta(dayLine(state, week), Modifier.padding(top = 6.dp))
    noteFor(state, week)?.let { Meta(it, Modifier.padding(top = 6.dp)) }
    val columns = weekColumns(state, week)
    PlanColumns(
        columns,
        guide = week.target.dailyGuide,
        description = resources.columnsText(columns, week.target.dailyGuide),
        modifier = Modifier.padding(top = 40.dp),
    )
    PlanKey(
        listOf(
            KeyItem.Used(stringResource(R.string.plan_key_used)),
            KeyItem.Guide(stringResource(R.string.plan_key_guide, week.target.dailyGuide.mgText())),
            KeyItem.AtOrUnder(stringResource(R.string.plan_key_at_or_under)),
        ),
        Modifier.padding(top = 14.dp),
    )
}

/** An NRT-free week, planned or in overtime: each day's 0, and in overtime what meeting it pays back. */
@Composable
private fun FreeWeek(state: PlanState.Running, week: ProgrammeWeek) {
    val resources = LocalResources.current
    val used = state.totalOf(week)
    Fig(used.mgText(), stringResource(R.string.plan_mg_this_week))
    val planned = state.plan.weeks.filter { it.isNrtFree && it.kind != WeekKind.OVERTIME }.map { it.number }
    val day = dayLine(state, week)
    Meta(
        if (week.kind == WeekKind.OVERTIME) {
            stringResource(R.string.plan_overtime_meta, resources.weeksText(planned), day)
        } else {
            stringResource(R.string.plan_free_meta, day)
        },
        Modifier.padding(top = 4.dp),
    )
    if (week.kind != WeekKind.OVERTIME) noteFor(state, week)?.let { Meta(it, Modifier.padding(top = 6.dp)) }
    val columns = weekColumns(state, week)
    val anyUsed = columns.any { it.total > Dose.ZERO }
    if (anyUsed) {
        PlanColumns(columns, guide = null, description = resources.columnsText(columns), modifier = Modifier.padding(top = 40.dp))
    } else {
        ZeroColumns(columns, description = resources.columnsText(columns, Dose.ZERO), modifier = Modifier.padding(top = 24.dp))
    }
    PlanKey(
        buildList {
            if (anyUsed) add(KeyItem.Used(stringResource(R.string.plan_key_used)))
            add(KeyItem.AtOrUnder(stringResource(R.string.plan_key_zero)))
        },
        Modifier.padding(top = 14.dp),
    )
    if (week.kind == WeekKind.OVERTIME) {
        val went = state.plan.weeks.last { it.kind != WeekKind.OVERTIME }.number
        PlanNote(
            stringResource(R.string.plan_overtime_title, went),
            listOf(
                stringResource(R.string.plan_overtime_body, resources.weekRef(state.plan.unpaid)),
                stringResource(R.string.plan_overtime_safe),
            ),
            Modifier.padding(top = 20.dp),
        )
    }
}

/** Before week 1: its allowance and mix, and when it runs. */
@Composable
private fun BeforeStart(state: PlanState.Running) {
    val resources = LocalResources.current
    val week = state.plan.week(1)
    Fig(week.target.weeklyAllowance.mgText(), stringResource(R.string.plan_for_week_one))
    val mix = stringResource(R.string.plan_mix_a_day, mixText(week.target.mix))
    Text(
        boldIn(stringResource(R.string.plan_week_one_mix, mix, week.target.dailyGuide.mgText()), mix),
        style = LastHit.type.body,
        color = LastHit.colors.text,
        modifier = Modifier.padding(top = 14.dp),
    )
    Meta(stringResource(R.string.plan_runs_dates, resources.longRange(week.startDate, week.endDate)), Modifier.padding(top = 6.dp))
}

/** Every week is over, and the last result is still to come. */
@Composable
private fun AfterTheLast(state: PlanState.Running, week: ProgrammeWeek) {
    val resources = LocalResources.current
    val used = state.totalOf(week)
    Fig(used.mgText(), stringResource(R.string.plan_mg_in_week, week.number))
    Meta(stringResource(R.string.plan_last_waiting, week.number, LongDate.format(week.endDate)), Modifier.padding(top = 4.dp))
    val columns = weekColumns(state, week)
    PlanColumns(
        columns,
        guide = week.target.dailyGuide.takeIf { !week.isNrtFree },
        description = resources.columnsText(columns, week.target.dailyGuide),
        modifier = Modifier.padding(top = 40.dp),
    )
}

/** "Day 5 of 7, with today, Thursday and Friday left." */
@Composable
private fun dayLine(state: PlanState.Running, week: ProgrammeWeek): String {
    val resources = LocalResources.current
    val day = week.dayOf(state.today)
    val left = (day until 7).map { week.startDate.plusDays(it.toLong()) }
    return when {
        day >= 7 -> stringResource(R.string.plan_day_last)
        left.size <= 2 -> stringResource(
            R.string.plan_day_left,
            day,
            resources.listText(listOf(stringResource(R.string.plan_today)) + left.map { DayName.format(it) }),
        )
        else -> stringResource(R.string.plan_day_ending, day, LongDate.format(week.endDate))
    }
}

/** What else the week means: a result still to come, or missed weeks meeting it would pay back. */
@Composable
private fun noteFor(state: PlanState.Running, week: ProgrammeWeek): String? {
    val resources = LocalResources.current
    val waiting = state.plan.awaitingResult(state.clock)?.takeIf { it.number < week.number }
    if (waiting != null) return stringResource(R.string.plan_result_after_today, waiting.number)
    val owed = state.plan.owed.filter { it < week.number }
    return when {
        owed.isEmpty() -> null
        owed.size == 1 -> stringResource(R.string.plan_back_too_one, owed.single())
        else -> stringResource(R.string.plan_back_too_many, resources.weeksText(owed))
    }
}

private fun weekColumns(state: PlanState.Running, week: ProgrammeWeek): List<ColumnDay> = (0 until 7).map { offset ->
    val date = week.startDate.plusDays(offset.toLong())
    val dayState = when {
        date < state.today -> DayState.DONE
        date == state.today -> DayState.TODAY
        else -> DayState.TO_COME
    }
    ColumnDay(date, state.totalOn(date), dayState)
}
