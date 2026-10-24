package app.thelasthit.nrt.domain.absorbed

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.ProductForm

/**
 * The estimated absorbed amount shown beside the labelled mg when the setting is on: the
 * labelled amount times the form's factor, in whole micrograms. Only screens read it. Guides,
 * allowances, results, achievements and Insights all work in labelled mg, and a test checks that
 * no other rule in the domain reaches for this.
 */
object AbsorbedEstimate {

    /** A product's [own] factor, from the catalogue, wins over its form's. Null for a form with none. */
    fun factorFor(form: ProductForm, own: AbsorptionFactor? = null): AbsorptionFactor? = own ?: Absorption.byForm[form]?.factor

    /**
     * About how much of [labelled] reaches the blood, rounded to the nearest microgram with halves
     * up. Null when there's no factor for [form], so nothing is shown rather than a guess.
     */
    fun of(labelled: Dose, form: ProductForm, own: AbsorptionFactor? = null): Dose? {
        val factor = factorFor(form, own) ?: return null
        return Dose((labelled.micrograms * factor.perMille * 2 + 1000) / 2000)
    }

    /** One logged dose's estimate, from the mg it was logged with. */
    fun of(entry: Entry, own: AbsorptionFactor? = null): Dose? = of(entry.total, entry.form, own)

    /**
     * Several doses' estimate, as each one's added up, so a day's figure matches its rows. Null
     * for no doses, or when any of them has no factor: a total that left one out would be wrong.
     */
    fun total(entries: List<Entry>, own: (Entry) -> AbsorptionFactor? = { null }): Dose? {
        if (entries.isEmpty()) return null
        var micrograms = 0L
        for (entry in entries) {
            micrograms += (of(entry, own(entry)) ?: return null).micrograms
        }
        return Dose(micrograms)
    }
}
