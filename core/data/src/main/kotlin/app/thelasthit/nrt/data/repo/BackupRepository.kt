package app.thelasthit.nrt.data.repo

import androidx.room.withTransaction
import app.thelasthit.nrt.data.db.AchievementEntity
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.db.BaselineUnitEntity
import app.thelasthit.nrt.data.db.BreathingSessionEntity
import app.thelasthit.nrt.data.db.DemonEntity
import app.thelasthit.nrt.data.db.EntryEntity
import app.thelasthit.nrt.data.db.EntryWithTags
import app.thelasthit.nrt.data.db.GearEntity
import app.thelasthit.nrt.data.db.HitLedgerEntity
import app.thelasthit.nrt.data.db.LadderUnitEntity
import app.thelasthit.nrt.data.db.ProductEntity
import app.thelasthit.nrt.data.db.ProgrammeEntity
import app.thelasthit.nrt.data.db.ProgrammeWeekEntity
import app.thelasthit.nrt.data.db.ProgrammeWeekUnitEntity
import app.thelasthit.nrt.data.db.ProgrammeWithParts
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.db.UnitsColumns
import app.thelasthit.nrt.data.db.balance
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.db.toEntities
import app.thelasthit.nrt.data.db.toRecord
import app.thelasthit.nrt.data.db.toWeekEntities
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.backup.EntriesCsv
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
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

/**
 * What a restore added. [skipped] counts entries that were already there or couldn't be read.
 * [programmesSkipped] counts programmes still running in the backup that a merge left out,
 * because the phone's own stays current (see [BackupRepository.import]): only replacing
 * everything brings them in.
 */
data class ImportSummary(
    val products: Int,
    val tags: Int,
    val entries: Int,
    val skipped: Int,
    val programmes: Int = 0,
    val programmesSkipped: Int = 0,
)

/** What's on the phone now, for the warning before a backup replaces it: everything a replace deletes. */
data class StoredCounts(
    val entries: Int,
    val products: Int,
    /** Where the current programme is, or null when there's none. */
    val currentProgramme: ProgrammeStatus?,
    /** The current programme's demon, with its HP and banked hits, once its plan has started. */
    val demon: DemonState?,
    /** Every programme, the current one and those closed. */
    val programmes: Int,
    val achievements: Int,
    val gear: Int,
    val breathingSessions: Int,
)

