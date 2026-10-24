package app.thelasthit.nrt.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.UsualDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The plan being built: its length, start day and weeks, edited week by week before it starts. */
data class BuildState(
    val usual: UsualDay,
    val length: ProgrammeLength,
    val plan: Programme,
    val today: LocalDate,
    val demonName: String?,
)

/**
 * Builds the plan from the usual day kept: 12 weeks from tomorrow unless picked otherwise. A
 * new length builds it again; a new start day moves the weeks and keeps their edits.
 */
class BuildViewModel(private val graph: DataGraph) : ViewModel() {

    private val _state = MutableStateFlow<BuildState?>(null)
    val state: StateFlow<BuildState?> = _state.asStateFlow()

    init {
        viewModelScope.launch { build(ProgrammeLength.DEFAULT, start = null) }
    }

    private suspend fun build(length: ProgrammeLength, start: LocalDate?) {
        val record = graph.programmes.current() ?: return
        val usual = record.usualDay ?: return
        val plan = graph.programmes.buildPlan(length, start) ?: return
        val today = graph.programmes.clock().today
        _state.value = BuildState(usual, length, plan, today, graph.programmes.nextDemon().name)
    }

    fun pickLength(length: ProgrammeLength) {
        val current = _state.value ?: return
        if (length == current.length) return
        viewModelScope.launch { build(length, current.plan.startDate) }
    }

    /** Moves the plan to start on [date], keeping each week as it is. */
    fun pickStart(date: LocalDate) = _state.update { current ->
        if (current == null || date < current.today) {
            current
        } else {
            val weeks = current.plan.weeks.mapIndexed { index, week -> week.copy(startDate = date.plusWeeks(index.toLong())) }
            current.copy(plan = current.plan.copy(weeks = weeks))
        }
    }

    /** Moves week [number] to [step] on the ladder, and later weeks down with it if they'd be above it. */
    fun editWeek(number: Int, step: Int) = _state.update { current ->
        current?.copy(plan = runCatching { current.plan.edit(number, step, current.today) }.getOrDefault(current.plan))
    }

    /** Starts the plan as it stands, then [then] goes back to the Plan tab. */
    fun start(then: () -> Unit) {
        val current = _state.value ?: return
        viewModelScope.launch {
            graph.programmes.startPlan(current.plan)
            then()
        }
    }
}
