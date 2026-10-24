package app.thelasthit.nrt.domain.catalogue

import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import kotlinx.serialization.Serializable

/** The shape of `catalogue/products.json`. One line per brand, product and market; one product per strength. */
@Serializable
internal data class CatalogueFile(
    val version: Int,
    val lines: List<LineEntry>,
) {
    @Serializable
    data class LineEntry(
        val id: String,
        val brand: String,
        val line: String,
        val shortName: String,
        val kind: LineKind,
        val form: ProductForm,
        val unit: DoseUnit,
        val markets: List<String>,
        val status: ProductStatus,
        val flavours: List<String>,
        val strengths: List<StrengthEntry>,
        val window: WindowEntry? = null,
        /** The line's own absorption factor in thousandths, where its form's doesn't fit it. Usually left out. */
        val absorptionPerMille: Int? = null,
    )

    @Serializable
    data class StrengthEntry(val mg: Double, val maxPerDay: Int? = null)

    @Serializable
    data class WindowEntry(val count: Int, val hours: Int)
}
