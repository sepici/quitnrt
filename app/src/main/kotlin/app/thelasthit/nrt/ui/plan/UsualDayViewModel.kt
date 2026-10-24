package app.thelasthit.nrt.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.programme.BaselineDays
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.Mix
import app.thelasthit.nrt.domain.programme.MixItem
import app.thelasthit.nrt.domain.programme.UsualDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One product's whole units in the usual day being set. */
data class UnitRow(val product: UserProduct, val units: Int) {
    val total: Dose get() = product.perUnit * units
}

/** The usual day page: the products and their units, and for one from the logs, what the logs showed. */
data class UsualDayState(
    val mode: UsualDayMode,
    val rows: List<UnitRow>,
    /** The days a measured usual day came from, and the usual day they made before any change. */
    val days: BaselineDays?,
    val measured: UsualDay?,
    /** The user's other products, which "Add a product" offers. */
    val others: List<UserProduct>,
) {
    val total: Dose get() = rows.map { it.total }.sum()

    val mix: Mix get() = Mix.of(rows.filter { it.units > 0 }.map { MixItem(it.product, it.units) })
}

/**
 * Checks the usual day the baseline measured, or types one in, product by product. Each row
 * steps a whole unit at a time. Keeping it moves on to building the plan.
 */
class UsualDayViewModel(private val graph: DataGraph, private val mode: UsualDayMode) : ViewModel() {

    private val units = MutableStateFlow<Map<String, Int>?>(null)
    private val added = MutableStateFlow<List<String>>(emptyList())
    private val loaded = MutableStateFlow<Loaded?>(null)

    private data class Loaded(val start: UsualDay?, val days: BaselineDays?, val measured: UsualDay?)

    init {
        viewModelScope.launch {
            val record = graph.programmes.current()
            val kept = record?.usualDay
            val days = if (mode == UsualDayMode.MEASURED) graph.programmes.measureBaseline() else null
            val measured = if (mode == UsualDayMode.MEASURED) graph.programmes.measuredUsualDay() else null
            val start = when (mode) {
                UsualDayMode.MEASURED -> kept?.takeIf { it.source == BaselineSource.MEASURED } ?: measured
                UsualDayMode.TYPED -> kept
            }
            units.value = start?.mix?.items?.associate { it.product.id to it.units }.orEmpty()
            loaded.value = Loaded(start, days, measured)
        }
    }

    val state: StateFlow<UsualDayState?> =
        combine(loaded, units, added, graph.products.everything) { loaded, units, added, products ->
            if (loaded == null || units == null) return@combine null
            val byId = products.associateBy { it.id }
            val first = loaded.start?.mix?.products.orEmpty().map { byId[it.id] ?: it }
            val rest = when (mode) {
                // Typing a day lists every product in use, so a 0 can become a 1.
                UsualDayMode.TYPED -> products.filter { !it.isHidden && first.none { kept -> kept.id == it.id } }.sortedBy { it.name.lowercase() }
                UsualDayMode.MEASURED -> emptyList()
            }
            val extra = added.mapNotNull { byId[it] }.filter { product -> (first + rest).none { it.id == product.id } }
            val listed = first + rest + extra
            UsualDayState(
                mode = mode,
                rows = listed.map { UnitRow(it, units[it.id] ?: 0) },
                days = loaded.days,
                measured = loaded.measured,
                others = products.filter { product -> listed.none { it.id == product.id } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One unit more or fewer of [productId], never below none or above [MAX_UNITS]. */
    fun step(productId: String, by: Int) = units.update { current ->
        val now = current.orEmpty()
        now + (productId to ((now[productId] ?: 0) + by).coerceIn(0, MAX_UNITS))
    }

    /** Adds one of the user's other products to the list, at one unit a day. */
    fun add(productId: String) {
        added.update { it + productId }
        units.update { current -> current.orEmpty() + (productId to maxOf(current.orEmpty()[productId] ?: 0, 1)) }
    }

    /** Keeps the usual day, then [then] goes on to build the plan. */
    fun keep(then: () -> Unit) {
        val current = state.value ?: return
        val mix = current.mix
        if (mix.isEmpty) return
        val usual = if (mode == UsualDayMode.MEASURED && current.measured != null) {
            UsualDay(mix, BaselineSource.MEASURED, current.measured.measured)
        } else {
            UsualDay.typed(mix)
        }
        viewModelScope.launch {
            graph.programmes.keepUsualDay(usual)
            then()
        }
    }

    companion object {
        const val MAX_UNITS = 99
    }
}
