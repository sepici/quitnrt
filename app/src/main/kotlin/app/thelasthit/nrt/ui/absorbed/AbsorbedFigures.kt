package app.thelasthit.nrt.ui.absorbed

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.absorbed.AbsorbedEstimate
import app.thelasthit.nrt.domain.absorbed.AbsorptionFactor
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.ui.format.mgText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Estimated absorbed amounts for the screens that show them beside the labelled mg: Today's
 * hero, History's rows and day totals, and the log sheet's amount. Only for showing. Nothing
 * here feeds a guide, an allowance, a result, an achievement or Insights.
 */
class AbsorbedFigures(
    /** A product's own factor from its catalogue line, by product ID, where it has one. */
    private val own: Map<String, AbsorptionFactor> = emptyMap(),
) {
    fun of(entry: Entry): Dose? = AbsorbedEstimate.of(entry, own[entry.productId])

    /** The doses' estimates added up, or null for none, or when one of them can't be estimated. */
    fun of(entries: List<Entry>): Dose? = AbsorbedEstimate.total(entries) { own[it.productId] }

    /** For [labelled] of [product], such as the log sheet's amount before it's saved. */
    fun of(product: UserProduct, labelled: Dose): Dose? = AbsorbedEstimate.of(labelled, product.form, own[product.id])
}

/** The figures while "Show estimated absorbed amounts" is on, and null while it's off. */
fun DataGraph.absorbedFigures(): Flow<AbsorbedFigures?> = combine(
    settings.settings.map { it.showAbsorbed }.distinctUntilChanged(),
    products.everything,
) { on, products ->
    if (!on) {
        null
    } else {
        AbsorbedFigures(
            products.mapNotNull { product ->
                product.catalogueId?.let(catalogue::find)?.absorption?.let { product.id to it }
            }.toMap(),
        )
    }
}

/** "about 1.1 mg absorbed", to sit beside the labelled mg. */
@Composable
fun absorbedText(estimate: Dose): String = stringResource(R.string.absorbed_about, estimate.mgText())

/**
 * The widest an amount column grows with the estimate under it: "about 13.4 mg absorbed" fits at
 * the normal font size, and at a large one it wraps, so the product's name keeps its room.
 */
val ABSORBED_WIDTH = 144.dp
