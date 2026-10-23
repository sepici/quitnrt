package app.thelasthit.nrt.data.repo

import app.thelasthit.nrt.data.db.ProductDao
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.db.toEntity
import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

class ProductRepository(
    private val dao: ProductDao,
    /** Loaded on first use, so starting the app doesn't parse the whole catalogue. */
    private val catalogue: () -> Catalogue,
    private val clock: Clock,
) {
    val all: Flow<List<UserProduct>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Every product's short name ("Nicorette gum"), hidden ones included, so an old entry still
     * reads well after its product is hidden.
     */
    val shortNames: Flow<Map<String, String>> =
        dao.observeAllIncludingHidden().map { list -> list.associate { it.id to it.shortName } }

    /** Every product, hidden ones included, for Settings. */
    val everything: Flow<List<UserProduct>> =
        dao.observeAllIncludingHidden().map { list -> list.map { it.toDomain() }.sortedBy { it.name.lowercase() } }

    /** The products on the quick log, in the user's order. */
    val quickLog: Flow<List<UserProduct>> = dao.observeQuickLog().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): UserProduct? = dao.get(id)?.toDomain()

    /**
     * Adds a catalogue product to the user's list (or returns it if it's already there), through
     * [app.thelasthit.nrt.domain.catalogue.CatalogueProduct.toUserProduct].
     */
    suspend fun addFromCatalogue(catalogueId: String, addToQuickLog: Boolean = true): UserProduct {
        dao.get(catalogueId)?.let { existing ->
            if (addToQuickLog && existing.quickLogPosition == null) appendToQuickLog(existing.id)
            return requireNotNull(dao.get(catalogueId)).toDomain()
        }
        val item = requireNotNull(catalogue().find(catalogueId)) { "Unknown catalogue product $catalogueId" }
        val product = item.toUserProduct(quickLogPosition = if (addToQuickLog) nextQuickLogPosition() else null)
        dao.upsert(product.toEntity(createdAt = clock.millis()))
        return product
    }

    /** A product the catalogue doesn't have, such as a nicotine pouch or an unusual pack. */
    suspend fun addCustom(
        name: String,
        form: ProductForm,
        perUnit: Dose,
        unit: DoseUnit = form.defaultUnit,
        label: LabelLimit = LabelLimit.NONE,
        addToQuickLog: Boolean = true,
    ): UserProduct {
        require(name.isNotBlank()) { "A product needs a name" }
        require(perUnit > Dose.ZERO) { "A product needs a dose" }
        val product = UserProduct(
            id = "$CUSTOM_ID_PREFIX${UUID.randomUUID()}",
            catalogueId = null,
            name = name.trim(),
            shortName = name.trim(),
            form = form,
            unit = unit,
            perUnit = perUnit,
            label = label,
            quickLogPosition = if (addToQuickLog) nextQuickLogPosition() else null,
        )
        dao.upsert(product.toEntity(createdAt = clock.millis()))
        return product
    }

    /**
     * Hides a product from the log sheet and the quick log, or brings it back. Doses already
     * logged with it stay in History.
     */
    suspend fun setHidden(id: String, hidden: Boolean) = dao.setHidden(id, hidden)

    /** A new name or pack limit. The dose stays, so entries logged later keep the same strength. */
    suspend fun update(id: String, name: String, label: LabelLimit) {
        val existing = dao.get(id)?.toDomain() ?: return
        require(name.isNotBlank()) { "A product needs a name" }
        val trimmed = name.trim()
        val renamed = trimmed != existing.name
        dao.upsert(
            existing.copy(
                name = trimmed,
                shortName = if (renamed && existing.catalogueId == null) trimmed else existing.shortName,
                label = label,
            ).toEntity(createdAt = dao.createdAt(id) ?: clock.millis()),
        )
    }

    /** Sets the quick log to exactly these products, in this order. */
    suspend fun setQuickLog(productIds: List<String>) {
        dao.clearQuickLog()
        productIds.distinct().forEachIndexed { index, id -> dao.setQuickLogPosition(id, index) }
    }

    suspend fun appendToQuickLog(productId: String) {
        val current = dao.quickLog().map { it.id }
        if (productId !in current) setQuickLog(current + productId)
    }

    suspend fun removeFromQuickLog(productId: String) {
        setQuickLog(dao.quickLog().map { it.id } - productId)
    }

    private suspend fun nextQuickLogPosition(): Int = dao.quickLog().size

    companion object {
        /** How a product the user made starts its ID: `custom-` and a random UUID, which means nothing to anyone. */
        const val CUSTOM_ID_PREFIX = "custom-"
    }
}
