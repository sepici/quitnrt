package app.thelasthit.nrt.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database. Version 2 adds the tables for the programme, the game and the breathing
 * timer, so phases 3 to 5 share one migration. Every version's schema is exported to
 * `core/data/schemas/`, and each migration is tested from version 1.
 */
@Database(
    entities = [
        ProductEntity::class, EntryEntity::class, EntryTagEntity::class, TagEntity::class,
        ProgrammeEntity::class, BaselineUnitEntity::class, LadderUnitEntity::class,
        ProgrammeWeekEntity::class, ProgrammeWeekUnitEntity::class,
        DemonEntity::class, HitLedgerEntity::class, AchievementEntity::class, GearEntity::class,
        BreathingSessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun entries(): EntryDao
    abstract fun tags(): TagDao
    abstract fun programmes(): ProgrammeDao
    abstract fun game(): GameDao
    abstract fun breathing(): BreathingDao

    companion object {
        const val NAME = "last-hit.db"

        /** Every migration, oldest first. */
        val MIGRATIONS = arrayOf(Migrations.FROM_1_TO_2)

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).addMigrations(*MIGRATIONS).build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
