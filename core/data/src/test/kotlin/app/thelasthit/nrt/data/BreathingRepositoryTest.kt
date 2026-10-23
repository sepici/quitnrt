package app.thelasthit.nrt.data

import app.thelasthit.nrt.domain.advice.BreathingPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BreathingRepositoryTest {

    private lateinit var data: TestData

    @Before
    fun setUp() {
        data = TestData()
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun onlySessionsRunToTheEndCount() = runTest {
        assertEquals(0, data.breathing.completedCount.first())

        val done = data.breathing.record(BreathingPattern.BOX, london("2026-10-23T13:00"), Duration.ofMillis(64_400), rounds = 4, ranToEnd = true)
        assertTrue(done.counts)
        // Kept in whole seconds.
        assertEquals(Duration.ofSeconds(64), done.duration)
        val stopped = data.breathing.record(BreathingPattern.FOUR_SEVEN_EIGHT, london("2026-10-23T14:00"), Duration.ofSeconds(40), rounds = 2, ranToEnd = false)
        assertFalse(stopped.counts)
        data.breathing.record(BreathingPattern.FOUR_SEVEN_EIGHT, london("2026-10-23T14:30"), Duration.ofSeconds(5), rounds = 0, ranToEnd = true)

        assertEquals(1, data.breathing.completedCount())
        assertEquals(1, data.breathing.completedCount.first())
        val stored = data.database.breathing().all()
        assertEquals(3, stored.size)
        assertEquals(listOf(4, 2, 0), stored.map { it.roundsCompleted })
        assertEquals(BreathingPattern.BOX, stored.first().type)
    }
}
