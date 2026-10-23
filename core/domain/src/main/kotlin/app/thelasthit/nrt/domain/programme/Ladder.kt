package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.catalogue.ProductStatus
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import java.util.Locale

/**
 * The steps a taper takes from the usual day down to one unit of the weakest product (PLAN §6.2).
 * Step 0 is the usual day, and step n is `steps[n - 1]`. Each step does one thing to the kind of
 * product (gum, lozenges, sprays, patches and so on) with the most mg in the mix:
 * - **One fewer** piece, lozenge, spray or session a day, the strongest first.
 * - **A lower strength,** once the kind's mg comes to no more pieces a day at that strength than
 *   the usual day had of the kind, and no more than the lower strength's pack allows: 4 pieces
 *   of 4 mg gum become 8 pieces of 2 mg. The switch is a step of its own, even when the mg stays
 *   the same, and it's floor(mg ÷ lower strength) pieces. A kind down to one piece that the
 *   switch doesn't cover moves to one piece of the lower strength.
 * - **Patches** step down one strength at a time (21, 14, then 7 mg), then come off. With more
 *   than one patch, the strongest steps down that has room under its pack's limit at the lower
 *   strength. When none has, the weakest comes off.
 *
 * A tie between kinds goes to the one with the stronger unit, then to the form listed first.
 * The ladder ends at one unit of the weakest strength available in the last kind left, so it
 * can have no steps at all: one spray a day is already there.
 *
 * No step puts a product over its pack's daily limit, since the programme never schedules above
 * one. A usual day over a limit isn't a step a week can be at, so step 1 brings it within: each
 * product over its limit comes down to it, though a patch over its limit steps down a strength
 * first where there's room. The mg never goes up from one step to the next.
 *
 * [newProducts] are the lower strengths the steps use that weren't in the user's list when the
 * ladder was built, for the data layer to add when the plan starts. A ladder read back from
 * storage has none, and it loads even if a pack's limit was lowered since it was built.
 */
data class Ladder(
    val usual: UsualDay,
    val steps: List<Mix>,
    val newProducts: List<UserProduct> = emptyList(),
) {
    init {
        require(steps.none { it.isEmpty }) { "Every step keeps some nicotine" }
        val totals = listOf(usual.total) + steps.map { it.total }
        require(totals.zipWithNext().all { (step, next) -> next <= step }) { "A step goes up: $totals" }
    }

    /** The number of steps below the usual day: the ladder's k. */
    val size: Int get() = steps.size

    /**
     * The highest step a week can be at: the first within every pack's daily limit. That's the
     * usual day, or step 1 when the usual day is over a limit. If a limit was lowered after the
     * ladder was built, it's the first step still within it.
     */
    val top: Int get() = (0..size).firstOrNull { mixAt(it).isWithinLabels } ?: size

    fun mixAt(step: Int): Mix {
        require(step in 0..size) { "The ladder has steps 0 to $size: $step" }
        return if (step == 0) usual.mix else steps[step - 1]
    }

    fun isNew(product: UserProduct): Boolean = newProducts.any { it.id == product.id }

    companion object {
        /**
         * The ladder down from [usual]. Lower strengths come from [own], the user's products
         * with hidden ones too, and from [catalogue]: the lines of the products in use, then
         * other lines of the same brand and form sold in [market] or where those lines are
         * sold. A user's product is used before a catalogue one of the same strength.
         */
        fun build(usual: UsualDay, own: List<UserProduct>, catalogue: Catalogue, market: String): Ladder {
            val steps = Walk(usual, Strengths(own, catalogue, market)).steps()
            check(steps.all { it.isWithinLabels }) { "A step goes over a label limit: $steps" }
            val known = (own + usual.mix.products).map { it.id }.toSet()
            val added = steps.flatMap { it.products }.filter { it.id !in known }.distinctBy { it.id }
            return Ladder(usual, steps, added)
        }
    }
}

/** The walk down a ladder from [usual], a step at a time. */
private class Walk(private val usual: UsualDay, private val strengths: Strengths) {

    private val usualKinds = usual.mix.kinds.associateBy { it.form }

    fun steps(): List<Mix> {
        val rungs = mutableListOf<Mix>()
        var mix = usual.mix
        if (!mix.isWithinLabels) {
            mix = withinLabels(mix)
            rungs += mix
        }
        while (true) {
            mix = stepDown(mix) ?: break
            rungs += mix
            check(rungs.size <= MAX_STEPS) { "The ladder from $usual doesn't end" }
        }
        return rungs
    }

    /**
     * [mix] with every product brought within its pack's daily limit, a unit at a time: a patch
     * over its limit steps down a strength where there's room, and anything else comes off.
     */
    private fun withinLabels(mix: Mix): Mix {
        var within = mix
        while (true) {
            val over = within.items.firstOrNull { !it.isWithinLabel }?.product ?: return within
            val lower = if (over.isPatch) strengths.below(over, lineage(within.kindOf(over.form))) else null
            within = if (lower != null && within.hasRoomFor(lower)) within.steppedDown(over, lower) else within.withOneFewer(over)
        }
    }

