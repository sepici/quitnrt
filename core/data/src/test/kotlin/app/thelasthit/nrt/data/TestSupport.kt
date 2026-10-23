package app.thelasthit.nrt.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.repo.EntryRepository
import app.thelasthit.nrt.data.repo.ProductRepository
import app.thelasthit.nrt.data.repo.TagRepository
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.catalogue.Catalogue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

internal val LONDON: ZoneId = ZoneId.of("Europe/London")

internal fun london(text: String): Instant = LocalDateTime.parse(text).atZone(LONDON).toInstant()

/** A clock tests can move forward. */
internal class MutableClock(var now: Instant, private val zone: ZoneId = LONDON) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(by: Duration) {
        now = now.plus(by)
    }
}

/** An in-memory database and a throwaway settings file, wired like the real app. */
internal class TestData(start: Instant = london("2026-10-23T15:05")) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsFile = File(context.cacheDir, "test-${System.nanoTime()}.preferences_pb")

    val clock = MutableClock(start)
    val database = AppDatabase.inMemory(context)
    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile }))
    val products = ProductRepository(database.products(), catalogue = { catalogue }, clock = clock)
    val tags = TagRepository(database.tags())
    val entries = EntryRepository(database.entries(), products, settings, clock)

    fun close() {
        database.close()
        scope.cancel()
        settingsFile.delete()
    }

    companion object {
        val catalogue: Catalogue by lazy { Catalogue.bundled() }
    }
}
