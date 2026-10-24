package app.thelasthit.nrt.ui.plan

import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.programme.ExtraGoals
import app.thelasthit.nrt.domain.programme.WeekGoals
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

// Seeds for the extra goals in stats (PLAN §6.3): the mockups' plan with every goal switched on
// as week 3 starts, and the doses of weeks 3 and 4 logged one by one, so the gaps and first doses
// have something to count.

/**
 * A day's doses of 2 mg gum ("G") and sprays ("S"), one each at the times given, such as
 * "09:10 G, 10:45 G, 12:30 S", logged with the clock at that evening's 23:30, or at [now] for a
 * day still running.
 */
internal fun TestApp.logDoses(date: String, doses: String, now: String? = null) = runBlocking {
    clock.now = london(now ?: "${date}T23:30")
    doses.split(",").map { it.trim().split(" ") }.forEach { (time, kind) ->
        val product = if (kind == "S") QUICKMIST else GUM_2
        graph.entries.log(LogRequest(product, at = london("${date}T$time"), allowDuplicate = true))
    }
}

/**
 * Wednesday 18 November, day 5 of week 4, with goals on since week 3. Week 3 (7 to 13 November)
 * had at most 8 pieces of 2 mg gum and 3 sprays a day, 90 minutes between doses and the first
 * dose from 08:45. Week 4 carried them on, with its first dose 15 minutes later, at 09:00, and
 * the gum cap brought down to its mix of 6.
 *
 * Week 3 kept the gum cap on 6 of 7 days, the sprays on all 7, 47 of 54 gaps and the first dose
 * on 5 of 7 days. Week 4's four days over kept the gum cap on 3, the sprays on all 4, 26 of 28
 * gaps and the first dose on 3. The last dose today was at 14:25.
 */
internal fun TestApp.seedGoalWeeks() {
    startMockupPlan()
    logWeek("2026-10-24", 27, 27, 27, 27, 27, 27, 27)
    logWeek("2026-10-31", 19, 19, 18, 18, 18, 18, 18)

    setTime("2026-11-07T07:00")
    runBlocking {
        graph.programmes.updateGoals { week ->
            WeekGoals(maxUnitsPerDay = ExtraGoals.startingCaps(week), minGapMinutes = 90, firstDoseNotBefore = LocalTime.of(8, 45))
        }
    }
    logDoses("2026-11-07", "09:10 G, 10:45 G, 12:30 S, 13:30 G, 15:10 G, 17:00 S, 18:40 G, 20:20 G, 22:00 S")
    logDoses("2026-11-08", "08:30 G, 10:15 G, 12:00 G, 13:40 S, 15:30 G, 17:10 G, 19:00 S, 20:45 G")
    logDoses("2026-11-09", "08:50 G, 10:30 G, 12:15 S, 14:00 G, 15:45 G, 17:30 G, 19:15 G, 21:00 S")
    logDoses("2026-11-10", "08:55 G, 10:40 G, 11:30 G, 13:15 S, 15:00 G, 16:45 G, 18:30 G, 20:15 G, 21:30 G, 22:30 S")
    logDoses("2026-11-11", "09:00 G, 10:45 G, 12:30 S, 14:15 G, 16:00 G, 17:45 G, 19:30 S, 21:15 G")
    logDoses("2026-11-12", "08:20 G, 09:30 G, 11:15 G, 13:00 S, 14:45 G, 16:30 G, 18:15 G, 20:00 G, 21:00 G, 22:15 G")
    logDoses("2026-11-13", "09:05 G, 10:50 G, 12:35 S, 14:20 G, 16:05 G, 17:50 S, 19:35 G, 21:20 G")

    setTime("2026-11-14T07:00")
    runBlocking { graph.programmes.updateGoals { week -> week.goals.copy(maxUnitsPerDay = ExtraGoals.startingCaps(week)) } }
    logDoses("2026-11-14", "09:15 G, 11:00 G, 12:45 S, 14:30 G, 16:15 G, 18:00 S, 19:45 G, 21:30 S")
    logDoses("2026-11-15", "08:40 G, 10:30 G, 12:15 G, 14:00 S, 15:45 G, 17:30 G, 19:15 G, 20:30 G, 22:00 S")
    logDoses("2026-11-16", "09:05 G, 10:50 G, 12:35 S, 14:20 G, 16:05 G, 17:50 G, 19:35 S")
    logDoses("2026-11-17", "09:20 G, 11:05 G, 12:50 S, 14:35 G, 15:30 G, 17:15 S, 19:00 G, 20:45 G")
    logDoses("2026-11-18", "09:10 G, 10:55 G, 12:40 S, 14:25 G", now = "2026-11-18T15:05")
}

/** Wednesday 18 November in week 4, as [seedGoalWeeks] leaves it. */
internal val GOAL_TODAY: LocalDate = LocalDate.parse("2026-11-18")