    /** The next step down from [mix], or null at the bottom of the ladder. */
    private fun stepDown(mix: Mix): Mix? {
        val kind = mix.kinds.sortedWith(
            compareByDescending<Mix.Kind> { it.total }
                .thenByDescending { it.strongest.product.perUnit }
                .thenBy { it.form.ordinal },
        ).first()
        return if (kind.isPatch) patchStep(mix, kind) else pieceStep(mix, kind)
    }

    /** The strongest patch with room at the lower strength steps down to it, or else the weakest comes off. */
    private fun patchStep(mix: Mix, kind: Mix.Kind): Mix? {
        val lineage = lineage(kind)
        for (item in kind.items) {
            val lower = strengths.below(item.product, lineage) ?: continue
            if (mix.hasRoomFor(lower)) return mix.steppedDown(item.product, lower)
        }
        return if (mix.units > 1) mix.withOneFewer(kind.items.last().product) else null
    }

    /**
     * The kind's switch to the lower strength, once its mg comes to no more pieces than the
     * usual day had of the kind and the lower strength's pack allows; otherwise one fewer of its
     * strongest pieces, or a last piece of the lower strength.
     */
    private fun pieceStep(mix: Mix, kind: Mix.Kind): Mix? {
        val strongest = kind.strongest.product
        val lower = strengths.below(strongest, lineage(kind))
        if (lower != null) {
            val usualPieces = usualKinds.getValue(kind.form).pieces
            val most = lower.label.maxUnitsPerDay?.let { minOf(it, usualPieces) } ?: usualPieces
            if (kind.total <= lower.perUnit * most) {
                val pieces = (kind.total.micrograms / lower.perUnit.micrograms).toInt()
                return mix.replacingKind(kind.form, listOf(MixItem(lower, pieces)))
            }
            if (kind.pieces == 1) return mix.replacingKind(kind.form, listOf(MixItem(lower, 1)))
        }
        return if (mix.units > 1) mix.withOneFewer(strongest) else null
    }

    /** The products whose lines a lower strength is looked for in: the kind's now, then in the usual day. */
    private fun lineage(kind: Mix.Kind): List<UserProduct> = (kind.items + usualKinds.getValue(kind.form).items).map { it.product }

    private val UserProduct.isPatch: Boolean get() = form.defaultUnit == DoseUnit.PATCH

    private fun Mix.kindOf(form: ProductForm): Mix.Kind = kinds.first { it.form == form }

    /** Whether one more unit of [product] stays within its pack's daily limit. */
    private fun Mix.hasRoomFor(product: UserProduct): Boolean =
        product.label.maxUnitsPerDay?.let { unitsOf(product.id) < it } ?: true

    /** One unit of [product] swapped for one of [lower]. */
    private fun Mix.steppedDown(product: UserProduct, lower: UserProduct): Mix = Mix.of(withOneFewer(product).items + MixItem(lower, 1))

    private companion object {
        /** Far more steps than any usual day within the labels could take. */
        const val MAX_STEPS = 10_000
    }
}

/**
 * Where lower strengths come from, in order of preference:
 * 1. The user's own products of the same form and unit, shown ones before hidden ones.
 * 2. The catalogue lines of the products in use, wherever they're sold, since the user buys them.
 * 3. Other catalogue lines of the same brand and form, sold in the user's market or where the
 *    lines in use are sold.
 *
 * Discontinued packs are never offered.
 */
internal class Strengths(own: List<UserProduct>, private val catalogue: Catalogue, market: String) {

    private val own = own.sortedBy { it.isHidden }

    private val market = market.uppercase(Locale.ROOT)

    private val onSale = catalogue.products.filter { it.status != ProductStatus.DISCONTINUED }

    /**
     * The product with the next strength below [product]'s, or null when there's none. The
     * catalogue lines searched are those of [lineage], in order, then the rest of their brands'.
     */
    fun below(product: UserProduct, lineage: List<UserProduct>): UserProduct? {
        val lines = lineage.mapNotNull { it.catalogueId?.let(catalogue::find) }.distinctBy { it.lineId }
        val lineIds = lines.map { it.lineId }
        val brands = lines.map { it.brand }.toSet()
        val markets = lines.flatMap { it.markets }.toSet() + market
        val sameForm = onSale.filter { it.form == product.form }
        val sameLine = lineIds.flatMap { line -> sameForm.filter { it.lineId == line } }
        val sameBrand = sameForm.filter { candidate ->
            candidate.lineId !in lineIds && candidate.brand in brands &&
                (candidate.markets.isEmpty() || candidate.markets.any { it in markets })
        }
        val candidates = (own + (sameLine + sameBrand).map { it.toUserProduct() })
            .filter { it.form == product.form && it.unit == product.unit && it.perUnit < product.perUnit }
        val best: Dose = candidates.maxOfOrNull { it.perUnit } ?: return null
        return candidates.first { it.perUnit == best }
    }
}
