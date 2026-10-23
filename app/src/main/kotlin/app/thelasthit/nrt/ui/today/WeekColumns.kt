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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * The week as seven columns, Monday to Sunday, each the day's mg in the neutral used colour.
 * Until a plan exists there's no guide, so the tallest day sets the scale and nothing is ticked.
 * A week with nothing logged has no bars, so it shrinks to the baselines.
 */
@Composable
fun WeekColumns(week: WeekSummary, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val tallest = week.days.maxOf { it.total }.takeIf { it > Dose.ZERO }
    val barArea = if (tallest == null) 0.dp else BarArea
    val summary = week.days.filter { it.date <= week.today }.joinToString(", ") { day ->
        val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)
        val text = if (day.date == week.today) R.string.today_week_day_so_far else R.string.today_week_day
        resources.getString(text, name, day.total.mgText())
    }
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }) {
        week.days.forEach { day ->
            val isToday = day.date == week.today
            val isFuture = day.date > week.today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.height(barArea + 24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!isFuture) {
                        Text(
                            day.total.mgText(),
                            style = LastHit.type.caption.copy(fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold),
                            color = if (isToday) colors.text else colors.text2,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    val fraction = tallest?.let { (day.total.micrograms.toFloat() / it.micrograms).coerceIn(0f, 1f) } ?: 0f
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .width(26.dp)
                                .height(barArea * fraction)
                                .background(colors.used, RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp)),
                        )
                    }
                }
                Baseline(dashed = isFuture)
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .size(28.dp)
                        .then(if (isToday) Modifier.border(2.dp, colors.text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK),
                        style = LastHit.type.caption.copy(fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold),
                        color = if (isToday) colors.text else colors.text2,
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
