package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.overtimeWeeks
import app.thelasthit.nrt.domain.programme.plannedWeeks
import app.thelasthit.nrt.ui.format.DayMonthFormatter
import app.thelasthit.nrt.ui.format.creatureName
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.theme.LastHit
import java.util.Locale

/**
 * The programme done: the demon at 0 HP, a summary of the run, and a rematch from current use,
 * with the same demon back or a new one. Until the last hits land, the demon still stands and
 * the rematch waits for it.
 */
@Composable
internal fun DonePage(state: PlanState.Done, onRematch: () -> Unit, onNewDemon: () -> Unit, onOpenLair: () -> Unit) {
    val resources = LocalResources.current
    val plan = state.plan
    val name = state.demonName ?: stringResource(R.string.demon_default_name)
    TabPage(date = null) {
        PlanBand(state.band, onOpenLair, Modifier.padding(top = 8.dp))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            SectionTitle(stringResource(R.string.plan_done_title))
            Meta(
                stringResource(R.string.plan_sentence, stringResource(R.string.plan_range, LongDate.format(plan.startDate), LongDate.format(plan.weeks.last().endDate))),
                Modifier.padding(top = 2.dp),
            )
            val weeks = if (plan.overtimeWeeks > 0) {
                stringResource(R.string.plan_sum_weeks_overtime, plan.plannedWeeks, plan.overtimeWeeks)
            } else {
                plan.plannedWeeks.toString()
            }
            val hits = when {
                !state.defeated -> stringResource(R.string.plan_sum_hits_earned, state.hitsEarned)
                state.defeatedOn == state.today -> stringResource(R.string.plan_sum_hits_today, state.hitsLanded)
                state.defeatedOn != null -> stringResource(R.string.plan_sum_hits_on, state.hitsLanded, DayMonthFormatter.format(state.defeatedOn))
                else -> state.hitsLanded.toString()
            }
            val triggers = state.triggers.map { tagText(it) }
            SummaryRows(
                buildList {
                    add(stringResource(R.string.plan_sum_weeks) to weeks)
                    add(stringResource(R.string.plan_sum_daily) to stringResource(R.string.plan_sum_daily_value, resources.mixText(plan.usualDay.mix)))
                    add(stringResource(R.string.plan_sum_hits, name) to hits)
                    if (triggers.isNotEmpty()) add(stringResource(R.string.plan_sum_triggers) to triggers.joinToString(", "))
                },
                Modifier.padding(top = 12.dp),
            )
            Prose(stringResource(R.string.plan_done_prose), Modifier.padding(top = 8.dp))
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            SectionTitle(stringResource(R.string.plan_new_programme))
            Prose(stringResource(R.string.plan_rematch_prose), Modifier.padding(top = 8.dp))
            if (!state.defeated) {
                Prose(stringResource(R.string.plan_finish_first, name, state.hp), Modifier.padding(top = 10.dp))
                LineButton(stringResource(R.string.plan_open_lair), onOpenLair, Modifier.padding(top = 20.dp))
            }
        }
        if (state.defeated) {
            PlanRows(Modifier.padding(top = 16.dp)) {
                PlanRow(
                    R.drawable.ic_plan_repeat,
                    stringResource(R.string.plan_round_two, name),
                    stringResource(R.string.plan_round_two_detail, stringResource(creatureName(state.creature)).lowercase(Locale.UK)),
                    first = true,
                    onClick = onRematch,
                )
                PlanRow(R.drawable.ic_plus, stringResource(R.string.plan_new_demon), stringResource(R.string.plan_new_demon_detail), first = false, onClick = onNewDemon)
            }
            Text(
                stringResource(R.string.plan_carry_over),
                style = LastHit.type.caption,
                color = LastHit.colors.text2,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
        }
    }
}
