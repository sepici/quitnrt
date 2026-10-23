package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.log.Entry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * ROADMAP P4-4: badges should come at a steady pace across a typical 12-week programme (PLAN 8.1).
 * This plays a plausible run day by day, from the first dose of the baseline to the last hit, and
 * checks when each badge unlocks:
 * - **No droughts:** never more than [MAX_DROUGHT_DAYS] days, two weekly results, from one new
 *   badge to the next. Nothing is logged in the NRT-free fortnight, so only the weekly results
 *   can unlock anything there, and a tighter bar would need a weeks-met tier for every week.
 * - **No bunching:** at most [MAX_A_DAY] on one day, and [MAX_A_WEEK] in any 7 days.
 *
 * PLAN's own thresholds failed it in every run. Day 22 brought four badges: for anyone who logs
 * every day, the streaks of 7, 14 and 30 days land on the same days as logged on 7, 14 and 30
 * days. And a run with every week met went 21 days without a badge, from 9 weeks met to the
 * defeat. So the streaks are 10, 21 and 40 days, and the last weeks-met tier is 10.
 *
 * The user is PLAN 6.2's example 2: 8 pieces of 4 mg gum a day, switching to 2 mg in week 3. They
 * log every dose, tag about two in three, and tag "with coffee or tea" less as the weeks go on.
 * They push their first dose a little later each week, try the breathing timer from week 2, and
 * forget one dose, which they log the next morning. Two users are played, each with a few seeds
 * for the times and tags: one who misses weeks 4 and 9 and carries on, and one who meets every week.
 * Logged on 90 days and 25 breathing sessions don't come in either: a 12-week programme has 77
 * days with NRT, and the user does 21 sessions. They stay for longer programmes and rematches.
 */
class UnlockPaceTest {

