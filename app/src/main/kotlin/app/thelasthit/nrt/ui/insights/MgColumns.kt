package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.LastHitColors
import kotlin.math.ceil
import kotlin.math.min

/**
 * One column: a day's total or a week's average a day. A [partial] one, still under way, is an
 * outline. A programme week can have its allowance as a [marker] across the column, never a fill,
 * and how it went as a [mark] under it, with its [label] inside the mark when it fits.
 */
data class ChartColumn(
    val value: Dose?,
    val partial: Boolean = false,
    val label: String? = null,
    val labelStrong: Boolean = false,
    val marker: Dose? = null,
    val mark: ColumnMark? = null,
)

/**
 * How a programme week went, on a dot under its column, as Today's week marks its days: green
 * only for a week met, a missed one plain, and one caught up later on the neutral tint.
 */
enum class ColumnMark { MET, MISSED, CAUGHT_UP, RUNNING, WAITING }

private val ChartHeight = 172.dp
private val PlotTop = 10.dp
private val Baseline = 142.dp
private val AxisWidth = 34.dp
private val TickGap = 8.dp
private val LabelTop = 150.dp
private val MaxBar = 24.dp

/** With marks under the columns: room for them, and for labels under the marks too small to hold them. */
private val MarkTop = 150.dp
private val MarkMax = 24.dp
private val MarkedHeight = 196.dp

/**
 * Stone columns of mg on a zero baseline, with an optional [line] over them: thin columns with
 * rounded tops, a hairline grid, and anything partial as an outline. [line] has a value or a gap
 * for each column. Tapping a column selects it and shows [tip] beside it; tapping it again, or
 * outside the columns, lets it go. Screen readers get [description] instead.
 *
 * A plan's daily [guide], a value or a gap for each column, is a dashed line that steps with the
 * weeks, behind the columns, as on Today's week.
 */
@Composable
fun MgColumns(
    columns: List<ChartColumn>,
    line: List<Dose?>?,
    description: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    tip: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
    guide: List<Dose?>? = null,
) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val tick = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val unit = stringResource(R.string.unit_mg)
    val scale = Scale.of(
        columns.mapNotNull { it.value } + columns.mapNotNull { it.marker } + line.orEmpty().filterNotNull() + guide.orEmpty().filterNotNull(),
    )
    val marked = columns.any { it.mark != null }
    val chartHeight = if (marked) MarkedHeight else ChartHeight
    // Wide enough for the longest tick at any font size, and never narrower than the mockups' 34 dp.
    val axis = with(LocalDensity.current) {
        maxOf(AxisWidth.toPx(), scale.ticks.maxOf { measurer.measure(tickText(it, scale, unit), tick).size.width } + TickGap.toPx())
    }
    Layout(
        content = {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(columns.size, selected, axis) {
                        detectTapGestures { at ->
                            val slot = (size.width - axis) / columns.size
                            val index = ((at.x - axis) / slot).toInt()
                            onSelect(if (at.x < axis || index == selected) null else index.coerceIn(0, columns.lastIndex))
                        }
                    },
            ) {
                drawGrid(scale, axis, measurer, tick.copy(color = colors.text3), unit, colors)
                guide?.let { drawGuide(it, scale, axis, columns.size, colors) }
                drawColumns(columns, scale, axis, selected, measurer, tick, colors)
                line?.let { drawLine(it, scale, axis, columns.size, colors) }
            }
            if (selected != null) {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { tip(selected) }
            }
            if (selected != null) {
                // The pointer's side is only known once the tip is measured, so both are drawn and one placed.
                TipPointer(PointerSide.LEFT)
                TipPointer(PointerSide.RIGHT)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = chartHeight.roundToPx()
        val chart = measurables[0].measure(Constraints.fixed(width, height))
        val tipBox = measurables.getOrNull(1)?.measure(Constraints(maxWidth = width))
        val toLeft = measurables.getOrNull(2)?.measure(Constraints())
        val toRight = measurables.getOrNull(3)?.measure(Constraints())
        val density: Density = this
        layout(width, height) {
            chart.place(0, 0)
            if (tipBox != null && selected != null && toLeft != null && toRight != null) {
                // Beside the column, on whichever side has room, near the top of the plot.
                val slot = (width - axis) / columns.size
                val centre = axis + slot * (selected + 0.5f)
                val gap = 12.dp.toPx() + min(MaxBar.toPx(), barWidth(slot)) / 2
                val right = centre + gap
                val onRight = right + tipBox.width <= width
                val x = if (onRight) right else (centre - gap - tipBox.width).coerceAtLeast(0f)
                val y = PlotTop.roundToPx() + 6.dp.roundToPx()
                tipBox.place(x.toInt(), y)
                val pointer = if (onRight) toLeft else toRight
                val side = if (onRight) PointerSide.LEFT else PointerSide.RIGHT
                pointer.place(pointerAt(side, IntRect(x.toInt(), y, x.toInt() + tipBox.width, y + tipBox.height), y + tipBox.height / 2f, pointer, density))
            }
        }
    }
}

