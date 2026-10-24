package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.domain.insights.CalendarHeat
import app.thelasthit.nrt.domain.insights.HeatScale
import app.thelasthit.nrt.domain.insights.HourHeatMap
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.LastHitColors
import app.thelasthit.nrt.ui.theme.heatFill
import app.thelasthit.nrt.ui.theme.heatInk
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.min

/** What the hour map shades by. */
enum class Shade { DOSES, MG }

/** A cell of the hour map, by column (Monday first) and row (an hour each, from the day start). */
data class HourSlot(val column: Int, val row: Int)

private val HourRow = 14.dp
private val HourGap = 2.dp
private val HourLabelMin = 42.dp
private val HeaderGap = 6.dp

/** Room between a mark and its tip for the tip's pointer, clear of a selection ring. */
private val TipGap = 10.dp

/** A row of the calendar, the full height of a touch target. */
private val CalendarRow = 48.dp
private val CalendarGapMin = 4.dp
private val CalendarCell = 40.dp

/** The step a cell takes on the ramp, shaded by doses or by mg. */
fun HourHeatMap.stepOf(cell: HourHeatMap.Cell, shade: Shade): Int = when (shade) {
    Shade.DOSES -> HeatScale.step(cell.doses.toLong(), maxDoses.toLong())
    Shade.MG -> HeatScale.step(cell.total.micrograms, maxTotal.micrograms)
}

/**
 * When doses happen: 24 hour rows from the day start down, by 7 day columns, each cell shaded on
 * the stone ramp. Rows are labelled every 4 by [timeLabel]. A weekday the window hasn't reached
 * yet is one dashed outline, as the calendar draws days before the first dose, not a run of
 * empty cells.
 *
 * Tapping a cell rings it and shows [tip] under it, or over it in the lower half; tapping it
 * again, or off the grid, lets it go. The cells are small for a finger, so a long press picks
 * the cell under it and sliding moves the pick, with the tip following. Screen readers get
 * [description] instead.
 */
