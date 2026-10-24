package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// The Plan tab's charts: a week as columns, the meter against the allowance, the daily guide
// across the plan, and the marks and keys that go with them. Nicotine used is always the neutral
// used tone; the guide is a dashed line or a marker, never a fill; green only marks at or under.

/** Where a day stands in a set of columns. */
internal enum class DayState {
    /** Over, with its total. */
    DONE,

    /** Over with nothing logged, in a baseline: it doesn't count. */
    NOTHING,
    TODAY,
    TO_COME,
}

internal data class ColumnDay(val date: LocalDate, val total: Dose, val state: DayState)

/**
 * Seven days as columns, A's style: each day's mg in the used tone with its number above, and
 * the day's letter under its baseline. With a [guide], a dashed line marks it and a day over and
 * at or under it gets a green circle. Days to come, and days with nothing logged, have a dashed
 * baseline and no column.
 */
@Composable
internal fun PlanColumns(
    days: List<ColumnDay>,
    guide: Dose?,
    description: String,
    modifier: Modifier = Modifier,
    mini: Boolean = false,
    labelBackground: Color = LastHit.colors.bg,
) {
    val colors = LastHit.colors
    val area = if (mini) 60.dp else 88.dp
    val barWidth = if (mini) 22.dp else 26.dp
    val labelRoom = 24.dp
    val counted = days.filter { it.state == DayState.DONE || it.state == DayState.TODAY }
    val tallest = counted.maxOfOrNull { it.total } ?: Dose.ZERO
    val top = if (guide != null && guide > Dose.ZERO) maxOf(tallest, guide).micrograms / 0.78 else tallest.micrograms / 0.82
    val dash = with(LocalDensity.current) { 4.dp.toPx() }
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                if (guide != null && guide > Dose.ZERO && top > 0) {
                    val y = labelRoom.toPx() + area.toPx() * (1 - (guide.micrograms / top)).toFloat()
                    drawLine(
                        colors.guide,
                        Offset(4.dp.toPx(), y),
                        Offset(size.width - 4.dp.toPx(), y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.75f)),
                    )
                }
            },
    ) {
        days.forEach { day ->
            val isToday = day.state == DayState.TODAY
            val shown = day.state == DayState.DONE || (isToday && (day.total > Dose.ZERO || guide != null))
            val ok = guide != null && day.state == DayState.DONE && day.total <= guide
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.height(area + labelRoom),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (shown) {
                        Text(
                            day.total.mgText(),
                            style = LastHit.type.caption.copy(
                                fontSize = if (mini) 12.sp else 13.sp,
                                lineHeight = if (mini) 16.sp else 18.sp,
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                            ),
                            color = if (isToday) colors.text else colors.text2,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(bottom = 3.dp)
                                .background(labelBackground)
                                .padding(horizontal = 4.dp),
                        )
                        val fraction = if (top > 0) (day.total.micrograms / top).toFloat().coerceIn(0f, 1f) else 0f
                        if (fraction > 0f) {
                            Box(
                                Modifier
                                    .width(barWidth)
                                    .height(area * fraction)
                                    .background(colors.used, RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp)),
                            )
                        }
                    }
                }
                Baseline(barWidth, dashed = !shown)
                DayLetter(day.date, isToday = isToday, ok = ok, muted = day.state == DayState.NOTHING, mini = mini)
            }
        }
    }
}

/** An NRT-free week: no columns and no guide line, just each day's 0 over its mark. */
@Composable
internal fun ZeroColumns(days: List<ColumnDay>, description: String, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        days.forEach { day ->
            val isToday = day.state == DayState.TODAY
            val shown = day.state == DayState.DONE || isToday
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (shown) day.total.mgText() else "",
                    style = LastHit.type.caption.copy(fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold),
                    color = if (isToday) colors.text else colors.text2,
                    modifier = Modifier.height(18.dp),
                )
                DayLetter(
                    day.date,
                    isToday = isToday,
                    ok = day.state == DayState.DONE && day.total == Dose.ZERO,
                    muted = false,
                    mini = false,
                    top = 6.dp,
                )
            }
        }
    }
}

@Composable
private fun Baseline(width: Dp, dashed: Boolean) {
    val colour = LastHit.colors.lineStrong
    Canvas(Modifier.width(width).height(1.dp)) {
        drawLine(
            colour,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
        )
    }
}

