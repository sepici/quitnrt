package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.insights.CalendarHeat
import app.thelasthit.nrt.domain.insights.HeatScale
import app.thelasthit.nrt.domain.insights.HourHeatMap
import app.thelasthit.nrt.domain.time.DayBoundary
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * "When you use": doses by hour and day of the week, shaded by doses or mg, as a heat map or a
 * table of every hour with a dose. Patches are left out.
 */
@Composable
internal fun WhenYouUse(state: InsightsState) {
    val map = state.hours ?: return
    val window = state.window ?: return
    val timeFormat = rememberTimeFormatter()
    var asTable by rememberSaveable { mutableStateOf(false) }
    var shade by rememberSaveable { mutableStateOf(Shade.DOSES) }
    var selected by remember(state.range, state.type, shade, asTable) { mutableStateOf<HourSlot?>(null) }
    val timeText: (LocalTime) -> String = { timeFormat.format(it) }
    val noun = state.type?.let { typeNoun(it) }
    val attribute = state.type?.let { typeAttribute(it) }
    Section(
        title = if (noun == null) stringResource(R.string.insights_when_title) else stringResource(R.string.insights_when_title_type, noun),
        action = {
            TableButton(
                asTable,
                toTable = if (noun == null) stringResource(R.string.insights_when_table) else stringResource(R.string.insights_when_table_type, noun),
                toChart = if (noun == null) stringResource(R.string.insights_when_chart) else stringResource(R.string.insights_when_chart_type, noun),
            ) { asTable = !asTable }
        },
    ) {
        Meta(
            listOfNotNull(
                if (attribute == null) stringResource(R.string.insights_when_meta) else stringResource(R.string.insights_when_meta_type, attribute).sentence(),
                if (state.patchesLeftOut) stringResource(R.string.insights_patches_not_counted) else null,
            ).joinToString(" "),
        )
        val cellWhen: @Composable (HourHeatMap.Cell) -> String = { cell ->
            stringResource(R.string.insights_cell_when, dayName(map, cell.day), timeText(cell.from), timeText(cell.to))
        }
        if (asTable) {
            val rows = (0 until 7).flatMap { column ->
                (0 until HourHeatMap.ROWS).map { row -> map.cell(DayOfWeek.entries[column], row) }
            }.filter { it.doses > 0 }
            if (rows.isEmpty()) {
                Meta(stringResource(R.string.insights_table_none), top = 14.dp)
            } else {
                ChartTable(rows.map { cell -> cellWhen(cell) to cellValue(cell) }, Modifier.padding(top = 14.dp))
            }
        } else {
            ShadeToggle(
                shade,
                label = { stringResource(if (it == Shade.DOSES) R.string.insights_shade_doses else R.string.insights_shade_mg) },
                onPick = { shade = it },
                modifier = Modifier.padding(top = 14.dp),
            )
            HourMap(
                map = map,
                shade = shade,
                timeLabel = timeText,
                description = hourSummary(map, shade, timeText),
                selected = selected,
                onSelect = { selected = it },
                tip = { cell ->
                    ChartTip(
                        if (cell.doses == 0) stringResource(R.string.insights_tip_no_doses) else cellValue(cell),
                        listOfNotNull(
                            cellWhen(cell),
                            // A weekday with one day so far is named by its date, which says it all.
                            when {
                                map.daysOf(cell.day).size == 1 -> null
                                window.days == 1 -> stringResource(R.string.insights_today)
                                else -> stringResource(R.string.insights_over_the, pluralStringResource(R.plurals.insights_days, window.days, window.days))
                            },
                        ),
                    )
                },
                // Over what follows, so a tip that runs past the map is never hidden under it.
                modifier = Modifier
                    .padding(top = 14.dp)
                    .zIndex(1f),
            )
            val tops = when (shade) {
                Shade.DOSES -> HeatScale.stepTops(map.maxDoses.toLong()).map { it?.toString() }
                Shade.MG -> HeatScale.stepTops(map.maxTotal.micrograms).map { top -> top?.let { Dose(it).mgText() } }
            }
            if (map.cells.isNotEmpty()) {
                HourLegend(
                    none = stringResource(R.string.insights_heat_none),
                    tops = tops,
                    unit = stringResource(if (shade == Shade.DOSES) R.string.insights_heat_unit_doses else R.string.unit_mg),
                )
            }
            if (DayOfWeek.entries.any { map.daysOf(it).isEmpty() }) {
                Keys { Key({ NoDataKey() }, stringResource(R.string.insights_key_no_data)) }
            }
            val example = DayBoundary(state.dayStart).lateDoseExample
            Hint(
                if (example == null) {
                    stringResource(R.string.insights_when_hint_midnight)
                } else {
                    stringResource(R.string.insights_when_hint, timeFormat.format(state.dayStart), timeFormat.format(example))
                },
            )
        }
    }
}

