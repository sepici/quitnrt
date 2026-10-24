package app.thelasthit.nrt.domain.programme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId

class GoalProgressTest {

    private val dayStart = LocalTime.of(4, 0)

    private val goals = WeekGoals(maxUnitsPerDay = mapOf(GUM_2.id to 5), minGapMinutes = 90, firstDoseNotBefore = LocalTime.of(9, 0))

    /** PLAN's first example, 12 pieces of 2 mg gum a day, with every goal on from week 1. Week 1 runs Saturday 24 to Friday 30 October. */
    private val programme = ExtraGoals.apply(planExample(), 1, goals)

    private val week1 = programme.week(1)

    @Test
    fun `a day keeps a cap at or under it, and a day with none keeps it too`() {
        val entries = listOf(
            use("2026-10-24T09:00", GUM_2, 5),
            use("2026-10-25T09:00", GUM_2, 2),
            use("2026-10-25T13:00", GUM_2, 4),
            // Nothing on the 26th.
            use("2026-10-27T09:00", GUM_2, 3),
            // Today, the 28th, isn't over, so it isn't counted yet.
            use("2026-10-28T09:00", GUM_2, 10),
        )
        val progress = GoalProgress.of(week1, entries, today = date("2026-10-28"), dayStart)
        assertEquals(4, progress.days)
        assertEquals(listOf(GoalProgress.Cap(GUM_2, 5, kept = 3, of = 4)), progress.caps)
        assertFalse(progress.caps.single().isAllKept)
    }

    @Test
    fun `a cap counts its own product's units, by the logical day`() {
        val entries = listOf(
            use("2026-10-24T20:00", GUM_2, 4),
            // 01:30 counts toward the evening before, which makes 6.
            use("2026-10-25T01:30", GUM_2, 2),
            use("2026-10-25T09:00", GUM_4, 6),
            use("2026-10-25T10:00", SPRAY, 6),
        )
        val cap = GoalProgress.of(week1, entries, today = date("2026-10-26"), dayStart).caps.single()
        assertEquals(1, cap.kept)
        assertEquals(2, cap.of)
    }

    @Test
    fun `a gap counts only between doses on the same logical day`() {
        val entries = listOf(
            // The 24th: 90 minutes, then 89, then most of the day.
            use("2026-10-24T09:00", GUM_2),
            use("2026-10-24T10:30", GUM_2),
            use("2026-10-24T11:59", GUM_2),
            // Across midnight, still the 24th: two hours.
            use("2026-10-24T23:30", GUM_2),
            use("2026-10-25T01:30", GUM_2),
            // The 25th starts at 04:00, so the three hours from 01:30 aren't a gap. Then 30 minutes.
            use("2026-10-25T04:30", GUM_2),
            use("2026-10-25T05:00", GUM_2),
        )
        val gap = checkNotNull(GoalProgress.of(week1, entries, today = date("2026-10-26"), dayStart).gap)
        assertEquals(GoalProgress.Gap(90, kept = 3, of = 5), gap)
    }

    @Test
    fun `a gap is read to the minute, as the times show`() {
        val entries = listOf(
            // A dose logged as now keeps its seconds, but 10:30 to 12:00 on the clock is 90 minutes.
            use("2026-10-24T10:30:45", GUM_2),
            use("2026-10-24T12:00:10", GUM_2),
            // 10:30 to 11:59 is 89, though it's nearer 90 to the second.
            use("2026-10-25T10:30:10", GUM_2),
            use("2026-10-25T11:59:50", GUM_2),
        )
        val gap = checkNotNull(GoalProgress.of(week1, entries, today = date("2026-10-26"), dayStart).gap)
        assertEquals(GoalProgress.Gap(90, kept = 1, of = 2), gap)
    }

    @Test
    fun `patches are left out of the gaps and the first dose`() {
        val entries = listOf(
            use("2026-10-24T07:00", PATCH_21),
            use("2026-10-24T09:30", GUM_2),
            use("2026-10-24T10:00", PATCH_21),
            use("2026-10-24T11:30", GUM_2),
        )
        val progress = GoalProgress.of(week1, entries, today = date("2026-10-25"), dayStart)
        assertEquals(GoalProgress.Gap(90, kept = 1, of = 1), progress.gap)
        assertEquals(GoalProgress.FirstDose(LocalTime.of(9, 0), kept = 1, of = 1), progress.firstDose)
    }

    @Test
    fun `a day keeps the first dose goal at the time or later, or with no dose`() {
        val entries = listOf(
            use("2026-10-24T09:00", GUM_2),
            use("2026-10-25T08:59", GUM_2),
            use("2026-10-25T12:00", GUM_2),
            // Nothing on the 26th.
            // The 27th's only dose is after midnight, which is late in its day, not early.
            use("2026-10-28T01:00", GUM_2),
        )
        val first = checkNotNull(GoalProgress.of(week1, entries, today = date("2026-10-28"), dayStart).firstDose)
        assertEquals(GoalProgress.FirstDose(LocalTime.of(9, 0), kept = 3, of = 4), first)
    }

    @Test
    fun `the first dose is read on its own clock`() {
        // Taken at 08:30 in New York, which is 13:30 in London: early on its own clock.
        val travelling = use("2026-10-24T13:30", GUM_2).copy(zone = ZoneId.of("America/New_York"))
        val first = checkNotNull(GoalProgress.of(week1, listOf(travelling), today = date("2026-10-25"), dayStart).firstDose)
        assertEquals(0, first.kept)
    }

