package app.thelasthit.nrt.ui.insights

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.insights.DailyIntake
import app.thelasthit.nrt.domain.insights.InsightRange
import app.thelasthit.nrt.domain.insights.ProductType
import app.thelasthit.nrt.domain.product.FormWord
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.IconTile
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.format.DayMonthFormatter
import app.thelasthit.nrt.ui.format.DayShortMonthFormatter
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.averageText
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.log.LogSheet
import app.thelasthit.nrt.ui.log.LogSheetViewModel
import app.thelasthit.nrt.ui.log.SavedDose
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/**
 * Insights: one row of filters, the range and the kind of product, over everything below it.
 * With nothing logged yet it says where the charts will come from, with a way to log.
 */
@Composable
fun InsightsScreen(
    snackbars: SnackbarHostState,
    viewModel: InsightsViewModel = graphViewModel { InsightsViewModel(it) },
    sheetViewModel: LogSheetViewModel = graphViewModel(key = "insights-sheet") { LogSheetViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    fun onSaved(saved: SavedDose) {
        sheetOpen = false
        scope.launch {
            val name = state?.productNames?.get(saved.entry.productId) ?: saved.entry.productName
            val result = snackbars.showSnackbar(
                message = resources.getString(R.string.today_logged, saved.entry.total.mgText(), name),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLog(saved.entry)
        }
    }

    val current = state
    when {
        current == null -> Box(Modifier.fillMaxSize())
        current.isEmpty -> EmptyInsights(onLog = {
            sheetViewModel.openNew()
            sheetOpen = true
        })
        else -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            TopBar()
            Filters(current, onRange = viewModel::pickRange, onType = viewModel::pickType)
            EachDay(current)
            if (current.onlyPatches) {
                PatchNote()
            } else {
                WhenYouUse(current)
                TriggersSection(current)
                HabitsSection(current)
            }
            CalendarSection(current)
            ByProductSection(current)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (sheetOpen) {
        LogSheet(viewModel = sheetViewModel, onDismiss = { sheetOpen = false }, onSaved = ::onSaved)
    }
}

@Composable
private fun TopBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.insights_title),
            style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
            color = LastHit.colors.text,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The kinds of product wrap onto a second line when they don't fit, so none is ever cut off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(state: InsightsState, onRange: (InsightRange) -> Unit, onType: (ProductType?) -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
        RangeButtons(state, onRange)
        state.nextLocked?.let { LockNote(it, state.today) }
        FlowRow(
            Modifier
                .padding(top = 12.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(stringResource(R.string.insights_all), selected = state.type == null, kind = ChipKind.Choice, onClick = { onType(null) })
            state.types.forEach { type ->
                Chip(typeName(type), selected = state.type == type, kind = ChipKind.Choice, onClick = { onType(type) })
            }
        }
    }
}

/**
 * The ranges in a sunk track. A locked one is dashed and says when it opens. Each label keeps
 * [RangePadding] clear either side; when a large font leaves less, the track takes two rows of two.
 */
@Composable
private fun RangeButtons(state: InsightsState, onPick: (InsightRange) -> Unit) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val names = state.ranges.map { rangeName(it.range) }
    val widest = names.maxOf { measurer.measure(it, style).size.width }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val segment = (maxWidth - 8.dp) / state.ranges.size
        val perRow = if (with(LocalDensity.current) { widest.toDp() } + RangePadding * 2 <= segment) state.ranges.size else 2
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.sunk)
                .padding(4.dp)
                .selectableGroup(),
        ) {
            state.ranges.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { option -> RangeButton(option, rangeName(option.range), option.range == state.range, state.today, onPick) }
                }
            }
        }
    }
}

private val RangePadding = 12.dp

