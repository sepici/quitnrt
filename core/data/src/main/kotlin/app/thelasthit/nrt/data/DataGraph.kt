package app.thelasthit.nrt.data

import android.content.Context
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.repo.EntryRepository
import app.thelasthit.nrt.data.repo.ProductRepository
import app.thelasthit.nrt.data.repo.TagRepository
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.catalogue.Catalogue
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
    val entries = EntryRepository(database.entries(), products, settings, clock, zone = { ZoneId.systemDefault() })
}
