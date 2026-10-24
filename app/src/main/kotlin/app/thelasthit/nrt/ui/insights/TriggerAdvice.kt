package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.advice.Shift
import app.thelasthit.nrt.domain.advice.TodayAdvice
import app.thelasthit.nrt.ui.advice.AdviceLinks
import app.thelasthit.nrt.ui.advice.AdviceNow
import app.thelasthit.nrt.ui.advice.adviceNow
import app.thelasthit.nrt.ui.advice.boldArgument
import app.thelasthit.nrt.ui.advice.strategyLine
import app.thelasthit.nrt.ui.advice.tagName
import app.thelasthit.nrt.ui.advice.timesText
import app.thelasthit.nrt.ui.advice.timingLine
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.today.AdviceActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/**
 * The advice Insights' triggers show: Today's advice for the top trigger, from the last 14 days
 * whatever range or kind of product Insights shows. A slip's advice stays on Today.
 */
class TriggerAdviceViewModel(graph: DataGraph, zone: () -> ZoneId = { ZoneId.systemDefault() }) : ViewModel() {
    val advice: StateFlow<AdviceNow?> = graph.adviceNow(zone, slips = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The tag [now]'s advice is for, or null when there's none. */
internal val AdviceNow.triggerKey: String?
    get() = (advice?.reason as? TodayAdvice.Reason.Trigger)?.trigger?.key

/**
 * The advice under the top trigger's row, from advice-insights.html: "From your last 14 days", the
 * usual times and the time to try, or the strategy and why it helps, then "How to do it" and "All
 * advice". [atTop] when the tag isn't among the rows shown, so the card names it.
 */
@Composable
internal fun TriggerAdviceCard(now: AdviceNow, atTop: Boolean, links: AdviceLinks, modifier: Modifier = Modifier) {
    val advice = now.advice ?: return
    val key = now.triggerKey ?: return
    val colors = LastHit.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.surface), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_bulb), contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.advice_logs_eyebrow),
                style = LastHit.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.text2,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            val top = if (atTop) boldArgument(stringResource(R.string.advice_in_top), tagName(now.tags, key)) + AnnotatedString(" ") else AnnotatedString("")
            Text(
                top + adviceText(advice),
                style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp, lineBreak = LineBreak.Paragraph),
                color = colors.text,
            )
            AdviceActions(
                modifier = Modifier.padding(top = 2.dp).offset(x = (-4).dp),
                how = { links.strategy(advice.strategy.key) },
                all = links.all,
            )
        }
    }
}

/**
 * With a timing tip, when these doses come and the time to try. Otherwise the strategy, in bold,
 * and why it helps.
 */
@Composable
private fun adviceText(advice: TodayAdvice): AnnotatedString {
    val time = rememberTimeFormatter()
    val timing = advice.timing
    if (timing != null) {
        val usual = if (timing.shift == Shift.LATER) {
            stringResource(R.string.advice_logs_first, time.format(timing.lead.usual))
        } else {
            stringResource(R.string.advice_in_around, timesText(timing.slots.map { time.format(it.usual) }))
        }
        return AnnotatedString("$usual ${timingLine(timing, time)}")
    }
    val line = strategyLine(advice.strategy)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line) }
        append(" ")
        append(advice.strategy.body)
    }
}
