package app.thelasthit.nrt.domain.product

import app.thelasthit.nrt.domain.dose.Dose

/**
 * A product in the user's own list: picked from the catalogue or added by hand. [perUnit] and
 * [label] are in the unit it's logged in, so an inhalator logged by session has a per-session
 * dose and a per-session limit.
 */
data class UserProduct(
    val id: String,
    val catalogueId: String?,
    val name: String,
    val shortName: String,
    val form: ProductForm,
    val unit: DoseUnit,
    val perUnit: Dose,
    val label: LabelLimit = LabelLimit.NONE,
    val cartridge: Dose? = null,
    val puffsPerCartridge: Int? = null,
    /** Position in the quick log, or null when it isn't there. */
    val quickLogPosition: Int? = null,
    val isHidden: Boolean = false,
)
