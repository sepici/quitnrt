package app.thelasthit.nrt.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.WeekSummary
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.format.TextStyle
import java.util.Locale

private val BarArea = 88.dp

/** Room over the bars for each day's number. */
internal val WeekColumnsLabelRoom = 24.dp

private val LabelRoom = WeekColumnsLabelRoom

/** An NRT-free week's numbers, with no bars under them (the mockups' .pl-nobar). */
private val NoBarValue = 18.dp

/**
 * The week as seven columns, each the day's mg in the neutral used colour. Until a plan exists
 * there's no guide, so the tallest day sets the scale and nothing is ticked. A week with nothing
 * logged has no bars, so it shrinks to the baselines. Days with no data, before the first dose or
 * still to come, get a dashed baseline and no number.
 *
 * With a daily [guide], the guide is a dashed line across the columns, never a fill, and each day
 * that's over and stayed at or under it has its letter on a green dot. The higher of the guide and
 * the tallest day sits at 7/9 of the bars' height, as in the mockups, so a day a little over the
 * guide has room above the line.
 *
 * An NRT-free week has no bars, baselines or line, only each day's mg over its letter (the
 * mockups' .pl-nobar). Against a guide of nothing, any bar would be all over, and a full column
 * for a 1 mg spray would shame a slip (PLAN §6.4); the number and the missing dot say it plainly.
 */
@Composable
fun WeekColumns(week: WeekSummary, modifier: Modifier = Modifier, guide: Dose? = null) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val tallestDay = week.days.maxOf { it.total }
    val noBars = guide == Dose.ZERO
    // The top of the bars, in micrograms: the tallest day until there's a guide, then 9/7 of the
    // higher of the guide and the tallest day.
    val top: Float? = when {
        guide == null -> tallestDay.takeIf { it > Dose.ZERO }?.micrograms?.toFloat()
        noBars -> null
        else -> maxOf(tallestDay, guide).micrograms * 9f / 7f
    }
    val barArea = if (top == null) 0.dp else BarArea
    val days = week.days.filter { it.tracked }.joinToString(", ") { day ->
        val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)
        val text = when {
            day.date == week.today -> R.string.today_week_day_so_far
            guide == null -> R.string.today_week_day
            week.isWithin(day, guide) -> R.string.today_plan_day_within
            else -> R.string.today_plan_day_over
        }
        resources.getString(text, name, day.total.mgText())
    }
    val summary = if (guide == null) days else resources.getString(R.string.today_plan_columns_guide, guide.mgText()) + " " + days
    val line = guide?.takeIf { it > Dose.ZERO }
    val guideColour = colors.guide
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = summary }
            .drawBehind {
                if (line == null || top == null) return@drawBehind
                // From the top of the columns: the room for numbers, then down the bar area to the guide.
                val y = (LabelRoom + barArea * (1f - line.micrograms / top)).toPx()
                val inset = 4.dp.toPx()
                drawLine(
                    guideColour,
                    Offset(inset, y),
                    Offset(size.width - inset, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            },
    ) {
        week.days.forEach { day ->
            val isToday = day.date == week.today
            val within = guide != null && week.isWithin(day, guide)
            // The plan mockups set the numbers at 700; the week before a plan keeps phase 1's 600.
            val valueStyle = LastHit.type.caption.copy(
                fontWeight = when {
                    isToday -> FontWeight.ExtraBold
                    guide != null -> FontWeight.Bold
                    else -> FontWeight.SemiBold
                },
            )
            val valueColour = if (isToday) colors.text else colors.text2
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (noBars) {
                    Box(Modifier.heightIn(min = NoBarValue), contentAlignment = Alignment.BottomCenter) {
                        if (day.tracked) Text(day.total.mgText(), style = valueStyle, color = valueColour)
                    }
                } else {
                    Column(
                        Modifier.height(barArea + LabelRoom),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (day.tracked) {
                            Text(
                                day.total.mgText(),
                                style = valueStyle,
                                color = valueColour,
                                // On the page's colour, so the guide line passes behind the number.
                                modifier = Modifier
                                    .padding(bottom = 3.dp)
                                    .then(if (line != null) Modifier.background(colors.bg).padding(horizontal = 4.dp) else Modifier),
                            )
                        }
                        val fraction = top?.let { (day.total.micrograms / it).coerceIn(0f, 1f) } ?: 0f
                        if (fraction > 0f) {
                            Box(
                                Modifier
                                    .width(26.dp)
                                    .height(barArea * fraction)
                                    .background(colors.used, RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp)),
                            )
                        }
                    }
                    Baseline(dashed = !day.tracked)
                }
                Box(
                    Modifier
                        .padding(top = if (noBars) 6.dp else 10.dp)
                        .size(28.dp)
                        .then(
                            when {
                                isToday -> Modifier.border(2.dp, colors.text, CircleShape)
                                within -> Modifier.background(colors.success, CircleShape)
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK),
                        style = LastHit.type.caption.copy(
                            fontWeight = when {
                                isToday -> FontWeight.ExtraBold
                                within -> FontWeight.Bold
                                else -> FontWeight.SemiBold
                            },
                        ),
                        color = when {
                            isToday -> colors.text
                            within -> colors.onSuccess
                            else -> colors.text2
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Baseline(dashed: Boolean) {
    val colour = LastHit.colors.lineStrong
    Canvas(Modifier.width(26.dp).height(1.dp)) {
        drawLine(
            colour,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
        )
    }
}
