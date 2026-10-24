package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.GoalProgress
import app.thelasthit.nrt.domain.programme.GoalWeeks
import app.thelasthit.nrt.ui.components.TickDot
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.plan.goalLabel
import app.thelasthit.nrt.ui.plan.goalSetting
import app.thelasthit.nrt.ui.plan.keptInLine
import app.thelasthit.nrt.ui.plan.keptValue
import app.thelasthit.nrt.ui.plan.sameSetting
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * Your goals (PLAN §6.3): a tile for each extra goal on this programme week or last, in Habits'
 * tiles, with this week so far as its figure and last week under it. A day counts once it's over.
 * Colours stay neutral, and only a goal kept every time this week gets the guide's green tick, as
 * at or under. Goals never change whether a week is met.
 */
@Composable
internal fun GoalsSection(state: InsightsState) {
    val goals = state.goals ?: return
    Section(title = stringResource(R.string.goals_title), action = {}) {
        Meta(stringResource(R.string.goals_meta))
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            goals.caps.forEach { GoalTile(it, goals) }
            goals.gap?.let { GoalTile(it, goals) }
            goals.firstDose?.let { GoalTile(it, goals) }
        }
    }
}

/** One goal: named as this week has it, or last week when it's off now, with both weeks' counts. */
@Composable
private fun GoalTile(line: GoalWeeks.Line<out GoalProgress.Count>, weeks: GoalWeeks) {
    val resources = LocalResources.current
    val time = rememberTimeFormatter()
    val now = line.thisWeek
    val before = line.lastWeek
    val named = now ?: before ?: return
    Tile(resources.goalLabel(named, time), Modifier.fillMaxWidth()) {
        val value = now?.let { resources.keptValue(it) }
        if (now != null && value != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TileValue(numbersLarge(value))
                if (now.isAllKept) TickDot(20.dp, fill = LastHit.colors.success, ink = LastHit.colors.bg, modifier = Modifier.padding(top = 2.dp))
            }
        } else {
            val quiet = when {
                now == null -> R.string.goals_off_this_week
                now is GoalProgress.Gap && (weeks.thisWeek?.days ?: 0) > 0 -> R.string.goals_no_gaps_yet
                else -> R.string.goals_no_days_yet
            }
            Text(
                stringResource(quiet),
                style = LastHit.type.headline.copy(fontSize = 20.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
                color = LastHit.colors.text2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val week = weeks.lastWeek?.week?.number
        if (before != null && week != null) {
            val kept = resources.keptInLine(before) ?: stringResource(R.string.goals_no_gaps_in_line)
            val text = if (now != null && sameSetting(now, before)) {
                stringResource(R.string.goals_week, week, kept)
            } else {
                stringResource(R.string.goals_week_with, week, resources.goalSetting(before, time), kept)
            }
            Text(text, style = LastHit.type.label, color = LastHit.colors.text2, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A tile value's words, a size down from its numbers, as Habits' durations have their units. */
private val SmallWords = SpanStyle(fontSize = 18.sp, letterSpacing = (-0.3).sp)

/** [text] with its numbers at the tile's size and its words a size down: "Kept on 6 of 7 days". */
private fun numbersLarge(text: String): AnnotatedString = buildAnnotatedString {
    var start = 0
    while (start < text.length) {
        val digit = text[start].isDigit()
        var end = start
        while (end < text.length && text[end].isDigit() == digit) end++
        if (digit) append(text.substring(start, end)) else withStyle(SmallWords) { append(text.substring(start, end)) }
        start = end
    }
}
