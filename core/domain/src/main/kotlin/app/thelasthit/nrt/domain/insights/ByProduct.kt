package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm

/** Each product's share of the mg, and its units a day. Patches count here, as in every mg total. */
data class ByProduct(val products: List<Share>) {

    /**
     * [name], [form] and [unit] are as last logged; [unitsPerDay] is across every day the period
     * covers, and [days] is how many of those days had any.
     */
    data class Share(
        val productId: String,
        val name: String,
        val form: ProductForm,
        val unit: DoseUnit,
        val total: Dose,
        val share: Double,
        val units: Int,
        val unitsPerDay: Double,
        val days: Int,
    )

    companion object {
        /** [days] is how many days [entries] cover, logged or not. Most mg first. */
        fun of(entries: List<Entry>, days: Int): ByProduct {
            val all = entries.map { it.total }.sum()
            val products = entries.groupBy { it.productId }.map { (id, list) ->
                val total = list.map { it.total }.sum()
                val latest = list.maxBy { it.occurredAt }
                val units = list.sumOf { it.quantity }
                Share(
                    productId = id,
                    name = latest.productName,
                    form = latest.form,
                    unit = latest.unit,
                    total = total,
                    share = if (all == Dose.ZERO) 0.0 else total.micrograms.toDouble() / all.micrograms,
                    units = units,
                    unitsPerDay = if (days <= 0) 0.0 else units.toDouble() / days,
                    days = list.map { it.logicalDate }.distinct().size,
                )
            }.sortedWith(compareByDescending<Share> { it.total }.thenBy { it.name })
            return ByProduct(products)
        }
    }
}
