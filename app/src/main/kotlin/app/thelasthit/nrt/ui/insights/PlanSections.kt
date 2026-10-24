package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.insights.CalendarHeat
import app.thelasthit.nrt.domain.insights.DaysWithin
import app.thelasthit.nrt.domain.insights.PlanGuides
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekReport
import app.thelasthit.nrt.ui.components.TickDot
import app.thelasthit.nrt.ui.components.drawBackArc
import app.thelasthit.nrt.ui.components.drawCheck
import app.thelasthit.nrt.ui.components.drawMinus
import app.thelasthit.nrt.ui.format.DayMonthFormatter
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.LocalDate

/** Past this many weeks, the marks under the columns are too small to hold their numbers, so only some are labelled. */
private const val LABEL_EVERY_WEEK = 13

/**
 * Week by week (PLAN §10): each week of the plan so far as a column of what was used, with its
 * allowance as a marker across it and how it went on a dot under it: green for met, the neutral
 * tint for missed then caught up, an outline for missed. The table lists the same.
 */
@Composable
internal fun PlanWeeksSection(state: InsightsState) {
    val weeks = state.planWeeks
    if (weeks.isEmpty()) return
    var asTable by rememberSaveable { mutableStateOf(false) }
    var selected by remember(asTable, weeks.size) { mutableStateOf<Int?>(null) }
    Section(
        title = stringResource(R.string.insights_weeks_title),
        action = {
            TableButton(
                asTable,
                toTable = stringResource(R.string.insights_weeks_table),
                toChart = stringResource(R.string.insights_weeks_chart),
            ) { asTable = !asTable }
        },
    ) {
        Meta(stringResource(R.string.insights_weeks_meta))
        if (asTable) {
            ChartTable(
                weeks.reversed().map { report ->
                    weekName(report) to stringResource(R.string.insights_weeks_row, weekValue(report), weekState(report))
                },
                Modifier.padding(top = 14.dp),
            )
        } else {
            val every = if (weeks.size <= LABEL_EVERY_WEEK) 1 else 4
            val columns = weeks.mapIndexed { index, report ->
                ChartColumn(
                    value = report.total,
                    partial = report.state == WeekReport.State.RUNNING,
                    label = if ((weeks.lastIndex - index) % every == 0) report.week.number.toString() else null,
                    marker = report.allowance,
                    mark = report.state.mark,
                )
            }
            MgColumns(
                columns = columns,
                line = null,
                description = weeks.map { stringResource(R.string.insights_weeks_summary_week, weekName(it), weekValue(it), weekState(it)) }
                    .plus(stringResource(R.string.insights_summary_table_weeks))
                    .joinToString(" "),
                selected = selected,
                onSelect = { selected = it },
                tip = { index ->
                    val report = weeks[index]
                    ChartTip(weekValue(report), listOf(weekDates(report), weekState(report).sentence()))
                },
                modifier = Modifier.padding(top = 18.dp),
            )
            Keys {
                Key(BarKey(), stringResource(R.string.insights_weeks_key_used))
                if (weeks.any { it.state == WeekReport.State.RUNNING }) Key(BarKey(outline = true), stringResource(R.string.insights_key_this_week))
                Key({ AllowanceKey() }, stringResource(R.string.insights_weeks_key_allowance))
                if (weeks.any { it.state == WeekReport.State.MET }) Key({ MarkKey(ColumnMark.MET) }, stringResource(R.string.insights_weeks_key_met))
                if (weeks.any { it.state == WeekReport.State.MISSED }) Key({ MarkKey(ColumnMark.MISSED) }, stringResource(R.string.insights_weeks_key_missed))
                if (weeks.any { it.state == WeekReport.State.CAUGHT_UP }) Key({ MarkKey(ColumnMark.CAUGHT_UP) }, stringResource(R.string.insights_weeks_key_caught_up))
            }
        }
    }
}

private val WeekReport.State.mark: ColumnMark
    get() = when (this) {
        WeekReport.State.RUNNING -> ColumnMark.RUNNING
        WeekReport.State.WAITING -> ColumnMark.WAITING
        WeekReport.State.MET -> ColumnMark.MET
        WeekReport.State.MISSED -> ColumnMark.MISSED
        WeekReport.State.CAUGHT_UP -> ColumnMark.CAUGHT_UP
    }

/** "Week 4", or "Overtime" for the NRT-free weeks after a missed final one. */
@Composable
private fun weekName(report: WeekReport): String =
    if (report.week.kind == WeekKind.OVERTIME) stringResource(R.string.insights_weeks_overtime) else stringResource(R.string.insights_weeks_name, report.week.number)

/** "128 of 133 mg". */
@Composable
private fun weekValue(report: WeekReport): String = stringResource(R.string.insights_weeks_value, report.total.mgText(), report.allowance.mgText())

/** "Week 2, 31 October to 6 November", with the month once when both ends share it. */
@Composable
private fun weekDates(report: WeekReport): String {
    val start = report.week.startDate
    val end = report.week.endDate
    val from = if (start.month == end.month) start.dayOfMonth.toString() else DayMonthFormatter.format(start)
    return stringResource(R.string.insights_weeks_dates, weekName(report), from, DayMonthFormatter.format(end))
}

