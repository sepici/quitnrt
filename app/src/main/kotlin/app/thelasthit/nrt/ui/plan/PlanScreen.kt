package app.thelasthit.nrt.ui.plan

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.BaselineDays
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.ui.format.averageText
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** How the usual day page opens: checking the one the logs measured, or typing one in. */
enum class UsualDayMode { MEASURED, TYPED }

/**
 * The Plan tab: the ways to start, the baseline as it builds, the usual day and the plan to
 * build, then the week against its allowance and the schedule, and the end of the programme.
 */
@Composable
fun PlanScreen(
    onUsualDay: (UsualDayMode) -> Unit,
    onBuild: () -> Unit,
    onGoals: () -> Unit,
    onOpenLair: () -> Unit,
    onNewDemon: () -> Unit,
    viewModel: PlanViewModel = graphViewModel { PlanViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        null -> Box(Modifier.fillMaxSize())
        is PlanState.Start -> StartPage(
            state = current,
            onUseLastSeven = { viewModel.useLastSevenDays { onUsualDay(UsualDayMode.MEASURED) } },
            onStartBaseline = viewModel::startBaseline,
            onTyped = { onUsualDay(UsualDayMode.TYPED) },
            onOpenLair = onOpenLair,
        )
        is PlanState.Building -> BuildingPage(current, onTyped = { onUsualDay(UsualDayMode.TYPED) }, onOpenLair = onOpenLair)
        is PlanState.Ready -> ReadyPage(current, onCheck = { onUsualDay(UsualDayMode.MEASURED) }, onTyped = { onUsualDay(UsualDayMode.TYPED) }, onOpenLair = onOpenLair)
        is PlanState.Kept -> KeptPage(
            current,
            onBuild = onBuild,
            onChange = { onUsualDay(if (current.measured) UsualDayMode.MEASURED else UsualDayMode.TYPED) },
            onOpenLair = onOpenLair,
        )
        is PlanState.Running -> RunningPage(current, viewModel, onGoals = onGoals, onOpenLair = onOpenLair)
        is PlanState.Done -> DonePage(current, onRematch = { viewModel.rematch() }, onNewDemon = onNewDemon, onOpenLair = onOpenLair)
    }
}

/** A Plan tab page: the top bar, then the page, scrolling. */
@Composable
internal fun TabPage(date: String?, bottom: androidx.compose.ui.unit.Dp = 24.dp, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LastHit.colors.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        PlanTopBar(date)
        content()
        Spacer(Modifier.height(bottom))
    }
}

// Before a programme.

@Composable
private fun StartPage(
    state: PlanState.Start,
    onUseLastSeven: () -> Unit,
    onStartBaseline: () -> Unit,
    onTyped: () -> Unit,
    onOpenLair: () -> Unit,
) {
    TabPage(date = null, bottom = 0.dp) {
        PageIntro(stringResource(R.string.plan_intro))
        val usual = state.usual
        if (usual != null) {
            LastSevenCard(state.lastSeven, usual, stringResource(R.string.plan_use_last_seven), onUseLastSeven)
        }
        RowsHeading(stringResource(if (usual != null) R.string.plan_other_ways else R.string.plan_two_ways))
        PlanRows(Modifier.padding(top = 8.dp)) {
            PlanRow(R.drawable.ic_plan_calendar, stringResource(R.string.plan_start_baseline), stringResource(R.string.plan_start_baseline_detail), first = true, onClick = onStartBaseline)
            PlanRow(R.drawable.ic_edit, stringResource(R.string.plan_type_usual), stringResource(R.string.plan_type_usual_detail), first = false, onClick = onTyped)
        }
        PlanBand(state.band, onOpenLair, Modifier.padding(top = 28.dp))
    }
}

@Composable
private fun ReadyPage(state: PlanState.Ready, onCheck: () -> Unit, onTyped: () -> Unit, onOpenLair: () -> Unit) {
    TabPage(date = null, bottom = 0.dp) {
        PageIntro(stringResource(R.string.plan_intro))
        LastSevenCard(state.days, state.usual, stringResource(R.string.plan_check_usual), onCheck)
        RowsHeading(stringResource(R.string.plan_other_ways))
        PlanRows(Modifier.padding(top = 8.dp)) {
            PlanRow(R.drawable.ic_edit, stringResource(R.string.plan_type_usual), stringResource(R.string.plan_type_usual_detail), first = true, onClick = onTyped)
        }
        PlanBand(state.band, onOpenLair, Modifier.padding(top = 28.dp))
    }
}

/** The last 7 days as a baseline, ready to use: the usual day they make, and the days themselves. */
@Composable
private fun LastSevenCard(days: BaselineDays, usual: UsualDay, button: String, onClick: () -> Unit) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Kicker(stringResource(R.string.plan_last_seven))
        Fig(usual.total.mgText(), stringResource(R.string.plan_mg_usual_day))
        MixLine(mixText(usual.mix))
        val withDoses = if (days.daysWithDoses == BaselineDays.DAYS) {
            stringResource(R.string.plan_all_days_doses)
        } else {
            stringResource(R.string.plan_days_with_doses, days.daysWithDoses)
        }
        Meta(
            stringResource(R.string.plan_rounded, resources.longRange(days.start, days.end), resources.unitsText(usual.mix)) + " " + withDoses,
            Modifier.padding(top = 6.dp),
        )
        PlanColumns(
            days.columns(today = null),
            guide = null,
            description = resources.columnsText(days.columns(today = null)),
            mini = true,
            labelBackground = colors.surface,
            modifier = Modifier.padding(top = 30.dp),
        )
        PlanKey(keyFor(days), Modifier.padding(top = 14.dp))
        MainButton(button, onClick, Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun keyFor(days: BaselineDays): List<KeyItem> = buildList {
    add(KeyItem.Used(stringResource(R.string.plan_key_used)))
    if (days.days.any { it.isOver && it.doses == 0 }) add(KeyItem.Nothing(stringResource(R.string.plan_key_nothing)))
}

@Composable
private fun RowsHeading(text: String) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
        color = LastHit.colors.text2,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
    )
}

