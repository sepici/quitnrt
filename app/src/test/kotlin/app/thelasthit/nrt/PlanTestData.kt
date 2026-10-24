package app.thelasthit.nrt

import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.UsualDay
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Monday 28 September: with it as the start, today is day 5 of week 4, as in the Today mockup. */
val PLAN_START: LocalDate = LocalDate.parse("2026-09-28")

/**
 * A plan that's running, from PLAN's first example: 12 pieces of 2 mg gum a day, over 12 weeks
 * from [start]. It's built and started the day before [start], with the clock moved back for it,
 * then the clock comes back to the tests' Friday and the results that are due come in. Then
 * [spend] of the hits they earned are spent on Gary.
 *
 * From [PLAN_START], today is in week 4, at a 14 mg guide and 98 mg a week, and Monday to
 * Thursday of [seedWeek] are all at or under it.
 */
fun TestApp.seedPlan(start: LocalDate = PLAN_START, spend: Int = 0) = runBlocking {
    val gum = checkNotNull(graph.products.get(GUM_2))
    clock.now = london("${start.minusDays(1)}T12:00")
    graph.programmes.keepUsualDay(UsualDay.typed(gum to 12))
    val plan = checkNotNull(graph.programmes.buildPlan(ProgrammeLength.WEEKS_12, start))
    graph.programmes.startPlan(plan)
    clock.now = TestApp.NOW
    graph.programmes.settle()
    repeat(spend) { graph.game.spendHit() }
}

/**
 * The plan in the mockups' state: two months of [seedHistory] and this week's [seedWeek], with a
 * rough second week (5 to 11 October) that goes over its 126 mg, so week 3 being met pays it
 * back. Weeks 1 and 3 and the catch-up earn 3 hits, and 2 are spent: Gary is at 100 of 120 HP
 * with 1 hit ready.
 */
fun TestApp.seedMockupPlan() {
    seedHistory()
    seedWeek()
    runBlocking {
        for (day in 5..9) {
            graph.entries.log(LogRequest(GUM_4, quantity = 2, at = london("2026-10-%02dT20:30".format(day)), tagKeys = setOf("stressed")))
        }
    }
    seedPlan(spend = 2)
}
