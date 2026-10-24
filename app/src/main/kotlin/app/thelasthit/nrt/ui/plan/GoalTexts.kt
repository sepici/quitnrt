package app.thelasthit.nrt.ui.plan

import android.content.res.Resources
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.GoalProgress
import app.thelasthit.nrt.domain.programme.MixItem
import app.thelasthit.nrt.ui.format.plural
import java.time.format.DateTimeFormatter

// How the extra goals (PLAN §6.3) are put into words, on the Plan tab and in Insights.

/** A goal by what it asks: "At most 5 pieces of 2 mg gum a day", "At least 1 h 30 min between doses", "First dose from 09:15". */
internal fun Resources.goalLabel(count: GoalProgress.Count, time: DateTimeFormatter): String = when (count) {
    is GoalProgress.Cap -> getString(R.string.goals_cap, itemText(MixItem(count.product, count.maxUnits)))
    is GoalProgress.Gap -> getString(R.string.goals_gap, gapText(count.minutes))
    is GoalProgress.FirstDose -> getString(R.string.goals_first_dose, time.format(count.notBefore))
}

/** A goal's setting alone, for a line about another week: "at most 6 pieces", "at least 1 h 15 min", "from 09:00". */
internal fun Resources.goalSetting(count: GoalProgress.Count, time: DateTimeFormatter): String = when (count) {
    is GoalProgress.Cap -> getString(R.string.goals_at_most, getQuantityString(count.product.unit.plural, count.maxUnits, count.maxUnits))
    is GoalProgress.Gap -> getString(R.string.goals_at_least, gapText(count.minutes))
    is GoalProgress.FirstDose -> getString(R.string.goals_from, time.format(count.notBefore))
}

/** Whether [a] and [b] are the same goal, set the same way, so a line about [b] needn't say its setting. */
internal fun sameSetting(a: GoalProgress.Count, b: GoalProgress.Count): Boolean = when (a) {
    is GoalProgress.Cap -> b is GoalProgress.Cap && a.product.id == b.product.id && a.maxUnits == b.maxUnits
    is GoalProgress.Gap -> b is GoalProgress.Gap && a.minutes == b.minutes
    is GoalProgress.FirstDose -> b is GoalProgress.FirstDose && a.notBefore == b.notBefore
}

/** "1 h 30 min", as the goals page shows a gap. */
internal fun Resources.gapText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> getString(R.string.plan_minutes, rest)
        rest == 0 -> getString(R.string.plan_hours, hours)
        else -> getString(R.string.plan_hours_minutes, hours, rest)
    }
}

/** "Kept on 6 of 7 days" or "18 of 22 gaps long enough", as a tile's figure, or null with nothing counted yet. */
internal fun Resources.keptValue(count: GoalProgress.Count): String? = when {
    count.of == 0 -> null
    count is GoalProgress.Gap -> getQuantityString(R.plurals.goals_kept_gaps, count.of, count.kept, count.of)
    else -> getQuantityString(R.plurals.goals_kept_days, count.of, count.kept, count.of)
}

/** [keptValue] inside a line: "kept on 6 of 7 days", or null with nothing counted yet. */
internal fun Resources.keptInLine(count: GoalProgress.Count): String? = when {
    count.of == 0 -> null
    count is GoalProgress.Gap -> getQuantityString(R.plurals.goals_kept_gaps_in_line, count.of, count.kept, count.of)
    else -> getQuantityString(R.plurals.goals_kept_days_in_line, count.of, count.kept, count.of)
}