@Composable
private fun RowScope.RangeButton(option: RangeOption, name: String, selected: Boolean, today: LocalDate, onPick: (InsightRange) -> Unit) {
    val colors = LastHit.colors
    val locked = if (option.unlocked) null else stringResource(R.string.insights_range_locked, name, opensText(option.opensOn, today))
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) colors.chipSelected else colors.sunk)
            .then(
                if (locked == null) {
                    Modifier.selectable(selected, role = Role.RadioButton, onClick = { onPick(option.range) })
                } else {
                    Modifier
                        .drawBehind {
                            val inset = Offset(3.dp.toPx(), 4.dp.toPx())
                            drawRoundRect(
                                colors.lineStrong,
                                topLeft = inset,
                                size = Size(size.width - inset.x * 2, size.height - inset.y * 2),
                                cornerRadius = CornerRadius(18.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        }
                        .semantics {
                            contentDescription = locked
                            disabled()
                        }
                },
            )
            .padding(horizontal = RangePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = if (locked == null) FontWeight.Bold else FontWeight.SemiBold),
            color = when {
                selected -> colors.onChipSelected
                locked == null -> colors.text2
                else -> colors.text3
            },
            maxLines = 1,
        )
    }
}

/** "90 days opens tomorrow, after 60 days of logging." */
@Composable
private fun LockNote(option: RangeOption, today: LocalDate) {
    val opens = option.opensOn ?: return
    val name = rangeName(option.range)
    val below = InsightRange.entries[option.range.ordinal - 1]
    val after = if (below == InsightRange.DAYS_7) {
        stringResource(R.string.insights_after_week)
    } else {
        pluralStringResource(R.plurals.insights_days, below.days, below.days)
    }
    Text(
        if (opens == today.plusDays(1)) {
            stringResource(R.string.insights_opens_tomorrow, name, after)
        } else {
            stringResource(R.string.insights_opens_on, name, DayMonthFormatter.format(opens), after)
        },
        style = LastHit.type.caption,
        color = LastHit.colors.text2,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp),
    )
}

