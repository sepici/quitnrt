package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class HabitsTest {

    private val dayStart = LocalTime.of(4, 0)

    @Test
    fun `the first dose averages across days`() {
        val habits = Habits.of(
            listOf(entry("2026-10-20T08:00"), entry("2026-10-20T12:00"), entry("2026-10-21T09:00")),
            previous = null,
            dayStart,
        )
        assertEquals(LocalTime.of(8, 30), habits.firstDose)
    }

    @Test
    fun `first doses either side of midnight average to midnight, not noon`() {
        val habits = Habits.of(
            // 01:00 on the 22nd counts toward the 21st, and it's that day's only dose.
            listOf(entry("2026-10-20T23:00"), entry("2026-10-22T01:00")),
            previous = null,
            dayStart,
        )
        assertEquals(LocalTime.MIDNIGHT, habits.firstDose)
    }

    @Test
    fun `gaps count within a day, never overnight`() {
        val habits = Habits.of(
            listOf(
                entry("2026-10-20T08:00"),
                entry("2026-10-20T10:00"),
                entry("2026-10-20T15:00"),
                entry("2026-10-21T09:00"),
                entry("2026-10-21T10:00"),
            ),
            previous = null,
            dayStart,
        )
        // Gaps of 2, 5 and 1 hours. The 18 hours overnight don't count.
        assertEquals(Duration.ofMinutes(160), habits.averageGap)
        assertEquals(Habits.Gap(Duration.ofHours(5), LocalDate.parse("2026-10-20")), habits.longestGap)
    }

    @Test
    fun `patches are left out, and a day with one dose has no gap`() {
        val habits = Habits.of(
            listOf(entry("2026-10-20T07:00", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14)), entry("2026-10-20T09:30")),
            previous = null,
            dayStart,
        )
        assertEquals(LocalTime.of(9, 30), habits.firstDose)
        assertNull(habits.averageGap)
        assertNull(habits.longestGap)
    }

    @Test
    fun `changes are in minutes against the previous period`() {
        val habits = Habits.of(
            listOf(entry("2026-10-20T08:15"), entry("2026-10-20T11:15")),
            previous = listOf(entry("2026-10-13T07:30"), entry("2026-10-13T09:30")),
            dayStart,
        )
        assertEquals(45, habits.firstDoseChange)
        assertEquals(60, habits.averageGapChange)
        assertEquals(60, habits.longestGapChange)
    }

    @Test
    fun `with no previous period there are no changes`() {
        val habits = Habits.of(listOf(entry("2026-10-20T08:15")), previous = null, dayStart)
        assertNull(habits.firstDoseChange)
        assertNull(habits.averageGapChange)
        assertNull(habits.longestGapChange)
    }
}
