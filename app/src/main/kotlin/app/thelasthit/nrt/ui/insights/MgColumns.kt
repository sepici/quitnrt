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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.LastHitColors
import kotlin.math.ceil
import kotlin.math.min

/** One column: a day's total or a week's average a day. A [partial] one, still under way, is an outline. */
data class ChartColumn(val value: Dose?, val partial: Boolean = false, val label: String? = null, val labelStrong: Boolean = false)

private val ChartHeight = 172.dp
private val PlotTop = 10.dp
private val Baseline = 142.dp
private val AxisWidth = 34.dp
private val TickGap = 8.dp
private val LabelTop = 150.dp
private val MaxBar = 24.dp

/**
 * Stone columns of mg on a zero baseline, with an optional [line] over them: thin columns with
 * rounded tops, a hairline grid, and anything partial as an outline. [line] has a value or a gap
 * for each column. Tapping a column selects it and shows [tip] beside it; tapping it again, or
 * outside the columns, lets it go. Screen readers get [description] instead.
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
) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val tick = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val unit = stringResource(R.string.unit_mg)
    val scale = Scale.of(columns.mapNotNull { it.value } + line.orEmpty().filterNotNull())
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
                drawColumns(columns, scale, axis, selected, measurer, tick, colors)
                line?.let { drawLine(it, scale, axis, columns.size, colors) }
            }
            if (selected != null) {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { tip(selected) }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = ChartHeight.roundToPx()
        val chart = measurables[0].measure(Constraints.fixed(width, height))
        val tipBox = measurables.getOrNull(1)?.measure(Constraints(maxWidth = width))
        layout(width, height) {
            chart.place(0, 0)
            if (tipBox != null && selected != null) {
                // Beside the column, on whichever side has room, near the top of the plot.
                val slot = (width - axis) / columns.size
                val centre = axis + slot * (selected + 0.5f)
                val gap = 12.dp.toPx() + min(MaxBar.toPx(), barWidth(slot)) / 2
                val right = centre + gap
                val x = if (right + tipBox.width <= width) right else (centre - gap - tipBox.width).coerceAtLeast(0f)
                tipBox.place(x.toInt(), PlotTop.roundToPx() + 6.dp.roundToPx())
            }
        }
    }
}

/** What a tap shows: the value first and strongest, then what it is. */
@Composable
fun ChartTip(value: String, lines: List<String>) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .shadow(8.dp, shape)
            .background(colors.surface, shape)
            .border(1.dp, if (colors.isDark) colors.lineStrong else colors.line, shape)
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
        }
        column.label?.let { label ->
            val style = if (column.labelStrong) tick.copy(fontWeight = FontWeight.ExtraBold, color = colors.text) else tick.copy(color = colors.text3)
            val layout = measurer.measure(label, style)
            drawText(layout, topLeft = Offset(centre - layout.size.width / 2f, LabelTop.toPx()))
        }
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
