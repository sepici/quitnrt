package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct

/**
 * The steps a taper takes from the usual day down to one unit of the weakest product (PLAN §6.2).
 * Step 0 is the usual day, and step n is `steps[n - 1]`. Each step does one thing to the kind of
 * product (gum, lozenges, sprays, patches and so on) with the most mg in the mix:
 * - **One fewer** piece, lozenge, spray or session a day, the strongest first.
 * - **A lower strength,** once the kind's mg comes to no more pieces a day at that strength than
 *   the usual day had of the kind: 4 pieces of 4 mg gum become 8 pieces of 2 mg. The switch is a
 *   step of its own, even when the mg stays the same, and it's floor(mg ÷ lower strength) pieces.
 *   A kind down to one piece that the switch doesn't cover moves to one piece of the lower
 *   strength.
 * - **Patches** step down one strength at a time (21, 14, then 7 mg), then come off.
 *
 * A tie between kinds goes to the one with the stronger unit, then to the form listed first.
 * The ladder ends at one unit of the weakest strength available in the last kind left, so it
 * can have no steps at all: one spray a day is already there.
 *
 * Steps that would put a product over its pack's daily limit are left out, since the programme
 * never schedules above one. The mg never goes up from one step to the next.
 *
 * [newProducts] are the lower strengths the steps use that weren't in the user's list when the
 * ladder was built, for the data layer to add when the plan starts. A ladder read back from
 * storage has none.
 */
data class Ladder(
    val usual: UsualDay,
    val steps: List<Mix>,
    val newProducts: List<UserProduct> = emptyList(),
) {
    init {
        require(steps.none { it.isEmpty }) { "Every step keeps some nicotine" }
        require(steps.all { it.isWithinLabels }) { "A step goes over a label limit: $steps" }
        val totals = listOf(usual.total) + steps.map { it.total }
        require(totals.zipWithNext().all { (step, next) -> next <= step }) { "A step goes up: $totals" }
    }

    /** The number of steps below the usual day: the ladder's k. */
    val size: Int get() = steps.size

    /** The highest step a week can be at: the usual day, unless it's over a label limit. */
    val top: Int get() = if (usual.mix.isWithinLabels) 0 else 1

    fun mixAt(step: Int): Mix {
        require(step in 0..size) { "The ladder has steps 0 to $size: $step" }
        return if (step == 0) usual.mix else steps[step - 1]
    }

    fun isNew(product: UserProduct): Boolean = newProducts.any { it.id == product.id }

    companion object {
        /**
         * The ladder down from [usual]. Lower strengths come from [own], the user's products
         * with hidden ones too, and from [catalogue]: the same brand line and form, sold in
         * [market]. A user's product is used before a catalogue one of the same strength.
         */
        fun build(usual: UsualDay, own: List<UserProduct>, catalogue: Catalogue, market: String): Ladder {
            val strengths = Strengths(own, catalogue, market)
            val usualKinds = usual.mix.kinds.associateBy { it.form }
            val rungs = mutableListOf<Mix>()
            var mix = usual.mix
            while (true) {
                mix = stepDown(mix, usualKinds, strengths) ?: break
                rungs += mix
                check(rungs.size <= MAX_STEPS) { "The ladder from $usual doesn't end" }
            }
            val steps = rungs.filter { it.isWithinLabels }
            val known = (own + usual.mix.products).map { it.id }.toSet()
            val added = steps.flatMap { it.products }.filter { it.id !in known }.distinctBy { it.id }
            return Ladder(usual, steps, added)
        }

        /** Far more steps than any usual day within the labels could take. */
        private const val MAX_STEPS = 10_000

        /** The next step down from [mix], or null at the bottom of the ladder. */
        private fun stepDown(mix: Mix, usualKinds: Map<ProductForm, Mix.Kind>, strengths: Strengths): Mix? {
            val kinds = mix.kinds
            val kind = kinds.sortedWith(
                compareByDescending<Mix.Kind> { it.total }
                    .thenByDescending { it.strongest.product.perUnit }
                    .thenBy { it.form.ordinal },
            ).first()
            val othersLeft = kinds.size > 1
            val strongest = kind.strongest.product
            val usualKind = usualKinds.getValue(kind.form)
            val lower = strengths.below(strongest, lineage = kind.items + usualKind.items)
            if (kind.isPatch) {
                return when {
                    lower != null -> Mix.of(mix.withOneFewer(strongest).items + MixItem(lower, 1))
                    kind.pieces > 1 || othersLeft -> mix.withOneFewer(strongest)
                    else -> null
                }
            }
            return when {
                lower != null && kind.total <= lower.perUnit * usualKind.pieces -> {
                    val pieces = (kind.total.micrograms / lower.perUnit.micrograms).toInt()
                    mix.replacingKind(kind.form, listOf(MixItem(lower, pieces)))
                }
                lower != null && kind.pieces == 1 -> mix.replacingKind(kind.form, listOf(MixItem(lower, 1)))
                kind.pieces > 1 || othersLeft -> mix.withOneFewer(strongest)
                else -> null
            }
        }
    }
}

/**
 * Where lower strengths come from: the user's own products of the same form and unit, shown ones
 * before hidden ones, then the catalogue's products of the same brand line and form, sold in the
 * user's market and not discontinued.
 */
internal class Strengths(private val own: List<UserProduct>, private val catalogue: Catalogue, private val market: String) {

    private val forMarket by lazy { catalogue.forMarket(market) }

    /**
     * The product with the next strength below [product]'s, or null when there's none. The
     * catalogue lines searched are those of [lineage], in order: the kind's products now, then
     * in the usual day.
     */
    fun below(product: UserProduct, lineage: List<MixItem>): UserProduct? {
        val lines = lineage
            .mapNotNull { item -> item.product.catalogueId?.let { catalogue.find(it)?.lineId } }
            .distinct()
        val fromCatalogue = lines.flatMap { line ->
            forMarket.filter { it.lineId == line && it.form == product.form }.map { it.toUserProduct() }
        }
        val candidates = (own.sortedBy { it.isHidden } + fromCatalogue)
            .filter { it.form == product.form && it.unit == product.unit && it.perUnit < product.perUnit }
        val best: Dose = candidates.maxOfOrNull { it.perUnit } ?: return null
        return candidates.first { it.perUnit == best }
    }
}