@Composable
fun HourMap(
    map: HourHeatMap,
    shade: Shade,
    timeLabel: (LocalTime) -> String,
    description: String,
    selected: HourSlot?,
    onSelect: (HourSlot?) -> Unit,
    tip: @Composable (HourHeatMap.Cell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val dayStyle = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.text2)
    val hourStyle = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = colors.text3)
    val days = DayOfWeek.entries.map { it.getDisplayName(DayStyle.SHORT, Locale.UK) }
    val hours = (0 until HourHeatMap.ROWS step 4).map { timeLabel(map.startOf(it)) }
    val noDays = DayOfWeek.entries.map { map.daysOf(it).isEmpty() }
    val density = LocalDensity.current
    // Room for the widest hour at any font size, and never narrower than the mockups' 42 dp.
    val labelWidth = with(density) { maxOf(HourLabelMin.toPx(), hours.maxOf { measurer.measure(it, hourStyle).size.width } + 6.dp.toPx()) }
    val header = with(density) { measurer.measure(days.first(), dayStyle).size.height + HeaderGap.toPx() }
    val height = with(density) { (header + (HourRow.toPx() + HourGap.toPx()) * HourHeatMap.ROWS).toDp() }
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)

    fun DrawScope.cellWidth() = (size.width - labelWidth - HourGap.toPx() * 7) / 7
    fun DrawScope.cellLeft(column: Int) = labelWidth + HourGap.toPx() + column * (cellWidth() + HourGap.toPx())
    fun DrawScope.rowTop(row: Int) = header + HourGap.toPx() + row * (HourRow.toPx() + HourGap.toPx())

    /** The cell at [at], or null off the grid or on a weekday with no days. */
    fun PointerInputScope.slotAt(at: Offset): HourSlot? {
        val gap = HourGap.toPx()
        val width = (size.width - labelWidth - gap * 7) / 7
        val column = ((at.x - labelWidth - gap) / (width + gap)).toInt()
        val row = ((at.y - header - gap) / (HourRow.toPx() + gap)).toInt()
        val inside = at.x >= labelWidth && at.y >= header && column in 0..6 && row in 0 until HourHeatMap.ROWS
        return if (inside && !noDays[column]) HourSlot(column, row) else null
    }

    Layout(
        content = {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(labelWidth, header, noDays) {
                        detectTapGestures(
                            onLongPress = { at -> slotAt(at)?.let(select) },
                            onTap = { at ->
                                val slot = slotAt(at)
                                select(if (slot == currentSelected) null else slot)
                            },
                        )
                    }
                    .pointerInput(labelWidth, header, noDays) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { at -> slotAt(at)?.let(select) },
                            onDrag = { change, _ ->
                                change.consume()
                                slotAt(change.position)?.let { if (it != currentSelected) select(it) }
                            },
                        )
                    },
            ) {
                days.forEachIndexed { column, day ->
                    val layout = measurer.measure(day, dayStyle)
                    drawText(layout, topLeft = Offset(cellLeft(column) + (cellWidth() - layout.size.width) / 2, 0f))
                }
                hours.forEachIndexed { index, hour ->
                    val layout = measurer.measure(hour, hourStyle)
                    drawText(layout, topLeft = Offset(0f, rowTop(index * 4) - layout.size.height / 2f))
                }
                val radius = CornerRadius(3.dp.toPx())
                for (column in 0 until 7) {
                    if (noDays[column]) {
                        val top = rowTop(0)
                        drawDashed(Offset(cellLeft(column), top), Size(cellWidth(), rowTop(HourHeatMap.ROWS - 1) + HourRow.toPx() - top), 5.dp.toPx(), colors)
                        continue
                    }
                    for (row in 0 until HourHeatMap.ROWS) {
                        val cell = map.cell(DayOfWeek.entries[column], row)
                        drawRoundRect(
                            colors.heatFill(map.stepOf(cell, shade)),
                            topLeft = Offset(cellLeft(column), rowTop(row)),
                            size = Size(cellWidth(), HourRow.toPx()),
                            cornerRadius = radius,
                        )
                    }
                }
                selected?.let { slot ->
                    drawRing(Offset(cellLeft(slot.column), rowTop(slot.row)), Size(cellWidth(), HourRow.toPx()), 3.dp.toPx(), colors)
                }
            }
            if (selected != null) {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    tip(map.cell(DayOfWeek.entries[selected.column], selected.row))
                }
                TipPointer(PointerSide.UP)
                TipPointer(PointerSide.DOWN)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val total = height.roundToPx()
        val chart = measurables[0].measure(Constraints.fixed(width, total))
        val tipBox = measurables.getOrNull(1)?.measure(Constraints(maxWidth = width))
        val up = measurables.getOrNull(2)?.measure(Constraints())
        val down = measurables.getOrNull(3)?.measure(Constraints())
        val scope: Density = this
        layout(width, total) {
            chart.place(0, 0)
            if (tipBox != null && up != null && down != null && selected != null) {
                val gap = HourGap.toPx()
                val cell = (width - labelWidth - gap * 7) / 7
                val centre = labelWidth + gap + selected.column * (cell + gap) + cell / 2
                val top = header + gap + selected.row * (HourRow.toPx() + gap)
                val bottom = top + HourRow.toPx()
                val x = (centre - tipBox.width / 2).coerceIn(0f, (width - tipBox.width).toFloat().coerceAtLeast(0f)).toInt()
                val y = tipTop(top, bottom, tipBox.height, total, TipGap.toPx(), preferBelow = selected.row < HourHeatMap.ROWS / 2).toInt()
                tipBox.place(x, y)
                val below = y >= bottom
                val pointer = if (below) up else down
                pointer.place(pointerAt(if (below) PointerSide.UP else PointerSide.DOWN, IntRect(x, y, x + tipBox.width, y + tipBox.height), centre, pointer, scope))
            }
        }
    }
}

/**
 * Where a tip goes for a mark from [markTop] to [markBottom], in a chart [total] tall: under it
 * when [preferBelow] and there's room, over it when there's room there, and under it when only
 * that has room. With room for neither it goes under the mark, over what follows the chart.
 */