/** Which way a tip's pointer points: up when the tip sits under its mark, and so on. */
enum class PointerSide { UP, DOWN, LEFT, RIGHT }

private val PointerLength = 6.dp
private val PointerWidth = 12.dp
private val TipCorner = 10.dp
private val TipBorder = 1.dp

/**
 * The small triangle that joins a tip to the mark it describes, in the tip's colours. Its base
 * runs a border's width into the tip, so the tip's edge doesn't cross it.
 */
@Composable
fun TipPointer(side: PointerSide) {
    val colors = LastHit.colors
    val border = tipBorder(colors)
    val across = side == PointerSide.UP || side == PointerSide.DOWN
    val length = PointerLength + TipBorder
    Canvas(Modifier.size(width = if (across) PointerWidth else length, height = if (across) length else PointerWidth)) {
        val w = size.width
        val h = size.height
        // The tip, then the two corners of the base.
        val (apex, a, b) = when (side) {
            PointerSide.UP -> Triple(Offset(w / 2, 0f), Offset(0f, h), Offset(w, h))
            PointerSide.DOWN -> Triple(Offset(w / 2, h), Offset(0f, 0f), Offset(w, 0f))
            PointerSide.LEFT -> Triple(Offset(0f, h / 2), Offset(w, 0f), Offset(w, h))
            PointerSide.RIGHT -> Triple(Offset(w, h / 2), Offset(0f, 0f), Offset(0f, h))
        }
        val path = Path().apply {
            moveTo(a.x, a.y)
            lineTo(apex.x, apex.y)
            lineTo(b.x, b.y)
            close()
        }
        drawPath(path, colors.surface)
        val stroke = TipBorder.toPx()
        drawLine(border, a, apex, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(border, apex, b, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * Where a [pointer] goes on the [side] of a tip at [tip] facing its mark, centred on [mark] along
 * that side (an x for up and down, a y for left and right), and kept clear of the tip's corners.
 */
fun pointerAt(side: PointerSide, tip: IntRect, mark: Float, pointer: Placeable, density: Density): IntOffset = with(density) {
    val overlap = TipBorder.roundToPx()
    val corner = TipCorner.roundToPx()
    when (side) {
        PointerSide.UP, PointerSide.DOWN -> {
            val x = (mark - pointer.width / 2f).toInt()
                .coerceIn(tip.left + corner, (tip.right - corner - pointer.width).coerceAtLeast(tip.left + corner))
            IntOffset(x, if (side == PointerSide.UP) tip.top - pointer.height + overlap else tip.bottom - overlap)
        }
        PointerSide.LEFT, PointerSide.RIGHT -> {
            val y = (mark - pointer.height / 2f).toInt()
                .coerceIn(tip.top + corner, (tip.bottom - corner - pointer.height).coerceAtLeast(tip.top + corner))
            IntOffset(if (side == PointerSide.LEFT) tip.left - pointer.width + overlap else tip.right - overlap, y)
        }
    }
}

private fun tipBorder(colors: LastHitColors) = if (colors.isDark) colors.lineStrong else colors.line

/**
 * What a tap shows: the value first and strongest, then what it is. A tight shadow, so it never
 * smudges the marks around it, and the charts add a [TipPointer] to the mark.
 */
@Composable
fun ChartTip(value: String, lines: List<String>) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(TipCorner)
    Column(
        Modifier
            .shadow(2.dp, shape)
            .background(colors.surface, shape)
            .border(TipBorder, tipBorder(colors), shape)
            .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
    ) {
        Text(value, style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold), color = colors.text)
        lines.forEach { Text(it, style = LastHit.type.caption, color = colors.text2) }
    }
}

/** A chart's numbers as a plain list, label on the left and value on the right. */
@Composable
fun ChartTable(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = colors.line)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = LastHit.type.label.copy(fontSize = 15.sp), color = colors.text, modifier = Modifier.weight(1f))
                Text(
                    value,
                    // No tabular figures: the bundled cuts make the comma and full stop tabular too,
                    // which leaves a gap before them ("1 dose , 8 mg").
                    style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                )
            }
        }
    }
}

