package app.thelasthit.nrt.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.insights.Habits
import app.thelasthit.nrt.domain.programme.ExtraGoals
import app.thelasthit.nrt.domain.programme.ProgrammeClock
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.WeekGoals
import app.thelasthit.nrt.ui.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

/** The extra goals page: the week they're set for, its goals, and the habits that suggest them. */
data class GoalsState(
    val week: ProgrammeWeek,
    /** Whether that week is running, or still to start: before the plan, it's week 1. */
    val running: Boolean,
    /** The next taper week, for "Next week 09:15", or null when this is the last. */
    val next: ProgrammeWeek?,
    val goals: WeekGoals,
    /** The last 30 days' habits, for the hints. */
    val habits: Habits,
)

/**
 * Sets the extra goals (PLAN §6.3) for this week and the weeks after it. Each change is stored at
 * once, and none of them ever changes whether a week is met.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val today = combine(minuteTicks(graph.clock), graph.settings.settings) { now, settings ->
        ProgrammeClock(now, zone(), settings.dayBoundary).today to settings.dayStart
    }.distinctUntilChanged()

    private val habits = today.flatMapLatest { (date, dayStart) ->
        graph.entries.observeRange(date.minusDays(HABIT_DAYS), date.minusDays(1)).map { Habits.of(it, previous = null, dayStart = dayStart) }
    }

    val state: StateFlow<GoalsState?> = combine(graph.programmes.observeCurrent(), today, habits) { record, (date, _), habits ->
        val plan = record?.plan ?: return@combine null
        val week = ExtraGoals.weekToSet(plan, date) ?: return@combine null
        GoalsState(
            week = week,
            running = date >= plan.startDate,
            next = plan.weeks.getOrNull(week.number)?.takeUnless { it.isNrtFree },
            goals = week.goals,
            habits = habits,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Changes the week's goals as they're stored, rather than as the screen last showed them, so
     * quick taps in a row each build on the one before.
     */
    private fun set(change: (goals: WeekGoals, week: ProgrammeWeek, habits: Habits) -> WeekGoals) {
        val habits = state.value?.habits ?: return
        viewModelScope.launch { graph.programmes.updateGoals { week -> change(week.goals, week, habits) } }
    }

    /** The cap on units a day: on, each product starts at its units in this week's mix. */
    fun switchCaps(on: Boolean) = set { goals, week, _ -> goals.copy(maxUnitsPerDay = if (on) ExtraGoals.startingCaps(week) else emptyMap()) }

    fun stepCap(productId: String, by: Int) = set { goals, _, _ ->
        val caps = goals.maxUnitsPerDay
        val now = caps[productId] ?: return@set goals
        goals.copy(maxUnitsPerDay = caps + (productId to (now + by).coerceIn(1, MAX_CAP)))
    }

    /** The gap between doses: on, it starts at 90 minutes, and moves 15 minutes at a time. */
    fun switchGap(on: Boolean) = set { goals, _, _ -> goals.copy(minGapMinutes = if (on) DEFAULT_GAP else null) }

    fun stepGap(by: Int) = set { goals, _, _ ->
        val gap = goals.minGapMinutes ?: return@set goals
        goals.copy(minGapMinutes = (gap + by * GAP_STEP).coerceIn(GAP_STEP, MAX_GAP))
    }

    /** The first dose: on, it starts at the last 30 days' average to the quarter hour, or 08:00. */
    fun switchFirstDose(on: Boolean) = set { goals, _, habits ->
        goals.copy(firstDoseNotBefore = if (on) startingFirstDose(habits.firstDose) else null)
    }

    fun stepFirstDose(by: Int) = set { goals, _, _ ->
        val time = goals.firstDoseNotBefore ?: return@set goals
        val minutes = (time.hour * 60 + time.minute + by * ExtraGoals.STEP_MINUTES.toInt()).coerceIn(0, LATEST)
        goals.copy(firstDoseNotBefore = LocalTime.of(minutes / 60, minutes % 60))
    }

    companion object {
        const val DEFAULT_GAP = 90
        const val GAP_STEP = 15
        const val MAX_GAP = 4 * 60
        const val MAX_CAP = 99
        private const val HABIT_DAYS = 30L
        private val LATEST = ExtraGoals.LATEST_FIRST_DOSE.hour * 60 + ExtraGoals.LATEST_FIRST_DOSE.minute
        private val FALLBACK = LocalTime.of(8, 0)

        /** [average] to the nearest quarter hour, or 08:00 with no average to go on. */
        fun startingFirstDose(average: LocalTime?): LocalTime {
            if (average == null) return FALLBACK
            val minutes = ((average.hour * 60 + average.minute + 7) / 15 * 15).coerceAtMost(LATEST)
            return LocalTime.of(minutes / 60, minutes % 60)
        }
    }
}