/** The day's letter in a circle: green when at or under the guide, ringed for today. */
@Composable
private fun DayLetter(date: LocalDate, isToday: Boolean, ok: Boolean, muted: Boolean, mini: Boolean, top: Dp = if (mini) 8.dp else 10.dp) {
    val colors = LastHit.colors
    val size = if (mini) 24.dp else 28.dp
    Box(
        Modifier
            .padding(top = top)
            .size(size)
            .clip(CircleShape)
            .then(
                when {
                    ok -> Modifier.background(colors.success)
                    isToday -> Modifier.border(2.dp, colors.text, CircleShape)
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.UK),
            style = LastHit.type.caption.copy(
                fontSize = if (mini) 12.sp else 13.sp,
                fontWeight = when {
                    isToday -> FontWeight.ExtraBold
                    ok -> FontWeight.Bold
                    else -> FontWeight.SemiBold
                },
            ),
            color = when {
                ok -> colors.onSuccess
                isToday -> colors.text
                muted -> colors.text3
                else -> colors.text2
            },
        )
    }
}

/** What a key can show. */
internal sealed interface KeyItem {
    val label: String

    data class Used(override val label: String) : KeyItem
    data class Guide(override val label: String) : KeyItem
    data class AtOrUnder(override val label: String) : KeyItem
    data class Nothing(override val label: String) : KeyItem
    data class Met(override val label: String) : KeyItem
    data class Missed(override val label: String) : KeyItem
    data class CaughtUp(override val label: String) : KeyItem
    data class Waiting(override val label: String) : KeyItem
    data class Hit(override val label: String) : KeyItem
}

/** A chart's key. Screen readers skip it, since each chart describes itself. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanKey(items: List<KeyItem>, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    FlowRow(
        modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (item) {
                    is KeyItem.Used -> Box(Modifier.size(10.dp).background(colors.used, RoundedCornerShape(3.dp)))
                    is KeyItem.Guide -> DashSwatch(colors.guide)
                    is KeyItem.Nothing -> DashSwatch(colors.lineStrong)
                    is KeyItem.AtOrUnder -> Box(Modifier.size(12.dp).background(colors.success, CircleShape))
                    is KeyItem.Met -> WeekMark(Mark.MET, size = 16.dp)
                    is KeyItem.Missed -> WeekMark(Mark.MISSED, size = 16.dp)
                    is KeyItem.CaughtUp -> WeekMark(Mark.CAUGHT_UP, size = 16.dp)
                    is KeyItem.Waiting -> WeekMark(Mark.WAITING, size = 16.dp)
                    is KeyItem.Hit -> HitChip()
                }
                Text(item.label, style = LastHit.type.caption, color = colors.text2)
            }
        }
    }
}

@Composable
private fun DashSwatch(colour: Color) {
    Canvas(Modifier.width(16.dp).height(2.dp)) {
        drawLine(
            colour,
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        )
    }
}

/** One hit, as a marigold chip: the game layer's colour. */
@Composable
internal fun HitChip() {
    val colors = LastHit.colors
    Box(
        Modifier
            .size(10.dp)
            .background(colors.marigold)
            .border(1.5.dp, colors.marigoldEdge),
    )
}

/** A week's status mark in the schedule. Green only for a week met; missed and caught up stay neutral. */
internal enum class Mark { MET, MISSED, CAUGHT_UP, WAITING, NOW, LATER }

