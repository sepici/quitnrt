package app.thelasthit.nrt.domain.programme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class GapNoteTest {

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(ZONE).toInstant()

    private val earlier = use("2026-10-26T12:00", GUM_2)

    @Test
    fun `a dose closer than the goal to the last one gets a note`() {
        val note = checkNotNull(GapNote.of(90, at("2026-10-26T12:55"), date("2026-10-26"), isPatch = false, entries = listOf(earlier)))
        assertEquals(90, note.goalMinutes)
        assertEquals(earlier, note.other)
        assertEquals(Duration.ofMinutes(55), note.apart)
        assertTrue(note.otherCameFirst)
    }

    @Test
    fun `a dose at the goal or further has none`() {
        assertNull(GapNote.of(90, at("2026-10-26T13:30"), date("2026-10-26"), isPatch = false, entries = listOf(earlier)))
        assertNull(GapNote.of(90, at("2026-10-26T15:00"), date("2026-10-26"), isPatch = false, entries = listOf(earlier)))
    }

    @Test
    fun `the gap is read to the minute, as the times show`() {
        // A dose logged as now keeps its seconds, but 10:30 to 12:00 on the clock is 90 minutes.
        val last = use("2026-10-26T10:30:45", GUM_2)
        assertNull(GapNote.of(90, at("2026-10-26T12:00:10"), date("2026-10-26"), isPatch = false, entries = listOf(last)))
        // 10:30 to 11:59 is 89, though it's nearer 90 to the second.
        val early = use("2026-10-26T10:30:10", GUM_2)
        val note = checkNotNull(GapNote.of(90, at("2026-10-26T11:59:50"), date("2026-10-26"), isPatch = false, entries = listOf(early)))
        assertEquals(Duration.ofMinutes(89), note.apart)
        assertTrue(note.otherCameFirst)
    }

    @Test
    fun `nothing when the goal is off, for a patch, or with nothing else that day`() {
        assertNull(GapNote.of(null, at("2026-10-26T12:10"), date("2026-10-26"), isPatch = false, entries = listOf(earlier)))
        assertNull(GapNote.of(90, at("2026-10-26T12:10"), date("2026-10-26"), isPatch = true, entries = listOf(earlier)))
        assertNull(GapNote.of(90, at("2026-10-26T12:10"), date("2026-10-26"), isPatch = false, entries = emptyList()))
    }

    @Test
    fun `patches beside the dose don't count`() {
        val patch = use("2026-10-26T12:30", PATCH_21)
        assertNull(GapNote.of(90, at("2026-10-26T13:45"), date("2026-10-26"), isPatch = false, entries = listOf(earlier, patch)))
    }

    @Test
    fun `the night isn't a gap, so a dose just after the day start has none`() {
        // 03:30 counts toward the 25th; 04:10 is the 26th's first dose.
        val late = use("2026-10-26T03:30", GUM_2)
        assertNull(GapNote.of(90, at("2026-10-26T04:10"), date("2026-10-26"), isPatch = false, entries = listOf(late)))
    }

    @Test
    fun `a dose backdated between two is held against the nearer one`() {
        val later = use("2026-10-26T14:00", GUM_2)
        val note = checkNotNull(GapNote.of(90, at("2026-10-26T13:40"), date("2026-10-26"), isPatch = false, entries = listOf(earlier, later)))
        assertEquals(later, note.other)
        assertEquals(Duration.ofMinutes(20), note.apart)
        assertFalse(note.otherCameFirst)
    }

    @Test
    fun `an edited dose isn't compared with itself`() {
        val editing = use("2026-10-26T12:50", GUM_2)
        val note = GapNote.of(90, at("2026-10-26T12:50"), date("2026-10-26"), isPatch = false, entries = listOf(earlier, editing), editing = editing.id)
        assertEquals(earlier, note?.other)
        assertNull(GapNote.of(90, at("2026-10-26T12:50"), date("2026-10-26"), isPatch = false, entries = listOf(editing), editing = editing.id))
    }

    @Test
    fun `the goal is the one for the dose's week`() {
        val programme = ExtraGoals.apply(planExample(), 1, WeekGoals(minGapMinutes = 90))
        val later = ExtraGoals.apply(programme, 2, WeekGoals(minGapMinutes = 120))
        assertEquals(90, GapNote.goalOn(later, date("2026-10-26")))
        assertEquals(120, GapNote.goalOn(later, date("2026-11-02")))
        assertNull(GapNote.goalOn(later, date("2026-10-23")))
        assertNull(GapNote.goalOn(planExample(), date("2026-10-26")))
        assertNull(GapNote.goalOn(null, date("2026-10-26")))
    }
}