/** The y axis: a clean top in whole mg and up to four equal steps under it. */
private class Scale(val top: Dose, val step: Dose) {
    val ticks: List<Dose> get() = (0..(top.micrograms / step.micrograms).toInt()).map { Dose(step.micrograms * it) }

    companion object {
        private val STEPS_MG = listOf(1, 2, 5, 10, 20, 50, 100)

        fun of(values: List<Dose>): Scale {
            val max = values.maxOfOrNull { it.micrograms } ?: 0L
            if (max <= 0) return Scale(Dose.mg(5), Dose.mg(5))
            val step = STEPS_MG.first { ceil(max / (it * 1000.0)) <= 4 }
            return Scale(Dose.mg(ceil(max / (step * 1000.0)).toInt() * step), Dose.mg(step))
        }
    }
}

private fun barWidth(slot: Float): Float = slot * 0.62f

private fun DrawScope.y(value: Dose, scale: Scale): Float {
    val top = PlotTop.toPx()
    val base = Baseline.toPx()
    return base - (base - top) * (value.micrograms.toFloat() / scale.top.micrograms)
}

/** A tick's label: whole mg, with the unit on the top one. */
private fun tickText(tick: Dose, scale: Scale, unit: String): String {
    val whole = (tick.micrograms / 1000).toString()
    return if (tick == scale.ticks.last()) "$whole $unit" else whole
}

