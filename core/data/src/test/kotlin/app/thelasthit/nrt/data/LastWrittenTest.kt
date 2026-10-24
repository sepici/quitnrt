package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.agent.AgentUndo
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

/**
 * [app.thelasthit.nrt.data.repo.EntryRepository.lastWritten], which an agent's undo takes back,
 * and the restore time that keeps a restore's entries out of it.
 */
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

    @Test
    fun aRestoreSaysWhenItRanSoUndoCanLeaveOutWhatItWrote() = runTest {
        assertNull(data.settings.restoredAt())
        log(LogRequest(GUM_2, at = london("2026-09-20T09:00")))
        log(LogRequest(GUM_4, at = london("2026-10-01T09:00")))
        val backup = data.backups.export()
        data.clock.advance(Duration.ofDays(2))

        for (mode in ImportMode.entries) {
            // For a merge, entries the phone doesn't have yet.
            val restoring = if (mode == ImportMode.MERGE) backup.copy(entries = backup.entries.map { it.copy(id = it.id + "-merged") }) else backup
            data.backups.import(restoring, mode)
            val restoredAt = data.settings.restoredAt()
            assertEquals(data.clock.now, restoredAt)
            // A backup doesn't say when its entries were written, so none of them looks just written.
            assertNull(data.entries.lastWritten(since = dayAgo))
            assertNull(data.entries.lastWritten(since = AgentUndo.since(data.clock.now, restoredAt)))
            data.clock.advance(Duration.ofMinutes(1))
        }

        val after = log(LogRequest(GUM_2))
        assertEquals(after.id, data.entries.lastWritten(since = AgentUndo.since(data.clock.now, data.settings.restoredAt()))?.id)
    }
}
