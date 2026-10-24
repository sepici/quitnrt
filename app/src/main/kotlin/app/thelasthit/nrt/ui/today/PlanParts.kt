package app.thelasthit.nrt.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.WeekSummary
import app.thelasthit.nrt.domain.programme.PlanToday
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.ui.components.GuideLineKey
import app.thelasthit.nrt.ui.components.TickDot
import app.thelasthit.nrt.ui.components.UsedKey
import app.thelasthit.nrt.ui.components.WithinKey
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.LocalDate

/** Where the guide sits on the hero's track, as the mockups place it, until the day runs past the end. */
private const val GUIDE_AT = 0.7f

/**
 * The hero's meter against today's guide (B's `today.html`): what's been used as a fill on the
 * track, and the guide as a marker, never a fill. Under it, what's left with a green tick while
 * today stays at or under the guide, or how far over it is without one, and the guide's mg under
 * its marker. An NRT-free guide sits at the start of the track and says so in the line itself.
 * With nothing to measure against, the track then runs to the usual day ([PlanToday.scale]), so
 * a small slip is a small fill rather than a full track.
 */
/**
 * With [sayTotal] off, a screen reader hears only the guide and the status, for a place that
 * already says the total, such as the hero, which reads "8 mg today" just before it.
 */
@Composable
internal fun GuideMeter(plan: PlanToday, total: Dose, modifier: Modifier = Modifier, sayTotal: Boolean = true) {
    val colors = LastHit.colors
    val against = plan.today(total)
    val guide = plan.guide
    val status = when {
        plan.isNrtFree && against.isWithin -> stringResource(R.string.today_plan_free_at)
        plan.isNrtFree -> stringResource(R.string.today_plan_free_over, against.over.mgText())
        against.left > Dose.ZERO -> stringResource(R.string.today_plan_left, against.left.mgText())
        against.isWithin -> stringResource(R.string.today_plan_at)
        else -> stringResource(R.string.today_plan_over, against.over.mgText())
    }
    val description = if (sayTotal) {
        stringResource(R.string.today_plan_meter_description, total.mgText(), guide.mgText(), status)
    } else {
        stringResource(R.string.today_plan_meter_guide_description, guide.mgText(), status)
    }
    // The guide sits at 70% of the track, so the end of the track is 10/7 of it, until today goes
    // past that. An NRT-free track ends at the usual day, and a day past that fills it.
    val end = if (plan.isNrtFree) plan.scale.micrograms else maxOf(guide.micrograms * 10 / 7, total.micrograms)
    val used = if (end == 0L) 0f else (total.micrograms.toFloat() / end).coerceAtMost(1f)
    val mark = if (end == 0L) 0f else guide.micrograms.toFloat() / end
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val track = 14.dp.toPx()
            val top = 6.dp.toPx()
            val radius = CornerRadius(track / 2)
            drawRoundRect(colors.heroTrack, Offset(0f, top), Size(size.width, track), radius)
            if (used > 0f) {
                drawRoundRect(colors.heroUsed, Offset(0f, top), Size(maxOf(track, size.width * used), track), radius)
            }
            // The marker, 4 dp wide, with a ring of the hero's green so it stands clear of the fill.
            val width = 4.dp.toPx()
            val ring = 2.dp.toPx()
            val x = (size.width * mark).coerceIn(width / 2 + ring, size.width - width / 2 - ring)
            drawRoundRect(colors.hero, Offset(x - width / 2 - ring, -ring), Size(width + ring * 2, size.height + ring * 2), CornerRadius(width / 2 + ring))
            drawRoundRect(colors.onHero, Offset(x - width / 2, 0f), Size(width, size.height), CornerRadius(width / 2))
        }
        MeterLabels(
            mark = mark,
            status = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (against.isWithin) {
                        TickDot(18.dp, fill = colors.successOnHero, ink = colors.hero, modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(status, style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold), color = colors.onHero)
                }
            },
            guide = if (plan.isNrtFree) {
                null
            } else {
                {
                    Text(
                        stringResource(R.string.today_plan_guide_mark, guide.mgText()),
                        style = LastHit.type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.onHero2,
                        maxLines = 1,
                    )
                }
            },
        )
    }
}

/**
 * The line under the meter: [status] at the start, and the [guide]'s label centred under the
 * marker at [mark] of the width, kept inside the edges. When the two would touch, at a large font
 * say, the guide's label drops to a line of its own.
 */
@Composable
private fun MeterLabels(mark: Float, status: @Composable () -> Unit, guide: (@Composable () -> Unit)?) {
    Layout(
        content = {
            status()
            guide?.invoke()
        },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val left = measurables[0].measure(constraints.copy(minWidth = 0))
        val label = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0))
        val gap = 12.dp.roundToPx()
        val labelX = label?.let { (width * mark - it.width / 2f).toInt().coerceIn(0, (width - it.width).coerceAtLeast(0)) } ?: 0
        val ownLine = label != null && labelX < left.width + gap
        // The label's 18 sp line sits 2 dp lower than the status's 22 sp one, as in the mockup.
        val labelY = if (ownLine) left.height else 2.dp.roundToPx()
        val height = maxOf(left.height, label?.let { labelY + it.height } ?: 0)
        layout(width, height) {
            left.place(0, 0)
            label?.place(labelX, labelY)
        }
    }
}