private fun tipTop(markTop: Float, markBottom: Float, tipHeight: Int, total: Int, gap: Float, preferBelow: Boolean): Float {
    val below = markBottom + gap
    val above = markTop - gap - tipHeight
    val fitsBelow = below + tipHeight <= total
    val fitsAbove = above >= 0
    return when {
        preferBelow && fitsBelow -> below
        fitsAbove -> above
        else -> below
    }
}

/**
 * The days of a range as Monday-to-Sunday weeks, each day a square shaded by its mg, with its
 * number in the corner and the month under the 1st. The squares run edge to edge of the content,
 * and each row is a full touch target tall. Days before the first dose are dashed outlines, and
 * today is ringed outside.
 *
 * Tapping a day picks it, with a ring inside the square in its number's colour, so it never looks
 * like today, and shows [tip] under its row, pointing at it. Tapping it again, or a day that isn't
 * tracked, lets it go. Screen readers get [description] instead.
 */
@Composable
fun CalendarMap(
    calendar: CalendarHeat,
    description: String,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
    tip: @Composable (CalendarHeat.Day) -> Unit,
    modifier: Modifier = Modifier,
    stepOf: (CalendarHeat.Day) -> Int = calendar::step,
) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val dayStyle = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.text2)
    val numberStyle = LastHit.type.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
    val monthStyle = numberStyle.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
    val names = DayOfWeek.entries.map { it.getDisplayName(DayStyle.SHORT, Locale.UK) }
    val density = LocalDensity.current
    val header = with(density) { measurer.measure(names.first(), dayStyle).size.height + 2.dp.toPx() }
    val height = with(density) { (header + CalendarRow.toPx() * calendar.weeks.size).toDp() }
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)

    /** The squares' side and the gap between them, for a calendar [width] wide. */
    fun Density.cells(width: Float): Pair<Float, Float> {
        val side = min(CalendarCell.toPx(), (width - CalendarGapMin.toPx() * 6) / 7)
        return side to (width - side * 7) / 6
    }

    Layout(
        content = {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(calendar, header) {
                        detectTapGestures { at ->
                            // Each day's touch area is its whole column and row, gaps included.
                            val (side, gap) = cells(size.width.toFloat())
                            val column = ((at.x + gap / 2) / (side + gap)).toInt().coerceIn(0, 6)
                            val row = ((at.y - header) / CalendarRow.toPx()).toInt()
                            val day = calendar.weeks.getOrNull(row)?.getOrNull(column)?.takeIf { at.y >= header }
                            val pick = day?.takeIf { it.state == CalendarHeat.State.TRACKED }?.date
                            select(if (pick == currentSelected) null else pick)
                        }
                    },
            ) {
                val (side, gap) = cells(size.width)
                fun origin(column: Int, row: Int) = Offset(column * (side + gap), header + row * CalendarRow.toPx() + (CalendarRow.toPx() - side) / 2)
                names.forEachIndexed { column, name ->
                    val layout = measurer.measure(name, dayStyle)
                    drawText(layout, topLeft = Offset(column * (side + gap) + (side - layout.size.width) / 2, 0f))
                }
                val radius = 8.dp.toPx()
                calendar.weeks.forEachIndexed { row, week ->
                    week.forEachIndexed { column, day ->
                        if (day.state == CalendarHeat.State.OUTSIDE) return@forEachIndexed
                        val origin = origin(column, row)
                        val step = stepOf(day)
                        val ink = if (day.state == CalendarHeat.State.NO_DATA) colors.text3 else colors.heatInk(step)
                        if (day.state == CalendarHeat.State.NO_DATA) {
                            drawDashed(origin, Size(side, side), radius, colors)
                        } else {
                            drawRoundRect(colors.heatFill(step), topLeft = origin, size = Size(side, side), cornerRadius = CornerRadius(radius))
                        }
                        val isToday = day.date == calendar.today
                        if (isToday) drawRing(origin, Size(side, side), radius, colors)
                        if (day.date == selected) {
                            val inset = 3.dp.toPx()
                            val stroke = 2.dp.toPx()
                            drawRoundRect(
                                ink,
                                topLeft = origin + Offset(inset, inset),
                                size = Size(side - inset * 2, side - inset * 2),
                                cornerRadius = CornerRadius(radius - inset),
                                style = Stroke(stroke),
                            )
                        }
                        val weight = if (isToday || day.date == selected) FontWeight.ExtraBold else FontWeight.SemiBold
                        val number = measurer.measure(day.date.dayOfMonth.toString(), numberStyle.copy(color = ink, fontWeight = weight))
                        val corner = origin + Offset(6.dp.toPx(), 4.dp.toPx())
                        drawText(number, topLeft = corner)
                        // The 1st names its month on a line of its own, so it fits the square at any font size.
                        if (day.date.dayOfMonth == 1) {
                            val month = measurer.measure(day.date.month.getDisplayName(DayStyle.SHORT, Locale.UK), monthStyle.copy(color = ink))
                            drawText(month, topLeft = corner + Offset(0f, number.size.height.toFloat()))
                        }
                    }
                }
            }
            if (selected != null) {
                calendar.weeks.flatten().firstOrNull { it.date == selected }?.let { day ->
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { tip(day) }
                    TipPointer(PointerSide.UP)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val total = height.roundToPx()
        val chart = measurables[0].measure(Constraints.fixed(width, total))
        val tipBox = measurables.getOrNull(1)?.measure(Constraints(maxWidth = width))
        val pointer = measurables.getOrNull(2)?.measure(Constraints())
        val scope: Density = this
        layout(width, total) {
            chart.place(0, 0)
            val row = calendar.weeks.indexOfFirst { week -> week.any { it.date == selected } }
            if (tipBox != null && pointer != null && row >= 0) {
                val column = calendar.weeks[row].indexOfFirst { it.date == selected }
                val (side, gap) = scope.cells(width.toFloat())
                val centre = column * (side + gap) + side / 2
                val bottom = header + row * CalendarRow.toPx() + (CalendarRow.toPx() + side) / 2
                // Always under the row, clear of the day names, and over the legend when it runs past the calendar.
                val x = (centre - tipBox.width / 2).coerceIn(0f, (width - tipBox.width).toFloat().coerceAtLeast(0f)).toInt()
                val y = (bottom + TipGap.toPx()).toInt()
                tipBox.place(x, y)
                pointer.place(pointerAt(PointerSide.UP, IntRect(x, y, x + tipBox.width, y + tipBox.height), centre, pointer, scope))
            }
        }
    }
}

/** A dashed outline, as for days with no data yet. */
private fun DrawScope.drawDashed(topLeft: Offset, size: Size, radius: Float, colors: LastHitColors) {
    val inset = 0.75.dp.toPx()
    drawRoundRect(
        colors.lineStrong,
        topLeft = topLeft + Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(radius - inset),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** A 2 dp ring in the text colour, held off the shape by a 2 dp gap in the page colour. */
private fun DrawScope.drawRing(topLeft: Offset, size: Size, radius: Float, colors: LastHitColors) {
    val gap = 2.dp.toPx()
    for ((colour, distance) in listOf(colors.bg to gap / 2, colors.text to gap * 1.5f)) {
        drawRoundRect(
            colour,
            topLeft = topLeft - Offset(distance, distance),
            size = Size(size.width + distance * 2, size.height + distance * 2),
            cornerRadius = CornerRadius(radius + distance),
            style = Stroke(gap),
        )
    }
}

/** Doses or mg, in a small sunk track, for what the hour map shades by. */
@Composable
fun ShadeToggle(shade: Shade, label: @Composable (Shade) -> String, onPick: (Shade) -> Unit, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.sunk)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        Shade.entries.forEach { option ->
            val isSelected = option == shade
            Box(
                Modifier
                    .widthIn(min = 80.dp)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) colors.chipSelected else colors.sunk)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onPick(option) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) colors.onChipSelected else colors.text2,
                    maxLines = 1,
                )
            }
        }
    }
}