    @Test
    fun `badges come at a steady pace across a plausible 12-week run`() {
        val problems = mutableListOf<String>()
        for (profile in Profile.entries) {
            for (seed in 1L..5L) {
                val run = PlausibleRun(profile, seed)
                val days = run.unlockDays()
                val found = paceProblems(days, run.defeatDay)
                val label = "${profile.name.lowercase()} run, seed $seed"
                println("$label:\n${run.describe(days)}\n${found.joinToString("\n") { "  ! $it" }}")
                problems += found.map { "$label: $it" }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `the plausible run is what it says it is`() {
        val bumpy = PlausibleRun(Profile.BUMPY, 1)
        assertEquals(
            listOf(GameWeek.Result.MET, GameWeek.Result.MET, GameWeek.Result.MET, GameWeek.Result.CAUGHT_UP) +
                List(4) { GameWeek.Result.MET } + GameWeek.Result.CAUGHT_UP + List(3) { GameWeek.Result.MET },
            bumpy.weeks.map { it.result },
        )
        assertEquals(
            Achievement.entries.toSet() - setOf(Achievement.LOGGED_90_DAYS, Achievement.BREATHING_25),
            bumpy.unlockDays().keys,
            "every other badge comes in the bumpy run",
        )
        val steady = PlausibleRun(Profile.STEADY, 1)
        assertTrue(steady.weeks.all { it.result == GameWeek.Result.MET })
        for (run in listOf(bumpy, steady)) {
            val end = run.recordAt(run.end)
            val programme = end.programmes.single()
            assertEquals(0, programme.ledger.hp, "the demon falls")
            assertEquals(run.defeatDay, BOUNDARY.logicalDate(programme.ledger.defeatedAt!!, ZONE))
            assertTrue(end.entries.count { it.tagKeys.isNotEmpty() } > 100)
            assertTrue(end.breathing.count { it.counts } in 15..24)
        }
    }

    /** What breaks the pace, if anything, as a line each. */
    private fun paceProblems(days: Map<Achievement, LocalDate>, defeatDay: LocalDate): List<String> = buildList {
        val byDay = days.values.groupingBy { it }.eachCount().toSortedMap()
        fun p(date: LocalDate) = "day ${ChronoUnit.DAYS.between(PLAN_START, date)}"
        if (byDay.lastKey() != defeatDay) add("something unlocks after the defeat")
        for ((a, b) in byDay.keys.zipWithNext()) {
            val gap = ChronoUnit.DAYS.between(a, b)
            if (gap > MAX_DROUGHT_DAYS) add("$gap days without a badge, from ${p(a)} to ${p(b)}")
        }
        for ((day, count) in byDay) if (count > MAX_A_DAY) add("$count badges on ${p(day)}")
        var day = byDay.firstKey()
        while (day <= defeatDay) {
            val week = byDay.filterKeys { it >= day && it < day.plusDays(7) }.values.sum()
            if (week > MAX_A_WEEK) add("$week badges in the 7 days from ${p(day)}")
            day = day.plusDays(1)
        }
    }

    enum class Profile(val pieces: List<List<Int>>) {
        /** Misses weeks 4 and 9, carries on, and pays both back the week after. */
        BUMPY(
            listOf(
                listOf(6, 6, 7, 5, 6, 6, 5), // 41 of 4 mg is 164 of 168 mg: met
                listOf(4, 5, 4, 4, 3, 4, 4), // 112 of 112: met, right at the allowance
                listOf(8, 8, 9, 7, 8, 8, 7), // now 2 mg: 110 of 112, met
                listOf(8, 7, 8, 9, 7, 8, 7), // 108 of 98: missed
                listOf(6, 6, 5, 6, 6, 5, 6), // 80 of 84: met, paying back week 4
                listOf(5, 4, 4, 5, 4, 5, 4), // 62 of 70: met, over 10% under
                listOf(3, 3, 4, 3, 3, 4, 3), // 46 of 56: met, 5 days under the guide
                listOf(3, 2, 3, 3, 3, 3, 3), // 40 of 42: met
                listOf(3, 2, 3, 2, 3, 2, 2), // 34 of 28: missed
                listOf(1, 1, 1, 1, 1, 1, 1), // 14 of 14: met, paying back week 9
            ),
        ),

        /** Meets every week, mostly right at the allowance. */
        STEADY(
            listOf(
                listOf(6, 6, 7, 5, 6, 6, 5),
                listOf(4, 5, 4, 4, 3, 4, 4),
                listOf(8, 8, 9, 7, 8, 8, 7),
                listOf(7, 7, 8, 7, 6, 7, 7),
                listOf(6, 6, 5, 6, 6, 6, 6),
                listOf(5, 5, 5, 5, 5, 5, 5),
                listOf(4, 4, 4, 4, 4, 4, 3),
                listOf(3, 3, 3, 3, 3, 3, 3),
                listOf(2, 2, 2, 2, 2, 2, 2),
                listOf(1, 1, 1, 1, 1, 1, 1),
            ),
        ),
    }

    /** One user's run, with everything that happens in it and when. */
    private class PlausibleRun(profile: Profile, seed: Long) {
        private val random = Random(seed)
        private val entries = mutableListOf<Entry>()
        private val loggedAt = mutableMapOf<String, Instant>()
        private val breathing = mutableListOf<BreathingSession>()

        /** The weeks as they end up, and when each caught-up week was paid back. */
        val weeks: List<GameWeek>
        private val caughtUpAt = mutableMapOf<Int, Instant>()
        private val ledger: HitLedger
        private val baseline = GameBaseline(EXAMPLE_2_USUAL_DAY, BASELINE_START)
        val defeatDay: LocalDate
        val end: Instant

        init {
            listOf(8, 9, 7, 8, 8, 9, 8).forEachIndexed { day, pieces ->
                logDay(BASELINE_START.plusDays(day.toLong()), pieces, gum4(1), weekIndex = 0)
            }
            val run = WeekRun(EXAMPLE_2_PLAN)
            val pieces = profile.pieces + listOf(List(7) { 0 }, List(7) { 0 })
            run.weeks.toList().forEachIndexed { index, week ->
                val unit = week.mix.firstOrNull()?.let { if (it.perUnit == gum4(1).perUnit) gum4(1) else gum2(1) }
                week.days.forEachIndexed { day, date ->
                    if (unit != null) logDay(date, pieces[index][day], unit, weekIndex = week.number)
                }
                val sessions = BREATHING[index]
                repeat(sessions) { n ->
                    val date = week.startDate.plusDays(random.nextLong(7))
                    val start = at(date, LocalTime.of(18, 0).plusMinutes(random.nextLong(240)))
                    val stopped = n == 0 && week.number in setOf(4, 9)
                    breathing += BreathingSession(BreathingSession.Type.BOX, start, Duration.ofMinutes(2), if (stopped) 2 else 7, !stopped)
                }
            }
            // The forgotten dose: week 3's second evening, written down the next morning.
            val forgotten = entries.filter { it.logicalDate == PLAN_START.plusDays(15) }.maxBy { it.occurredAt }
            loggedAt[forgotten.id] = at(PLAN_START.plusDays(16), LocalTime.of(8, 30))

            // Results in order, as the programme brings them in, and the hits spent as they come.
            var spending = HitLedger(120)
            while (run.next != null) {
                val week = run.next!!
                val total = week.totalOf(entries)
                val due = run.result(met = total <= week.weeklyAllowance)
                run.weeks.filter { it.result == GameWeek.Result.CAUGHT_UP && it.number !in caughtUpAt }
                    .forEach { caughtUpAt[it.number] = due }
                spending = spending.withEarned(programmeOf(run.weeks).earnedHits)
                var t = due.plus(Duration.ofHours(10))
                if (spending.canCombo) spending = spending.combo(t).also { t = t.plusSeconds(60) }
                if (week.number !in SAVING_FOR_COMBO) {
                    while (spending.canHit) spending = spending.hit(t).also { t = t.plusSeconds(60) }
                }
            }
            check(run.weeks.size == 12) { "No overtime in a plausible run" }
            weeks = run.weeks
            ledger = spending
            defeatDay = BOUNDARY.logicalDate(checkNotNull(ledger.defeatedAt), ZONE)
            end = startOf(defeatDay.plusDays(1)).minusSeconds(1)
        }

        /** A day's doses: the first a little later each week, the last in the evening, the rest spread between. */
        private fun logDay(date: LocalDate, pieces: Int, unit: MixUnit, weekIndex: Int) {
            if (pieces == 0) return
            val first = 7 * 60 + 10 + 6 * weekIndex + random.nextInt(-20, 21)
            val last = 21 * 60 + 40 + random.nextInt(-30, 31)
            val minutes = if (pieces == 1) {
                listOf(first)
            } else {
                List(pieces) { i -> first + (last - first) * i / (pieces - 1) + if (i in 1 until pieces - 1) random.nextInt(-15, 16) else 0 }.sorted()
            }
            for (minute in minutes) {
                val entry = dose(
                    LocalDateTime.of(date, LocalTime.MIDNIGHT).plusMinutes(minute.toLong()).toString(),
                    unit,
                    tags = tags(weekIndex),
                )
                entries += entry
                // Now and then a dose goes in a quarter of an hour late, which isn't a forgotten one.
                loggedAt[entry.id] = entry.occurredAt.plus(Duration.ofMinutes(if (random.nextInt(10) == 0) 15 else 0))
            }
        }

        private fun tags(weekIndex: Int): Set<String> {
            if (random.nextDouble() >= 0.65) return emptySet()
            val weights = mapOf(
                "coffee_tea" to maxOf(0.40 - 0.03 * weekIndex, 0.15),
                "after_meal" to 0.20,
                "stressed" to 0.15,
                "work_break" to 0.10,
                "bored" to 0.10,
                "before_bed" to 0.05,
            )
            fun pick(): String {
                var r = random.nextDouble() * weights.values.sum()
                for ((key, weight) in weights) {
                    r -= weight
                    if (r < 0) return key
                }
                return weights.keys.last()
            }
            return if (random.nextDouble() < 0.15) setOf(pick(), pick()) else setOf(pick())
        }

        private fun at(date: LocalDate, time: LocalTime): Instant = date.atTime(time).atZone(ZONE).toInstant()

        /** The record as it stood at [t]: only what had been written, and only the results in. */
        fun recordAt(t: Instant): GameRecord {
            val today = BOUNDARY.logicalDate(t, ZONE)
            val written = entries.filter { loggedAt.getValue(it.id) <= t }
            val weeksNow = if (today < PLAN_START) emptyList() else weeks.map { week ->
                when {
                    week.resultAt == null || week.resultAt > t -> week.copy(result = GameWeek.Result.PENDING, resultAt = null)
                    week.result == GameWeek.Result.CAUGHT_UP && caughtUpAt.getValue(week.number) > t ->
                        week.copy(result = GameWeek.Result.MISSED)
                    else -> week
                }
            }
            val programme = GameProgramme(
                baseline,
                weeksNow,
                HitLedger(HitLedger.HP_PER_HIT * weeksNow.count { it.earnsHit }, ledger.rows.filter { it.at <= t && weeksNow.isNotEmpty() }),
            )
            return GameRecord(
                today = today,
                entries = written,
                loggedAt = written.associate { it.id to loggedAt.getValue(it.id) },
                programmes = listOf(programme),
                breathing = breathing.filter { it.startedAt.plus(it.duration) <= t },
            )
        }

        /** The logical day each badge unlocked, checked at the end of every day. */
        fun unlockDays(): Map<Achievement, LocalDate> {
            val days = linkedMapOf<Achievement, LocalDate>()
            var date = BASELINE_START
            while (date <= defeatDay) {
                val record = recordAt(startOf(date.plusDays(1)).minusSeconds(1))
                for (achievement in AchievementRules.unlocked(record)) days.putIfAbsent(achievement, date)
                date = date.plusDays(1)
            }
            return days
        }

        /** The timeline, a line a day, counting days from the plan's start. */
        fun describe(days: Map<Achievement, LocalDate>): String = days.entries.groupBy({ it.value }, { it.key.key })
            .toSortedMap()
            .map { (day, keys) -> "  day ${ChronoUnit.DAYS.between(PLAN_START, day)}: ${keys.joinToString()}" }
            .joinToString("\n")
    }

    companion object {
        const val MAX_DROUGHT_DAYS = 14
        const val MAX_A_DAY = 3
        const val MAX_A_WEEK = 6

        /** Breathing sessions in each programme week: more in the hard weeks and the NRT-free ones. */
        val BREATHING = listOf(0, 1, 2, 3, 2, 1, 1, 1, 3, 2, 4, 3)

        /** The player saves up through these weeks for a 5× attack. */
        val SAVING_FOR_COMBO = setOf(5, 6, 7)
    }
}
