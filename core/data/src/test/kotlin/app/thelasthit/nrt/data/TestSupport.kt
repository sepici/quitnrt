package app.thelasthit.nrt.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.repo.BackupRepository
import app.thelasthit.nrt.data.repo.BreathingRepository
import app.thelasthit.nrt.data.repo.EntryRepository
import app.thelasthit.nrt.data.repo.GameRepository
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.data.repo.ProductRepository
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.data.repo.ProgrammeRepository
import app.thelasthit.nrt.data.repo.TagRepository
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    val programmes = ProgrammeRepository(database, products, settings, catalogue = { catalogue }, clock = clock)
    val entries = EntryRepository(database.entries(), products, settings, clock, results = programmes)
    val game = GameRepository(database, settings, clock)
    val breathing = BreathingRepository(database.breathing())
    val backups = BackupRepository(database, settings, entries, results = programmes, clock = clock)

    fun close() {
        database.close()
        scope.cancel()
        settingsFile.delete()
    }

    companion object {
        val catalogue: Catalogue by lazy { Catalogue.bundled() }
    }
}

internal const val GUM_4 = "nicorette-gum-uk-4mg"
internal const val GUM_2 = "nicorette-gum-uk-2mg"

/** Saturday 24 October 2026, the day after the tests' Friday: a plan's default start. */
internal val PLAN_START: LocalDate = LocalDate.parse("2026-10-24")

/**
 * A 12-week plan from [PLAN_START], down from a typed usual day of 8 pieces of 4 mg gum, PLAN's
 * second example: 6 × 4 mg in week 1 (168 mg), 4 × 4 mg in week 2, then 8 × 2 mg.
 */
internal suspend fun TestData.startGumPlan(): ProgrammeRecord {
    settings.setCountry("GB")
    val gum = products.addFromCatalogue(GUM_4)
    programmes.keepUsualDay(UsualDay.typed(gum to 8))
    return programmes.startPlan(checkNotNull(programmes.buildPlan()))
}

/** [pieces] of 4 mg gum at [at] on London's wall clock. */
internal suspend fun TestData.chew(at: String, pieces: Int = 1): Entry {
    val result = entries.log(LogRequest(GUM_4, quantity = pieces, at = london(at), allowDuplicate = true))
    return (result as LogResult.Saved).entry
}

/** The clock at [at] on London's wall clock. */
internal fun TestData.setTime(at: String) {
    clock.now = london(at)
}

/** The current plan's result for week [number]. */
internal suspend fun TestData.resultOf(number: Int): WeekResult =
    checkNotNull(programmes.current()?.plan).week(number).result

/**
 * 8 pieces of 4 mg gum at noon on each day of a gum plan's week 1, 224 mg against its 168, logged
 * late on its last day.
 */
internal suspend fun TestData.chewAllWeekOne() {
    setTime("2026-10-31T00:30")
    for (day in 24..30) chew("2026-10-${day}T12:00", pieces = 8)
}

/** Runs [block] and checks it fails with a [T]. */
internal suspend inline fun <reified T : Throwable> assertFailsWith(crossinline block: suspend () -> Unit) {
    val error = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName} but got $error", error is T)
}
