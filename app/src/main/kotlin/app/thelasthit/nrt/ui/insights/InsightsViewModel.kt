package app.thelasthit.nrt.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.insights.DailyIntake
import app.thelasthit.nrt.domain.insights.InsightRange
import app.thelasthit.nrt.domain.insights.InsightWindow
import app.thelasthit.nrt.domain.insights.ProductType
import app.thelasthit.nrt.domain.insights.ofType
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.ui.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** A range button: whether it's open yet, and if not, the day it opens. */
data class RangeOption(val range: InsightRange, val unlocked: Boolean, val opensOn: LocalDate?)

/** What Insights shows. With nothing logged, [firstDay] is null and the screen says so. */
data class InsightsState(
    val today: LocalDate,
    val firstDay: LocalDate?,
    val ranges: List<RangeOption>,
    val range: InsightRange,
    /** The kinds of product in the window, most mg first, plus the chosen one if it isn't. */
    val types: List<ProductType> = emptyList(),
    val type: ProductType? = null,
    val window: InsightWindow? = null,
    val intake: DailyIntake? = null,
    /** The average a day in the period before, when that period is all logged. */
    val previousAverage: Dose? = null,
    /** Product ID to its short name ("Nicorette gum"). */
    val productNames: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = firstDay == null

    /** The first range still locked, for the line under the buttons. */
    val nextLocked: RangeOption? get() = ranges.firstOrNull { !it.unlocked }

    /** Ranges too long for a column a day show a column a week. */
    val weekly: Boolean get() = range.days > DailyIntake.MAX_DAILY_COLUMNS

    /** While the 7-day line has nothing to draw yet, the day it first appears. */
    val averageStarts: LocalDate? get() = firstDay?.plusDays(DailyIntake.ROLLING_DAYS.toLong())?.takeIf { it > today }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val chosenRange = MutableStateFlow<InsightRange?>(null)
    private val chosenType = MutableStateFlow<ProductType?>(null)

    private val today: Flow<LocalDate> = combine(minuteTicks(graph.clock), graph.settings.settings) { instant, settings ->
        settings.dayBoundary.logicalDate(instant, zone())
    }.distinctUntilChanged()

    val state: StateFlow<InsightsState?> =
        combine(today, graph.entries.observeFirstDay(), chosenRange, chosenType) { today, firstDay, chosen, type ->
            Choice(today, firstDay, chosen, type)
        }.flatMapLatest { choice -> stateFor(choice) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pickRange(range: InsightRange) {
        chosenRange.value = range
    }

    fun pickType(type: ProductType?) {
        chosenType.value = type
    }

    /** Takes back a dose just logged from the empty screen, from its snackbar. */
    fun undoLog(entry: Entry) {
        viewModelScope.launch { graph.entries.delete(entry.id) }
    }

    private data class Choice(val today: LocalDate, val firstDay: LocalDate?, val range: InsightRange?, val type: ProductType?)

    private fun stateFor(choice: Choice): Flow<InsightsState> {
        val (today, firstDay) = choice
        val history = InsightRange.historyDays(today, firstDay)
        val ranges = InsightRange.entries.map { range ->
            val unlocked = range.isUnlocked(history)
            RangeOption(range, unlocked, if (unlocked || firstDay == null) null else opensOn(range, firstDay))
        }
        // Thirty days once there's that much to show, so a new week doesn't look thin.
        val range = choice.range?.takeIf { it.isUnlocked(history) }
            ?: if (InsightRange.DAYS_30.isUnlocked(history)) InsightRange.DAYS_30 else InsightRange.DAYS_7
        val base = InsightsState(today, firstDay, ranges, range)
        val window = range.window(today, firstDay)
        if (firstDay == null || window == null) return flowOf(base)
        // The period before is as long again, and it covers the 6 days the first averages reach back.
        val previous = window.previous
        return combine(graph.entries.observeRange(previous.start, today), graph.products.shortNames) { entries, names ->
            val inWindow = entries.filter { it.logicalDate in window }
            val types = ProductType.inUse(inWindow).let { inUse ->
                if (choice.type == null || choice.type in inUse) inUse else inUse + choice.type
            }
            val chosen = entries.ofType(choice.type)
            base.copy(
                types = types,
                type = choice.type,
                window = window,
                intake = DailyIntake.of(window, today, firstDay, chosen),
                previousAverage = if (window.hasFullPrevious(firstDay)) {
                    DailyIntake.of(previous, today, firstDay, chosen.filter { it.logicalDate in previous }).averagePerDay
                } else {
                    null
                },
                productNames = names,
            )
        }
    }

    /** A locked range opens once there's more history than the range below it covers. */
    private fun opensOn(range: InsightRange, firstDay: LocalDate): LocalDate =
        firstDay.plusDays(InsightRange.entries[range.ordinal - 1].days.toLong())
}
