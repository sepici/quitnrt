package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.log.Entry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/** [app.thelasthit.nrt.data.repo.EntryRepository.lastWritten], which an agent's undo takes back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LastWrittenTest {

    private lateinit var data: TestData

    @Before
    fun setUp() = runTest {
        data = TestData()
        data.products.addFromCatalogue(GUM_2)
        data.products.addFromCatalogue(GUM_4)
    }

    @After
    fun tearDown() = data.close()

    private suspend fun log(request: LogRequest): Entry = (data.entries.log(request) as LogResult.Saved).entry

    private val dayAgo get() = data.clock.now.minus(Duration.ofHours(24))

    @Test
    fun theEntryWrittenLastWinsOverOneTakenLater() = runTest {
        val now = log(LogRequest(GUM_2))
        data.clock.advance(Duration.ofMinutes(2))
        // Written after, but backdated to yesterday.
        val backdated = log(LogRequest(GUM_4, at = london("2026-10-22T15:00")))
        assertEquals(backdated.id, data.entries.lastWritten(since = dayAgo)?.id)

        data.entries.delete(backdated.id)
        assertEquals(now.id, data.entries.lastWritten(since = dayAgo)?.id)
    }

    @Test
    fun nothingWrittenSinceTheTimeGivenIsNull() = runTest {
        log(LogRequest(GUM_2))
        data.clock.advance(Duration.ofHours(25))
        assertNull(data.entries.lastWritten(since = dayAgo))
    }
}
