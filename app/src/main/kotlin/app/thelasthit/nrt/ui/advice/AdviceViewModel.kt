package app.thelasthit.nrt.ui.advice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.advice.AdviceIndex
import app.thelasthit.nrt.domain.advice.AdviceMatcher
import app.thelasthit.nrt.domain.advice.AdviceWindow
import app.thelasthit.nrt.domain.advice.PersonalAdvice
import app.thelasthit.nrt.domain.advice.Quitline
import app.thelasthit.nrt.domain.advice.Strategy
import app.thelasthit.nrt.domain.advice.StrategyContext
import app.thelasthit.nrt.domain.advice.StrategySection
import app.thelasthit.nrt.domain.advice.TimingTip
import app.thelasthit.nrt.domain.advice.TopTrigger
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.ui.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What the advice list and a strategy's page show, from the last 14 days of logs. */
data class AdviceState(
    val personal: PersonalAdvice,
    /** Every strategy for the products in use, by section. */
    val sections: List<StrategySection>,
    /** Tag key to the tag, for its name and group. */
    val tags: Map<String, TagEntity>,
    val forms: Set<ProductForm>,
    /** The free lines for the phone's country, from the settings. */
    val quitlines: List<Quitline>,
    val country: String?,
    val dayStart: LocalTime,
) {
    /** A tag's group, for one the user added as well as the built-in ones. */
    fun groupOf(key: String): TagGroup? = tags[key]?.group ?: BuiltInTags.find(key)?.group

    /** The timing tip that goes with [strategy] under [trigger]. */
    fun tipFor(trigger: TopTrigger, strategy: Strategy): TimingTip? =
        personal.tips.firstOrNull { it.tag == trigger.key && it.strategy == strategy.key }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdviceViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val library = graph.advice
    private val matcher = AdviceMatcher(library)

    private val today: Flow<LocalDate> = combine(minuteTicks(graph.clock), graph.settings.settings) { now, settings ->
        settings.dayBoundary.logicalDate(now, zone())
    }.distinctUntilChanged()

    val state: StateFlow<AdviceState?> = today.flatMapLatest { date ->
        val window = AdviceWindow.of(date)
        combine(
            graph.entries.observeRange(window.start, window.end),
            graph.tags.all,
            graph.products.all,
            graph.settings.settings,
        ) { entries, tags, products, settings ->
            val tagMap = tags.associateBy { it.key }
            val forms = products.map { it.form }.toSet()
            val groupOf: (String) -> TagGroup? = { key -> tagMap[key]?.group ?: BuiltInTags.find(key)?.group }
            AdviceState(
                personal = matcher.personal(entries, date, settings.dayStart, groupOf, forms),
                sections = AdviceIndex.sections(library, forms),
                tags = tagMap,
                forms = forms,
                quitlines = settings.country?.let(library::quitlinesFor).orEmpty(),
                country = settings.country,
                dayStart = settings.dayStart,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun strategy(key: String): Strategy? = library.find(key)

    /** What [strategy]'s page shows from the logs in [state]. */
    fun context(strategy: Strategy, state: AdviceState): StrategyContext =
        StrategyContext.of(strategy, state.personal, matcher, state.forms, state::groupOf)
}
