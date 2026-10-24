package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.StrengthSwitch
import app.thelasthit.nrt.domain.programme.stepsTaken
import app.thelasthit.nrt.domain.programme.switches
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.settings.TextLink
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.components.DialogButton
import app.thelasthit.nrt.ui.components.lastHitDatePickerColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Building the plan: its length and start day, the daily guide across every week, and a table of
 * the weeks with each one's mix, guide and allowance. A week a lower strength takes over has a
 * note above it. Any taper week can be changed before it starts, then the plan starts.
 */
@Composable
fun BuildScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    viewModel: BuildViewModel = graphViewModel { BuildViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize().background(LastHit.colors.bg))
        return
    }
    val resources = LocalResources.current
    val plan = current.plan
    PlanPage(
        title = stringResource(R.string.plan_build_title),
        onBack = onBack,
        bar = {
            Text(
                stringResource(R.string.plan_build_summary, plan.weeks.size, resources.getString(R.string.plan_range, LongDate.format(plan.startDate), LongDate.format(plan.weeks.last().endDate))),
                style = LastHit.type.label,
                color = LastHit.colors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            MainButton(stringResource(R.string.plan_start_plan), onClick = { viewModel.start(onStarted) })
        },
    ) {
        PageIntro(stringResource(R.string.plan_build_intro, resources.mixText(current.usual.mix), current.usual.total.mgText()))
        LengthField(current, viewModel::pickLength)
        StartField(plan.startDate, current.today, onChange = { picking = true })
        WeekByWeek(plan, current.today, onEdit = { editing = it })
    }

    editing?.let { number ->
        EditWeekSheet(
            plan = plan,
            number = number,
            today = current.today,
            onSave = { step ->
                viewModel.editWeek(number, step)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (picking) {
        StartPicker(
            start = plan.startDate,
            today = current.today,
            onPick = {
                viewModel.pickStart(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun LengthField(state: BuildState, onPick: (ProgrammeLength) -> Unit) {
    val colors = LastHit.colors
    val name = state.demonName ?: stringResource(R.string.demon_default_name)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
        FieldLabel(stringResource(R.string.plan_length))
        LengthPicker(state.length, onPick)
        Hint(stringResource(R.string.plan_length_hint, state.length.weeks, state.length.taperWeeks), Modifier.padding(top = 10.dp))
        val hp = state.plan.maxHp
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(colors.stageSky)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HpBlocks(hp / 10, hp / 10, Modifier.width(120.dp), height = 10.dp, gap = 2.dp, edge = 2.dp)
            Text(
                stringResource(R.string.plan_hp_strip, name, hp),
                style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold),
                color = colors.stageText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The four lengths in a sunk track, as B's segmented control. Each label keeps 10 dp clear either
 * side; when a large font leaves less, the track takes two rows of two.
 */
@Composable
private fun LengthPicker(selected: ProgrammeLength, onPick: (ProgrammeLength) -> Unit) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val names = ProgrammeLength.entries.map { stringResource(R.string.plan_weeks_option, it.weeks) }
    val widest = names.maxOf { measurer.measure(it, style).size.width }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val segment = (maxWidth - 8.dp) / names.size
        val perRow = if (with(LocalDensity.current) { widest.toDp() } + 20.dp <= segment) names.size else 2
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.sunk)
                .padding(4.dp)
                .selectableGroup(),
        ) {
            ProgrammeLength.entries.zip(names).chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (length, name) ->
                        val isSelected = length == selected
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (isSelected) colors.chipSelected else colors.sunk)
                                .selectable(isSelected, role = Role.RadioButton, onClick = { onPick(length) })
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(name, style = style, color = if (isSelected) colors.onChipSelected else colors.text2, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartField(start: LocalDate, today: LocalDate, onChange: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    val runs = stringResource(R.string.plan_runs_from_to, DayName.format(start), DayName.format(start.plusDays(6)))
    val detail = when (start) {
        today -> stringResource(R.string.plan_start_today, runs)
        today.plusDays(1) -> stringResource(R.string.plan_start_tomorrow, runs)
        else -> runs
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
        FieldLabel(stringResource(R.string.plan_start_day))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.line, shape)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.iconTile), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_plan_calendar), contentDescription = null, tint = colors.onIconTile, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(LongDate.format(start), style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
            TextLink(stringResource(R.string.plan_change), onClick = onChange, description = stringResource(R.string.plan_change_start))
        }
    }
}

/** The start day, from today on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartPicker(start: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val first = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= first
            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    val pickerColors = lastHitDatePickerColors()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogButton(stringResource(R.string.plan_use_start)) {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }
        },
        dismissButton = { DialogButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
        colors = pickerColors,
    ) {
        DatePicker(state = picker, showModeToggle = false, colors = pickerColors)
    }
}

/** The guide across the plan, then each week, with a note above each week a lower strength takes over. */
@Composable
private fun WeekByWeek(plan: Programme, today: LocalDate, onEdit: (Int) -> Unit) {
    val resources = LocalResources.current
    val switches = plan.switches()
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
        SectionTitle(stringResource(R.string.plan_week_by_week))
        Meta(stepsIntro(plan), Modifier.padding(top = 4.dp))
        val first = switches.firstOrNull()
        GuideChart(
            steps = plan.weeks.map { GuideStep(it.number, it.target.dailyGuide, it.isNrtFree) },
            usual = plan.usualDay.total,
            usualLabel = stringResource(R.string.plan_chart_usual, plan.usualDay.total.mgText()),
            switchWeek = first?.week,
            switchLabel = first?.let { stringResource(R.string.plan_chart_from_here, resources.kindText(it.product)) },
            weekLabel = stringResource(R.string.plan_chart_week),
            freeLabel = stringResource(R.string.plan_nrt_free),
            unit = stringResource(R.string.unit_mg),
            description = stringResource(
                R.string.plan_chart_description,
                plan.weeks.joinToString(", ") { it.target.dailyGuide.mgText() },
                plan.usualDay.total.mgText(),
            ),
            modifier = Modifier.padding(top = 16.dp),
        )
        Meta(stringResource(R.string.plan_guide_note), Modifier.padding(top = 14.dp))
        TableHeader()
        plan.weeks.forEach { week ->
            switches.filter { it.week == week.number }.forEach { SwitchNote(it, week) }
            WeekRow(week, editable = plan.editableSteps(week.number, today) != null, onEdit = onEdit)
        }
        Hint(stringResource(R.string.plan_tap_week), Modifier.padding(top = 14.dp))
    }
}

/** "Each step is one piece or spray fewer, or a lower strength. Weeks 1, 2 and 4 to 6 take two steps…" */
@Composable
private fun stepsIntro(plan: Programme): String {
    val resources = LocalResources.current
    val each = stringResource(R.string.plan_steps_each, resources.unitWordsText(plan.usualDay.mix))
    val taken = plan.stepsTaken().filterValues { it > 1 }
    if (taken.isEmpty()) return each
    val weeks = taken.keys.toList()
    val counts = taken.values.toSet()
    val more = when {
        counts.size > 1 && weeks.size == 1 -> stringResource(R.string.plan_steps_more_one, weeks.single())
        counts.size > 1 -> stringResource(R.string.plan_steps_more_many, resources.weeksText(weeks))
        counts.single() == 2 && weeks.size == 1 -> stringResource(R.string.plan_steps_two_one, weeks.single())
        counts.single() == 2 -> stringResource(R.string.plan_steps_two_many, resources.weeksText(weeks))
        weeks.size == 1 -> stringResource(R.string.plan_steps_n_one, weeks.single(), counts.single())
        else -> stringResource(R.string.plan_steps_n_many, resources.weeksText(weeks), counts.single())
    }
    return "$each $more"
}

@Composable
private fun TableHeader() {
    val colors = LastHit.colors
    val style = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    Column(Modifier.padding(top = 22.dp).clearAndSetSemantics {}) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.plan_table_week), style = style, color = colors.text2, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.plan_table_guide), style = style, color = colors.text2, textAlign = TextAlign.End, modifier = Modifier.width(ColumnWidth))
            Text(stringResource(R.string.plan_table_allowance), style = style, color = colors.text2, textAlign = TextAlign.End, modifier = Modifier.width(ColumnWidth))
            Box(Modifier.width(PenWidth))
        }
        HorizontalDivider(color = colors.line)
    }
}

