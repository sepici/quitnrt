package app.thelasthit.nrt.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProductEntity::class, EntryEntity::class, EntryTagEntity::class, TagEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun entries(): EntryDao
    abstract fun tags(): TagDao

    companion object {
        private const val NAME = "last-hit.db"

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