private val SwatchHeight = 14.dp
private val SwatchMin = 28.dp
private val SwatchGap = 2.dp

/** One step's sample in a legend. */
@Composable
private fun Swatch(colour: Color, width: Dp) {
    Box(
        Modifier
            .size(width = width, height = SwatchHeight)
            .background(colour, RoundedCornerShape(3.dp)),
    )
}

private val legendStyle: TextStyle
    @Composable get() = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, color = LastHit.colors.text2)

/** Wide enough for every label at any font size, and never narrower than the mockups' 28 dp. */
@Composable
private fun swatchWidth(labels: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val style = legendStyle
    val density = LocalDensity.current
    val widest = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
    return with(density) { maxOf(SwatchMin, widest.toDp() + 6.dp) }
}

/** "None" with an empty swatch over it, at the left of a legend. */
@Composable
private fun NoneKey(text: String, width: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Swatch(LastHit.colors.sunk, width)
        Text(text, style = legendStyle)
    }
}

/**
 * The hour map's key: none, then the steps in use, each with the largest value on it underneath,
 * then the unit. [tops] has a label for each of the five steps, or null for one nothing reaches,
 * which is left out: with 4 doses at most, 1 to 4 sit on the darker four.
 */
@Composable
fun HourLegend(none: String, tops: List<String?>, unit: String, modifier: Modifier = Modifier) {
    val width = swatchWidth(tops.filterNotNull() + none)
    val colors = LastHit.colors
    Row(modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NoneKey(none, width)
        Row(horizontalArrangement = Arrangement.spacedBy(SwatchGap)) {
            tops.forEachIndexed { index, top ->
                if (top == null) return@forEachIndexed
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Swatch(colors.heatFill(index + 1), width)
                    Text(top, style = legendStyle)
                }
            }
        }
        Text(unit, style = legendStyle, modifier = Modifier.offset(x = (-8).dp))
    }
}