/** "5 doses, 20 mg". */
@Composable
private fun cellValue(cell: HourHeatMap.Cell): String =
    stringResource(R.string.insights_tip_cell, pluralStringResource(R.plurals.insights_doses, cell.doses, cell.doses), cell.total.mgText())

/** What a screen reader hears for the hour map: its busiest hour, by what it's shaded by. */
@Composable
private fun hourSummary(map: HourHeatMap, shade: Shade, timeText: (LocalTime) -> String): String {
    val busiest = when (shade) {
        Shade.DOSES -> map.busiest
        Shade.MG -> map.cells.maxWithOrNull(compareBy<HourHeatMap.Cell> { it.total }.thenBy { it.doses })
    } ?: return stringResource(R.string.insights_when_summary_none)
    val first = when (shade) {
        Shade.DOSES -> stringResource(
            R.string.insights_when_summary_doses,
            dayName(map, busiest.day),
            timeText(busiest.from),
            pluralStringResource(R.plurals.insights_doses, busiest.doses, busiest.doses),
        )
        Shade.MG -> stringResource(R.string.insights_when_summary_mg, dayName(map, busiest.day), timeText(busiest.from), busiest.total.mgText())
    }
    return first + " " + stringResource(R.string.insights_summary_table_hours)
}

/**
 * The calendar: each day of the range shaded by mg, from the lightest finished day with doses to
 * the heaviest, as squares in weeks or a table of every day.
 */
@Composable
internal fun CalendarSection(state: InsightsState) {
    val calendar = state.calendar ?: return
    var asTable by rememberSaveable { mutableStateOf(false) }
    var selected by remember(state.range, state.type, asTable) { mutableStateOf<LocalDate?>(null) }
    val today = state.today
    val tracked = calendar.weeks.flatten().filter { it.state == CalendarHeat.State.TRACKED }
    val attribute = state.type?.let { typeAttribute(it) }
    val noun = state.type?.let { typeNoun(it) }
    Section(
        title = if (attribute == null) stringResource(R.string.insights_calendar_title) else stringResource(R.string.insights_calendar_title_type, attribute).sentence(),
        // Over the section after it, so a tip under the last row is never hidden.
        modifier = Modifier.zIndex(1f),
        action = {
            TableButton(
                asTable,
                toTable = if (attribute == null) {
                    stringResource(R.string.insights_calendar_table)
                } else {
                    stringResource(R.string.insights_calendar_table_type, attribute)
                },
                toChart = if (attribute == null) {
                    stringResource(R.string.insights_calendar_chart)
                } else {
                    stringResource(R.string.insights_calendar_chart_type, attribute)
                },
            ) { asTable = !asTable }
        },
    ) {
        Meta(
            when {
                noun == null -> stringResource(if (asTable) R.string.insights_calendar_meta_table else R.string.insights_calendar_meta)
                asTable -> stringResource(R.string.insights_calendar_meta_table_type, noun)
                else -> stringResource(R.string.insights_calendar_meta_type, noun)
            },
        )
        if (asTable) {
            ChartTable(tracked.reversed().map { day -> dayRow(day, today) }, Modifier.padding(top = 14.dp))
        } else {
            CalendarMap(
                calendar = calendar,
                description = calendarSummary(calendar),
                selected = selected,
                onSelect = { selected = it },
                tip = { day ->
                    val (label, value) = dayRow(day, today)
                    ChartTip(value, listOf(label))
                },
                // Over the legend and keys, so a tip that runs past the calendar is never hidden under them.
                modifier = Modifier
                    .padding(top = 14.dp)
                    .zIndex(1f),
            )
            val low = calendar.lowTotal
            val high = calendar.highTotal
            CalendarLegend(
                none = if (tracked.any { it.total == Dose.ZERO }) stringResource(R.string.insights_heat_none) else null,
                starts = HeatScale.stepStarts(low.micrograms, high.micrograms).map { Dose(it).mgText() },
                single = if (high > Dose.ZERO && high <= low) high.mgText() else null,
                unit = stringResource(R.string.unit_mg),
            )
            Keys {
                if (calendar.weeks.flatten().any { it.state == CalendarHeat.State.NO_DATA }) {
                    Key({ NoDataKey() }, stringResource(R.string.insights_key_no_data))
                }
                Key({ TodayRingKey() }, stringResource(R.string.insights_key_today_ring))
            }
        }
    }
}