/** The guide and allowance columns: the mockup's 76 dp, wider at a large font so "Allowance" fits. */
private val ColumnWidth: Dp
    @Composable get() = 76.dp * maxOf(1f, LocalDensity.current.fontScale)

private val PenWidth = 44.dp

/** A week of the plan: its number and start, guide and allowance, and its mix under them. The whole row opens the week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekRow(week: ProgrammeWeek, editable: Boolean, onEdit: (Int) -> Unit) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val from = stringResource(R.string.plan_from, ShortDate.format(week.startDate))
    val guide = resources.mg(week.target.dailyGuide)
    val allowance = resources.mg(week.target.weeklyAllowance)
    val mix = if (week.isNrtFree) null else resources.mixText(week.target.mix)
    val row = if (mix == null) {
        stringResource(R.string.plan_week_row_free, week.number, LongDate.format(week.startDate))
    } else {
        stringResource(R.string.plan_week_row, week.number, LongDate.format(week.startDate), mix, week.target.dailyGuide.mgText(), week.target.weeklyAllowance.mgText())
    }
    val description = if (editable) "$row. " + stringResource(R.string.plan_change_it) else "$row."
    val value = LastHit.type.title.copy(fontWeight = FontWeight.Bold)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (editable) Modifier.clickable(role = Role.Button) { onEdit(week.number) } else Modifier)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (editable) role = Role.Button
                }
                .heightIn(min = 64.dp)
                .padding(top = 10.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.plan_week_n, week.number), style = value, color = colors.text, maxLines = 1)
                        Text(from, style = LastHit.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.text2, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Text(guide, style = value, color = if (mix == null) colors.text2 else colors.text, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(ColumnWidth))
                    Text(
                        allowance,
                        style = value.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (mix == null) colors.text2 else colors.text,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.width(ColumnWidth),
                    )
                }
                if (mix != null) {
                    Text(mix, style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 21.sp), color = colors.text, modifier = Modifier.padding(top = 1.dp))
                } else {
                    Text(
                        stringResource(R.string.plan_nrt_free),
                        style = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.text2,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.sunk)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
            Box(Modifier.width(PenWidth), contentAlignment = Alignment.CenterEnd) {
                if (editable) Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = colors.line)
    }
}

/** A lower strength taking over: "2 mg gum from week 3, Saturday 7 November", and that it joins the user's products. */
@Composable
private fun SwitchNote(switch: StrengthSwitch, week: ProgrammeWeek) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val title = stringResource(R.string.plan_switch_title, resources.kindText(switch.product), week.number, LongDate.format(week.startDate))
    val text = if (switch.isNew) "$title " + stringResource(R.string.plan_switch_new, resources.productTitle(switch.product)) else title
    Row(
        Modifier
            .padding(vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_plan_steps), contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp))
        Text(boldIn(text, title), style = LastHit.type.label, color = colors.text)
    }
}