/** Each day's mg, or each week's average a day for the longer ranges, as a chart or a table. */
@Composable
private fun EachDay(state: InsightsState) {
    val intake = state.intake ?: return
    val window = state.window ?: return
    val weekly = state.weekly
    var asTable by rememberSaveable(weekly) { mutableStateOf(false) }
    var selected by remember(state.range, state.type, asTable) { mutableStateOf<Int?>(null) }
    val noun = state.type?.let { typeNoun(it) }
    val toggle = if (noun == null) {
        stringResource(
            when {
                weekly && asTable -> R.string.insights_week_chart
                weekly -> R.string.insights_week_table
                asTable -> R.string.insights_day_chart
                else -> R.string.insights_day_table
            },
        )
    } else {
        stringResource(
            when {
                weekly && asTable -> R.string.insights_week_chart_type
                weekly -> R.string.insights_week_table_type
                asTable -> R.string.insights_day_chart_type
                else -> R.string.insights_day_table_type
            },
            noun,
        )
    }
    Section(
        title = if (noun == null) {
            stringResource(if (weekly) R.string.insights_each_week else R.string.insights_each_day)
        } else {
            stringResource(if (weekly) R.string.insights_each_week_type else R.string.insights_each_day_type, noun).sentence()
        },
        action = {
            IconButton(onClick = { asTable = !asTable }) {
                Icon(
                    painterResource(if (asTable) R.drawable.ic_nav_insights else R.drawable.ic_table),
                    contentDescription = toggle,
                    tint = LastHit.colors.text,
                )
            }
        },
    ) {
        Headline(state, intake)
        val days = intake.days
        val today = state.today
        if (weekly) {
            val weeks = intake.weeks
            val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (asTable) {
                ChartTable(
                    weeks.reversed().mapNotNull { week ->
                        week.averagePerDay?.let {
                            stringResource(R.string.insights_week_of, DayMonthFormatter.format(week.monday)) to
                                stringResource(R.string.insights_row_mg_a_day, it.averageText())
                        }
                    },
                    Modifier.padding(top = 14.dp),
                )
            } else {
                val every = when {
                    weeks.size <= 6 -> 1
                    weeks.size <= 10 -> 2
                    else -> 3
                }
                val columns = weeks.mapIndexed { index, week ->
                    ChartColumn(
                        value = week.averagePerDay,
                        partial = week.monday == thisMonday,
                        label = if ((weeks.lastIndex - index) % every == 0) DayShortMonthFormatter.format(week.monday) else null,
                    )
                }
                MgColumns(
                    columns = columns,
                    line = null,
                    description = summary(intake, window.days, weekly = true),
                    selected = selected,
                    onSelect = { index -> selected = index?.takeIf { columns[it].value != null } },
                    tip = { index ->
                        val week = weeks[index]
                        ChartTip(
                            stringResource(R.string.insights_tip_week, week.averagePerDay?.averageText().orEmpty()),
                            listOf(
                                stringResource(R.string.insights_week_of, DayMonthFormatter.format(week.monday)),
                                pluralStringResource(
                                    if (week.monday == thisMonday) R.plurals.insights_week_days_so_far else R.plurals.insights_week_days,
                                    week.days,
                                    week.days,
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.padding(top = 18.dp),
                )
                Keys {
                    Key(BarKey(), stringResource(R.string.insights_key_week))
                    if (weeks.any { it.monday == thisMonday && it.averagePerDay != null }) {
                        Key(BarKey(outline = true), stringResource(R.string.insights_key_this_week))
                    }
                    if (columns.any { !it.partial && it.value == Dose.ZERO }) Key({ ZeroTickKey() }, stringResource(R.string.insights_tip_no_doses))
                }
            }
        } else {
            if (asTable) {
                ChartTable(
                    days.reversed().map { day ->
                        if (day.date == today) {
                            stringResource(R.string.insights_today) to stringResource(R.string.insights_row_mg_so_far, day.total.mgText())
                        } else {
                            DayTitleFormatter.format(day.date) to stringResource(R.string.insights_row_mg, day.total.mgText())
                        }
                    },
                    Modifier.padding(top = 14.dp),
                )
            } else {
                val short = days.size <= 7
                val columns = days.map { day ->
                    ChartColumn(
                        value = day.total,
                        partial = day.date == today,
                        label = when {
                            short -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK)
                            day.date.dayOfWeek == DayOfWeek.MONDAY -> DayShortMonthFormatter.format(day.date)
                            else -> null
                        },
                        labelStrong = short && day.date == today,
                    )
                }
                val line = days.map { it.average }.takeIf { averages -> averages.any { it != null } }
                MgColumns(
                    columns = columns,
                    line = line,
                    description = summary(intake, window.days, weekly = false),
                    selected = selected,
                    onSelect = { selected = it },
                    tip = { index ->
                        val day = days[index]
                        val doses = pluralStringResource(R.plurals.insights_doses, day.doses, day.doses)
                        if (day.date == today) {
                            ChartTip(stringResource(R.string.insights_tip_today, day.total.mgText(), doses), listOf(stringResource(R.string.insights_today)))
                        } else {
                            ChartTip(
                                if (day.doses == 0) stringResource(R.string.insights_tip_no_doses) else stringResource(R.string.insights_tip_day, day.total.mgText(), doses),
                                listOfNotNull(
                                    DayTitleFormatter.format(day.date),
                                    day.average?.let { stringResource(R.string.insights_tip_average, it.averageText()) },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 18.dp),
                )
                Keys {
                    Key(BarKey(), stringResource(R.string.insights_key_day))
                    if (line != null) Key(LineKey(), stringResource(R.string.insights_key_average))
                    days.lastOrNull { it.date == today }?.let { Key(BarKey(outline = true), stringResource(R.string.insights_key_today, it.total.mgText())) }
                    if (columns.any { !it.partial && it.value == Dose.ZERO }) Key({ ZeroTickKey() }, stringResource(R.string.insights_tip_no_doses))
                }
                state.averageStarts?.let {
                    Text(
                        stringResource(R.string.insights_average_starts, DayMonthFormatter.format(it)),
                        style = LastHit.type.caption,
                        color = LastHit.colors.text2,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** The average a day as a big figure, the change against the period before, and what it's an average of. */
@Composable
private fun Headline(state: InsightsState, intake: DailyIntake) {
    val colors = LastHit.colors
    val average = intake.averagePerDay
    val today = intake.days.lastOrNull { it.date == state.today }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            average?.averageText() ?: (today?.total ?: Dose.ZERO).mgText(),
            style = LastHit.type.display.copy(lineHeight = 48.sp),
            color = colors.text,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            stringResource(if (average != null) R.string.insights_mg_a_day else R.string.insights_mg_so_far),
            style = LastHit.type.title.copy(fontSize = 17.sp, lineHeight = 24.sp),
            color = colors.text2,
            modifier = Modifier.alignByBaseline(),
        )
    }
    val previous = state.previousAverage
    if (average != null && previous != null) {
        val period = periodBefore(state.range)
        val change = average.micrograms - previous.micrograms
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Under 0.05 mg either way rounds to no change at one decimal place.
            if (abs(change) >= 50) ChangeArrow(if (change < 0) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up)
            Text(
                when {
                    abs(change) < 50 -> stringResource(R.string.insights_same_as, period)
                    change < 0 -> stringResource(R.string.insights_less_than, Dose(-change).mgText(), period)
                    else -> stringResource(R.string.insights_more_than, Dose(change).mgText(), period)
                },
                style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text2,
            )
        }
    }
    val full = intake.days.count { it.date != state.today }
    Text(
        if (average != null) pluralStringResource(R.plurals.insights_full_days, full, full) else stringResource(R.string.insights_no_full_days),
        style = LastHit.type.label,
        color = colors.text2,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** What a screen reader hears for the chart; the table has every number. */
@Composable
private fun summary(intake: DailyIntake, windowDays: Int, weekly: Boolean): String {
    val period = pluralStringResource(R.plurals.insights_days, windowDays, windowDays)
    val today = intake.days.lastOrNull { it.date == intake.today }
    val highest = intake.days.filter { it.date != intake.today && it.total > Dose.ZERO }.maxByOrNull { it.total }
    return buildList {
        if (weekly) {
            add(stringResource(R.string.insights_summary_weekly, period))
        } else {
            intake.averagePerDay?.let { add(stringResource(R.string.insights_summary_average, period, it.averageText())) }
            highest?.let { add(stringResource(R.string.insights_summary_highest, it.total.mgText(), DayTitleFormatter.format(it.date))) }
        }
        today?.let { add(stringResource(R.string.insights_summary_today, it.total.mgText())) }
        add(stringResource(if (weekly) R.string.insights_summary_table_weeks else R.string.insights_summary_table))
    }.joinToString(" ")
}

@Composable
internal fun Section(title: String, action: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 34.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
                color = LastHit.colors.text,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            // The button's touch area runs past the edge, so its icon lines up with the content.
            Box(Modifier.offset(x = 12.dp)) { action() }
        }
        content()
    }
}

/**
 * A change's arrow, or a dash for none, in the text colour. It's sized in sp so it grows with the
 * text beside it, and centred on that text's first [line], so it stays with it when the text wraps.
 */
@Composable
internal fun ChangeArrow(@DrawableRes icon: Int, line: TextUnit = 20.sp) {
    val density = LocalDensity.current
    val size = with(density) { 16.sp.toDp() }
    val top = (with(density) { line.toDp() } - size) / 2
    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = LastHit.colors.text,
        modifier = Modifier
            .padding(top = top)
            .size(size),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Keys(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** One key under a chart: a small sample of the mark, then what it means. */
@Composable
internal fun Key(sample: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.clipToBounds()) { sample() }
        Text(text, style = LastHit.type.caption, color = LastHit.colors.text2)
    }
}

private fun BarKey(outline: Boolean = false): @Composable () -> Unit = {
    val used = LastHit.colors.used
    Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
        val radius = CornerRadius(3.dp.toPx())
        if (outline) {
            val inset = 0.75.dp.toPx()
            drawRoundRect(used, Offset(inset, inset), Size(size.width - inset * 2, size.height), radius, style = Stroke(1.5.dp.toPx()))
        } else {
            drawRoundRect(used, Offset.Zero, Size(size.width, size.height + radius.x), radius)
        }
    }
}

private fun LineKey(): @Composable () -> Unit = {
    Box(
        Modifier
            .size(width = 18.dp, height = 2.dp)
            .background(LastHit.colors.text, RoundedCornerShape(1.dp)),
    )
}

@Composable
private fun EmptyInsights(onLog: () -> Unit) {
    val colors = LastHit.colors
    Column(Modifier.fillMaxSize()) {
        TopBar()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(R.drawable.ic_nav_insights, size = 64.dp)
            Text(
                stringResource(R.string.insights_empty_title),
                style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.insights_empty_body), style = LastHit.type.body, color = colors.text2, textAlign = TextAlign.Center)
            PrimaryButton(onClick = onLog, modifier = Modifier.padding(top = 12.dp)) { content ->
                Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.log_title), style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = content)
            }
        }
    }
}

@Composable
private fun rangeName(range: InsightRange): String = stringResource(
    when (range) {
        InsightRange.DAYS_7 -> R.string.insights_range_7
        InsightRange.DAYS_30 -> R.string.insights_range_30
        InsightRange.DAYS_60 -> R.string.insights_range_60
        InsightRange.DAYS_90 -> R.string.insights_range_90
    },
)

@Composable
private fun typeName(type: ProductType): String = stringResource(type.word?.text ?: R.string.form_other)

/** A kind of product as the thing used: "gum", "lozenges", for "When you use lozenges" and "Lozenges each day". */
@Composable
internal fun typeNoun(type: ProductType): String = stringResource(
    when (type.word) {
        FormWord.GUM -> R.string.insights_noun_gum
        FormWord.LOZENGE -> R.string.insights_noun_lozenge
        FormWord.MINI_LOZENGE -> R.string.insights_noun_mini_lozenge
        FormWord.PATCH -> R.string.insights_noun_patch
        FormWord.MOUTH_SPRAY -> R.string.insights_noun_mouth_spray
        FormWord.NASAL_SPRAY -> R.string.insights_noun_nasal_spray
        FormWord.INHALATOR -> R.string.insights_noun_inhalator
        FormWord.MICROTAB -> R.string.insights_noun_microtab
        FormWord.STRIP -> R.string.insights_noun_strip
        FormWord.POUCH -> R.string.insights_noun_pouch
        null -> R.string.insights_noun_other
    },
)

/** A kind of product before another word: "gum", "lozenge", for "Lozenge triggers" and "lozenge doses". */
@Composable
internal fun typeAttribute(type: ProductType): String = stringResource(
    when (type.word) {
        FormWord.GUM -> R.string.insights_attribute_gum
        FormWord.LOZENGE -> R.string.insights_attribute_lozenge
        FormWord.MINI_LOZENGE -> R.string.insights_attribute_mini_lozenge
        FormWord.PATCH -> R.string.insights_attribute_patch
        FormWord.MOUTH_SPRAY -> R.string.insights_attribute_mouth_spray
        FormWord.NASAL_SPRAY -> R.string.insights_attribute_nasal_spray
        FormWord.INHALATOR -> R.string.insights_attribute_inhalator
        FormWord.MICROTAB -> R.string.insights_attribute_microtab
        FormWord.STRIP -> R.string.insights_attribute_strip
        FormWord.POUCH -> R.string.insights_attribute_pouch
        null -> R.string.insights_attribute_other
    },
)

/** A title or line that starts with a kind of product, "gum calendar", given its capital: "Gum calendar". */
internal fun String.sentence(): String = replaceFirstChar { it.titlecase(Locale.UK) }

/** "1 November", with a no-break space so the day and month never wrap apart. */
internal fun keptTogether(date: LocalDate): String = DayMonthFormatter.format(date).replace(' ', ' ')

/** The period a change is against, for "the week before" or "the 30 days before". */
@Composable
internal fun periodBefore(range: InsightRange): String =
    if (range == InsightRange.DAYS_7) stringResource(R.string.insights_period_week) else pluralStringResource(R.plurals.insights_days, range.days, range.days)

@Composable
private fun opensText(opensOn: LocalDate?, today: LocalDate): String = when (opensOn) {
    null -> ""
    today.plusDays(1) -> stringResource(R.string.insights_tomorrow)
    else -> stringResource(R.string.insights_on_date, DayMonthFormatter.format(opensOn))
}
