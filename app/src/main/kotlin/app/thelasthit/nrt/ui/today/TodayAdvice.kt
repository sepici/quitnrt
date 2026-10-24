package app.thelasthit.nrt.ui.today

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.advice.AdviceIndex
import app.thelasthit.nrt.domain.advice.AdviceMatcher
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.domain.advice.ShowingTip
import app.thelasthit.nrt.domain.advice.Slips
import app.thelasthit.nrt.domain.advice.Strategy
import app.thelasthit.nrt.domain.advice.TipCard
import app.thelasthit.nrt.domain.advice.TodayAdvice
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.ui.advice.AdviceLink
import app.thelasthit.nrt.ui.advice.AdviceLinks
import app.thelasthit.nrt.ui.advice.adviceNow
import app.thelasthit.nrt.ui.advice.tagName
import app.thelasthit.nrt.ui.advice.todayLineText
import app.thelasthit.nrt.ui.components.IconTile
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** A tip after logging that's on Today, with its strategy from the library. */
data class TipShown(val strategy: Strategy, val tip: ShowingTip)

/** Today's advice: the line under the hero, or the tip after logging in its place. */
data class TodayAdviceState(
    val date: LocalDate,
    /** The line's advice, or null with too little logged and no slip. */
    val line: TodayAdvice?,
    /** The tip after logging, until it's closed or the day ends. It takes the line's place. */
    val tip: TipShown?,
    val tags: Map<String, TagEntity>,
)

/**
 * Today's advice (PLAN §9 and §15): the line from the last 14 days, settled by the date, and the
 * tip straight after a dose is logged on Today, kept on this phone until it's closed or the day
 * ends. A tip after a slip comes when a dose takes today over its guide.
 */
class TodayAdviceViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val matcher by lazy { AdviceMatcher(graph.advice) }

    val state: StateFlow<TodayAdviceState?> = combine(graph.adviceNow(zone, slips = true), graph.reminders.adviceTip) { now, card ->
        TodayAdviceState(
            date = now.date,
            line = now.advice,
            tip = card?.showingOn(now.date)?.let { showing -> graph.advice.find(showing.strategy)?.let { TipShown(it, showing) } },
            tags = now.tags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * A dose was logged on Today, from the log sheet or a quick-log pill: its tip, if it brings
     * one. The tip is the best among the tags that haven't had one today, picked in the same edit
     * that keeps it, so two doses close together can't both have a tag's tip.
     */
    fun afterLogging(entry: Entry): Job = viewModelScope.launch {
        val today = today()
        val record = graph.programmes.current()
        val plan = record?.plan?.takeIf { record.status == ProgrammeStatus.ACTIVE || record.status == ProgrammeStatus.OVERTIME }
        val day = graph.entries.observeDay(today).first().entries
        val slip = Slips.afterLog(plan, entry, day, today)
        val tags = graph.tags.all.first().associateBy { it.key }
        val groupOf: (String) -> TagGroup? = { key -> tags[key]?.group ?: BuiltInTags.find(key)?.group }
        graph.reminders.updateAdviceTip { card ->
            val shown = card?.takeIf { it.date == today }?.shown.orEmpty()
            TipCard.afterLog(card, today, matcher.afterLogging(entry, groupOf, slip, shown), entry.id)
        }
    }

    /** The cross on the tip. Its tag still counts as having had today's. */
    fun closeTip() {
        viewModelScope.launch {
            val today = today()
            graph.reminders.updateAdviceTip { TipCard.close(it, today) }
        }
    }

    /** Undo took [entry] back, and the tip it brought with it. */
    fun undone(entry: Entry) {
        viewModelScope.launch { graph.reminders.updateAdviceTip { TipCard.undo(it, entry.id) } }
    }

    private suspend fun today(): LocalDate = graph.settings.current().dayBoundary.logicalDate(graph.clock.instant(), zone())
}

/**
 * The advice line under the last dose, from advice-today.html: a bulb, the reason and what to
 * try, then "How to do it" and "All advice". After a slip there's nothing to try in the line, so
 * its link says "What helps".
 */
@Composable
internal fun AdviceLine(advice: TodayAdvice, tags: Map<String, TagEntity>, links: AdviceLinks) {
    val colors = LastHit.colors
    val text = todayLineText(advice, tags, rememberTimeFormatter())
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(R.drawable.ic_bulb, Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp, lineBreak = LineBreak.Paragraph),
                color = colors.text,
            )
            AdviceActions(
                modifier = Modifier.padding(top = 4.dp).offset(x = (-4).dp),
                how = { links.strategy(advice.strategy.key) },
                all = links.all,
                howText = stringResource(if (advice.reason is TodayAdvice.Reason.AfterSlip) R.string.advice_slip_how else R.string.advice_line_how),
            )
        }
    }
}

