package app.thelasthit.nrt.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

/** The time now, then again at the start of every minute: for "1 h 25 min ago" and the day rolling over. */
fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        val now = clock.instant()
        emit(now)
        delay(60_000 - now.toEpochMilli() % 60_000)
    }
}
