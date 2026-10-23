package app.thelasthit.nrt.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.insights.ByProduct
import app.thelasthit.nrt.domain.insights.CalendarHeat
import app.thelasthit.nrt.domain.insights.DailyIntake
import app.thelasthit.nrt.domain.insights.Habits
import app.thelasthit.nrt.domain.insights.HourHeatMap
import app.thelasthit.nrt.domain.insights.InsightRange
import app.thelasthit.nrt.domain.insights.InsightWindow
import app.thelasthit.nrt.domain.insights.ProductType
import app.thelasthit.nrt.domain.insights.Triggers
import app.thelasthit.nrt.domain.insights.ofType
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.time.DayBoundary
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
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
    /** Where the hour map's rows start. */
    val dayStart: LocalTime = DayBoundary.DEFAULT_DAY_START,
    val hours: HourHeatMap? = null,
    val calendar: CalendarHeat? = null,
    /** The window has patches beside other doses, and the hour map leaves them out. */
    val patchesLeftOut: Boolean = false,
    /** Everything in the window is a patch, so the hour map, triggers and habits have nothing to show. */
    val onlyPatches: Boolean = false,
    /** Tag key to the tag, for its name. */
    val tags: Map<String, TagEntity> = emptyMap(),
    val triggers: Triggers? = null,
    val habits: Habits? = null,
    /**
     * Over the window's finished days, or today alone when it's the only day so far or the only
     * one with doses, so a kind first used today shows what's been used so far.
     */
    val byProduct: ByProduct? = null,
    /** The finished days [byProduct] covers, or 0 when it's today alone. */
    val productDays: Int = 0,
    /** While the period before isn't all logged, the day changes against it start. */
    val comparesFrom: LocalDate? = null,
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

    private val dayStart: Flow<LocalTime> = graph.settings.settings.map { it.dayStart }.distinctUntilChanged()

    val state: StateFlow<InsightsState?> =
        combine(today, graph.entries.observeFirstDay(), chosenRange, chosenType, dayStart) { today, firstDay, chosen, type, dayStart ->
            Choice(today, firstDay, chosen, type, dayStart)
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

    private data class Choice(
        val today: LocalDate,
        val firstDay: LocalDate?,
        val range: InsightRange?,
        val type: ProductType?,
        val dayStart: LocalTime,
    )

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
        val base = InsightsState(today, firstDay, ranges, range, dayStart = choice.dayStart)
        val window = range.window(today, firstDay)
        if (firstDay == null || window == null) return flowOf(base)
        // The period before is as long again, and it covers the 6 days the first averages reach back.
        val previous = window.previous
        return combine(graph.entries.observeRange(previous.start, today), graph.products.shortNames, graph.tags.all) { entries, names, tags ->
            val inWindow = entries.filter { it.logicalDate in window }
            val types = ProductType.inUse(inWindow).let { inUse ->
                if (choice.type == null || choice.type in inUse) inUse else inUse + choice.type
            }
            val chosen = entries.ofType(choice.type)
            val chosenInWindow = chosen.filter { it.logicalDate in window }
            val (patches, others) = chosenInWindow.partition { it.isBackground }
            // The period before, only when it's all logged, so a change never counts days from before the first.
            val fullPrevious = window.hasFullPrevious(firstDay)
            val before = if (fullPrevious) chosen.filter { it.logicalDate in previous } else null
            val tagMap = tags.associateBy { it.key }
            val finished = window.dates.count { it != today }
            val finishedEntries = chosenInWindow.filter { it.logicalDate != today }
            // Today alone when it's the only day so far, or the only one with any of this kind yet.
            val todayOnly = finished == 0 || finishedEntries.isEmpty()
            base.copy(
                types = types,
                type = choice.type,
                window = window,
                intake = DailyIntake.of(window, today, firstDay, chosen),
                previousAverage = before?.let { DailyIntake.of(previous, today, firstDay, it).averagePerDay },
                productNames = names,
                hours = HourHeatMap.of(chosenInWindow, choice.dayStart, window.dates),
                calendar = CalendarHeat.of(range, today, firstDay, chosenInWindow),
                patchesLeftOut = patches.isNotEmpty() && others.isNotEmpty(),
                onlyPatches = patches.isNotEmpty() && others.isEmpty(),
                tags = tagMap,
                triggers = Triggers.of(chosenInWindow, before, groupOf = { tagMap[it]?.group }),
                habits = Habits.of(chosenInWindow, before, choice.dayStart),
                byProduct = if (todayOnly) ByProduct.of(chosenInWindow, days = 1) else ByProduct.of(finishedEntries, days = finished),
                productDays = if (todayOnly) 0 else finished,
                // The period before is all logged once the first day is two ranges back.
                comparesFrom = if (fullPrevious) null else firstDay.plusDays(range.days * 2L - 1),
            )
        }
    }

    /** A locked range opens once there's more history than the range below it covers. */
    private fun opensOn(range: InsightRange, firstDay: LocalDate): LocalDate =
        firstDay.plusDays(InsightRange.entries[range.ordinal - 1].days.toLong())
}
