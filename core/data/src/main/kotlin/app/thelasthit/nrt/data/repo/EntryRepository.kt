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
import app.thelasthit.nrt.domain.log.HistoryFilter
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.log.LogChecks
import app.thelasthit.nrt.domain.log.LogTime
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.time.DayBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

/**
 * Logging, editing and deleting doses. Every write settles the programme results that are due
 * first, through [results], so a result counts the logs as they stood when it came due.
 */
class EntryRepository(
    private val dao: EntryDao,
    private val products: ProductRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val results: ResultSettler,
    /** The phone's current zone, read on every log so travel is handled. */
    private val zone: () -> ZoneId = { clock.zone },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _logged = MutableSharedFlow<Entry>(extraBufferCapacity = 64)

    /**
     * Each new dose as it's saved, from whichever way it was logged, for what follows a log, such
     * as the allowance nudge. Edits and restores aren't new doses, so they aren't here.
     */
    val logged: SharedFlow<Entry> = _logged.asSharedFlow()

    suspend fun log(request: LogRequest): LogResult {
        results.settleDue()
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
        _logged.tryEmit(entry)
        return LogResult.Saved(entry, warning)
    }

    /**
     * Replaces an entry's details. The product snapshot is only refreshed when the product or
     * quantity changes, so editing a note never changes an old dose.
     */
    suspend fun edit(id: String, request: LogRequest): LogResult {
        results.settleDue()
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

    suspend fun delete(id: String) {
        results.settleDue()
        dao.softDelete(id, clock.millis())
    }

    suspend fun undoDelete(id: String) {
        results.settleDue()
        dao.restore(id, clock.millis())
    }

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

    /** The first logical day with a live entry, or null when nothing is logged. */
    fun observeFirstDay(): Flow<LocalDate?> = dao.observeFirstDay().map { day -> day?.let(LocalDate::ofEpochDay) }

    /**
     * Every live entry that [filter] matches, across all of History, newest first. The queries do
     * in SQL what [HistoryFilter.matches] says, so History needn't load every entry to filter it;
     * EntryRepositoryTest checks that they keep exactly the entries it keeps.
     */
    fun observeMatching(filter: HistoryFilter): Flow<List<Entry>> = when (filter) {
        is HistoryFilter.Product -> dao.observeWithProduct(filter.productId)
        is HistoryFilter.Tag -> dao.observeWithTag(filter.tagKey)
    }.map { list -> list.map { it.toDomain() } }

    /** Product ID to its number of live entries, for the filter's choices and counts. */
    fun observeProductUses(): Flow<Map<String, Int>> =
        dao.observeProductUses().map { list -> list.associate { it.productId to it.uses } }

    /** Each logical day from [from] to [to] with a live entry, to its number of live entries. */
    fun observeDosesPerDay(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.observeDosesPerDay(from.toEpochDay(), to.toEpochDay()).map { list ->
            list.associate { LocalDate.ofEpochDay(it.epochDay) to it.doses }
        }

    /**
     * Changes the day start and moves every entry to the day it now belongs to, once the results
     * due under the old day start are in.
     */
    suspend fun changeDayStart(time: LocalTime) {
        results.settleDue()
        settings.setDayStart(time)
        recalculateLogicalDays(DayBoundary(time))
    }

    /**
     * Moves every entry to the day it belongs to under [boundary]. Callers settle results first:
     * [changeDayStart] does, and so does a restore from backup.
     */
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
