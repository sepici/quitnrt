package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct

/** Whole units a day of one product: "5 pieces of 4 mg gum". */
data class MixItem(val product: UserProduct, val units: Int) {
    init {
        require(units >= 1) { "A mix holds whole units of ${product.id}: $units" }
    }

    val total: Dose get() = product.perUnit * units

    /** Whether [units] a day stay within the pack's daily limit. */
    val isWithinLabel: Boolean get() = product.label.maxUnitsPerDay?.let { units <= it } ?: true
}

/**
 * A day of NRT as whole units of each product, such as 8 pieces of 4 mg gum and 3 sprays. A
 * programme week's mix is its daily guide, and an empty mix is an NRT-free day.
 *
 * Each product has one item, and items keep a fixed order (by form, strongest first, then by
 * product), so two mixes with the same units are equal however they were put together.
 */
class Mix private constructor(val items: List<MixItem>) {

    val total: Dose get() = items.map { it.total }.sum()

    val isEmpty: Boolean get() = items.isEmpty()

    /** Every unit in the mix, of every product. */
    val units: Int get() = items.sumOf { it.units }

    val products: List<UserProduct> get() = items.map { it.product }

    fun unitsOf(productId: String): Int = items.firstOrNull { it.product.id == productId }?.units ?: 0

    /** Whether no product goes over its pack's daily limit. The programme never schedules above one. */
    val isWithinLabels: Boolean get() = items.all { it.isWithinLabel }

    /** The items grouped by form, in mix order: the kinds a taper steps down through. */
    internal val kinds: List<Kind>
        get() = items.groupBy { it.product.form }.map { (form, inKind) -> Kind(form, inKind) }

    override fun equals(other: Any?): Boolean = other is Mix && other.items == items

    override fun hashCode(): Int = items.hashCode()

    override fun toString(): String =
        if (isEmpty) "Mix(none)" else items.joinToString(prefix = "Mix(", postfix = ")") { "${it.units} × ${it.product.id}" }

    /** One form's products in a mix, strongest first. */
    internal data class Kind(val form: ProductForm, val items: List<MixItem>) {
        val total: Dose get() = items.map { it.total }.sum()
        val pieces: Int get() = items.sumOf { it.units }
        val strongest: MixItem get() = items.first()

        /** Patches step down a strength at a time. Every other kind counts pieces of its logging unit. */
        val isPatch: Boolean get() = form.defaultUnit == DoseUnit.PATCH
    }

    companion object {
        val NONE = Mix(emptyList())

        /** The mix of [items], with units of the same product added together. */
        fun of(items: List<MixItem>): Mix {
            val merged = items.groupBy { it.product.id }.map { (_, same) -> MixItem(same.first().product, same.sumOf { it.units }) }
            return Mix(merged.sortedWith(ORDER))
        }

        /** Units a day of each product. A product with none is left out. */
        fun of(vararg units: Pair<UserProduct, Int>): Mix = of(units.filter { it.second > 0 }.map { MixItem(it.first, it.second) })

        private val ORDER = compareBy<MixItem> { it.product.form.ordinal }
            .thenByDescending { it.product.perUnit }
            .thenBy { it.product.id }
    }
}

/** [this] with one unit fewer of [product], dropping it when none are left. */
internal fun Mix.withOneFewer(product: UserProduct): Mix =
    Mix.of(
        items.mapNotNull { item ->
            when {
                item.product.id != product.id -> item
                item.units > 1 -> MixItem(item.product, item.units - 1)
                else -> null
            }
        },
    )

/** [this] with [form]'s items swapped for [replacement]. */
internal fun Mix.replacingKind(form: ProductForm, replacement: List<MixItem>): Mix =
    Mix.of(items.filter { it.product.form != form } + replacement)