@Composable
internal fun WeekMark(mark: Mark, size: Dp = 28.dp) {
    val colors = LastHit.colors
    val scale = size / 28.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                when (mark) {
                    Mark.MET -> Modifier.background(colors.success)
                    Mark.CAUGHT_UP -> Modifier.background(colors.sunk)
                    Mark.MISSED, Mark.WAITING -> Modifier.border(1.5.dp, colors.lineStrong, CircleShape)
                    Mark.NOW -> Modifier.border(2.dp, colors.text, CircleShape)
                    Mark.LATER -> Modifier.border(1.5.dp, colors.line, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (mark) {
            Mark.MET -> Glyph(size * 0.58f, colors.bg, 2.2.dp * scale) { s -> check(s) }
            Mark.MISSED -> Glyph(size * 0.58f, colors.text2, 2.dp * scale) { s -> minus(s) }
            Mark.WAITING -> Glyph(size * 0.64f, colors.text2, 1.6.dp * scale) { s -> clock(s) }
            Mark.CAUGHT_UP -> Icon(painterResource(R.drawable.ic_plan_caught_up), contentDescription = null, tint = colors.text, modifier = Modifier.size(size * 0.64f))
            Mark.NOW -> Box(Modifier.size(size * 0.36f).background(colors.text, CircleShape))
            Mark.LATER -> Box(Modifier.size(size * 0.22f).background(colors.lineStrong, CircleShape))
        }
    }
}

/** A line drawing on a 24-unit grid, as the mockups' icons are, with a stroke to suit its size. */
@Composable
private fun Glyph(size: Dp, colour: Color, stroke: Dp, path: Path.(Float) -> Unit) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val shape = Path().apply { path(unit) }
        drawPath(shape, colour, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun Path.check(u: Float) {
    moveTo(5f * u, 12.5f * u)
    lineTo(9.5f * u, 17f * u)
    lineTo(19f * u, 7.5f * u)
}

private fun Path.minus(u: Float) {
    moveTo(6f * u, 12f * u)
    lineTo(18f * u, 12f * u)
}

private fun Path.clock(u: Float) {
    addOval(Rect(4f * u, 4f * u, 20f * u, 20f * u))
    moveTo(12f * u, 8f * u)
    lineTo(12f * u, 12.5f * u)
    lineTo(15f * u, 14f * u)
}

/**
 * The week against its allowance: an outlined track, the mg used in the used tone, and the
 * allowance as a marker at four fifths of the way, with what's left (or over) under it. A week
 * over its allowance pushes the marker left, so the used bar always fits.
 */
@Composable
internal fun Meter(used: Dose, allowance: Dose, left: String, isUnder: Boolean, markLabel: String, description: String, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val scale = maxOf(allowance.micrograms / 0.8, used.micrograms.toDouble()).takeIf { it > 0 } ?: 1.0
    val usedFraction = (used.micrograms / scale).toFloat().coerceIn(0f, 1f)
    val markFraction = (allowance.micrograms / scale).toFloat().coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth().padding(top = 18.dp).clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val trackTop = 6.dp.toPx()
            val trackHeight = 14.dp.toPx()
            val radius = CornerRadius(trackHeight / 2)
            val inset = 0.75.dp.toPx()
            drawRoundRect(
                colors.lineStrong,
                topLeft = Offset(inset, trackTop + inset),
                size = Size(size.width - inset * 2, trackHeight - inset * 2),
                cornerRadius = radius,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            if (usedFraction > 0f) {
                drawRoundRect(colors.used, topLeft = Offset(0f, trackTop), size = Size(size.width * usedFraction, trackHeight), cornerRadius = radius)
            }
            val x = size.width * markFraction
            val markWidth = 4.dp.toPx()
            val halo = 2.dp.toPx()
            drawRoundRect(colors.bg, Offset(x - markWidth / 2 - halo, -halo), Size(markWidth + halo * 2, size.height + halo * 2), CornerRadius(halo * 2))
            drawRoundRect(colors.text, Offset(x - markWidth / 2, 0f), Size(markWidth, size.height), CornerRadius(markWidth / 2))
        }
        MeterLabels(markFraction = markFraction, left = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isUnder) {
                    Box(Modifier.size(18.dp).background(colors.success, CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(12.dp, colors.bg, 1.8.dp) { s -> check(s) }
                    }
                }
                Text(left, style = LastHit.type.title.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.text)
            }
        }) {
            Text(markLabel, style = LastHit.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.text2, maxLines = 1)
        }
    }
}

/**
 * What's left at the start, and the allowance's label centred under its marker. When the two
 * would run into each other, the label sits on a line of its own, ending at the marker.
 */
