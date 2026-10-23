package app.thelasthit.nrt.data.repo

import app.thelasthit.nrt.data.db.ProductDao
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.db.toEntity
import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.DoseMath
import app.thelasthit.nrt.domain.dose.InhalatorUnit
import app.thelasthit.nrt.domain.log.LogChecks
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

    /** The products on the quick log, in the user's order. */
    val quickLog: Flow<List<UserProduct>> = dao.observeQuickLog().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): UserProduct? = dao.get(id)?.toDomain()

    /**
     * Adds a catalogue product to the user's list (or returns it if it's already there). The
     * inhalator is logged by session, so its dose and label limit are converted from cartridges.
     */
    suspend fun addFromCatalogue(catalogueId: String, addToQuickLog: Boolean = true): UserProduct {
        dao.get(catalogueId)?.let { existing ->
            if (addToQuickLog && existing.quickLogPosition == null) appendToQuickLog(existing.id)
            return requireNotNull(dao.get(catalogueId)).toDomain()
        }
        val item = requireNotNull(catalogue().find(catalogueId)) { "Unknown catalogue product $catalogueId" }
        val unit = item.form.defaultUnit
        val isInhalator = item.form == ProductForm.INHALATOR
        val product = UserProduct(
            id = item.id,
            catalogueId = item.id,
            name = item.displayName,
            shortName = item.shortName,
            form = item.form,
            unit = unit,
            perUnit = if (isInhalator) DoseMath.inhalatorPerUnit(item.strength, InhalatorUnit.SESSION) else item.strength,
            label = LogChecks.limitInLoggedUnit(item.label, item.unit, unit),
            cartridge = if (isInhalator) item.strength else null,
            quickLogPosition = if (addToQuickLog) nextQuickLogPosition() else null,
        )
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
            id = "custom-${UUID.randomUUID()}",
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
}
