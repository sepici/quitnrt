package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.programme.MissedWeekChoice.CARRY_ON
import app.thelasthit.nrt.domain.programme.MissedWeekChoice.REPEAT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MissedWeekChoicesTest {

    // Week 3 is missed. Its result comes in at 04:00 on Sunday 15 November, and week 4 runs to
    // 04:00 on Saturday 21 November.
    private val missed = play(planExample(), "mmx").programme

    private fun at(text: String) = LocalDateTime.parse(text).atZone(ZONE).toInstant()

    private fun carriedOnAt(text: String) = mapOf(3 to WeekChoice(3, CARRY_ON, at(text)))

    @Test
    fun `a missed week waits for an answer until one is recorded`() {
        val clock = clockAt("2026-11-15T09:00")
        assertEquals(missed.week(3), MissedWeekChoices.awaiting(missed, emptyMap(), clock))
        assertNull(MissedWeekChoices.awaiting(missed, carriedOnAt("2026-11-15T09:00"), clock))
        // Once the next week ends, carrying on stands without an answer.
        assertNull(MissedWeekChoices.awaiting(missed, emptyMap(), clockAt("2026-11-21T04:00")))
    }

    @Test
    fun `either choice can be made while the next week runs`() {
        val clock = clockAt("2026-11-18T12:00")
        assertTrue(MissedWeekChoices.canChoose(missed, 3, CARRY_ON, emptyMap(), clock))
        assertTrue(MissedWeekChoices.canChoose(missed, 3, REPEAT, emptyMap(), clock))
        val late = clockAt("2026-11-21T04:00")
        assertFalse(MissedWeekChoices.canChoose(missed, 3, CARRY_ON, emptyMap(), late))
        assertFalse(MissedWeekChoices.canChoose(missed, 3, REPEAT, emptyMap(), late))
    }

    @Test
    fun `carrying on can change to a repeat until the end of the day it was picked`() {
        val choices = carriedOnAt("2026-11-15T09:00")
        assertTrue(MissedWeekChoices.canChoose(missed, 3, REPEAT, choices, clockAt("2026-11-16T03:59")))
        assertFalse(MissedWeekChoices.canChoose(missed, 3, REPEAT, choices, clockAt("2026-11-16T04:00")))
        // Picking carry on again changes nothing, so it's allowed.
        assertTrue(MissedWeekChoices.canChoose(missed, 3, CARRY_ON, choices, clockAt("2026-11-17T12:00")))
    }

    @Test
    fun `a repeat can't be taken back or made twice`() {
        val clock = clockAt("2026-11-15T09:00")
        val repeated = missed.repeat(3, clock)
        val choices = mapOf(3 to WeekChoice(3, REPEAT, clock.now))
        assertFalse(MissedWeekChoices.canChoose(repeated, 3, CARRY_ON, choices, clock))
        assertFalse(MissedWeekChoices.canChoose(repeated, 3, REPEAT, choices, clock))
        assertNull(MissedWeekChoices.awaiting(repeated, choices, clock))
    }

    @Test
    fun `weeks that weren't missed, and a missed final week, have no choice`() {
        val clock = clockAt("2026-11-15T09:00")
        assertFalse(MissedWeekChoices.canChoose(missed, 2, CARRY_ON, emptyMap(), clock))
        val overtime = play(planExample(), "mmmmmmmmmmmx").programme
        val due = dueFor(overtime.week(12))
        assertFalse(MissedWeekChoices.canChoose(overtime, 12, CARRY_ON, emptyMap(), due))
        assertNull(MissedWeekChoices.awaiting(overtime, emptyMap(), due))
    }
}
