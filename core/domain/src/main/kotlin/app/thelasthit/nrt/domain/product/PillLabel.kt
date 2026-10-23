package app.thelasthit.nrt.domain.product

import app.thelasthit.nrt.domain.dose.Dose

/** The word a quick-log pill uses for a form. Both patch forms are just "patch". */
enum class FormWord { GUM, LOZENGE, MINI_LOZENGE, PATCH, MOUTH_SPRAY, NASAL_SPRAY, INHALATOR, MICROTAB, STRIP, POUCH }

/** Null for [ProductForm.OTHER], which has no word of its own, so its pill uses the product's name. */
val ProductForm.word: FormWord?
    get() = when (this) {
        ProductForm.GUM -> FormWord.GUM
        ProductForm.LOZENGE -> FormWord.LOZENGE
        ProductForm.MINI_LOZENGE -> FormWord.MINI_LOZENGE
        ProductForm.PATCH_16H, ProductForm.PATCH_24H -> FormWord.PATCH
        ProductForm.MOUTH_SPRAY -> FormWord.MOUTH_SPRAY
        ProductForm.NASAL_SPRAY -> FormWord.NASAL_SPRAY
        ProductForm.INHALATOR -> FormWord.INHALATOR
        ProductForm.MICROTAB -> FormWord.MICROTAB
        ProductForm.ORAL_FILM -> FormWord.STRIP
        ProductForm.POUCH -> FormWord.POUCH
        ProductForm.OTHER -> null
    }

/**
 * A short label for a quick-log pill: "Gum 2 mg". It names the form, and switches to the
 * product's own name ("Nicorette gum 2 mg") only when two pills would otherwise read the same.
 */
sealed interface PillLabel {
    /** The strength on the pack. For the inhalator that's the cartridge, not one session. */
    val strength: Dose

    data class ByForm(val word: FormWord, override val strength: Dose) : PillLabel
    data class ByName(val name: String, override val strength: Dose) : PillLabel

    companion object {
        /** Labels for [products], keyed by product ID. */
        fun of(products: List<UserProduct>): Map<String, PillLabel> {
            val byForm = products.associate { it.id to (it.form.word to it.packStrength) }
            val clashing = byForm.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            return products.associate { product ->
                val (word, strength) = byForm.getValue(product.id)
                product.id to if (word == null || (word to strength) in clashing) {
                    ByName(product.shortName, strength)
                } else {
                    ByForm(word, strength)
                }
            }
        }

        private val UserProduct.packStrength: Dose get() = cartridge ?: perUnit
    }
}