/**
 * Today's week once a plan runs: its total against the allowance, which week and day of the plan
 * it is with what's left, the week's columns against the daily guide, and their key.
 */
@Composable
internal fun PlanWeekSection(plan: PlanToday, week: WeekSummary) {
    val colors = LastHit.colors
    val against = plan.week(week.total)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp)) {
        // Heard as the meters say it, "61 mg used of the 98 mg allowance", since "61 of 98" spoken
        // sounds like progress towards the allowance, as if it were a target.
        val spoken = if (plan.isNrtFree) {
            stringResource(R.string.today_plan_week_free_description, week.total.mgText())
        } else {
            stringResource(R.string.today_plan_week_description, week.total.mgText(), plan.allowance.mgText())
        }
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.Bottom) {

                Text(week.total.mgText(), style = LastHit.type.display, color = colors.text, modifier = Modifier.alignByBaseline())
                Text(
                    if (plan.isNrtFree) stringResource(R.string.today_week_total) else stringResource(R.string.today_plan_week_of, plan.allowance.mgText()),
                    style = LastHit.type.title.copy(fontSize = 17.sp, lineHeight = 24.sp),
                    color = colors.text2,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f, fill = false)
                        .alignByBaseline(),
                )
            }
            Text(
                weekMeta(plan, against.left, against.over, against.isWithin),
                style = LastHit.type.label.copy(fontWeight = FontWeight.Medium),
                color = colors.text2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // 40 dp from the line above to the top of the bars, as the mockups' .pl-cols, less the room
        // the columns keep for their numbers. An NRT-free week has no bars (.pl-nobar), so its
        // numbers sit 24 dp down.
        Spacer(Modifier.height(if (plan.isNrtFree) 24.dp else 40.dp - WeekColumnsLabelRoom))
        WeekColumns(week, guide = plan.guide)
        PlanWeekKey(plan)
    }
}

/** "Week 4 of 12, day 5. 37 mg left this week." */
@Composable
private fun weekMeta(plan: PlanToday, left: Dose, over: Dose, within: Boolean): String {
    plan.awaiting?.let { awaiting ->
        // Today calls every overtime week "Overtime", so the one awaited isn't given its number.
        return if (awaiting.kind == WeekKind.OVERTIME) {
            stringResource(R.string.today_plan_awaiting_overtime)
        } else {
            stringResource(R.string.today_plan_awaiting, awaiting.number)
        }
    }
    val where = if (plan.isOvertime) {
        stringResource(R.string.today_plan_overtime_meta, plan.day)
    } else {
        stringResource(R.string.today_plan_week_meta, plan.week.number, plan.plannedWeeks, plan.day)
    }
    val rest = when {
        plan.isNrtFree -> stringResource(R.string.today_plan_week_free)
        left > Dose.ZERO -> stringResource(R.string.today_plan_week_left, left.mgText())
        within -> stringResource(R.string.today_plan_week_at)
        else -> stringResource(R.string.today_plan_week_over, over.mgText())
    }
    return "$where $rest"
}

/** The key under the columns: used, the guide's line and the green dot, or for an NRT-free week, with no bars, the dot alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanWeekKey(plan: PlanToday) {
    FlowRow(
        Modifier
            .padding(top = 14.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!plan.isNrtFree) KeyItem({ UsedKey() }, stringResource(R.string.today_plan_key_used))
        if (plan.isNrtFree) {
            KeyItem({ WithinKey() }, stringResource(R.string.today_plan_key_free))
        } else {
            KeyItem({ GuideLineKey() }, stringResource(R.string.today_plan_key_guide, plan.guide.mgText()))
            KeyItem({ WithinKey() }, stringResource(R.string.today_plan_key_within))
        }
    }
}

@Composable
private fun KeyItem(sample: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sample()
        Text(text, style = LastHit.type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium), color = LastHit.colors.text2)
    }
}

/** Under the calendar week while a plan waits for its first day: "Your plan starts tomorrow." */
@Composable
internal fun PlanStartsLine(startsOn: LocalDate, today: LocalDate) {
    Text(
        if (startsOn == today.plusDays(1)) {
            stringResource(R.string.today_plan_starts_tomorrow)
        } else {
            stringResource(R.string.today_plan_starts_on, DayTitleFormatter.format(startsOn))
        },
        style = LastHit.type.label.copy(fontWeight = FontWeight.Medium),
        color = LastHit.colors.text2,
        modifier = Modifier.padding(top = 4.dp),
    )
}
