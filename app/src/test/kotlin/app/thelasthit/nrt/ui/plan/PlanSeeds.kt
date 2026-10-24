package app.thelasthit.nrt.ui.plan

import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.PATCH_14
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekGoals
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

// Seeds for the Plan tab's tests and screenshots: the mockups' usual day of 8 pieces of 4 mg gum
// and 3 sprays, 35 mg, and its 12-week plan from Saturday 24 October, played to each mockup's
// moment. Results only come in when they're due, so each day is logged with the clock moved to
// that evening first, as it would be on a phone.

internal val PLAN_START: LocalDate = LocalDate.parse("2026-10-24")

/**
 * Set up as onboarding leaves it, as `setUp` does, but without 2 mg gum: the mockups' plan
 * switches to it in week 3, so building the plan says it's added, and starting the plan adds it.
 */
internal fun TestApp.setUpForPlan() = runBlocking {
    graph.tags.ensureBuiltIns()
    graph.settings.setCountry("GB")
    listOf(GUM_4, QUICKMIST, PATCH_14).forEach { graph.products.addFromCatalogue(it) }
    graph.settings.setDemon("Gary", Creature.DRAGON, "forest")
    graph.settings.completeOnboarding()
}

private fun date(text: String) = LocalDate.parse(text)

/** The clock at [at] on London's wall clock. */
internal fun TestApp.setTime(at: String) {
    clock.now = london(at)
}

/**
 * [pieces] of gum at 09:00 and [sprays] at 13:00 on [date], as one entry each so the seeds stay
 * quick, with the clock moved to that evening first. A day still running logs before [until].
 * With [tags], the gum is "Just woke up" and the sprays "After a meal".
 */
internal fun TestApp.logDay(
    date: LocalDate,
    pieces: Int,
    sprays: Int,
    gum: String = GUM_4,
    until: LocalTime? = null,
    tags: Boolean = false,
) = runBlocking {
    if (until == null) clock.now = london("${date}T21:00")
    if (pieces > 0) {
        val tag = if (tags) setOf("woke_up") else emptySet()
        graph.entries.log(LogRequest(gum, pieces, at = london("${date}T09:00"), tagKeys = tag, allowDuplicate = true))
    }
    if (sprays > 0 && (until == null || until > LocalTime.of(13, 0))) {
        val tag = if (tags) setOf("after_meal") else emptySet()
        graph.entries.log(LogRequest(QUICKMIST, sprays, at = london("${date}T13:00"), tagKeys = tag, allowDuplicate = true))
    }
}

/** [mg] on [date] in gum of [gumMg] a piece, with any odd mg as sprays. */
internal fun TestApp.logMg(date: LocalDate, mg: Int, gumMg: Int, until: LocalTime? = null, tags: Boolean = false) {
    if (mg == 0) return
    logDay(date, pieces = mg / gumMg, sprays = mg % gumMg, gum = if (gumMg == 4) GUM_4 else GUM_2, until = until, tags = tags)
}

/** Each day's mg of a week from [start], in 4 mg gum before 7 November and 2 mg gum from then. */
internal fun TestApp.logWeek(start: String, vararg days: Int, tags: Boolean = false) {
    days.forEachIndexed { offset, mg ->
        val day = date(start).plusDays(offset.toLong())
        logMg(day, mg, gumMg = if (day < date("2026-11-07")) 4 else 2, tags = tags)
    }
}

/** The mockups' last 7 days: 35, 40, nothing, 34, 35, 34 and 36 mg, Friday 16 to Thursday 22 October. */
internal fun TestApp.seedLastSevenDays() {
    val days = listOf(8 to 3, 9 to 4, null, 8 to 2, 8 to 3, 8 to 2, 8 to 4)
    days.forEachIndexed { offset, day ->
        if (day != null) logDay(date("2026-10-16").plusDays(offset.toLong()), day.first, day.second, until = LocalTime.of(14, 0))
    }
    setTime("2026-10-23T15:05")
}

/** A baseline started on Tuesday 20 October, with Tuesday to Thursday logged and 16 mg so far today: day 4 of 7. */
internal fun TestApp.seedBaselineBuilding() {
    setTime("2026-10-20T08:00")
    runBlocking { graph.programmes.startBaseline() }
    logDay(date("2026-10-20"), 8, 3)
    logDay(date("2026-10-21"), 8, 2)
    logDay(date("2026-10-22"), 8, 4)
    setTime("2026-10-23T15:05")
    logDay(date("2026-10-23"), 4, 0, until = LocalTime.of(15, 0))
}

