package app.thelasthit.nrt.ui.game

import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.NewUnlocks
import app.thelasthit.nrt.domain.programme.UsualDay
import kotlinx.coroutines.runBlocking

/** Runs the unlock checks now, as the app does when it opens, and returns what they stored. */
fun TestApp.checkUnlocks(): NewUnlocks = runBlocking { graph.unlocks.check() }

/**
 * A fortnight of tagged doses, from Friday 9 to Thursday 22 October, all logged now: seven badges
 * at once, as when phase 4 first looks at the history. One summary card, not seven toasts.
 */
fun TestApp.seedFortnight() = runBlocking {
    for (day in 9..22) {
        for (time in listOf("08:00", "13:00")) {
            graph.entries.log(LogRequest(GUM_4, at = london("2026-10-%02dT%s".format(day, time)), tagKeys = setOf("coffee_tea")))
        }
    }
}

/**
 * A 12-week plan from Saturday 24 October, down from a typed usual day of 8 pieces of 4 mg gum,
 * then the clock on to Sunday 1 November, when week 1, with nothing logged, is met.
 */
fun TestApp.meetFirstWeek() = runBlocking {
    val gum = checkNotNull(graph.products.get(GUM_4))
    graph.programmes.keepUsualDay(UsualDay.typed(gum to 8))
    graph.programmes.startPlan(checkNotNull(graph.programmes.buildPlan()))
    clock.now = london("2026-11-01T09:00")
}

/**
 * The shelf four weeks into a 12-week plan, as lair.html has it, stored as the unlock checks
 * store them: 15 badges, the gear they bring, and the wooden sword equipped.
 */
fun TestApp.seedShelf() = runBlocking {
    val earned = listOf(
        Achievement.FIRST_ENTRY to "2026-09-19T08:10",
        Achievement.LOGGED_3_DAYS to "2026-09-21T09:00",
        Achievement.LOGGED_7_DAYS to "2026-09-25T09:00",
        Achievement.BASELINE_DONE to "2026-09-26T09:00",
        Achievement.HONEST_LEDGER to "2026-09-27T21:15",
        Achievement.STREAK_10_DAYS to "2026-09-28T09:00",
        Achievement.LOGGED_14_DAYS to "2026-10-02T09:00",
        Achievement.FIRST_DAY_IN_GUIDE to "2026-09-29T09:00",
        Achievement.TAGGED_25 to "2026-09-30T09:00",
        Achievement.FIRST_WEEK_MET to "2026-10-04T09:00",
        Achievement.BREATHING_1 to "2026-10-06T18:30",
        Achievement.STREAK_21_DAYS to "2026-10-09T09:00",
        Achievement.FIVE_DAYS_UNDER to "2026-10-11T09:00",
        Achievement.LOGGED_28_DAYS to "2026-10-16T09:00",
        Achievement.WEEKS_MET_3 to "2026-10-18T09:00",
    )
    for ((achievement, at) in earned) graph.game.unlockAchievement(achievement.key, london(at))
    graph.game.unlockGear(Gear.WOODEN_SWORD.key, london("2026-09-29T09:00"))
    graph.game.unlockGear(Gear.IRON_SWORD.key, london("2026-10-04T09:00"))
    graph.game.unlockGear(Gear.AXE.key, london("2026-10-18T09:00"))
    check(graph.unlocks.equip(Gear.WOODEN_SWORD))
}