    @Test
    fun `a week that's over counts all seven days, and one kept every time is all kept`() {
        val entries = (0L until 7L).map { use(week1.startDate.plusDays(it).atTime(10, 0).toString(), GUM_2, 4) }
        val progress = GoalProgress.of(week1, entries, today = date("2026-11-05"), dayStart)
        assertEquals(7, progress.days)
        assertTrue(progress.caps.single().isAllKept)
        assertTrue(checkNotNull(progress.firstDose).isAllKept)
        // One dose a day leaves no gaps, so there's nothing to keep.
        assertEquals(GoalProgress.Gap(90, kept = 0, of = 0), progress.gap)
        assertFalse(checkNotNull(progress.gap).isAllKept)
    }

    @Test
    fun `on a week's first day nothing is counted yet`() {
        val progress = GoalProgress.of(week1, listOf(use("2026-10-24T06:00", GUM_2, 9)), today = week1.startDate, dayStart)
        assertEquals(0, progress.days)
        assertEquals(listOf(0, 0, 0), progress.counts.map { it.of })
        assertTrue(progress.counts.none { it.isAllKept })
    }

    @Test
    fun `a week with its goals off has nothing to count`() {
        val progress = GoalProgress.of(planExample().week(1), listOf(use("2026-10-24T06:00", GUM_2, 9)), date("2026-10-28"), dayStart)
        assertTrue(progress.isEmpty)
        assertNull(progress.gap)
        assertNull(progress.firstDose)
    }

    @Test
    fun `a goal never changes whether the week is met`() {
        // Well within the allowance, with every goal broken every day.
        val entries = (0L until 7L).flatMap { day ->
            val date = week1.startDate.plusDays(day)
            listOf(use(date.atTime(6, 0).toString(), GUM_2, 6), use(date.atTime(6, 10).toString(), GUM_2, 1))
        }
        val played = programme.settle(entries, dueFor(week1)).programme
        assertEquals(WeekResult.MET, played.week(1).result)
        val progress = GoalProgress.of(played.week(1), entries, today = date("2026-11-05"), dayStart)
        assertTrue(progress.counts.all { it.kept == 0 })
    }

    // This week and last.

    @Test
    fun `this week and last each show their own goals`() {
        val entries = listOf(
            use("2026-10-24T09:00", GUM_2, 3),
            use("2026-10-31T09:10", GUM_2, 3),
        )
        // Saturday 1 November: week 2's second day.
        val weeks = checkNotNull(GoalWeeks.of(programme, entries, today = date("2026-11-01"), dayStart))
        assertEquals(2, weeks.thisWeek?.week?.number)
        assertEquals(1, weeks.lastWeek?.week?.number)
        val first = checkNotNull(weeks.firstDose)
        // Week 2's first dose moved 15 minutes later, so 09:10 is before it.
        assertEquals(GoalProgress.FirstDose(LocalTime.of(9, 15), kept = 0, of = 1), first.thisWeek)
        assertEquals(GoalProgress.FirstDose(LocalTime.of(9, 0), kept = 7, of = 7), first.lastWeek)
        assertEquals(listOf(GUM_2.id), weeks.caps.map { (it.thisWeek ?: it.lastWeek)!!.product.id })
        assertEquals(5, weeks.caps.single().thisWeek?.maxUnits)
        assertEquals(5, weeks.caps.single().lastWeek?.maxUnits)
    }

    @Test
    fun `a goal on in only one of the two weeks has nothing for the other`() {
        // The gap goal switched on in week 2 only.
        val later = ExtraGoals.apply(programme, 2, programme.week(2).goals.copy(minGapMinutes = 120, firstDoseNotBefore = null))
        val weeks = checkNotNull(GoalWeeks.of(later, emptyList(), today = date("2026-11-03"), dayStart))
        assertEquals(120, weeks.gap?.thisWeek?.minutes)
        assertEquals(90, weeks.gap?.lastWeek?.minutes)
        assertNull(weeks.firstDose?.thisWeek)
        assertEquals(LocalTime.of(9, 0), weeks.firstDose?.lastWeek?.notBefore)
    }

    @Test
    fun `there's nothing to show before the plan, or with every goal off`() {
        assertNull(GoalWeeks.of(programme, emptyList(), today = date("2026-10-23"), dayStart))
        assertNull(GoalWeeks.of(planExample(), emptyList(), today = date("2026-11-01"), dayStart))
        // In week 1, only week 1 has something to show.
        val first = checkNotNull(GoalWeeks.of(programme, emptyList(), today = date("2026-10-26"), dayStart))
        assertEquals(1, first.thisWeek?.week?.number)
        assertNull(first.lastWeek)
    }

    @Test
    fun `NRT-free weeks have no goals, so the last taper week shows as last week`() {
        // Week 11 is the first NRT-free week.
        val nrtFree = programme.week(11)
        val weeks = checkNotNull(GoalWeeks.of(programme, emptyList(), today = nrtFree.startDate.plusDays(2), dayStart))
        assertNull(weeks.thisWeek)
        assertEquals(10, weeks.lastWeek?.week?.number)
        // Once every week is over, the last week with goals is too far back to show.
        assertNull(GoalWeeks.of(programme, emptyList(), today = programme.weeks.last().endDate.plusDays(1), dayStart))
    }
}