private fun DrawScope.drawGrid(scale: Scale, axis: Float, measurer: TextMeasurer, style: TextStyle, unit: String, colors: LastHitColors) {
    scale.ticks.forEach { tick ->
        val y = y(tick, scale)
        drawLine(
            if (tick == Dose.ZERO) colors.lineStrong else colors.line,
            Offset(axis, y),
            Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
        val layout = measurer.measure(tickText(tick, scale, unit), style)
        drawText(layout, topLeft = Offset(axis - TickGap.toPx() - layout.size.width, y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawColumns(
    columns: List<ChartColumn>,
    scale: Scale,
    axis: Float,
    selected: Int?,
    measurer: TextMeasurer,
    tick: TextStyle,
    colors: LastHitColors,
) {
    val slot = (size.width - axis) / columns.size
    val width = min(MaxBar.toPx(), barWidth(slot))
    val radius = min(4.dp.toPx(), width / 2)
    columns.forEachIndexed { index, column ->
        val centre = axis + slot * (index + 0.5f)
        val value = column.value
        if (value != null && value > Dose.ZERO) {
            val top = y(value, scale)
            if (column.partial) {
                val inset = 0.75.dp.toPx()
                drawPath(
                    roundTop(centre - width / 2 + inset, top + inset, centre + width / 2 - inset, Baseline.toPx(), radius),
                    colors.used,
                    style = Stroke(1.5.dp.toPx()),
                )
            } else {
                drawPath(roundTop(centre - width / 2, top, centre + width / 2, Baseline.toPx(), radius), colors.used)
            }
            if (index == selected) {
                val pad = 3.dp.toPx()
                drawRoundRect(
                    colors.text,
                    topLeft = Offset(centre - width / 2 - pad, top - pad),
                    size = Size(width + pad * 2, Baseline.toPx() - top + pad),
                    cornerRadius = CornerRadius(radius + pad),
                    style = Stroke(2.dp.toPx()),
                )
            }
        } else if (value == Dose.ZERO && !column.partial) {
            // A day with no doses is a short tick on the baseline, so it never looks like a gap in the data.
            drawZeroTick(centre, min(width, ZeroTick.toPx()), if (index == selected) colors.text else colors.text2)
        }
        column.marker?.let { drawMarker(centre, min(slot - 2.dp.toPx(), width + 12.dp.toPx()), y(it, scale), colors) }
        val mark = column.mark
        if (mark != null) {
            drawMark(mark, centre, min(MarkMax.toPx(), slot - 3.dp.toPx()), column.label, measurer, tick, colors)
        } else {
            column.label?.let { label ->
                val style = if (column.labelStrong) tick.copy(fontWeight = FontWeight.ExtraBold, color = colors.text) else tick.copy(color = colors.text3)
                val layout = measurer.measure(label, style)
                drawText(layout, topLeft = Offset(centre - layout.size.width / 2f, LabelTop.toPx()))
            }
        }
    }
}

/**
 * An allowance across a column, [width] wide at [y]: a 2.5 dp line in the guide's colour on a halo
 * of the page, so it reads over the column. A marker, never a fill.
 */
private fun DrawScope.drawMarker(centre: Float, width: Float, y: Float, colors: LastHitColors) {
    for ((stroke, colour) in listOf(6.5.dp.toPx() to colors.bg, 2.5.dp.toPx() to colors.guide)) {
        drawLine(colour, Offset(centre - width / 2, y), Offset(centre + width / 2, y), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * A week's [mark] as a dot [size] across under its column, with its [label] inside when it fits
 * and under it when it doesn't: met is green with the label in the page's colour, caught up is on
 * the neutral tint inside a ring, missed and waiting are outlined, and the week running is ringed.
 */
private fun DrawScope.drawMark(
    mark: ColumnMark,
    centre: Float,
    size: Float,
    label: String?,
    measurer: TextMeasurer,
    tick: TextStyle,
    colors: LastHitColors,
) {
    val radius = size / 2
    val middle = Offset(centre, MarkTop.toPx() + radius)
    val outline = 1.5.dp.toPx()
    when (mark) {
        ColumnMark.MET -> drawCircle(colors.success, radius, middle)
        // The tint alone is too faint against the page, so it has the outline's ring as well.
        ColumnMark.CAUGHT_UP -> {
            drawCircle(colors.sunk, radius, middle)
            drawCircle(colors.lineStrong, radius - outline / 2, middle, style = Stroke(outline))
        }
        ColumnMark.MISSED -> drawCircle(colors.lineStrong, radius - outline / 2, middle, style = Stroke(outline))
        ColumnMark.WAITING -> drawCircle(
            colors.lineStrong,
            radius - outline / 2,
            middle,
            style = Stroke(outline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
        )
        ColumnMark.RUNNING -> drawCircle(colors.text, radius - 1.dp.toPx(), middle, style = Stroke(2.dp.toPx()))
    }
    label ?: return
    val ink = when (mark) {
        ColumnMark.MET -> colors.onSuccess
        ColumnMark.CAUGHT_UP, ColumnMark.RUNNING -> colors.text
        ColumnMark.MISSED, ColumnMark.WAITING -> colors.text2
    }
    val style = tick.copy(fontSize = 11.sp, fontWeight = if (mark == ColumnMark.RUNNING || mark == ColumnMark.MET) FontWeight.ExtraBold else FontWeight.SemiBold, color = ink)
    val inside = measurer.measure(label, style)
    if (inside.size.width + 6.dp.toPx() <= size && inside.size.height <= size) {
        drawText(inside, topLeft = middle - Offset(inside.size.width / 2f, inside.size.height / 2f))
    } else {
        val under = measurer.measure(label, tick.copy(color = colors.text3))
        drawText(under, topLeft = Offset(centre - under.size.width / 2f, MarkTop.toPx() + size + 4.dp.toPx()))
    }
}

/**
 * The plan's daily guide as a dashed line in the guide's colour, flat across each column's slot
 * and stepping where the guide changes, with a gap for columns outside the plan. It's drawn before
 * the columns, so a column over the guide hides the line where it passes.
 */
private fun DrawScope.drawGuide(guide: List<Dose?>, scale: Scale, axis: Float, count: Int, colors: LastHitColors) {
    val slot = (size.width - axis) / count
    val stroke = Stroke(
        1.5.dp.toPx(),
        cap = StrokeCap.Butt,
        join = StrokeJoin.Miter,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
    var path: Path? = null
    guide.forEachIndexed { index, value ->
        if (value == null) {
            path?.let { drawPath(it, colors.guide, style = stroke) }
            path = null
            return@forEachIndexed
        }
        val left = axis + slot * index
        val y = y(value, scale)
        val run = path ?: Path().also { it.moveTo(left, y) }
        run.lineTo(left, y)
        run.lineTo(left + slot, y)
        path = run
    }
    path?.let { drawPath(it, colors.guide, style = stroke) }
}

private val ZeroTick = 10.dp

/** A 2 dp tick [width] wide just over the baseline at [centre], for a column of nothing. */
private fun DrawScope.drawZeroTick(centre: Float, width: Float, colour: Color) {
    val height = 2.dp.toPx()
    val bottom = Baseline.toPx() - 2.dp.toPx()
    drawRoundRect(colour, topLeft = Offset(centre - width / 2, bottom - height), size = Size(width, height), cornerRadius = CornerRadius(height / 2))
}

/** The key's sample for a day with no doses: the tick the chart draws. */
@Composable
fun ZeroTickKey() {
    val colour = LastHit.colors.text2
    Canvas(Modifier.size(width = 10.dp, height = 14.dp)) {
        val height = 2.dp.toPx()
        drawRoundRect(colour, topLeft = Offset(0f, size.height - height), size = Size(size.width, height), cornerRadius = CornerRadius(height / 2))
    }
}

/** The average as a 2 dp line in the text colour, on a halo of the page so it reads over the columns. */
private fun DrawScope.drawLine(line: List<Dose?>, scale: Scale, axis: Float, count: Int, colors: LastHitColors) {
    val slot = (size.width - axis) / count
    val points = line.mapIndexed { index, value -> value?.let { Offset(axis + slot * (index + 0.5f), y(it, scale)) } }
    val runs = buildList {
        var run = mutableListOf<Offset>()
        points.forEach { point ->
            if (point == null) {
                if (run.isNotEmpty()) add(run)
                run = mutableListOf()
            } else {
                run.add(point)
            }
        }
        if (run.isNotEmpty()) add(run)
    }
    for ((width, colour) in listOf(6.dp.toPx() to colors.bg, 2.dp.toPx() to colors.text)) {
        runs.forEach { run ->
            val path = Path().apply {
                moveTo(run.first().x, run.first().y)
                run.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, colour, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    points.lastOrNull { it != null }?.let { end ->
        drawCircle(colors.bg, radius = 6.dp.toPx(), center = end)
        drawCircle(colors.text, radius = 4.dp.toPx(), center = end)
    }
}

private fun roundTop(left: Float, top: Float, right: Float, bottom: Float, radius: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            topLeftCornerRadius = CornerRadius(radius),
            topRightCornerRadius = CornerRadius(radius),
            bottomRightCornerRadius = CornerRadius.Zero,
            bottomLeftCornerRadius = CornerRadius.Zero,
        ),
    )
}