/** A day's label and mg, as the table lists it and a tap shows it: today is "so far". */
@Composable
private fun dayRow(day: CalendarHeat.Day, today: LocalDate): Pair<String, String> =
    if (day.date == today) {
        stringResource(R.string.insights_today) to stringResource(R.string.insights_row_mg_so_far, day.total.mgText())
    } else {
        DayTitleFormatter.format(day.date) to stringResource(R.string.insights_row_mg, day.total.mgText())
    }

@Composable
private fun calendarSummary(calendar: CalendarHeat): String {
    val today = calendar.weeks.flatten().firstOrNull { it.date == calendar.today }
    return buildList {
        if (calendar.highTotal > Dose.ZERO) {
            add(stringResource(R.string.insights_calendar_summary, calendar.lowTotal.mgText(), calendar.highTotal.mgText()))
        } else {
            add(stringResource(R.string.insights_calendar_summary_plain))
        }
        today?.let { add(stringResource(R.string.insights_summary_today, it.total.mgText())) }
        add(stringResource(R.string.insights_summary_table))
    }.joinToString(" ")
}

/**
 * In place of the sections that leave patches out, when the window has nothing but patches: a
 * plain note under the chart, saying why they have nothing to show.
 */
@Composable
internal fun PatchNote() {
    Text(
        stringResource(R.string.insights_patch_note),
        style = LastHit.type.label,
        color = LastHit.colors.text2,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
    )
}

/** The table button in a section's header, which swaps the chart for its numbers and back. */
@Composable
internal fun TableButton(asTable: Boolean, toTable: String, toChart: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (asTable) R.drawable.ic_nav_insights else R.drawable.ic_table),
            contentDescription = if (asTable) toChart else toTable,
            tint = LastHit.colors.text,
        )
    }
}

/** The line under a section's title saying what it shows. */
@Composable
internal fun Meta(text: String, top: Dp = 2.dp) {
    Text(text, style = LastHit.type.label, color = LastHit.colors.text2, modifier = Modifier.padding(top = top))
}

/** A small note under a chart. */
@Composable
internal fun Hint(text: String) {
    Text(text, style = LastHit.type.caption, color = LastHit.colors.text2, modifier = Modifier.padding(top = 12.dp))
}

/**
 * The day an hour of the map falls on: its date when the window has only one of that weekday,
 * "Tuesday 20 October", or the weekday in the plural, "Tuesdays".
 */
@Composable
private fun dayName(map: HourHeatMap, day: DayOfWeek): String =
    map.daysOf(day).singleOrNull()?.let { DayTitleFormatter.format(it) } ?: daysName(day)

/** "Thursdays", for an hour that recurs each week. */
@Composable
internal fun daysName(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.insights_mondays
        DayOfWeek.TUESDAY -> R.string.insights_tuesdays
        DayOfWeek.WEDNESDAY -> R.string.insights_wednesdays
        DayOfWeek.THURSDAY -> R.string.insights_thursdays
        DayOfWeek.FRIDAY -> R.string.insights_fridays
        DayOfWeek.SATURDAY -> R.string.insights_saturdays
        DayOfWeek.SUNDAY -> R.string.insights_sundays
    },
)
