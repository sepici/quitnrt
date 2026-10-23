package app.thelasthit.nrt.domain.product

/**
 * The physical kind of product. Patches are background doses: they count toward the day they
 * were applied, but stay out of the hourly heat map, spacing and trigger stats.
 */
enum class ProductForm(
    val defaultUnit: DoseUnit,
    val isBackground: Boolean = false,
    val ratedHours: Int? = null,
) {
    GUM(DoseUnit.PIECE),
    LOZENGE(DoseUnit.LOZENGE),
    MINI_LOZENGE(DoseUnit.LOZENGE),
    PATCH_16H(DoseUnit.PATCH, isBackground = true, ratedHours = 16),
    PATCH_24H(DoseUnit.PATCH, isBackground = true, ratedHours = 24),
    MOUTH_SPRAY(DoseUnit.SPRAY),
    NASAL_SPRAY(DoseUnit.SPRAY),
    INHALATOR(DoseUnit.SESSION),
    MICROTAB(DoseUnit.TABLET),
    ORAL_FILM(DoseUnit.STRIP),
    POUCH(DoseUnit.POUCH),
    OTHER(DoseUnit.CUSTOM),
}

enum class DoseUnit {
    PIECE,
    LOZENGE,
    PATCH,
    SPRAY,
    SESSION,
    CARTRIDGE,
    PUFF,
    TABLET,
    STRIP,
    POUCH,
    CUSTOM,
}
