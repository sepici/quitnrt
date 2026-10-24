package app.thelasthit.nrt

import app.thelasthit.nrt.data.DataGraph
import kotlinx.coroutines.cancel
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * The app on an in-memory database, so each test starts empty, with the clock stopped at Friday
 * 23 October 2026, 15:05 in London, the time the mockups show. Set in robolectric.properties.
 */
class TestApp : LastHitApp() {

    val clock = TestClock(NOW)

    override fun onCreate() {
        TimeZone.setDefault(TimeZone.getTimeZone(LONDON))
        super.onCreate()
    }

    override fun createGraph(): DataGraph = DataGraph.inMemory(this, clock)

    /**
     * Robolectric ends each test's app here. Its own work, such as the reminders following the
     * logs, stops with it, so nothing an earlier test's app does late can reach a later test,
     * whose app is a new one.
     */
    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }

    companion object {
        val LONDON: ZoneId = ZoneId.of("Europe/London")
        val NOW: Instant = london("2026-10-23T15:05")

        fun london(text: String): Instant = LocalDateTime.parse(text).atZone(LONDON).toInstant()
    }
}

/** A clock tests can move. */
class TestClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = TestApp.LONDON
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}
