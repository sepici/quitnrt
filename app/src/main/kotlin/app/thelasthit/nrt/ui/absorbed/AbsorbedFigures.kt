package app.thelasthit.nrt.ui.absorbed

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.absorbed.AbsorbedEstimate
import app.thelasthit.nrt.domain.absorbed.AbsorptionFactor
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.max

/**
 * Estimated absorbed amounts for the screens that show them beside the labelled mg: Today's
 * hero and rows, History's rows and day totals, and the log sheet's amount. Only for showing.
 * Nothing here feeds a guide, an allowance, a result, an achievement or Insights.
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

/** "about 1.1 mg absorbed", under a History day's total, and what screen readers hear on a row. */
@Composable
fun absorbedText(estimate: Dose): String = stringResource(R.string.absorbed_about, estimate.mgText())

/** "About 4 mg absorbed", a line of its own under Today's hero total. */
@Composable
fun absorbedLine(estimate: Dose): String = stringResource(R.string.absorbed_about_line, estimate.mgText())

/**
 * A dose row's estimate under its mg: "about 1 mg", since the day's total in History, or the hero
 * on Today, says "absorbed". Screen readers hear the whole "about 1 mg absorbed".
 */
@Composable
fun RowEstimate(estimate: Dose, modifier: Modifier = Modifier) {
    val heard = absorbedText(estimate)
    Text(
        stringResource(R.string.absorbed_about_short, estimate.mgText()),
        style = LastHit.type.caption,
        color = LastHit.colors.text2,
        textAlign = TextAlign.End,
        modifier = modifier.clearAndSetSemantics {
            text = AnnotatedString(heard)
            testTag = ROW_ESTIMATE_TAG
        },
    )
}

/**
 * [start] beside [amount], with [estimate] under the amount, as a dose row or a History day's
 * header has them while the setting is on. The estimate is usually wider than the amount, so when
 * it would make [start] wrap where it doesn't beside the amount alone, it takes a line of its own
 * under both instead, at the end. Either way [start] keeps the lines it has with the setting off.
 * [bottom] lines the two sides up at the bottom, as a day's header does, rather than the centre.
 */
@Composable
fun BesideTheAmount(
    gap: Dp,
    start: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    estimate: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bottom: Boolean = false,
) {
    Layout(
        content = {
            Box { start() }
            Box { amount() }
            Box { estimate() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (startPart, amountPart, estimatePart) = measurables
        val gapPx = gap.roundToPx()
        val amountPlaced = amountPart.measure(Constraints(maxWidth = constraints.maxWidth))
        val right = max(amountPlaced.width, estimatePart.maxIntrinsicWidth(Constraints.Infinity))
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            startPart.maxIntrinsicWidth(Constraints.Infinity) + gapPx + right
        }
        // The room [start] has beside the amount alone, and beside the amount with the estimate under it.
        val wide = (width - gapPx - amountPlaced.width).coerceAtLeast(0)
        val narrow = (width - gapPx - right).coerceAtLeast(0)
        fun offset(size: Int, space: Int) = if (bottom) space - size else (space - size) / 2

        if (startPart.maxIntrinsicHeight(narrow) <= startPart.maxIntrinsicHeight(wide)) {
            val startPlaced = startPart.measure(Constraints.fixedWidth(narrow))
            val estimatePlaced = estimatePart.measure(Constraints(maxWidth = right))
            val rightHeight = amountPlaced.height + estimatePlaced.height
            val height = max(startPlaced.height, rightHeight)
            layout(width, height) {
                startPlaced.placeRelative(0, offset(startPlaced.height, height))
                val top = offset(rightHeight, height)
                amountPlaced.placeRelative(width - amountPlaced.width, top)
                estimatePlaced.placeRelative(width - estimatePlaced.width, top + amountPlaced.height)
            }
        } else {
            val startPlaced = startPart.measure(Constraints.fixedWidth(wide))
            val estimatePlaced = estimatePart.measure(Constraints(maxWidth = width))
            val top = max(startPlaced.height, amountPlaced.height)
            layout(width, top + estimatePlaced.height) {
                startPlaced.placeRelative(0, offset(startPlaced.height, top))
                amountPlaced.placeRelative(width - amountPlaced.width, offset(amountPlaced.height, top))
                estimatePlaced.placeRelative(width - estimatePlaced.width, top)
            }
        }
    }
}

/** The tag on a row's estimate, for tests. */
const val ROW_ESTIMATE_TAG = "row-absorbed"

/** The tag on Today's hero estimate, which screen readers hear with the total instead, for tests. */
const val HERO_ESTIMATE_TAG = "hero-absorbed"