/**
 * The calendar's key: the five steps with where each one starts marked between them, then the
 * unit. [none] shows first when some tracked day had nothing. With every day the same, [single]
 * is that one value under the middle step.
 */
@Composable
fun CalendarLegend(none: String?, starts: List<String>, single: String?, unit: String, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val width = swatchWidth(starts + listOfNotNull(single, none))
    Row(modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (none != null) NoneKey(none, width)
        if (single != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Swatch(colors.heatFill(3), width)
                Text(single, style = legendStyle)
            }
        } else if (starts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SwatchGap)) {
                    (1..5).forEach { Swatch(colors.heatFill(it), width) }
                }
                BoundaryTicks(starts, width)
            }
        }
        if (single != null || starts.isNotEmpty()) {
            Text(unit, style = legendStyle, modifier = Modifier.offset(x = (-8).dp))
        }
    }
}

/** Labels centred on the gaps between the swatches, where each step starts. */
@Composable
private fun BoundaryTicks(starts: List<String>, swatch: Dp) {
    val style = legendStyle
    Layout(
        content = { starts.forEach { Text(it, style = style, maxLines = 1) } },
        modifier = Modifier.width(swatch * 5 + SwatchGap * 4),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val k = index + 1
                val centre = swatch.toPx() * k + SwatchGap.toPx() * (k - 1) + SwatchGap.toPx() / 2
                placeable.place((centre - placeable.width / 2f).toInt(), 0)
            }
        }
    }
}

/** The calendar key's sample for today: the ring alone, since today's square can take any shade. */
@Composable
fun TodayRingKey() {
    val colors = LastHit.colors
    Canvas(Modifier.size(20.dp)) {
        val inset = 4.dp.toPx()
        val side = size.width - inset * 2
        drawRing(Offset(inset, inset), Size(side, side), 3.dp.toPx(), colors)
    }
}

/** The key's sample for days before the first dose, on the calendar and the hour map: a dashed square. */
@Composable
fun NoDataKey() {
    val colors = LastHit.colors
    Canvas(Modifier.size(16.dp)) {
        val inset = 0.75.dp.toPx()
        drawRoundRect(
            colors.lineStrong,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(5.dp.toPx()),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
        )
    }
}