@Composable
private fun MeterLabels(markFraction: Float, left: @Composable () -> Unit, mark: @Composable () -> Unit) {
    Layout(contents = listOf(left, mark), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { (leftPart, markPart), constraints ->
        val width = constraints.maxWidth
        val a = leftPart.first().measure(constraints.copy(minWidth = 0))
        val b = markPart.first().measure(constraints.copy(minWidth = 0))
        val gap = 8.dp.roundToPx()
        val centred = (width * markFraction - b.width / 2f).roundToInt().coerceIn(0, (width - b.width).coerceAtLeast(0))
        val fits = centred >= a.width + gap
        val markX = if (fits) centred else (width * markFraction - b.width).roundToInt().coerceIn(0, (width - b.width).coerceAtLeast(0))
        val height = if (fits) maxOf(a.height, b.height) else a.height + b.height
        layout(width, height) {
            a.place(0, 0)
            if (fits) b.place(markX, (a.height - b.height).coerceAtLeast(0) / 2 + 2.dp.roundToPx()) else b.place(markX, a.height)
        }
    }
}

/** The demon's HP in blocks of 10: full ones in the HP green with a lighter top edge, empty in the bar's sand. */
@Composable
internal fun HpBlocks(
    blocks: Int,
    full: Int,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    gap: Dp = 3.dp,
    edge: Dp = 3.dp,
    added: Int? = null,
) {
    val colors = LastHit.colors
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
        if (blocks <= 0) return@Canvas
        val gapPx = gap.toPx()
        val width = (size.width - gapPx * (blocks - 1)) / blocks
        for (index in 0 until blocks) {
            val x = index * (width + gapPx)
            val isFull = index < full
            drawRect(if (isFull) colors.hpFull else colors.hpEmpty, Offset(x, 0f), Size(width, size.height))
            if (isFull) drawRect(colors.hpHigh, Offset(x, 0f), Size(width, edge.toPx()))
            if (index == added) {
                val ring = 2.dp.toPx()
                drawRect(colors.marigold, Offset(x - ring / 2, -ring / 2), Size(width + ring, size.height + ring), style = Stroke(ring))
            }
        }
    }
}

/** A step on the guide chart: a week and its daily guide. */
internal data class GuideStep(val week: Int, val guide: Dose, val isNrtFree: Boolean)

/**
 * The daily guide across every week of the plan, as a stepped line over the usual day's dashed
 * line: never a fill. Each step down has its mg over it, the week a lower strength takes over
 * has a thin dashed rule, and the NRT-free weeks are named under the axis.
 */
