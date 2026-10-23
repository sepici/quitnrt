package app.thelasthit.nrt.data.repo

import app.thelasthit.nrt.data.db.BreathingDao
import app.thelasthit.nrt.data.db.BreathingSessionEntity
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.advice.BreathingSession
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The breathing timer's sessions, kept for the breathing badges (PLAN §8.1 and §9). */
class BreathingRepository(
    private val dao: BreathingDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * Records a session that started at [startedAt] and ran for [duration], in whole seconds,
     * with [rounds] whole rounds done. [ranToEnd] is false when it was stopped early.
     */
    suspend fun record(
        pattern: BreathingPattern,
        startedAt: Instant,
        duration: Duration,
        rounds: Int,
        ranToEnd: Boolean,
    ): BreathingSession {
        val session = BreathingSession(newId(), pattern, startedAt, Duration.ofSeconds(duration.seconds), rounds, ranToEnd)
        dao.insert(
            BreathingSessionEntity(
                id = session.id,
                type = session.pattern,
                startedAt = session.startedAt.toEpochMilli(),
                durationSeconds = session.duration.seconds,
                roundsCompleted = session.rounds,
                ranToEnd = session.ranToEnd,
            ),
        )
        return session
    }

    /** How many sessions count towards the badges: run to the end, with a whole round done. */
    val completedCount: Flow<Int> = dao.observeCompletedCount()

    suspend fun completedCount(): Int = dao.completedCount()
}
