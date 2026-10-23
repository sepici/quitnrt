package app.thelasthit.nrt.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.ui.catalogue.CatalogueBrowse
import app.thelasthit.nrt.ui.catalogue.CataloguePicker
import app.thelasthit.nrt.ui.catalogue.browseFor
import app.thelasthit.nrt.ui.graphViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogueFilter(val brand: String? = null, val query: String = "", val otherCountries: Boolean = false)

data class CatalogueState(
    val country: String,
    val filter: CatalogueFilter,
    val browse: CatalogueBrowse,
    /** Products on the log sheet now. Hidden ones aren't, so they can be picked again. */
    val inUse: Set<String>,
)

class CatalogueViewModel(private val graph: DataGraph) : ViewModel() {

    private val filter = MutableStateFlow(CatalogueFilter())

    val state: StateFlow<CatalogueState?> = combine(filter, graph.settings.settings, graph.products.everything) { current, settings, products ->
        val country = settings.country ?: Markets.ELSEWHERE
        CatalogueState(
            country = country,
            filter = current,
            browse = graph.catalogue.browseFor(country, current.otherCountries, current.brand, current.query),
            inUse = products.filter { !it.isHidden }.map { it.id }.toSet(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun search(query: String) = filter.update { it.copy(query = query.take(60)) }

    fun pickBrand(brand: String) = filter.update { it.copy(brand = brand) }

    fun showOtherCountries(show: Boolean) = filter.update { it.copy(otherCountries = show, brand = null) }

    /** Adds a product, or hides it if it's already in use. A hidden one comes back. */
    fun toggle(productId: String) {
        val inUse = state.value?.inUse ?: return
        viewModelScope.launch {
            when {
                productId in inUse -> graph.products.setHidden(productId, true)
                graph.products.get(productId) != null -> graph.products.setHidden(productId, false)
                else -> graph.products.addFromCatalogue(productId)
            }
        }
    }
}

@Composable
fun CatalogueScreen(onBack: () -> Unit, viewModel: CatalogueViewModel = graphViewModel { CatalogueViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsPage(stringResource(R.string.catalogue_title), stringResource(R.string.settings_back), onBack) {
        val current = state ?: return@SettingsPage
        Intro(stringResource(R.string.catalogue_intro))
        CataloguePicker(
            browse = current.browse,
            country = current.country,
            query = current.filter.query,
            otherCountries = current.filter.otherCountries,
            picked = { it in current.inUse },
            order = { null },
            onToggle = viewModel::toggle,
            onSearch = viewModel::search,
            onBrand = viewModel::pickBrand,
            onOtherCountries = viewModel::showOtherCountries,
        )
    }
}
