package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One logged use. The product's name and dose are copied in at the time of logging, so editing
 * or removing a product later never rewrites history.
 */
data class Entry(
    val id: String,
    val productId: String,
    val productName: String,
    val form: ProductForm,
    val unit: DoseUnit,
    val perUnit: Dose,
    val quantity: Int,
    val total: Dose,
    val occurredAt: Instant,
    val zone: ZoneId,
    val logicalDate: LocalDate,
    val kind: EntryKind = EntryKind.UNSPECIFIED,
    val cravingStrength: Int? = null,
    val tagKeys: Set<String> = emptySet(),
    val note: String? = null,
    val source: EntrySource = EntrySource.APP,
) {
    init {
        require(quantity >= 1) { "Quantity must be at least 1: $quantity" }
        require(cravingStrength == null || cravingStrength in 1..5) { "Craving strength is 1 to 5: $cravingStrength" }
    }

    /** Patches release steadily, so they stay out of "last dose", spacing and trigger stats. */
    val isBackground: Boolean get() = form.isBackground
}

/** Whether a dose was taken on schedule or because of a craving. */
enum class EntryKind { PLANNED, CRAVING, UNSPECIFIED }

/** Where an entry was logged from. Used for duplicate checks and debugging. */
enum class EntrySource { APP, WIDGET, TILE, SHORTCUT, DEEP_LINK, APP_FUNCTION, WATCH, IMPORT }