/**
 * Backups in JSON (format version 2, which still reads version 1 files) and the entries as CSV.
 * An export and a restore both settle the programme results that are due first: the export so
 * the file has them, and the restore since it writes entries.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val entries: EntryRepository,
    private val results: ResultSettler,
    private val clock: Clock,
) {
    /**
     * The whole backup, read in one transaction so the file is one moment's data: a week's
     * result and the hit it earned, and the entries it counted, always come together.
     */
    suspend fun export(): Backup {
        results.settleDue()
        val current = settings.current()
        val game = database.game()
        return database.withTransaction {
            Backup(
                exportedAt = clock.instant().toString(),
                settings = Backup.Settings(
                    dayStart = current.dayStart.toString(),
                    country = current.country,
                    stoppedSmokingOn = current.stoppedSmokingOn?.toString(),
                    demonName = current.demonName,
                    demonCreature = if (current.demonName != null || current.demonVariant != null) current.demonCreature.key else null,
                    demonVariant = current.demonVariant,
                    soundOn = current.soundOn,
                    equippedGear = current.equippedGear,
                ),
                products = database.products().allIncludingHidden().map { it.toBackup() },
                tags = database.tags().all().map { it.toBackup() },
                entries = database.entries().allLiveWithTags().map { it.toBackup() },
                programmes = database.programmes().all().map { it.toBackup() },
                demons = game.allDemons().map { it.toBackup() },
                hitLedger = game.allLedger().map { it.toBackup() },
                achievements = game.achievements().map { Backup.Unlock(it.key, Instant.ofEpochMilli(it.unlockedAt).toString()) },
                gear = game.gear().map { Backup.Unlock(it.key, Instant.ofEpochMilli(it.unlockedAt).toString()) },
                breathingSessions = database.breathing().all().map { it.toBackup() },
            )
        }
    }

    suspend fun counts(): StoredCounts {
        results.settleDue()
        return database.withTransaction {
            val game = database.game()
            StoredCounts(
                entries = database.entries().countLive(),
                products = database.products().count(),
                currentProgramme = database.programmes().currentRow()?.status,
                demon = game.currentDemon()?.let { DemonState(it.demon.toDomain(), it.balance()) },
                programmes = database.programmes().count(),
                achievements = game.achievements().size,
                gear = game.gear().size,
                breathingSessions = database.breathing().count(),
            )
        }
    }

    suspend fun exportCsv(): String = EntriesCsv.write(database.entries().allLiveWithTags().map { it.toDomain() })

    /**
     * Restores [backup]. Merging adds what isn't on the phone and changes nothing that is, with
     * one exception for the programme, since only one can run at a time. A plan still running in
     * the backup takes over from a baseline on the phone, which closes as abandoned: a baseline
     * holds nothing the entries don't, and they merge too. When the phone has a plan of its own
     * running, or both are at their baselines, the phone's stays, and the backup's is counted in
     * [ImportSummary.programmesSkipped]. Replacing wipes everything first, settings included.
     */
    suspend fun import(backup: Backup, mode: ImportMode): ImportSummary {
        results.settleDue()
        var skipped = 0
        val now = clock.millis()
        val products = backup.products.map { it.toEntity(now) }
        val tags = backup.tags.map { it.toEntity() }
        val entryRows = backup.entries.mapNotNull { entry ->
            entry.toEntity(now).also { if (it == null) skipped++ }?.let { it to entry.tags }
        }
        val programmes = backup.programmes.mapNotNull { it.toParts() }
        val demons = backup.demons.mapNotNull { it.toEntity() }
        val ledger = backup.hitLedger.mapNotNull { it.toEntity() }
        val achievements = backup.achievements.mapNotNull { unlock -> unlock.time()?.let { AchievementEntity(unlock.key, it) } }
        val gear = backup.gear.mapNotNull { unlock -> unlock.time()?.let { GearEntity(unlock.key, it) } }
        val sessions = backup.breathingSessions.mapNotNull { it.toEntity() }

        // Every entry below is written at `now`, so an agent's undo needs to know where they stop.
        // Set first, so a restore that fails halfway never leaves restored entries to undo.
        settings.setRestoredAt(Instant.ofEpochMilli(now))
        val summary = database.withTransaction {
            val game = database.game()
            if (mode == ImportMode.REPLACE) {
                database.entries().deleteAllTags()
                database.entries().deleteAll()
                database.tags().deleteAll()
                database.products().deleteAll()
                // Weeks, mixes, demons and the ledger go with their programmes.
                database.programmes().deleteAll()
                game.deleteAllAchievements()
                game.deleteAllGear()
                database.breathing().deleteAll()
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

            val programmeIds = database.programmes().allIds().toSet()
            var phone = database.programmes().currentRow()
            var running = phone != null
            val added = mutableSetOf<String>()
            var programmesSkipped = 0
            // The latest running programme in the backup is the one that can be added.
            for (parts in programmes.sortedByDescending { it.programme.createdAt }) {
                val id = parts.programme.id
                if (id in programmeIds) continue
                if (parts.programme.endedAt == null) {
                    val baseline = phone?.takeIf { it.status == ProgrammeStatus.BASELINE }
                    when {
                        !running -> Unit
                        baseline != null && parts.programme.status != ProgrammeStatus.BASELINE -> {
                            database.programmes().update(baseline.copy(status = ProgrammeStatus.ABANDONED, endedAt = now))
                            phone = null
                        }
                        else -> {
                            programmesSkipped++
                            continue
                        }
                    }
                    running = true
                }
                database.programmes().insertWhole(parts)
                added += id
            }
            game.insertDemonsIgnoringExisting(demons.filter { it.programmeId in added })
            game.insertLedgerIgnoringExisting(ledger.filter { it.programmeId in added })
            game.insertAchievements(achievements)
            game.insertGear(gear)
            database.breathing().insertIgnoringExisting(sessions)
            ImportSummary(productsAdded, tagsAdded, entriesAdded, skipped, programmes = added.size, programmesSkipped = programmesSkipped)
        }

        if (mode == ImportMode.REPLACE) {
            val restored = backup.settings
            runCatching { LocalTime.parse(restored.dayStart) }.getOrNull()?.let { settings.setDayStart(it) }
            restored.country?.let { settings.setCountry(it) }
            settings.setStoppedSmokingOn(restored.stoppedSmokingOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            // Version 1 files have none of these, so the phone keeps its own.
            if (restored.demonName != null || restored.demonCreature != null || restored.demonVariant != null) {
                settings.setDemon(restored.demonName, Creature.fromKey(restored.demonCreature), restored.demonVariant)
            }
            restored.soundOn?.let { settings.setSoundOn(it) }
            // Only gear the backup unlocked can stay equipped, since the phone's own is gone.
            settings.setEquippedGear(restored.equippedGear?.takeIf { key -> gear.any { it.key == key } })
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

// The programme and the game, from version 2.

private fun epochMillis(text: String): Long = Instant.parse(text).toEpochMilli()

private fun epochDay(text: String): Long = LocalDate.parse(text).toEpochDay()

private fun Long.instantText(): String = Instant.ofEpochMilli(this).toString()

private fun Long.dateText(): String = LocalDate.ofEpochDay(this).toString()

private fun UnitsColumns.toBackup() = Backup.Units(productId, productName, form, unit, perUnitMicrograms, unitsPerDay)

private fun Backup.Units.toColumns() = UnitsColumns(productId, productName, form, unit, perUnitMicrograms, unitsPerDay)

private fun ProgrammeWithParts.toBackup(): Backup.Programme {
    val row = programme
    val mixes = weekUnits.groupBy { it.weekNumber }
    return Backup.Programme(
        id = row.id,
        status = row.status,
        createdAt = row.createdAt.instantText(),
        endedAt = row.endedAt?.instantText(),
        baselineStartedOn = row.baselineStartedOn?.dateText(),
        baselineStart = row.baselineStart?.dateText(),
        baselineEnd = row.baselineEnd?.dateText(),
        baselineSource = row.baselineSource,
        baselineMeasuredMicrograms = row.baselineMeasuredMicrograms,
        lengthWeeks = row.lengthWeeks,
        nrtFreeWeeks = row.nrtFreeWeeks,
        startDate = row.startDate?.dateText(),
        usualDay = usualDay.map { it.units.toBackup() }.sortedBy { it.productId },
        ladder = ladder.groupBy { it.step }.toSortedMap().map { (step, rows) ->
            Backup.Step(step, rows.map { it.units.toBackup() }.sortedBy { it.productId })
        },
        weeks = weeks.sortedBy { it.number }.map { week ->
            Backup.Week(
                number = week.number,
                startDate = week.startDate.dateText(),
                kind = week.kind,
                step = week.ladderStep,
                dailyGuideMicrograms = week.dailyGuideMicrograms,
                weeklyAllowanceMicrograms = week.weeklyAllowanceMicrograms,
                units = mixes[week.number].orEmpty().map { it.units.toBackup() }.sortedBy { it.productId },
                result = week.result,
                resultAt = week.resultAt?.instantText(),
                choice = week.choice,
                choiceAt = week.choiceAt?.instantText(),
                maxUnitsPerDay = week.maxUnitsPerDay,
                minGapMinutes = week.minGapMinutes,
                firstDoseNotBefore = week.firstDoseNotBeforeMinuteOfDay?.let { LocalTime.ofSecondOfDay(it * 60L).toString() },
            )
        },
    )
}

/**
 * A programme from a backup as rows, or null when it can't be trusted: a broken date, or rows
 * that don't make a programme. The rows are written out again from the programme they make, so
 * the guides, allowances and dates always agree with the mixes.
 */
private fun Backup.Programme.toParts(): ProgrammeWithParts? = runCatching {
    require(id.isNotBlank())
    val row = ProgrammeEntity(
        id = id,
        status = status,
        createdAt = epochMillis(createdAt),
        endedAt = endedAt?.let(::epochMillis),
        baselineStartedOn = baselineStartedOn?.let(::epochDay),
        baselineStart = baselineStart?.let(::epochDay),
        baselineEnd = baselineEnd?.let(::epochDay),
        baselineSource = baselineSource,
        baselineMeasuredMicrograms = baselineMeasuredMicrograms,
        baselineDailyMicrograms = null,
        lengthWeeks = lengthWeeks,
        nrtFreeWeeks = nrtFreeWeeks,
        startDate = startDate?.let(::epochDay),
    )
    val raw = ProgrammeWithParts(
        programme = row,
        usualDay = usualDay.map { BaselineUnitEntity(id, it.toColumns()) },
        ladder = ladder.flatMap { step -> step.units.map { LadderUnitEntity(id, step.step, it.toColumns()) } },
        weeks = weeks.map { week ->
            val start = epochDay(week.startDate)
            ProgrammeWeekEntity(
                programmeId = id, number = week.number, startDate = start, endDate = start + 6, kind = week.kind,
                ladderStep = week.step, dailyGuideMicrograms = week.dailyGuideMicrograms,
                weeklyAllowanceMicrograms = week.weeklyAllowanceMicrograms, isNrtFree = week.step == null,
                maxUnitsPerDay = week.maxUnitsPerDay, minGapMinutes = week.minGapMinutes,
                firstDoseNotBeforeMinuteOfDay = week.firstDoseNotBefore?.let { LocalTime.parse(it) }?.let { it.hour * 60 + it.minute },
                result = week.result, resultAt = week.resultAt?.let(::epochMillis),
                choice = week.choice, choiceAt = week.choiceAt?.let(::epochMillis),
            )
        },
        weekUnits = weeks.flatMap { week -> week.units.map { ProgrammeWeekUnitEntity(id, week.number, it.toColumns()) } },
        demons = emptyList(),
    )
    // Stand-ins from the rows themselves are enough to check the programme holds together.
    val record = raw.toRecord(emptyMap())
    val plan = record.plan
    when (status) {
        ProgrammeStatus.BASELINE -> require(plan == null)
        ProgrammeStatus.ABANDONED -> Unit
        else -> require(plan != null && plan.status == status)
    }
    val usual = record.usualDay
    val (weekRows, unitRows) = plan?.toWeekEntities(id, record.choices) ?: (emptyList<ProgrammeWeekEntity>() to emptyList())
    ProgrammeWithParts(
        programme = row.copy(baselineDailyMicrograms = usual?.total?.micrograms),
        usualDay = usual?.toEntities(id).orEmpty(),
        ladder = plan?.ladder?.toEntities(id).orEmpty(),
        weeks = weekRows,
        weekUnits = unitRows,
        demons = emptyList(),
    )
}.getOrNull()

private fun DemonEntity.toBackup() = Backup.Demon(
    id = id,
    programmeId = programmeId,
    name = name,
    creature = creature.key,
    variant = variant,
    maxHp = maxHp,
    createdAt = createdAt.instantText(),
    defeatedAt = defeatedAt?.instantText(),
)

private fun Backup.Demon.toEntity(): DemonEntity? = runCatching {
    require(id.isNotBlank() && maxHp >= 0)
    DemonEntity(id, programmeId, name, Creature.fromKey(creature), variant, maxHp, epochMillis(createdAt), defeatedAt?.let(::epochMillis))
}.getOrNull()

private fun HitLedgerEntity.toBackup() = Backup.LedgerRow(id, programmeId, kind, hits, week, earnedBy, createdAt.instantText())

private fun Backup.LedgerRow.toEntity(): HitLedgerEntity? = runCatching {
    require(id.isNotBlank() && hits == kind.hits)
    require((week == null) == (hits < 0)) { "Only earned hits pay for a week" }
    HitLedgerEntity(id, programmeId, kind, hits, week, earnedBy, epochMillis(at))
}.getOrNull()

private fun Backup.Unlock.time(): Long? = runCatching { require(key.isNotBlank()); epochMillis(unlockedAt) }.getOrNull()

private fun BreathingSessionEntity.toBackup() =
    Backup.BreathingSession(id, type, startedAt.instantText(), durationSeconds, roundsCompleted, ranToEnd)

private fun Backup.BreathingSession.toEntity(): BreathingSessionEntity? = runCatching {
    require(id.isNotBlank() && durationSeconds >= 0 && rounds >= 0)
    BreathingSessionEntity(id, pattern, epochMillis(startedAt), durationSeconds, rounds, ranToEnd)
}.getOrNull()
