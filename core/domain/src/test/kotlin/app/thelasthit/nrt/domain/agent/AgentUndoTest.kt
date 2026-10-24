package app.thelasthit.nrt.domain.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AgentUndoTest {

    private val now = Instant.parse("2026-10-23T14:05:00Z")

    @Test
    fun `an entry written in the last day can be undone, and one before that can't`() {
        assertEquals(now.minus(Duration.ofHours(24)), AgentUndo.since(now))
        assertTrue(AgentUndo.canUndo(now.minus(Duration.ofHours(23)), now))
        assertTrue(AgentUndo.canUndo(now.minus(Duration.ofHours(24)), now))
        assertFalse(AgentUndo.canUndo(now.minus(Duration.ofHours(25)), now))
    }

    @Test
    fun `nothing a restore wrote, or anything before it, is undone`() {
        val restoredAt = now.minus(Duration.ofHours(2))
        assertFalse(AgentUndo.canUndo(restoredAt, now, restoredAt))
        assertFalse(AgentUndo.canUndo(restoredAt.minus(Duration.ofMinutes(10)), now, restoredAt))
        assertTrue(AgentUndo.canUndo(restoredAt.plusMillis(1), now, restoredAt))
        assertTrue(AgentUndo.canUndo(now, now, restoredAt))
    }

    @Test
    fun `a restore more than a day ago leaves the day's window as it is`() {
        val restoredAt = now.minus(Duration.ofDays(3))
        assertEquals(now.minus(Duration.ofHours(24)), AgentUndo.since(now, restoredAt))
    }
}
