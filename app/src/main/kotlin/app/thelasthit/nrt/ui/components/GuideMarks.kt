package app.thelasthit.nrt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.ui.theme.LastHit

/*
 * The marks the plan adds wherever a day or a week meets its guide (CLAUDE.md's colour rules): a
 * green dot with a tick only means at or under the guide, and the guide itself is a dashed line,
 * never a fill. They're drawn rather than taken from the icons, so the tick keeps the mockups'
 * heavier stroke at the small sizes it's used at.
 */

/** The mockups' check (M5 12.5 l4.5 4.5 L19 7.5) in a 24 unit box, scaled to [box] and centred on [centre]. */
fun DrawScope.drawCheck(centre: Offset, box: Float, ink: Color, stroke: Float) {
    val unit = box / 24f
    val origin = centre - Offset(box / 2, box / 2)
    val path = Path().apply {
        moveTo(origin.x + 5f * unit, origin.y + 12.5f * unit)
        lineTo(origin.x + 9.5f * unit, origin.y + 17f * unit)
        lineTo(origin.x + 19f * unit, origin.y + 7.5f * unit)
    }
    drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The mockups' minus (M5 12 h14), for a missed week. */
fun DrawScope.drawMinus(centre: Offset, box: Float, ink: Color, stroke: Float) {
    val unit = box / 24f
    drawLine(ink, centre + Offset(-7f * unit, 0f), centre + Offset(7f * unit, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * The mockups' arc back (M9 7.5 H14 a5 5 0 0 1 0 10 H8, with the head M11.5 4 L8 7.5 l3.5 3.5),
 * for a missed week that was caught up later.
 */
fun DrawScope.drawBackArc(centre: Offset, box: Float, ink: Color, stroke: Float) {
    val unit = box / 24f
    val o = centre - Offset(box / 2, box / 2)
    fun at(x: Float, y: Float) = Offset(o.x + x * unit, o.y + y * unit)
    val arc = Path().apply {
        moveTo(at(9f, 7.5f).x, at(9f, 7.5f).y)
        lineTo(at(14f, 7.5f).x, at(14f, 7.5f).y)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(at(9f, 7.5f), at(19f, 17.5f)),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        lineTo(at(8f, 17.5f).x, at(8f, 17.5f).y)
        moveTo(at(11.5f, 4f).x, at(11.5f, 4f).y)
        lineTo(at(8f, 7.5f).x, at(8f, 7.5f).y)
        lineTo(at(11.5f, 11f).x, at(11.5f, 11f).y)
    }
    drawPath(arc, ink, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A green dot with a tick: a day or a week at or under its guide. */
@Composable
fun TickDot(size: Dp, fill: Color, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawCircle(fill)
        // The mockups' 11 or 12 px tick in a 16 or 18 px dot, at a stroke of 3.2 to 3.4.
        drawCheck(center, this.size.width * 0.68f, ink, this.size.width * 0.68f / 24f * 3.3f)
    }
}

/** The key's sample for the guide: a short dashed line in the guide's colour. */
@Composable
fun GuideLineKey(modifier: Modifier = Modifier) {
    val guide = LastHit.colors.guide
    Canvas(modifier.size(width = 16.dp, height = 10.dp)) {
        val y = this.size.height / 2
        drawLine(
            guide,
            Offset(0f, y),
            Offset(this.size.width, y),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        )
    }
}

/** The key's sample for nicotine used: a small square in the neutral used colour. */
@Composable
fun UsedKey(modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(LastHit.colors.used, RoundedCornerShape(3.dp)))
}

/** The key's sample for at or under the guide: a plain green dot, as the mockups' key has it. */
@Composable
fun WithinKey(modifier: Modifier = Modifier) {
    val success = LastHit.colors.success
    Canvas(modifier.size(12.dp)) { drawCircle(success) }
}
