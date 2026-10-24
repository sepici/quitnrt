package app.thelasthit.nrt.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.repo.BackupRepository
import app.thelasthit.nrt.data.repo.BreathingRepository
import app.thelasthit.nrt.data.repo.EntryRepository
import app.thelasthit.nrt.data.repo.GameRepository
import app.thelasthit.nrt.data.repo.ProductRepository
import app.thelasthit.nrt.data.repo.ProgrammeRepository
import app.thelasthit.nrt.data.repo.TagRepository
import app.thelasthit.nrt.data.repo.UnlockRepository
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.catalogue.Catalogue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import java.time.ZoneId

/**
 * Everything the app needs from storage, created once in the Application. Plain constructor
 * wiring: the app is small enough not to need a dependency injection framework.
 */
class DataGraph(
    context: Context,
    val clock: Clock = Clock.systemUTC(),
    database: AppDatabase = AppDatabase.open(context),
    settingsStore: SettingsStore = SettingsStore(SettingsStore.createDataStore(context)),
) {
    val catalogue: Catalogue by lazy { Catalogue.bundled() }
    val settings: SettingsStore = settingsStore
    val products = ProductRepository(database.products(), catalogue = { catalogue }, clock = clock)
    val tags = TagRepository(database.tags())
    val programmes = ProgrammeRepository(database, products, settings, catalogue = { catalogue }, clock = clock, zone = { ZoneId.systemDefault() })

    /** Every write settles the programme's due results first. */
    val entries = EntryRepository(database.entries(), products, settings, clock, results = programmes, zone = { ZoneId.systemDefault() })

    /** Reading the demon and spending hits settle the due results first. */
    val game = GameRepository(database, settings, results = programmes, clock = clock)
    val breathing = BreathingRepository(database.breathing())

    /** Badges and gear: the unlock checks, the trophy shelf and the gear equipped. */
    val unlocks = UnlockRepository(database, settings, results = programmes, clock = clock, zone = { ZoneId.systemDefault() })
    val backups = BackupRepository(database, settings, entries, results = programmes, clock = clock)

    companion object {
        /** An in-memory database and a throwaway settings file, for UI tests. */
        fun inMemory(context: Context, clock: Clock = Clock.systemUTC()): DataGraph {
            val file = File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb")
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { file },
            )
            return DataGraph(context, clock, AppDatabase.inMemory(context), SettingsStore(store))
        }
    }
}