/** The baseline building: the usual day so far, the days, and when it ends. */
@Composable
private fun BuildingPage(state: PlanState.Building, onTyped: () -> Unit, onOpenLair: () -> Unit) {
    val resources = LocalResources.current
    val days = state.days
    val day = (state.today.toEpochDay() - state.startedOn.toEpochDay()).toInt() + 1
    val runningOn = day > BaselineDays.DAYS
    TabPage(date = if (runningOn) stringResource(R.string.plan_baseline_running_on) else stringResource(R.string.plan_baseline_day, day), bottom = 0.dp) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Kicker(stringResource(R.string.plan_usual_so_far))
            val soFar = state.soFar
            val counted = days.days.filter { it.counts }
            if (soFar != null && counted.isNotEmpty()) {
                Fig(soFar.total.mgText(), stringResource(R.string.plan_mg_a_day))
                MixLine(mixText(soFar.mix))
                Meta(
                    stringResource(R.string.plan_so_far_meta, resources.dayNames(counted.first().date, counted.last().date), resources.unitsText(soFar.mix)),
                    Modifier.padding(top = 6.dp),
                )
            } else {
                Meta(stringResource(R.string.plan_first_day), Modifier.padding(top = 6.dp))
            }
            val columns = days.columns(today = state.today)
            PlanColumns(columns, guide = null, description = resources.columnsText(columns), modifier = Modifier.padding(top = 40.dp))
            val count = pluralStringResource(R.plurals.plan_full_days, days.daysWithDoses, days.daysWithDoses)
            Meta(
                boldIn(stringResource(R.string.plan_full_days_line, count), count),
                Modifier.padding(top = 18.dp),
            )
            PlanNote(stringResource(R.string.plan_log_normally), listOf(stringResource(R.string.plan_log_normally_body)), Modifier.padding(top = 20.dp))
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            SectionTitle(stringResource(R.string.plan_when_it_ends))
            val end = state.startedOn.plusDays(BaselineDays.DAYS - 1L)
            Prose(
                if (runningOn) {
                    stringResource(R.string.plan_runs_on_body)
                } else {
                    stringResource(R.string.plan_ends_body, LongDate.format(end), DayName.format(end.plusDays(1)))
                },
                Modifier.padding(top = 8.dp),
            )
            LineButton(stringResource(R.string.plan_type_instead), onTyped, Modifier.padding(top = 20.dp), icon = R.drawable.ic_edit)
        }
        PlanBand(state.band, onOpenLair, Modifier.padding(top = 28.dp))
    }
}

/** A usual day kept, and the plan still to build. */
@Composable
private fun KeptPage(state: PlanState.Kept, onBuild: () -> Unit, onChange: () -> Unit, onOpenLair: () -> Unit) {
    TabPage(date = null, bottom = 0.dp) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Kicker(stringResource(R.string.plan_usual_title))
            Fig(state.usual.total.mgText(), stringResource(R.string.plan_mg_a_day))
            MixLine(mixText(state.usual.mix))
            Meta(stringResource(R.string.plan_next_length), Modifier.padding(top = 6.dp))
            MainButton(stringResource(R.string.plan_build_button), onBuild, Modifier.padding(top = 20.dp))
        }
        PlanRows(Modifier.padding(top = 28.dp)) {
            PlanRow(R.drawable.ic_edit, stringResource(R.string.plan_change_usual), stringResource(R.string.plan_change_usual_detail), first = true, onClick = onChange)
        }
        PlanBand(state.band, onOpenLair, Modifier.padding(top = 28.dp))
    }
}

/** A baseline's seven days as columns. [today] is ringed, and days after it are still to come. */
internal fun BaselineDays.columns(today: LocalDate?): List<ColumnDay> = days.map { day ->
    val state = when {
        day.date == today -> DayState.TODAY
        !day.isOver -> DayState.TO_COME
        day.doses == 0 -> DayState.NOTHING
        else -> DayState.DONE
    }
    ColumnDay(day.date, day.total, state)
}

/** What a screen reader hears for a set of columns: "Friday 35 mg, Sunday nothing logged, Monday to come". */
internal fun Resources.columnsText(days: List<ColumnDay>, guide: Dose? = null): String {
    val parts = days.map { day ->
        val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)
        when (day.state) {
            DayState.NOTHING -> getString(R.string.plan_col_nothing, name)
            DayState.TO_COME -> getString(R.string.plan_col_to_come, name)
            DayState.TODAY -> getString(R.string.plan_col_so_far, name, day.total.mgText())
            DayState.DONE -> if (guide != null && day.total <= guide) {
                getString(R.string.plan_col_at_or_under, name, day.total.mgText())
            } else {
                getString(R.string.plan_col_day, name, day.total.mgText())
            }
        }
    }
    val list = parts.joinToString(", ")
    return if (guide == null) list else getString(R.string.plan_col_guide, guide.mgText(), list)
}

/** An average in mg a day: "35.7 mg a day". */
internal fun Resources.averageMg(dose: Dose): String = getString(R.string.plan_mg_a_day_value, dose.averageText())
