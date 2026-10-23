package app.thelasthit.nrt.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProgrammeDao {
    @Insert
    abstract suspend fun insert(programme: ProgrammeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnoringExisting(programmes: List<ProgrammeEntity>): List<Long>

    @Update
    abstract suspend fun update(programme: ProgrammeEntity)

    @Query("UPDATE programmes SET status = :status WHERE id = :id")
    abstract suspend fun setStatus(id: String, status: ProgrammeStatus)

    @Query("SELECT * FROM programmes WHERE id = :id")
    abstract suspend fun get(id: String): ProgrammeEntity?

    /** The current programme: the latest one not yet closed. */
    @Query("SELECT * FROM programmes WHERE endedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    abstract suspend fun currentRow(): ProgrammeEntity?

    @Query("SELECT * FROM programmes WHERE endedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    abstract fun observeCurrentRow(): Flow<ProgrammeEntity?>

    @Transaction
    @Query("SELECT * FROM programmes WHERE endedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    abstract suspend fun current(): ProgrammeWithParts?

    @Transaction
    @Query("SELECT * FROM programmes WHERE endedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    abstract fun observeCurrent(): Flow<ProgrammeWithParts?>

    @Transaction
    @Query("SELECT * FROM programmes ORDER BY createdAt")
    abstract suspend fun all(): List<ProgrammeWithParts>

    @Query("SELECT id FROM programmes")
    abstract suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM programmes")
    abstract suspend fun count(): Int

    @Query("DELETE FROM programmes")
    abstract suspend fun deleteAll()

    // The usual day and the ladder.

    @Insert
    abstract suspend fun insertUsualDay(units: List<BaselineUnitEntity>)

    @Query("DELETE FROM baseline_units WHERE programmeId = :programmeId")
    abstract suspend fun clearUsualDay(programmeId: String)

    @Transaction
    open suspend fun replaceUsualDay(programmeId: String, units: List<BaselineUnitEntity>) {
        clearUsualDay(programmeId)
        insertUsualDay(units)
    }

    @Insert
    abstract suspend fun insertLadder(units: List<LadderUnitEntity>)

    @Query("DELETE FROM ladder_units WHERE programmeId = :programmeId")
    abstract suspend fun clearLadder(programmeId: String)

    @Transaction
    open suspend fun replaceLadder(programmeId: String, units: List<LadderUnitEntity>) {
        clearLadder(programmeId)
        insertLadder(units)
    }

    // Weeks.

    @Insert
    abstract suspend fun insertWeeks(weeks: List<ProgrammeWeekEntity>)

    @Insert
    abstract suspend fun insertWeekUnits(units: List<ProgrammeWeekUnitEntity>)

    /** Clears a programme's weeks, and their mixes with them. */
    @Query("DELETE FROM programme_weeks WHERE programmeId = :programmeId")
    abstract suspend fun clearWeeks(programmeId: String)

    @Transaction
    open suspend fun replaceWeeks(programmeId: String, weeks: List<ProgrammeWeekEntity>, units: List<ProgrammeWeekUnitEntity>) {
        clearWeeks(programmeId)
        insertWeeks(weeks)
        insertWeekUnits(units)
    }

    /** Inserts a whole programme read from a backup. */
    @Transaction
    open suspend fun insertWhole(parts: ProgrammeWithParts) {
        insert(parts.programme)
        insertUsualDay(parts.usualDay)
        insertLadder(parts.ladder)
        insertWeeks(parts.weeks)
        insertWeekUnits(parts.weekUnits)
    }
}

@Dao
interface GameDao {
    // Demons.

    @Insert
    suspend fun insertDemon(demon: DemonEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDemonsIgnoringExisting(demons: List<DemonEntity>): List<Long>

    @Query("SELECT * FROM demons WHERE programmeId = :programmeId")
    suspend fun demonOf(programmeId: String): DemonEntity?

    @Query("UPDATE demons SET maxHp = :maxHp WHERE programmeId = :programmeId")
    suspend fun setMaxHp(programmeId: String, maxHp: Int)

    @Query("UPDATE demons SET name = :name, creature = :creature, variant = :variant WHERE id = :id")
    suspend fun restyle(id: String, name: String?, creature: Creature, variant: String?)

    @Query("UPDATE demons SET defeatedAt = :at WHERE id = :id AND defeatedAt IS NULL")
    suspend fun setDefeated(id: String, at: Long): Int

    /** The current programme's demon, with the hits earned and spent on it. */
    @Query(
        """
        SELECT demons.*,
            (SELECT COALESCE(SUM(hits), 0) FROM hit_ledger WHERE hit_ledger.programmeId = demons.programmeId AND hits > 0) AS earned,
            (SELECT COALESCE(-SUM(hits), 0) FROM hit_ledger WHERE hit_ledger.programmeId = demons.programmeId AND hits < 0) AS spent
        FROM demons JOIN programmes ON programmes.id = demons.programmeId
        WHERE programmes.endedAt IS NULL
        ORDER BY programmes.createdAt DESC LIMIT 1
        """,
    )
    fun observeCurrentDemon(): Flow<DemonWithLedger?>

    @Query(
        """
        SELECT demons.*,
            (SELECT COALESCE(SUM(hits), 0) FROM hit_ledger WHERE hit_ledger.programmeId = demons.programmeId AND hits > 0) AS earned,
            (SELECT COALESCE(-SUM(hits), 0) FROM hit_ledger WHERE hit_ledger.programmeId = demons.programmeId AND hits < 0) AS spent
        FROM demons JOIN programmes ON programmes.id = demons.programmeId
        WHERE programmes.endedAt IS NULL
        ORDER BY programmes.createdAt DESC LIMIT 1
        """,
    )
    suspend fun currentDemon(): DemonWithLedger?

    @Query("SELECT * FROM demons ORDER BY createdAt")
    suspend fun allDemons(): List<DemonEntity>

    @Query("DELETE FROM demons")
    suspend fun deleteAllDemons()

    // The hit ledger.

    @Insert
    suspend fun insertLedger(row: HitLedgerEntity)

    /** Rows already there, by ID or by the week they pay for, are left as they are. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedgerIgnoringExisting(rows: List<HitLedgerEntity>): List<Long>

    @Query("SELECT * FROM hit_ledger WHERE programmeId = :programmeId ORDER BY createdAt, id")
    suspend fun ledgerOf(programmeId: String): List<HitLedgerEntity>

    @Query("SELECT * FROM hit_ledger WHERE programmeId = :programmeId ORDER BY createdAt, id")
    fun observeLedgerOf(programmeId: String): Flow<List<HitLedgerEntity>>

    @Query("SELECT * FROM hit_ledger ORDER BY createdAt, id")
    suspend fun allLedger(): List<HitLedgerEntity>

    @Query("DELETE FROM hit_ledger")
    suspend fun deleteAllLedger()

    // Achievements and gear.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievements(rows: List<AchievementEntity>): List<Long>

    @Query("SELECT * FROM achievements ORDER BY unlockedAt, `key`")
    suspend fun achievements(): List<AchievementEntity>

    @Query("SELECT * FROM achievements ORDER BY unlockedAt, `key`")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("DELETE FROM achievements")
    suspend fun deleteAllAchievements()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGear(rows: List<GearEntity>): List<Long>

    @Query("SELECT * FROM gear ORDER BY unlockedAt, `key`")
    suspend fun gear(): List<GearEntity>

    @Query("SELECT * FROM gear ORDER BY unlockedAt, `key`")
    fun observeGear(): Flow<List<GearEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM gear WHERE `key` = :key)")
    suspend fun hasGear(key: String): Boolean

    @Query("DELETE FROM gear")
    suspend fun deleteAllGear()
}

@Dao
interface BreathingDao {
    @Insert
    suspend fun insert(session: BreathingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(sessions: List<BreathingSessionEntity>): List<Long>

    /** Sessions that count towards the badges: run to the end, with a whole round done. */
    @Query("SELECT COUNT(*) FROM breathing_sessions WHERE ranToEnd = 1 AND roundsCompleted >= 1")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM breathing_sessions WHERE ranToEnd = 1 AND roundsCompleted >= 1")
    suspend fun completedCount(): Int

    @Query("SELECT * FROM breathing_sessions ORDER BY startedAt")
    suspend fun all(): List<BreathingSessionEntity>

    @Query("SELECT COUNT(*) FROM breathing_sessions")
    suspend fun count(): Int

    @Query("DELETE FROM breathing_sessions")
    suspend fun deleteAll()
}
