package app.thelasthit.nrt.data.repo

import androidx.room.withTransaction
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.db.EntryEntity
import app.thelasthit.nrt.data.db.EntryWithTags
import app.thelasthit.nrt.data.db.ProductEntity
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.backup.EntriesCsv
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ImportMode {
    /** Adds anything not already on the phone. Nothing existing is changed. */
    MERGE,

    /** Wipes the phone's data and settings, then restores the backup. */
    REPLACE,
}

data class ImportSummary(val products: Int, val tags: Int, val entries: Int, val skipped: Int)

/** What's on the phone now, for the warning before a backup replaces it. */
data class StoredCounts(val entries: Int, val products: Int)

class BackupRepository(
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val entries: EntryRepository,
    private val clock: Clock,
) {
    suspend fun export(): Backup {
        val current = settings.current()
        return Backup(
            exportedAt = clock.instant().toString(),
            settings = Backup.Settings(
                dayStart = current.dayStart.toString(),
                country = current.country,
                stoppedSmokingOn = current.stoppedSmokingOn?.toString(),
            ),
            products = database.products().allIncludingHidden().map { it.toBackup() },
            tags = database.tags().all().map { it.toBackup() },
            entries = database.entries().allLiveWithTags().map { it.toBackup() },
        )
    }

    suspend fun counts(): StoredCounts = StoredCounts(database.entries().countLive(), database.products().count())

    suspend fun exportCsv(): String = EntriesCsv.write(database.entries().allLiveWithTags().map { it.toDomain() })

    suspend fun import(backup: Backup, mode: ImportMode): ImportSummary {
        var skipped = 0
        val now = clock.millis()
        val products = backup.products.map { it.toEntity(now) }
        val tags = backup.tags.map { it.toEntity() }
        val entryRows = backup.entries.mapNotNull { entry ->
            entry.toEntity(now).also { if (it == null) skipped++ }?.let { it to entry.tags }
        }

        val summary = database.withTransaction {
            if (mode == ImportMode.REPLACE) {
                database.entries().deleteAllTags()
                database.entries().deleteAll()
                database.tags().deleteAll()
                database.products().deleteAll()
            }
            val productsAdded = database.products().insertIgnoringExisting(products).count { it != -1L }
            val tagsAdded = database.tags().insertIgnoringExisting(tags).count { it != -1L }
            val existing = database.entries().allIds().toSet()
            var entriesAdded = 0
            for ((row, tagKeys) in entryRows) {
                if (row.id in existing) {
                    skipped++
                } else {
                    database.entries().insertWithTags(row, tagKeys)
                    entriesAdded++
                }
            }
            ImportSummary(productsAdded, tagsAdded, entriesAdded, skipped)
        }

        if (mode == ImportMode.REPLACE) {
            runCatching { LocalTime.parse(backup.settings.dayStart) }.getOrNull()?.let { settings.setDayStart(it) }
            backup.settings.country?.let { settings.setCountry(it) }
            settings.setStoppedSmokingOn(backup.settings.stoppedSmokingOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
        }
        // The backup's day start may differ from this phone's, so put every entry on the right day.
        entries.recalculateLogicalDays(settings.current().dayBoundary)
        return summary
    }
}

private fun ProductEntity.toBackup() = Backup.Product(
    id = id,
    catalogueId = catalogueId,
    name = name,
    shortName = shortName,
    form = form,
    unit = unit,
    perUnitMicrograms = perUnitMicrograms,
    cartridgeMicrograms = cartridgeMicrograms,
    puffsPerCartridge = puffsPerCartridge,
    labelMaxPerDay = labelMaxPerDay,
    labelWindowMax = labelWindowMax,
    labelWindowMinutes = labelWindowMinutes,
    quickLogPosition = quickLogPosition,
    isHidden = isHidden,
)

private fun Backup.Product.toEntity(now: Long) = ProductEntity(
    id = id,
    catalogueId = catalogueId,
    name = name,
    shortName = shortName,
    form = form,
    unit = unit,
    perUnitMicrograms = perUnitMicrograms,
    cartridgeMicrograms = cartridgeMicrograms,
    puffsPerCartridge = puffsPerCartridge,
    labelMaxPerDay = labelMaxPerDay,
    labelWindowMax = labelWindowMax,
    labelWindowMinutes = labelWindowMinutes,
    quickLogPosition = quickLogPosition,
    isHidden = isHidden,
    createdAt = now,
)

private fun TagEntity.toBackup() = Backup.Tag(key, group, customName, isBuiltIn, isHidden, sortOrder)

private fun Backup.Tag.toEntity() = TagEntity(key, group, customName, isBuiltIn, isHidden, sortOrder)

private fun EntryWithTags.toBackup() = Backup.Entry(
    id = entry.id,
    productId = entry.productId,
    productName = entry.productName,
    form = entry.form,
    unit = entry.unit,
    perUnitMicrograms = entry.perUnitMicrograms,
    quantity = entry.quantity,
    totalMicrograms = entry.totalMicrograms,
    occurredAt = Instant.ofEpochMilli(entry.occurredAt).toString(),
    zone = entry.zoneId,
    logicalDate = LocalDate.ofEpochDay(entry.logicalEpochDay).toString(),
    kind = entry.kind,
    cravingStrength = entry.cravingStrength,
    tags = tags.map { it.tagKey }.sorted(),
    note = entry.note,
    source = entry.source,
)

/** Null when the entry can't be trusted, e.g. a broken time or zone; those are skipped and counted. */
private fun Backup.Entry.toEntity(now: Long): EntryEntity? = runCatching {
    require(id.isNotBlank() && quantity >= 1 && perUnitMicrograms >= 0 && totalMicrograms >= 0)
    require(cravingStrength == null || cravingStrength in 1..5)
    EntryEntity(
        id = id,
        productId = productId,
        productName = productName,
        form = form,
        unit = unit,
        perUnitMicrograms = perUnitMicrograms,
        quantity = quantity,
        totalMicrograms = totalMicrograms,
        occurredAt = Instant.parse(occurredAt).toEpochMilli(),
        zoneId = ZoneId.of(zone).id,
        logicalEpochDay = LocalDate.parse(logicalDate).toEpochDay(),
        kind = kind,
        cravingStrength = cravingStrength,
        note = note,
        source = source,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )
}.getOrNull()