/**
 * How the week went, in the Plan tab's words, to follow a comma: "met, paid back week 1", "caught
 * up in week 2". A tip starts with it, so gives it its capital.
 */
@Composable
private fun weekState(report: WeekReport): String = when (report.state) {
    WeekReport.State.RUNNING -> stringResource(R.string.insights_weeks_running)
    WeekReport.State.WAITING -> stringResource(R.string.insights_weeks_waiting)
    WeekReport.State.MISSED -> stringResource(R.string.insights_weeks_missed)
    WeekReport.State.CAUGHT_UP -> report.caughtUpBy?.let { stringResource(R.string.insights_weeks_caught_up_in, it) }
        ?: stringResource(R.string.insights_weeks_caught_up)
    WeekReport.State.MET -> when (report.paidBack.size) {
        0 -> stringResource(R.string.insights_weeks_met)
        1 -> stringResource(R.string.insights_weeks_met_paid_back, report.paidBack.single())
        else -> stringResource(R.string.insights_weeks_met_paid_back_weeks, report.paidBack.first(), report.paidBack.last())
    }
}

/** The key's sample for an allowance: the marker the chart draws across a column. */
@Composable
private fun AllowanceKey() {
    val guide = LastHit.colors.guide
    Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
        drawLine(guide, Offset(1.dp.toPx(), size.height / 2), Offset(size.width - 1.dp.toPx(), size.height / 2), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

/**
 * The key's sample for a week's mark: the dot the chart draws, with the Plan tab's sign for it in
 * place of the number, a tick, a minus or the arc back, as the Plan mockups' key has them.
 */
@Composable
private fun MarkKey(mark: ColumnMark) {
    val colors = LastHit.colors
    Canvas(Modifier.size(16.dp)) {
        val outline = 1.5.dp.toPx()
        val box = size.minDimension * 0.72f
        val stroke = box / 24f * 3f
        when (mark) {
            ColumnMark.MET -> {
                drawCircle(colors.success)
                drawCheck(center, box, colors.bg, stroke)
            }
            ColumnMark.CAUGHT_UP -> {
                drawCircle(colors.sunk)
                drawBackArc(center, box, colors.text, stroke * 0.85f)
            }
            else -> {
                drawCircle(colors.lineStrong, size.minDimension / 2 - outline / 2, style = Stroke(outline))
                drawMinus(center, box, colors.text2, stroke)
            }
        }
    }
}

/** Under the daily chart: "At or under the guide on 18 of 22 days.", with the guide's green tick. */
@Composable
internal fun DaysWithinLine(days: DaysWithin) {
    val colors = LastHit.colors
    Row(
        Modifier
            .padding(top = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TickDot(16.dp, fill = colors.success, ink = colors.bg)
        Text(daysWithinText(days), style = LastHit.type.label, color = colors.text)
    }
}

@Composable
internal fun daysWithinText(days: DaysWithin): String =
    pluralStringResource(R.plurals.insights_days_within, days.days, days.within, days.days)

// The calendar against the guide.

/** What the calendar says it's shaded by, once a plan runs. */
@Composable
internal fun guideCalendarMeta(guides: PlanGuides, calendar: CalendarHeat, asTable: Boolean): String {
    val tracked = calendar.weeks.flatten().filter { it.state == CalendarHeat.State.TRACKED }.map { it.date }
    val first = stringResource(if (asTable) R.string.insights_calendar_meta_guide_table else R.string.insights_calendar_meta_guide)
    return if (guides.hasDaysBefore(tracked)) {
        first + " " + stringResource(R.string.insights_calendar_meta_guide_before, guides.usualDay.mgText())
    } else {
        first
    }
}

/** A day's label and mg with what it's measured against, for the table and a tap. */
@Composable
internal fun guideDayRow(guides: PlanGuides, day: CalendarHeat.Day, today: LocalDate): Pair<String, String> {
    val label = if (day.date == today) stringResource(R.string.insights_today) else DayTitleFormatter.format(day.date)
    val guide = guides.guideOn(day.date)
    val value = when {
        guide != null && day.date == today -> stringResource(R.string.insights_row_mg_so_far_guide, day.total.mgText(), guide.mgText())
        guide != null -> stringResource(R.string.insights_row_mg_guide, day.total.mgText(), guide.mgText())
        day.date < guides.start -> stringResource(R.string.insights_row_mg_usual, day.total.mgText(), guides.usualDay.mgText())
        day.date == today -> stringResource(R.string.insights_row_mg_so_far, day.total.mgText())
        else -> stringResource(R.string.insights_row_mg, day.total.mgText())
    }
    return label to value
}

/** What a screen reader hears for the calendar against the guide. */
@Composable
internal fun guideCalendarSummary(calendar: CalendarHeat, daysWithin: DaysWithin?): String {
    val today = calendar.weeks.flatten().firstOrNull { it.date == calendar.today }
    return buildList {
        add(stringResource(R.string.insights_calendar_summary_guide))
        daysWithin?.let { add(daysWithinText(it)) }
        today?.let { add(stringResource(R.string.insights_summary_today, it.total.mgText())) }
        add(stringResource(R.string.insights_summary_table))
    }.joinToString(" ")
}
