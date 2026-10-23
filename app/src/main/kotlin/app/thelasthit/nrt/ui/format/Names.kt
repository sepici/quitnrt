package app.thelasthit.nrt.ui.format

import androidx.annotation.StringRes
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

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

/** "mg a piece", beside a strength typed by hand. */
@StringRes
fun perUnitLabel(unit: DoseUnit): Int = when (unit) {
    DoseUnit.PIECE -> R.string.per_unit_piece
    DoseUnit.LOZENGE -> R.string.per_unit_lozenge
    DoseUnit.PATCH -> R.string.per_unit_patch
    DoseUnit.SPRAY -> R.string.per_unit_spray
    DoseUnit.SESSION -> R.string.per_unit_session
    DoseUnit.CARTRIDGE -> R.string.per_unit_cartridge
    DoseUnit.PUFF -> R.string.per_unit_puff
    DoseUnit.TABLET -> R.string.per_unit_tablet
    DoseUnit.STRIP -> R.string.per_unit_strip
    DoseUnit.POUCH -> R.string.per_unit_pouch
    DoseUnit.CUSTOM -> R.string.per_unit_custom
}

/** The unit's plural on its own, as in "pieces a day". */
@StringRes
fun unitsName(unit: DoseUnit): Int = when (unit) {
    DoseUnit.PIECE -> R.string.units_piece
    DoseUnit.LOZENGE -> R.string.units_lozenge
    DoseUnit.PATCH -> R.string.units_patch
    DoseUnit.SPRAY -> R.string.units_spray
    DoseUnit.SESSION -> R.string.units_session
    DoseUnit.CARTRIDGE -> R.string.units_cartridge
    DoseUnit.PUFF -> R.string.units_puff
    DoseUnit.TABLET -> R.string.units_tablet
    DoseUnit.STRIP -> R.string.units_strip
    DoseUnit.POUCH -> R.string.units_pouch
    DoseUnit.CUSTOM -> R.string.units_custom
}

/** A product's pack limit in words: "Pack limit 4 sprays an hour, 64 a day". */
@Composable
fun packLimitText(product: UserProduct): String {
    val label = product.label
    val daily = label.maxUnitsPerDay
    val window = label.window
    if (daily == null && window == null) {
        return stringResource(if (product.catalogueId == null) R.string.limit_none_by_hand else R.string.limit_none)
    }
    if (window == null) {
        return stringResource(R.string.limit_per_day, pluralStringResource(product.unit.plural, daily!!, daily))
    }
    val units = pluralStringResource(product.unit.plural, window.maxUnits, window.maxUnits)
    val hours = window.span.toHours().toInt().coerceAtLeast(1)
    val windowText = if (hours == 1) {
        stringResource(R.string.limit_per_hour, units)
    } else {
        pluralStringResource(R.plurals.limit_per_hours, hours, units, hours)
    }
    return if (daily == null) windowText else stringResource(R.string.limit_and_daily, windowText, daily)
}

/**
 * A product's name as lists show it. Catalogue names carry their strength ("Nicorette gum 2 mg");
 * a product added by hand gets its strength added ("Holiday gum 2 mg").
 */
@Composable
fun productTitle(product: UserProduct): String =
    if (product.catalogueId == null) stringResource(R.string.dose_named, product.name, product.perUnit.mgText()) else product.name
