package app.thelasthit.nrt.ui.format

import androidx.annotation.StringRes
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.product.ProductForm

/** A market's name, such as "United Kingdom", or "Somewhere else". */
@StringRes
fun countryName(code: String): Int = when (code.uppercase()) {
    "GB" -> R.string.country_gb
    "IE" -> R.string.country_ie
    "US" -> R.string.country_us
    "CA" -> R.string.country_ca
    "AU" -> R.string.country_au
    "NZ" -> R.string.country_nz
    else -> R.string.country_elsewhere
}

/** The brands a market's catalogue has, as onboarding lists them under the country. */
@StringRes
fun countryBrands(code: String): Int = when (code.uppercase()) {
    "GB" -> R.string.country_gb_brands
    "IE" -> R.string.country_ie_brands
    "US" -> R.string.country_us_brands
    "CA" -> R.string.country_ca_brands
    "AU" -> R.string.country_au_brands
    "NZ" -> R.string.country_nz_brands
    else -> R.string.country_elsewhere_brands
}

/** A short name for a pack from another country: "US pack". Null for "somewhere else". */
@StringRes
fun countryShort(code: String): Int? = when (code.uppercase()) {
    "GB" -> R.string.country_gb_short
    "IE" -> R.string.country_ie_short
    "US" -> R.string.country_us_short
    "CA" -> R.string.country_ca_short
    "AU" -> R.string.country_au_short
    "NZ" -> R.string.country_nz_short
    Markets.ELSEWHERE -> null
    else -> null
}

@StringRes
fun creatureName(creature: Creature): Int = when (creature) {
    Creature.DRAGON -> R.string.creature_dragon
    Creature.DEMON -> R.string.creature_demon
    Creature.BEAR -> R.string.creature_bear
    Creature.KRAKEN -> R.string.creature_kraken
    Creature.GOBLIN -> R.string.creature_goblin
    Creature.SLIME -> R.string.creature_slime
    Creature.WRAITH -> R.string.creature_wraith
    Creature.SERPENT -> R.string.creature_serpent
}

/** A palette variant's name. Unknown variants read as the default, forest. */
@StringRes
fun variantName(variant: String): Int = when (variant) {
    "ember" -> R.string.variant_ember
    "moss" -> R.string.variant_moss
    "frost" -> R.string.variant_frost
    else -> R.string.variant_forest
}

/** How a form is counted, under a product line: "Per piece", "16-hour patch". */
@StringRes
fun formDetail(form: ProductForm): Int = when (form) {
    ProductForm.GUM -> R.string.form_detail_gum
    ProductForm.LOZENGE -> R.string.form_detail_lozenge
    ProductForm.MINI_LOZENGE -> R.string.form_detail_mini_lozenge
    ProductForm.PATCH_16H -> R.string.form_detail_patch_16h
    ProductForm.PATCH_24H -> R.string.form_detail_patch_24h
    ProductForm.MOUTH_SPRAY, ProductForm.NASAL_SPRAY -> R.string.form_detail_spray
    ProductForm.INHALATOR -> R.string.form_detail_inhalator
    ProductForm.MICROTAB -> R.string.form_detail_microtab
    ProductForm.ORAL_FILM -> R.string.form_detail_strip
    ProductForm.POUCH -> R.string.form_detail_pouch
    ProductForm.OTHER -> R.string.form_detail_other
}