/** "How to do it" and "All advice", as the advice line and Insights have them. */
@Composable
internal fun AdviceActions(
    modifier: Modifier = Modifier,
    how: () -> Unit,
    all: () -> Unit,
    howText: String = stringResource(R.string.advice_line_how),
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        AdviceLink(howText, how)
        AdviceLink(stringResource(R.string.advice_all), all)
    }
}

/**
 * The tip after logging, from advice-after-log.html: a card in the advice line's place with what
 * to try next time and why, a way to try it, "Other ideas", and a cross that closes it.
 */
@Composable
internal fun TipAfterLogging(shown: TipShown, tags: Map<String, TagEntity>, links: AdviceLinks, onClose: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(24.dp)
    val strategy = shown.strategy
    // Read out once, as it arrives after a dose. A pane title would be said again each time the
    // card scrolled back into view, so the title is a live region only while the tip is new.
    var fresh by rememberSaveable(shown.tip.entryId) { mutableStateOf(true) }
    LaunchedEffect(shown.tip.entryId) {
        delay(TIP_NEW_FOR_MS)
        fresh = false
    }
    Box(
        Modifier
            .padding(start = 8.dp, end = 8.dp, top = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (colors.isDark) colors.lineStrong else colors.line, shape),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp)) {
            Text(
                tipTitle(shown),
                style = LastHit.type.headline.copy(fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp, lineBreak = LineBreak.Heading),
                color = colors.text,
                modifier = Modifier
                    .padding(end = 48.dp)
                    .semantics {
                        heading()
                        if (fresh) liveRegion = LiveRegionMode.Polite
                    },
            )
            Text(
                tipBody(shown, tags),
                style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp, lineBreak = LineBreak.Paragraph),
                color = colors.text2,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
            )
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                when (strategy.interactive) {
                    Interactive.BREATHING_TIMER -> TipButton(R.drawable.ic_wind, stringResource(R.string.advice_tip_breathe), links.breatheNow)
                    Interactive.DELAY_TIMER -> TipButton(R.drawable.ic_timer, stringResource(R.string.advice_open_timer)) {
                        links.timer(Interactive.DELAY_TIMER)
                    }
                    null -> TipButton(
                        R.drawable.ic_bulb,
                        stringResource(if (shown.tip.slip != null) R.string.advice_slip_how else R.string.advice_tip_how),
                    ) { links.strategy(strategy.key) }
                }
                AdviceLink(stringResource(R.string.advice_tip_other), links.all)
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 2.dp)
                .size(48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.advice_tip_close), tint = colors.text2)
        }
    }
}

/** How long a tip counts as new, and so is read out as it arrives: long enough for the snackbar before it. */
private const val TIP_NEW_FOR_MS = 10_000L

/** "Try box breathing next time", or for most strategies "Next time, get up from the table". */
@Composable
private fun tipTitle(shown: TipShown): String {
    val strategy = shown.strategy
    return when {
        shown.tip.slip != null -> stringResource(R.string.advice_tip_title_slip)
        strategy.interactive == Interactive.BREATHING_TIMER -> stringResource(R.string.advice_tip_title_breathing)
        strategy.interactive == Interactive.DELAY_TIMER -> stringResource(R.string.advice_tip_title_delay)
        strategy.key == AdviceIndex.FOUR_DS -> stringResource(R.string.advice_tip_title_four_ds)
        else -> stringResource(R.string.advice_tip_title, strategy.title.lowerFirst())
    }
}

/** Why: the tag the dose had, then what the strategy does. After a slip, that it doesn't undo anything. */
@Composable
private fun tipBody(shown: TipShown, tags: Map<String, TagEntity>): String {
    if (shown.tip.slip != null) return stringResource(R.string.advice_tip_slip_body)
    val why = if (shown.strategy.interactive == Interactive.BREATHING_TIMER) {
        stringResource(R.string.advice_tip_breathing_body)
    } else {
        shown.strategy.body
    }
    val tag = shown.tip.tag ?: return why
    return stringResource(R.string.advice_tip_tagged, tagName(tags, tag)) + " " + why
}

/** A title in sentence case, made to follow "Next time,": its first letter in lower case, unless it starts a word like NRT. */
private fun String.lowerFirst(): String =
    if (length > 1 && this[1].isUpperCase()) this else replaceFirstChar { it.lowercaseChar() }

/** The tip's outlined button, as the mockup's btn-line in the card: 48 dp tall, with its icon. */
@Composable
private fun TipButton(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.5.dp, colors.lineStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        Text(text, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
    }
}