@Composable
internal fun GuideChart(
    steps: List<GuideStep>,
    usual: Dose,
    usualLabel: String,
    switchWeek: Int?,
    switchLabel: String?,
    weekLabel: String,
    freeLabel: String,
    unit: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val small = TextStyle(fontFamily = LastHit.type.caption.fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val value = small.copy(fontWeight = FontWeight.ExtraBold)
    val density = LocalDensity.current
    val line = with(density) { measurer.measure("0", small).size.height.toDp() }
    val plotTop = line / 2 + 8.dp
    val plotHeight = 106.dp
    val height = plotTop + plotHeight + 10.dp + line * 2 + 6.dp
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description }) {
        if (steps.isEmpty()) return@Canvas
        val maxMg = usual.micrograms / 1000.0
        val gridStep = when {
            maxMg <= 8 -> 2.0
            maxMg <= 20 -> 5.0
            maxMg <= 50 -> 10.0
            maxMg <= 100 -> 20.0
            else -> 50.0
        }
        var yMax = ceil(maxMg / gridStep) * gridStep
        if (yMax - maxMg < gridStep * 0.2) yMax += gridStep
        val labelWidth = (0..(yMax / gridStep).toInt()).maxOf { index ->
            val text = gridText(index * gridStep, if (index == (yMax / gridStep).toInt()) unit else null)
            measurer.measure(text, small).size.width
        }
        val left = maxOf(30.dp.toPx(), labelWidth + 8.dp.toPx())
        val right = size.width - 4.dp.toPx()
        val top = plotTop.toPx()
        val bottom = top + plotHeight.toPx()
        fun y(mg: Double) = (bottom - (mg / yMax) * (bottom - top)).toFloat()
        val column = (right - left) / steps.size

        // The grid and its labels.
        val count = (yMax / gridStep).toInt()
        for (index in 0..count) {
            val mg = index * gridStep
            val at = y(mg)
            drawLine(if (index == 0) colors.lineStrong else colors.line, Offset(left, at), Offset(right, at), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(gridText(mg, if (index == count) unit else null), small.copy(color = colors.text3))
            drawText(text, topLeft = Offset(left - 8.dp.toPx() - text.size.width, at - text.size.height / 2f))
        }

        // The usual day, dashed, labelled at the right on the page's colour.
        val usualY = y(maxMg)
        drawLine(colors.lineStrong, Offset(left, usualY), Offset(right, usualY), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        val usualText = measurer.measure(usualLabel, small.copy(color = colors.text2))
        val usualBox = Offset(right - usualText.size.width - 4.dp.toPx(), usualY - usualText.size.height - 4.dp.toPx())
        drawRoundRect(colors.bg, usualBox, Size(usualText.size.width + 4.dp.toPx(), usualText.size.height.toFloat()), CornerRadius(3.dp.toPx()))
        drawText(usualText, topLeft = Offset(right - usualText.size.width.toFloat(), usualBox.y))

        // Where a lower strength takes over. Its label keeps its place, and step labels give way to it.
        var switchBox: Rect? = null
        if (switchWeek != null && switchLabel != null) {
            val x = left + column * (switchWeek - 1)
            drawLine(colors.text3, Offset(x, usualY + 10.dp.toPx()), Offset(x, bottom), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
            val text = measurer.measure(switchLabel, small.copy(color = colors.text2))
            val textX = if (x + 5.dp.toPx() + text.size.width <= right) x + 5.dp.toPx() else x - 5.dp.toPx() - text.size.width
            val at = Offset(textX, usualY + 10.dp.toPx())
            drawText(text, topLeft = at)
            switchBox = Rect(at, Size(text.size.width.toFloat(), text.size.height.toFloat())).inflate(2.dp.toPx())
        }

        // The stepped line, on a halo of the page so it reads over the grid.
        val path = Path()
        steps.forEachIndexed { index, step ->
            val at = y(step.guide.micrograms / 1000.0)
            val x0 = left + column * index
            if (index == 0) path.moveTo(x0, at) else path.lineTo(x0, at)
            path.lineTo(x0 + column, at)
        }
        drawPath(path, colors.bg, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, colors.guide, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Each week's guide over its step, where there's room.
        var lastRight = Float.NEGATIVE_INFINITY
        steps.forEachIndexed { index, step ->
            if (step.isNrtFree) return@forEachIndexed
            val text = measurer.measure(step.guide.mgText(), value.copy(color = colors.text))
            val centre = left + column * index + column / 2
            val x = centre - text.size.width / 2f
            if (x < lastRight + 2.dp.toPx()) return@forEachIndexed
            val at = Offset(x, y(step.guide.micrograms / 1000.0) - 4.dp.toPx() - text.size.height)
            if (switchBox?.overlaps(Rect(at, Size(text.size.width.toFloat(), text.size.height.toFloat()))) == true) return@forEachIndexed
            drawText(text, topLeft = at)
            lastRight = x + text.size.width
        }

        // Week numbers, as many as fit, then "Week" and "NRT-free" under them.
        val numberY = bottom + 8.dp.toPx()
        val widest = measurer.measure(steps.size.toString(), small).size.width
        val every = ceil((widest + 4.dp.toPx()) / column).toInt().coerceAtLeast(1)
        // Every so many weeks from the first, and the last too when it has room.
        val numbered = steps.indices.filter { it % every == 0 }.toMutableList()
        if (steps.lastIndex !in numbered) {
            if (steps.lastIndex - numbered.last() < every) numbered.removeAt(numbered.lastIndex)
            numbered += steps.lastIndex
        }
        steps.forEachIndexed { index, step ->
            if (index !in numbered) return@forEachIndexed
            val text = measurer.measure(step.week.toString(), small.copy(color = colors.text3))
            drawText(text, topLeft = Offset(left + column * index + column / 2 - text.size.width / 2f, numberY))
        }
        val captionY = numberY + line.toPx() + 2.dp.toPx()
        drawText(measurer.measure(weekLabel, small.copy(color = colors.text2)), topLeft = Offset(left, captionY))
        val free = steps.indices.filter { steps[it].isNrtFree }
        if (free.isNotEmpty()) {
            val text = measurer.measure(freeLabel, small.copy(color = colors.text2))
            val centre = left + column * (free.first() + free.last() + 1) / 2f
            val x = (centre - text.size.width / 2f).coerceAtMost(right - text.size.width)
            drawText(text, topLeft = Offset(x, captionY))
        }
    }
}

private fun gridText(mg: Double, unit: String?): String {
    val number = if (mg % 1.0 == 0.0) mg.toInt().toString() else mg.toString()
    return if (unit == null) number else "$number\u00A0$unit"
}
