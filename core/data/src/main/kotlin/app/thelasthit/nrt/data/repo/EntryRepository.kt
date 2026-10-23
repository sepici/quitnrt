package app.thelasthit.nrt.data.repo

import app.thelasthit.nrt.data.db.EntryDao
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.db.toEntity
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.DoseMath
import app.thelasthit.nrt.domain.log.DaySummary
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.log.LogChecks
import app.thelasthit.nrt.domain.log.LogTime
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.time.DayBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class LogRequest(
    val productId: String,
    val quantity: Int = 1,
    /** Null means now. */
    val at: Instant? = null,
    val kind: EntryKind = EntryKind.UNSPECIFIED,
    val cravingStrength: Int? = null,
    val tagKeys: Set<String> = emptySet(),
    val note: String? = null,
    val source: EntrySource = EntrySource.APP,
    /** For a patch that came off early. */
    val patchWorn: Duration? = null,
    /** Set after the user confirms that a likely duplicate is a separate dose. */
    val allowDuplicate: Boolean = false,
)

sealed interface LogResult {
    data class Saved(val entry: Entry, val labelWarning: LabelWarning?) : LogResult
    data class PossibleDuplicate(val existing: Entry) : LogResult
    data object UnknownProduct : LogResult
    data object NotFound : LogResult
    data object TimeInFuture : LogResult
}

class EntryRepository(
    private val dao: EntryDao,
    private val products: ProductRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    /** The phone's current zone, read on every log so travel is handled. */
    private val zone: () -> ZoneId = { clock.zone },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun log(request: LogRequest): LogResult {
        val now = clock.instant()
        val at = request.at ?: now
        if (!LogTime.isAcceptable(at, now)) return LogResult.TimeInFuture
        val product = products.get(request.productId) ?: return LogResult.UnknownProduct

        if (!request.allowDuplicate) {
            val nearby = dao.between(
                at.minus(LogChecks.DUPLICATE_WINDOW).toEpochMilli(),
                at.plus(LogChecks.DUPLICATE_WINDOW).toEpochMilli(),
            ).map { it.toDomain() }
            LogChecks.possibleDuplicateOf(product.id, at, request.source, nearby)?.let {
                return LogResult.PossibleDuplicate(it)
            }
        }

        val entry = buildEntry(newId(), product, request, at, settings.current().dayBoundary)
        val warning = labelWarningFor(product, entry)
        dao.insertWithTags(entry.toEntity(createdAt = now.toEpochMilli(), updatedAt = now.toEpochMilli()), entry.tagKeys)
        return LogResult.Saved(entry, warning)
    }

    /**
     * Replaces an entry's details. The product snapshot is only refreshed when the product or
     * quantity changes, so editing a note never changes an old dose.
     */
    suspend fun edit(id: String, request: LogRequest): LogResult {
        val existing = dao.get(id) ?: return LogResult.NotFound
        val now = clock.instant()
        val at = request.at ?: Instant.ofEpochMilli(existing.entry.occurredAt)
        if (!LogTime.isAcceptable(at, now)) return LogResult.TimeInFuture
        val old = existing.toDomain()
        val boundary = settings.current().dayBoundary

        val updated = if (request.productId != old.productId || request.quantity != old.quantity || request.patchWorn != null) {
            val product = products.get(request.productId) ?: return LogResult.UnknownProduct
            buildEntry(id, product, request, at, boundary, zoneOverride = old.zone).copy(source = old.source)
        } else {
            old.copy(
                occurredAt = at,
                logicalDate = boundary.logicalDate(at, old.zone),
                kind = request.kind,
                cravingStrength = request.cravingStrength,
                tagKeys = request.tagKeys,
                note = request.note?.trim()?.ifBlank { null },
            )
        }
        dao.updateWithTags(
            updated.toEntity(createdAt = existing.entry.createdAt, updatedAt = now.toEpochMilli()),
            updated.tagKeys,
        )
        return LogResult.Saved(updated, labelWarning = null)
    }

    suspend fun delete(id: String) = dao.softDelete(id, clock.millis())

    suspend fun undoDelete(id: String) = dao.restore(id, clock.millis())

    /** Deleted entries stay restorable for 30 days. */
    suspend fun purgeDeleted(): Int = dao.purgeDeletedBefore(clock.instant().minus(Duration.ofDays(30)).toEpochMilli())

    suspend fun get(id: String): Entry? = dao.get(id)?.toDomain()

    /** The day's entries and total, plus the last dose, which can be from the evening before. */
    fun observeDay(date: LocalDate): Flow<DaySummary> =
        dao.observeDays(date.toEpochDay() - 1, date.toEpochDay()).map { list ->
            DaySummary.of(date, list.map { it.toDomain() })
        }

    /** Every live entry from [from] to [to] inclusive, newest first. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Entry>> =
        dao.observeDays(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toDomain() } }

    /** Run after the day start changes: moves every entry to the day it now belongs to. */
    suspend fun recalculateLogicalDays(boundary: DayBoundary) {
        dao.allLive().forEach { entity ->
            val day = boundary.logicalDate(Instant.ofEpochMilli(entity.occurredAt), ZoneId.of(entity.zoneId)).toEpochDay()
            if (day != entity.logicalEpochDay) dao.setLogicalDay(entity.id, day)
        }
    }

    private fun buildEntry(
        id: String,
        product: UserProduct,
        request: LogRequest,
        at: Instant,
        boundary: DayBoundary,
        zoneOverride: ZoneId? = null,
    ): Entry {
        val entryZone = zoneOverride ?: zone()
        val ratedHours = product.form.ratedHours
        val total: Dose = if (ratedHours != null && request.patchWorn != null) {
            DoseMath.patch(product.perUnit, ratedHours, request.patchWorn) * request.quantity
        } else {
            DoseMath.total(product.perUnit, request.quantity)
        }
        return Entry(
            id = id,
            productId = product.id,
            productName = product.name,
            form = product.form,
            unit = product.unit,
            perUnit = product.perUnit,
            quantity = request.quantity,
            total = total,
            occurredAt = at,
            zone = entryZone,
            logicalDate = boundary.logicalDate(at, entryZone),
            kind = request.kind,
            cravingStrength = request.cravingStrength,
            tagKeys = request.tagKeys,
            note = request.note?.trim()?.ifBlank { null },
            source = request.source,
        )
    }

    private suspend fun labelWarningFor(product: UserProduct, entry: Entry): LabelWarning? {
        val today = dao.onDay(entry.logicalDate.toEpochDay()).map { it.toDomain() }.filter { it.productId == product.id }
        val window = product.label.window
        val recent = if (window == null) {
            today
        } else {
            dao.recentForProduct(product.id, entry.occurredAt.minus(window.span).toEpochMilli()).map { it.toDomain() }
        }
        return LogChecks.labelWarning(product.label, entry.quantity, entry.occurredAt, today, recent)
    }
}