/** The mockups' usual day, typed: 8 pieces of 4 mg gum and 3 sprays. */
internal fun TestApp.keepMockupUsualDay() = runBlocking {
    val gum = checkNotNull(graph.products.get(GUM_4))
    val spray = checkNotNull(graph.products.get(QUICKMIST))
    graph.programmes.keepUsualDay(UsualDay.typed(gum to 8, spray to 3))
}

/** The mockups' plan, started on Friday 23 October to run from the Saturday. */
internal fun TestApp.startMockupPlan(length: ProgrammeLength = ProgrammeLength.WEEKS_12) {
    setTime("2026-10-23T15:05")
    keepMockupUsualDay()
    runBlocking {
        val plan = checkNotNull(graph.programmes.buildPlan(length, PLAN_START))
        graph.programmes.startPlan(plan)
    }
}

private fun TestApp.spend(hits: Int) = runBlocking { repeat(hits) { graph.game.spendHit() } }

/**
 * Week 4 on Wednesday 18 November: week 1 missed then caught up in week 2, week 3 missed, and
 * 60 mg of week 4's 105 so far. One hit is spent, so the demon is at 110 of 120 HP with one ready.
 */
internal fun TestApp.seedWeekFour() {
    startMockupPlan()
    logWeek("2026-10-24", 28, 28, 28, 28, 28, 27, 27)
    logWeek("2026-10-31", 19, 19, 18, 18, 18, 18, 18)
    logWeek("2026-11-07", 20, 20, 20, 20, 20, 20, 20)
    logWeek("2026-11-14", 16, 13)
    // Week 3's result came in that Sunday morning, and the sheet was closed: carry on.
    runBlocking { graph.programmes.chooseAfterMiss(3, MissedWeekChoice.CARRY_ON) }
    logWeek("2026-11-16", 11, 13)
    setTime("2026-11-18T15:05")
    logMg(date("2026-11-18"), 7, gumMg = 2, until = LocalTime.of(15, 0))
    spend(1)
}

/** Day 1 of week 5, Saturday 21 November, with week 4's result still to come. */
internal fun TestApp.seedWeekFivePending() {
    seedWeekFour()
    logWeek("2026-11-19", 14, 14)
    setTime("2026-11-21T15:05")
    logMg(date("2026-11-21"), 4, gumMg = 2, until = LocalTime.of(15, 0))
}

/**
 * Day 2 of week 7, Sunday 6 December, as week 6's result comes in missed: 53 mg against 49.
 * Four hits are spent, so a repeat would take the demon from 80 of 120 HP to 90 of 130.
 */
internal fun TestApp.seedWeekSixMissed() {
    seedWeekFivePending()
    logWeek("2026-11-22", 10, 10, 10, 10, 10, 10)
    logWeek("2026-11-28", 8, 8, 8, 8, 7, 7, 7)
    logWeek("2026-12-05", 5)
    setTime("2026-12-06T15:05")
    logMg(date("2026-12-06"), 2, gumMg = 2, until = LocalTime.of(15, 0))
    spend(3)
}

/** Every taper week met at its guide, with week 10 at 6 mg and week 1's doses tagged. */
private fun TestApp.meetTheTaper() {
    val guides = listOf(27, 19, 19, 15, 11, 7, 5, 4, 2)
    guides.forEachIndexed { index, guide ->
        logWeek(PLAN_START.plusWeeks(index.toLong()).toString(), *IntArray(7) { guide }, tags = index == 0)
    }
    logWeek("2026-12-26", 1, 1, 1, 1, 1, 1, 0)
}

/**
 * Overtime, Tuesday 19 January: weeks 1 to 11 met, 4 mg in week 12, and nothing since. Eleven
 * hits are spent, so the demon is at 10 of 120 HP.
 */
internal fun TestApp.seedOvertime() {
    startMockupPlan()
    meetTheTaper()
    logMg(date("2027-01-12"), 4, gumMg = 2)
    setTime("2027-01-19T15:05")
    spend(11)
}

/** The programme done on Sunday 24 January: overtime met, every hit spent today, and the demon defeated. */
internal fun TestApp.seedDone() {
    seedOvertime()
    setTime("2027-01-24T15:05")
    spend(1)
    runBlocking { graph.game.markDefeated() }
}

/** Week 4 with the first dose goal on at 09:00. */
internal fun TestApp.seedGoals() {
    seedWeekFour()
    runBlocking { graph.programmes.setGoals(WeekGoals(firstDoseNotBefore = LocalTime.of(9, 0))) }
}
