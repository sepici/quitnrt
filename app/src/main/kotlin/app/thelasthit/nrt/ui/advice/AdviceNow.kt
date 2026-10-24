package app.thelasthit.nrt.ui.advice

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.advice.AdviceIndex
import app.thelasthit.nrt.domain.advice.AdviceMatcher
import app.thelasthit.nrt.domain.advice.AdviceWindow
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.domain.advice.Shift
import app.thelasthit.nrt.domain.advice.Slip
import app.thelasthit.nrt.domain.advice.Slips
import app.thelasthit.nrt.domain.advice.Strategy
import app.thelasthit.nrt.domain.advice.TimingTip
import app.thelasthit.nrt.domain.advice.TodayAdvice
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.minuteTicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The advice for one logical day, from the 14 days before it (STATUS: 14 days everywhere): what
 * Today's line and Insights' triggers show, with the tags to name it by. [advice] is null when
 * there's too little logged, and no slip to answer.
 */
data class AdviceNow(
    val date: LocalDate,
    val advice: TodayAdvice?,
    /** Tag key to the tag, for the user's own names. */
    val tags: Map<String, TagEntity>,
)

/**
 * [AdviceNow] for each logical day as it comes. With [slips], a running programme's slip (a missed
 * week, or yesterday over its guide) brings the advice for that first, as on Today. Insights leaves
 * it out, since its advice is about triggers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun DataGraph.adviceNow(zone: () -> ZoneId, slips: Boolean): Flow<AdviceNow> {
    // The library is read on first use, off the main thread with the rest of the work.
    val matcher by lazy { AdviceMatcher(advice) }
    val today = combine(minuteTicks(clock), settings.settings) { now, settings -> settings.dayBoundary.logicalDate(now, zone()) }
        .distinctUntilChanged()
    return today.flatMapLatest { date ->
        val window = AdviceWindow.of(date)
        val plan = if (slips) {
            programmes.observeCurrent().map { record ->
                record?.plan?.takeIf { record.status == ProgrammeStatus.ACTIVE || record.status == ProgrammeStatus.OVERTIME }
            }.distinctUntilChanged()
        } else {
            flowOf(null)
        }
        combine(entries.observeRange(window.start, window.end), tags.all, products.all, settings.settings, plan) { entries, tags, products, settings, running ->
            val tagMap = tags.associateBy { it.key }
            val groupOf: (String) -> TagGroup? = { key -> tagMap[key]?.group ?: BuiltInTags.find(key)?.group }
            val slip = Slips.onToday(running, date, entries)
            val personal = matcher.personal(entries, date, settings.dayStart, groupOf, products.map { it.form }.toSet(), slip)
            AdviceNow(date, personal.today, tagMap)
        }
    }.flowOn(Dispatchers.Default)
}

// The words, shared by Today's line, the tip after logging and Insights.

/** A tag's name: the user's own, or the built-in one's. */
@Composable
internal fun tagName(tags: Map<String, TagEntity>, key: String): String = tags[key]?.let { tagText(it) } ?: tagText(key)

/**
 * What to try, as a sentence: a strategy's title, which is always something to do ("Get up from
 * the table."), or the timers' and the 4 Ds' own lines, whose titles don't read as one.
 */
@Composable
internal fun strategyLine(strategy: Strategy): String = when {
    strategy.interactive == Interactive.BREATHING_TIMER -> stringResource(R.string.advice_line_breathing)
    strategy.interactive == Interactive.DELAY_TIMER -> stringResource(R.string.advice_line_delay)
    strategy.key == AdviceIndex.FOUR_DS -> stringResource(R.string.advice_line_four_ds)
    else -> stringResource(R.string.advice_line_strategy, strategy.title)
}

/** A timing tip's time to try: "Try having your dose at 12:30, before lunch.", from the slot with the most doses. */
@Composable
internal fun timingLine(tip: TimingTip, time: DateTimeFormatter): String {
    val lead = tip.lead
    val at = time.format(lead.suggested)
    return when {
        tip.shift == Shift.LATER -> stringResource(R.string.advice_tip_later, at)
        else -> lead.meal?.let { stringResource(R.string.advice_line_before_meal, at, stringResource(it.word())) }
            ?: stringResource(R.string.advice_line_before_eating, at)
    }
}

/** "13:00", "13:00 and 19:00", "08:00, 13:00 and 19:00". */
@Composable
internal fun timesText(times: List<String>): String = when (times.size) {
    0 -> ""
    1 -> times.single()
    2 -> stringResource(R.string.advice_and, times[0], times[1])
    else -> stringResource(R.string.advice_in_times, times.dropLast(2).joinToString(", "), times[times.size - 2], times.last())
}

/** The reason for Today's advice and what to try, with the tag, or the week or day that went over, in bold. */
@Composable
internal fun todayLineText(advice: TodayAdvice, tags: Map<String, TagEntity>, time: DateTimeFormatter): AnnotatedString {
    val reason = when (val why = advice.reason) {
        is TodayAdvice.Reason.Trigger -> boldArgument(stringResource(R.string.advice_line_trigger), tagName(tags, why.trigger.key))
        is TodayAdvice.Reason.AfterSlip -> when (why.slip) {
            Slip.MISSED_WEEK ->
                boldArgument(stringResource(R.string.advice_line_missed_week), stringResource(R.string.advice_line_last_week))
            Slip.OVER_GUIDE ->
                boldArgument(stringResource(R.string.advice_line_over_guide), stringResource(R.string.advice_line_yesterday))
        }
    }
    val timing = advice.timing
    val action = when {
        advice.reason is TodayAdvice.Reason.AfterSlip -> null
        timing != null -> timingLine(timing, time)
        else -> strategyLine(advice.strategy)
    }
    return if (action == null) reason else reason + AnnotatedString(" $action")
}

/**
 * [template], a string resource read without formatting, with its "%1$s" swapped for [argument]
 * set in bold. The argument can sit anywhere in the sentence.
 */
internal fun boldArgument(template: String, argument: String): AnnotatedString = buildAnnotatedString {
    val at = template.indexOf(ARGUMENT)
    if (at < 0) {
        append(template)
        return@buildAnnotatedString
    }
    append(template.substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(argument) }
    append(template.substring(at + ARGUMENT.length))
}

private const val ARGUMENT = "%1\$s"
