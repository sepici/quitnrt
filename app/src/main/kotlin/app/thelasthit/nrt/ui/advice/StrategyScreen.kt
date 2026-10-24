package app.thelasthit.nrt.ui.advice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.advice.AdviceIndex
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.domain.advice.Shift
import app.thelasthit.nrt.domain.advice.Strategy
import app.thelasthit.nrt.domain.advice.StrategyContext
import app.thelasthit.nrt.domain.advice.TimingTip
import app.thelasthit.nrt.domain.advice.hourly
import app.thelasthit.nrt.domain.advice.minutesIntoDay
import app.thelasthit.nrt.domain.insights.HeatScale
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.heatFill
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One strategy (PLAN §9): what it is, what it's good for, what someone's own logs say about
 * it, how to do it, other ideas for the same moment, and where the advice comes from.
 */
@Composable
fun StrategyScreen(
    strategyKey: String,
    onBack: () -> Unit,
    onAllAdvice: () -> Unit,
    onStrategy: (String) -> Unit,
    onTimer: (Interactive) -> Unit,
    viewModel: AdviceViewModel = graphViewModel(key = "strategy-$strategyKey") { AdviceViewModel(it) },
) {
    val strategy = remember(strategyKey) { viewModel.strategy(strategyKey) }
    if (strategy == null) {
        // A link to a strategy the library no longer has.
        LaunchedEffect(strategyKey) { onBack() }
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdvicePage(
        title = null,
        onBack = onBack,
        trailing = { AdviceLink(stringResource(R.string.advice_all), onAllAdvice, Modifier.padding(end = 8.dp)) },
    ) {
        Header(strategy, state)
        strategy.interactive?.let { interactive ->
            LineButton(
                stringResource(R.string.advice_open_timer),
                onClick = { onTimer(interactive) },
                icon = if (interactive == Interactive.BREATHING_TIMER) R.drawable.ic_wind else R.drawable.ic_timer,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp),
            )
        }
        val loaded = state
        val context = remember(strategy, loaded) { loaded?.let { viewModel.context(strategy, it) } }
        if (loaded != null && context != null && context.fromLogs) FromLogs(context, loaded)
        HowToDoIt(strategy)
        val alsoTag = context?.tag
        if (loaded != null && context != null && context.also.isNotEmpty() && alsoTag != null) {
            ListHeading(stringResource(R.string.advice_also, alsoFor(loaded, alsoTag)), top = 32.dp)
            Ruled {
                context.also.forEachIndexed { index, other ->
                    // Another strategy's page, timers' too, so its sources are always a tap away.
                    StrategyRow(
                        title = other.title,
                        sub = AnnotatedString(other.summary),
                        first = index == 0,
                        timer = other.interactive != null,
                        onClick = { onStrategy(other.key) },
                    )
                }
            }
        }
        Sources(strategy, ownTiming = context?.tip != null)
    }
}

/** The eyebrow, the title, why it helps and what it's good for. */
@Composable
private fun Header(strategy: Strategy, state: AdviceState?) {
    val colors = LastHit.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
        Text(
            sectionName(AdviceIndex.sectionOf(strategy)),
            style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
            color = colors.text2,
        )
        Text(
            strategy.title,
            style = LastHit.type.headline.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.035).em, lineBreak = LineBreak.Heading),
            color = colors.text,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        Text(
            strategy.body,
            style = LastHit.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
            color = colors.text,
            modifier = Modifier.padding(top = 12.dp),
        )
        val goodFor = if (strategy.suitsAnyTime) {
            stringResource(R.string.advice_any_craving)
        } else {
            strategy.tags.map { key -> state?.let { tagName(it, key) } ?: tagText(key) }.joinToString(", ")
        }
        FlowRow(
            Modifier.padding(top = 14.dp).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp)
            Text(stringResource(R.string.advice_good_for), style = style.copy(fontWeight = FontWeight.Bold), color = colors.text2)
            Text(goodFor, style = style.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
        }
    }
}

/** What the last 14 days of logs say, with the hour strip and the time to try when there's a timing tip. */
@Composable
private fun FromLogs(context: StrategyContext, state: AdviceState) {
    val colors = LastHit.colors
    val time = rememberTimeFormatter()
    val tag = context.tag?.let { tagName(state, it) }.orEmpty()
    val tip = context.tip
    AdviceCard(Modifier.padding(top = 24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_clock), contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.advice_logs_eyebrow), style = LastHit.type.label.copy(fontWeight = FontWeight.Bold), color = colors.text2)
            }
            val share = stringResource(R.string.advice_logs_share, context.tagged, context.doses, tag)
            val usual = when {
                tip == null -> null
                tip.shift == Shift.LATER -> stringResource(R.string.advice_logs_first, time.format(tip.lead.usual))
                else -> stringResource(R.string.advice_logs_around, joinTimes(tip.slots.map { time.format(it.usual) }))
            }
            Text(
                listOfNotNull(share, usual).joinToString(" "),
                style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = colors.text,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (tip != null) {
                HourStrip(tip, state.dayStart, time, tag)
                StripKey()
                Text(
                    tipLine(tip, time),
                    style = LastHit.type.title.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

/** "Try having your dose at 12:30 and 18:30, before you eat, instead of after." */
@Composable
private fun tipLine(tip: TimingTip, time: DateTimeFormatter): String {
    val times = joinTimes(tip.slots.map { time.format(it.suggested) })
    val meal = tip.slots.singleOrNull()?.meal
    return when {
        tip.shift == Shift.LATER -> stringResource(R.string.advice_tip_later, time.format(tip.lead.suggested))
        meal != null -> stringResource(R.string.advice_tip_meal_one, times, stringResource(meal.word()))
        else -> stringResource(R.string.advice_tip_meal_many, times)
    }
}

/** "13:00", "13:00 and 19:00", "08:00, 13:00 and 19:00". */
@Composable
private fun joinTimes(times: List<String>): String =
    if (times.size <= 1) times.firstOrNull().orEmpty() else stringResource(R.string.advice_and, times.dropLast(1).joinToString(", "), times.last())

/**
 * Doses with the tag in each hour of the logical day, shaded from fewer to more, with a caret
 * over each time the tip suggests, and the day start, 8 and 16 hours on, and the day start again
 * under it.
 */
@Composable
private fun HourStrip(tip: TimingTip, dayStart: LocalTime, time: DateTimeFormatter, tag: String) {
    val colors = LastHit.colors
    val hours = tip.hourly
    val max = hours.max().toLong()
    val counts = hours.withIndex().filter { it.value > 0 }.map { (hour, count) ->
        stringResource(R.string.advice_strip_hour, count, time.format(dayStart.plusHours(hour.toLong())))
    }
    val description = stringResource(
        R.string.advice_strip_description,
        tag,
        counts.joinToString(", "),
        joinTimes(tip.slots.map { time.format(it.suggested) }),
    )
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clearAndSetSemantics { contentDescription = description }) {
        SuggestedTimes(tip.slots.map { minutesIntoDay(it.suggested, dayStart) / MINUTES_IN_DAY }) {
            tip.slots.forEach { slot ->
                Text(
                    time.format(slot.suggested),
                    style = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hours.forEach { count ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.heatFill(HeatScale.step(count.toLong(), max))),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            val width = maxWidth
            val labels = listOf(0, 8, 16, 24)
            labels.forEach { hour ->
                Text(
                    time.format(dayStart.plusHours(hour.toLong())),
                    style = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.text3,
                    modifier = Modifier.centredAt(width * (hour / 24f), width),
                )
            }
        }
    }
}

private const val MINUTES_IN_DAY = 24f * 60f

/**
 * A caret over each suggested time, at [fractions] of the way along the strip, with its time over
 * it. The [labels] are the times, in the same order. Times close together, such as 10:00 and 11:30,
 * would overlap, so their labels move apart while the carets stay where they are.
 */
@Composable
private fun SuggestedTimes(fractions: List<Float>, labels: @Composable () -> Unit) {
    Layout(
        content = {
            labels()
            fractions.forEach { _ -> Caret(width = 12.dp, height = 7.dp) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val count = fractions.size
        val texts = measurables.take(count).map { it.measure(loose) }
        val carets = measurables.drop(count).map { it.measure(loose) }
        val width = constraints.maxWidth
        val centres = fractions.map { (it * width).roundToInt() }
        val lefts = spreadLabels(centres, texts.map { it.width }, width, gap = LABEL_GAP.roundToPx())
        val textHeight = texts.maxOfOrNull { it.height } ?: 0
        val caretTop = textHeight + 1.dp.roundToPx()
        val height = caretTop + (carets.maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            texts.forEachIndexed { index, text -> text.place(lefts[index], textHeight - text.height) }
            carets.forEachIndexed { index, caret ->
                caret.place((centres[index] - caret.width / 2).coerceIn(0, (width - caret.width).coerceAtLeast(0)), caretTop)
            }
        }
    }
}

private val LABEL_GAP = 8.dp

/**
 * Where labels of [widths] go along a strip [width] wide, as their left edges: each centred on
 * its place in [centres], which run left to right, unless that overlaps a neighbour. Labels that
 * would overlap are set side by side, [gap] apart, centred where they want to be on average. All
 * of them stay inside the strip.
 */
internal fun spreadLabels(centres: List<Int>, widths: List<Int>, width: Int, gap: Int): List<Int> {
    // Runs of neighbouring labels set side by side, from first to last.
    val runs = mutableListOf<IntRange>()
    fun spanOf(run: IntRange) = run.sumOf { widths[it] } + gap * (run.last - run.first)
    fun leftOf(run: IntRange): Int {
        var offset = 0
        var total = 0
        for (index in run) {
            total += centres[index] - offset - widths[index] / 2
            offset += widths[index] + gap
        }
        return Math.floorDiv(total, run.count())
    }
    for (index in centres.indices) {
        runs += index..index
        while (runs.size > 1) {
            val before = runs[runs.size - 2]
            val last = runs.last()
            if (leftOf(before) + spanOf(before) + gap <= leftOf(last)) break
            runs.removeAt(runs.lastIndex)
            runs[runs.lastIndex] = before.first..last.last
        }
    }
    val lefts = IntArray(centres.size)
    runs.forEach { run ->
        var x = leftOf(run)
        for (index in run) {
            lefts[index] = x
            x += widths[index] + gap
        }
    }
    // Inside the strip: in from the left edge, then from the right.
    for (index in lefts.indices) {
        val least = if (index == 0) 0 else lefts[index - 1] + widths[index - 1] + gap
        lefts[index] = maxOf(lefts[index], least)
    }
    for (index in lefts.indices.reversed()) {
        val most = if (index == lefts.lastIndex) width - widths[index] else lefts[index + 1] - widths[index] - gap
        lefts[index] = minOf(lefts[index], most)
    }
    return lefts.toList()
}

/** Places the content centred on [x], kept inside [width]. */
private fun Modifier.centredAt(x: Dp, width: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val left = (x.roundToPx() - placeable.width / 2).coerceIn(0, (width.roundToPx() - placeable.width).coerceAtLeast(0))
    layout(constraints.maxWidth, placeable.height) { placeable.place(left, 0) }
}

/** A small triangle pointing down, for a suggested time. */
@Composable
private fun Caret(modifier: Modifier = Modifier, width: Dp, height: Dp) {
    val colour = LastHit.colors.text
    Canvas(modifier.size(width, height)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2, size.height)
            close()
        }
        drawPath(path, colour)
    }
}

/** "Fewer to more doses" with three shades, then the caret, "Move it here". */
@Composable
private fun StripKey() {
    val colors = LastHit.colors
    val style = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp)
    FlowRow(
        Modifier.padding(top = 10.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(1, 3, 5).forEach { step ->
                Box(Modifier.size(14.dp, 10.dp).clip(RoundedCornerShape(2.dp)).background(colors.heatFill(step)))
            }
        }
        Text(stringResource(R.string.advice_key_doses), style = style, color = colors.text2)
        Caret(Modifier.padding(start = 12.dp), width = 10.dp, height = 6.dp)
        Text(stringResource(R.string.advice_key_move), style = style, color = colors.text2)
    }
}

/** The steps, numbered in grey circles and ruled between. */
@Composable
private fun HowToDoIt(strategy: Strategy) {
    val colors = LastHit.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
        Text(
            stringResource(R.string.advice_how_title),
            style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.padding(top = 12.dp)) {
            strategy.steps.forEachIndexed { index, step ->
                if (index > 0) HorizontalDivider(color = colors.line)
                val description = stringResource(R.string.advice_step_description, index + 1, step)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 13.dp)
                        .clearAndSetSemantics { contentDescription = description },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(colors.sunk), contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = LastHit.type.label.copy(fontWeight = FontWeight.ExtraBold),
                            color = colors.text,
                        )
                    }
                    Text(step, style = LastHit.type.body, color = colors.text, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Where the advice comes from, with the month it was checked and the usual note. */
@Composable
private fun Sources(strategy: Strategy, ownTiming: Boolean) {
    val colors = LastHit.colors
    val style = LastHit.type.label.copy(fontWeight = FontWeight.Normal)
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = colors.text)
    val checked = strategy.sources.maxOf { it.read }.format(CHECKED)
    val ownLabel = stringResource(R.string.advice_source_own_label)
    val own = stringResource(R.string.advice_source_own)
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = colors.text2,
            modifier = Modifier.padding(top = 1.dp).size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.advice_based_on), style = style.copy(fontWeight = FontWeight.Bold), color = colors.text)
            val lines = strategy.sources.map { source ->
                buildAnnotatedString {
                    withStyle(bold) { append("${source.publisher}:") }
                    append(" ${source.title}.")
                }
            } + listOfNotNull(
                if (ownTiming) {
                    buildAnnotatedString {
                        withStyle(bold) { append(ownLabel) }
                        append(" $own")
                    }
                } else {
                    null
                },
            )
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { line ->
                    Row {
                        Box(Modifier.padding(start = 2.dp, top = 8.dp, end = 8.dp).size(4.dp).clip(CircleShape).background(colors.text3))
                        Text(line, style = style, color = colors.text2)
                    }
                }
            }
            Text(
                stringResource(R.string.advice_checked, checked),
                style = style,
                color = colors.text2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private val CHECKED: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)

/** The tag in "Also for after a meal": a built-in name starts lower case there, and one the user typed stays as it is. */
@Composable
private fun alsoFor(state: AdviceState, key: String): String {
    val name = tagName(state, key)
    val own = state.tags[key]?.customName != null
    return if (own) name else name.replaceFirstChar { it.lowercase(Locale.UK) }
}

