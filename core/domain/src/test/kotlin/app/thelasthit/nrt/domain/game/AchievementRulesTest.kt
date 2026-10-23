package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.log.Entry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class AchievementRulesTest {

    // ---------------------------------------------------------------- building records

    private fun record(
        today: LocalDate,
        entries: List<Entry> = emptyList(),
        programmes: List<GameProgramme> = emptyList(),
        breathing: List<BreathingSession> = emptyList(),
        loggedAt: Map<String, Instant> = emptyMap(),
    ) = GameRecord(today, entries, loggedAt, programmes, breathing)

    private fun days(from: LocalDate, count: Int, step: Long = 1): List<LocalDate> = List(count) { from.plusDays(it * step) }

    /** A dose at 10:00 on each of [days]. */
    private fun loggedOn(days: List<LocalDate>): List<Entry> = days.map { dose("${it}T10:00") }

    /** [pieces] of [unit] in one entry at [time] on [date]. */
    private fun onDay(date: LocalDate, pieces: Int, unit: MixUnit = gum4(1), time: String = "09:00", tags: Set<String> = emptySet()) =
        dose("${date}T$time", unit, pieces, tags)

    /** One entry per day of the week starting [start], with each day's pieces. */
    private fun week(start: LocalDate, vararg pieces: Int, unit: MixUnit = gum4(1)): List<Entry> =
        pieces.toList().mapIndexedNotNull { day, n -> if (n == 0) null else onDay(start.plusDays(day.toLong()), n, unit) }

    /** [count] doses, ten minutes apart from 08:00 on the baseline's first day. */
    private fun doses(count: Int, tags: Set<String> = emptySet()): List<Entry> = List(count) {
        dose(LocalDateTime.of(BASELINE_START, java.time.LocalTime.of(8, 0)).plusMinutes(it * 10L).toString(), tags = tags)
    }

    /** Weeks 1 and 2 of example 2 under their allowances: 156 of 168 mg, and 108 of 112. */
    private val firstWeeksUnder: List<Entry> by lazy {
        week(PLAN_START, 6, 6, 6, 6, 6, 6, 3) + week(PLAN_START.plusWeeks(1), 4, 4, 4, 4, 4, 4, 3)
    }

    /** Example 2's plan with these results for its first weeks. */
    private fun results(vararg met: Boolean): List<GameWeek> = WeekRun(EXAMPLE_2_PLAN).apply { met.forEach { result(it) } }.weeks

    private fun planRecord(weeks: List<GameWeek>, entries: List<Entry> = emptyList(), today: LocalDate? = null): GameRecord {
        val due = weeks.lastOrNull { it.hasResult }?.let { it.endDate.plusDays(2) } ?: PLAN_START
        return record(today ?: due, entries, listOf(programmeOf(weeks)))
    }

    private fun session(day: Long, completed: Boolean = true, rounds: Int = 4) = BreathingSession(
        BreathingSession.Type.BOX,
        startOf(PLAN_START.plusDays(day)).plus(Duration.ofHours(16)),
        Duration.ofMinutes(1),
        rounds,
        completed,
    )

    private fun sessions(completed: Int) = List(completed) { session(it.toLong()) }

    /** Every week of example 2 met, and [spent] of the 12 hits spent. */
    private fun fight(spent: Int): GameRecord {
        val weeks = results(*BooleanArray(12) { true })
        var ledger = HitLedger(120).withEarned(programmeOf(weeks).earnedHits)
        repeat(spent) { ledger = ledger.hit(resultDue(weeks.last()).plusSeconds(it * 60L)) }
        return record(weeks.last().endDate.plusDays(2), programmes = listOf(programmeOf(weeks, ledger = ledger)))
    }

    /**
     * A measured baseline whose days each have doses at [times], and week 1 with doses at
     * [weekTimes] on [weekDays] days. Week 1's result is in, unless [resultIn] is false.
     */
    private fun habits(
        times: List<String>,
        weekTimes: List<String>,
        weekDays: Int = 5,
        tags: (index: Int) -> Set<String> = { emptySet() },
        weekTags: (index: Int) -> Set<String> = { emptySet() },
        resultIn: Boolean = true,
    ): GameRecord {
        var i = 0
        var j = 0
        val baseline = days(BASELINE_START, 7).flatMap { day -> times.map { dose("${day}T$it", tags = tags(i++)) } }
        val inWeek = days(PLAN_START, weekDays).flatMap { day -> weekTimes.map { dose("${day}T$it", tags = weekTags(j++)) } }
        return if (resultIn) {
            record(PLAN_START.plusDays(8), baseline + inWeek, listOf(programmeOf(results(true))))
        } else {
            record(PLAN_START.plusDays(7), baseline + inWeek, listOf(programmeOf(results())))
        }
    }

    // ---------------------------------------------------------------- each rule, both ways

    /** For each achievement: a record it holds for, and a close one it doesn't. */
    private val cases: Map<Achievement, Pair<GameRecord, GameRecord>> by lazy {
        val logged = { n: Int -> record(BASELINE_START.plusDays(200), loggedOn(days(BASELINE_START, n, step = 2))) }
        val streak = { n: Int -> record(BASELINE_START.plusDays(200), loggedOn(days(BASELINE_START, n))) }
        val brokenStreak = { n: Int ->
            record(BASELINE_START.plusDays(200), loggedOn(listOf(BASELINE_START) + days(BASELINE_START.plusDays(2), n - 1)))
        }
        val baselineOn = { dayIndexes: List<Int> ->
            record(
                BASELINE_START.plusDays(7),
                loggedOn(dayIndexes.map { BASELINE_START.plusDays(it.toLong()) }),
                listOf(programmeOf(emptyList())),
            )
        }
        val late = dose("2026-11-02T20:00")
        val backdated = { by: Duration -> record(BASELINE_START.plusDays(1), listOf(late), loggedAt = mapOf(late.id to late.occurredAt.plus(by))) }
        val tagged = { n: Int -> record(BASELINE_START.plusDays(1), doses(n, setOf("stressed")) + doses(5)) }
        val all = { n: Int -> BooleanArray(n) { true } }
        val week1 = results()
        val finalTaper = PLAN_START.plusWeeks(9)

        mapOf(
            Achievement.FIRST_ENTRY to (record(BASELINE_START, listOf(late)) to record(BASELINE_START)),
            Achievement.LOGGED_3_DAYS to (
                logged(3) to record(
                    BASELINE_START.plusDays(5),
                    // The 02:00 dose counts towards the evening before, so these are two days.
                    listOf(dose("2026-11-02T10:00"), dose("2026-11-03T02:00"), dose("2026-11-03T10:00")),
                )
                ),
            Achievement.LOGGED_7_DAYS to (logged(7) to logged(6)),
            Achievement.LOGGED_14_DAYS to (logged(14) to logged(13)),
            Achievement.LOGGED_28_DAYS to (logged(28) to logged(27)),
            Achievement.LOGGED_60_DAYS to (logged(60) to logged(59)),
            Achievement.LOGGED_90_DAYS to (logged(90) to logged(89)),
            Achievement.STREAK_10_DAYS to (streak(10) to brokenStreak(10)),
            Achievement.STREAK_21_DAYS to (streak(21) to brokenStreak(21)),
            Achievement.STREAK_40_DAYS to (streak(40) to brokenStreak(40)),
            Achievement.BASELINE_DONE to (baselineOn(listOf(0, 1, 2, 4, 6)) to baselineOn(listOf(0, 1, 2, 4))),
            Achievement.HONEST_LEDGER to (backdated(Duration.ofHours(1)) to backdated(Duration.ofMinutes(59))),
            Achievement.TAGGED_25 to (tagged(25) to tagged(24)),
            Achievement.TAGGED_100 to (tagged(100) to tagged(99)),

            // Day 1 is judged on day 3, once its 24 hours for late logs are over.
            Achievement.FIRST_DAY_IN_GUIDE to (
                planRecord(week1, listOf(onDay(PLAN_START, 6)), PLAN_START.plusDays(2)) to
                    planRecord(week1, listOf(onDay(PLAN_START, 7)), PLAN_START.plusDays(2))
                ),
            Achievement.FIRST_WEEK_MET to (planRecord(results(true)) to planRecord(results(false))),
            // A caught-up week counts as squared.
            Achievement.WEEKS_MET_3 to (planRecord(results(true, false, true)) to planRecord(results(true, true, false))),
            Achievement.WEEKS_MET_6 to (planRecord(results(*all(6))) to planRecord(results(*all(5), false))),
            Achievement.WEEKS_MET_10 to (planRecord(results(*all(10))) to planRecord(results(*all(9), false))),
            Achievement.BACK_IN_STEP to (planRecord(results(false, true)) to planRecord(results(true, false))),
            // Week 2's guide is 16 mg, half the usual day's 32.
            Achievement.HALFWAY to (planRecord(results(true, true)) to planRecord(results(true, false))),
            // Week 3 switches to 2 mg gum. Week 2 has fewer 4 mg pieces, which isn't a lower strength.
            Achievement.DOWN_A_STRENGTH to (planRecord(results(true, true, true)) to planRecord(results(true, true, false))),
            Achievement.FINAL_TAPER_WEEK to (planRecord(week1, today = finalTaper) to planRecord(week1, today = finalTaper.minusDays(1))),

            // Week 1's allowance is 168 mg, so 10% under is 151.2 mg or less.
            Achievement.WEEK_10_PERCENT_UNDER to (
                planRecord(results(true), week(PLAN_START, 6, 5, 5, 5, 5, 5, 6)) to
                    planRecord(results(true), week(PLAN_START, 6, 6, 5, 5, 5, 5, 6))
                ),
            // Week 1's guide is 6 pieces: 5 is under it, and 6 is at it.
            Achievement.FIVE_DAYS_UNDER to (
                planRecord(week1, week(PLAN_START, 5, 5, 5, 5, 5), PLAN_START.plusDays(6)) to
                    planRecord(week1, week(PLAN_START, 5, 5, 5, 5, 6), PLAN_START.plusDays(6))
                ),
            // Allowances of 168, 112, 112 and 98 mg: 156, 108 and 110 are under, and 112 isn't.
            Achievement.THREE_WEEKS_UNDER to (
                planRecord(results(true, true, true), firstWeeksUnder + week(PLAN_START.plusWeeks(2), 8, 8, 8, 8, 8, 8, 7, unit = gum2(1))) to
                    planRecord(
                        results(true, true, true, true),
                        firstWeeksUnder + week(PLAN_START.plusWeeks(2), 8, 8, 8, 8, 8, 8, 8, unit = gum2(1)) +
                            week(PLAN_START.plusWeeks(3), 7, 7, 7, 7, 7, 7, 6, unit = gum2(1)),
                    )
                ),

            Achievement.LATER_FIRST_DOSE to (
                habits(listOf("07:00", "12:00"), listOf("07:30", "12:00")) to habits(listOf("07:00", "12:00"), listOf("07:29", "12:00"))
                ),
            Achievement.LONGER_GAPS to (
                habits(listOf("08:00", "10:00"), listOf("08:00", "10:30")) to habits(listOf("08:00", "10:00"), listOf("08:00", "10:29"))
                ),
            // 8 of 14 is exactly two thirds of 12 of 14, so down by a third; 9 of 14 isn't.
            Achievement.TOP_TRIGGER_DOWN to (
                trigger(weekCoffee = 8) to trigger(weekCoffee = 9)
                ),
            Achievement.BREATHING_1 to (record(PLAN_START.plusDays(30), breathing = sessions(1)) to breathingShort(1)),
            Achievement.BREATHING_10 to (record(PLAN_START.plusDays(30), breathing = sessions(10)) to breathingShort(10)),
            Achievement.BREATHING_25 to (record(PLAN_START.plusDays(30), breathing = sessions(25)) to breathingShort(25)),

            Achievement.DEMON_DEFEATED to (fight(spent = 12) to fight(spent = 11)),
        )
    }

    /**
     * Coffee is on 12 of the baseline's 14 doses, every one of them tagged. In week 1 it's on
     * [weekCoffee] of the 14 doses, the next [weekOther] are tagged "bored", and the rest have no tag.
     */
    private fun trigger(weekCoffee: Int, weekOther: Int = 14 - weekCoffee): GameRecord = habits(
        times = listOf("08:00", "12:00"),
        weekTimes = listOf("08:00", "12:00"),
        weekDays = 7,
        tags = { if (it < 12) setOf("coffee_tea") else setOf("bored") },
        weekTags = {
            when {
                it < weekCoffee -> setOf("coffee_tea")
                it < weekCoffee + weekOther -> setOf("bored")
                else -> emptySet()
            }
        },
    )

    /** One completed session short of [count], plus one stopped early and one with no whole round. */
    private fun breathingShort(count: Int) = record(
        PLAN_START.plusDays(30),
        breathing = sessions(count - 1) + session(40, completed = false) + session(41, rounds = 0),
    )

    @Test
    fun `every achievement has a case it unlocks for and one it doesn't`() {
        assertEquals(Achievement.entries.toSet(), cases.keys)
        for ((achievement, case) in cases) {
            val (unlocks, doesNot) = case
            assertTrue(AchievementRules.holds(achievement, unlocks), "${achievement.key} unlocks")
            assertFalse(AchievementRules.holds(achievement, doesNot), "${achievement.key} doesn't unlock")
            assertTrue(achievement in AchievementRules.unlocked(unlocks), "${achievement.key} is in the unlocked set")
        }
    }

    // ---------------------------------------------------------------- the finer points

    @Test
    fun `unlocked lists achievements in PLAN's order`() {
        val record = record(BASELINE_START.plusDays(40), loggedOn(days(BASELINE_START, 30)) + doses(30, setOf("bored")))
        val unlocked = AchievementRules.unlocked(record).toList()
        assertEquals(Achievement.entries.filter { it in unlocked }, unlocked)
        assertTrue(unlocked.containsAll(listOf(Achievement.FIRST_ENTRY, Achievement.STREAK_21_DAYS, Achievement.TAGGED_25)))
    }

    @Test
    fun `a day counts against its guide once its late logs are in`() {
        val sixPieces = listOf(onDay(PLAN_START, 6))
        assertFalse(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, planRecord(results(), sixPieces, PLAN_START)))
        assertFalse(
            AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, planRecord(results(), sixPieces, PLAN_START.plusDays(1))),
            "the morning after, a dose from the night before can still be logged",
        )
        // A 7th piece at 22:00, logged the next morning, takes day 1 over its guide of 6.
        val lateLog = sixPieces + onDay(PLAN_START, 1, time = "22:00")
        assertFalse(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, planRecord(results(), lateLog, PLAN_START.plusDays(2))))
        assertTrue(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, planRecord(results(), sixPieces, PLAN_START.plusDays(2))))

        val fiveUnder = week(PLAN_START, 5, 5, 5, 5, 5)
        assertFalse(
            AchievementRules.holds(Achievement.FIVE_DAYS_UNDER, planRecord(results(), fiveUnder, PLAN_START.plusDays(5))),
            "the fifth day's late logs can still come",
        )
        assertTrue(AchievementRules.holds(Achievement.FIVE_DAYS_UNDER, planRecord(results(), fiveUnder, PLAN_START.plusDays(6))))
    }

    @Test
    fun `a day with nothing logged is never within or under the guide`() {
        val nothing = planRecord(results(), emptyList(), PLAN_START.plusDays(7))
        assertFalse(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, nothing))
        assertFalse(AchievementRules.holds(Achievement.FIVE_DAYS_UNDER, nothing))
        assertEquals(NewUnlocks.NONE, NewUnlocks.since(emptySet(), emptySet(), nothing), "five days away bring nothing")

        // Four days under and three with nothing logged: not five days under.
        val fourDays = planRecord(results(), week(PLAN_START, 5, 0, 5, 0, 5, 0, 5), PLAN_START.plusDays(8))
        assertFalse(AchievementRules.holds(Achievement.FIVE_DAYS_UNDER, fourDays))
        assertTrue(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, fourDays))
    }

    @Test
    fun `a baseline is done when its 7 days are over with 5 logged, and never when it was typed in`() {
        val logged = loggedOn(days(BASELINE_START, 7))
        val running = record(BASELINE_START.plusDays(6), logged, listOf(programmeOf(emptyList())))
        assertFalse(AchievementRules.holds(Achievement.BASELINE_DONE, running))
        val typed = record(BASELINE_START.plusDays(7), logged, listOf(programmeOf(emptyList(), measuredFrom = null)))
        assertFalse(AchievementRules.holds(Achievement.BASELINE_DONE, typed))
    }

    @Test
    fun `an entry with no time it was written is never taken as backdated`() {
        assertFalse(AchievementRules.holds(Achievement.HONEST_LEDGER, record(BASELINE_START.plusDays(1), doses(3))))
    }

    @Test
    fun `beating the plan needs a result in and a week with some NRT`() {
        val under = week(PLAN_START, 5, 5, 5, 5, 5, 5, 5)
        assertFalse(
            AchievementRules.holds(Achievement.WEEK_10_PERCENT_UNDER, planRecord(results(), under, PLAN_START.plusDays(8))),
            "a week under, with its result not in",
        )
        // Every taper day right at its guide, and nothing at all in the NRT-free weeks.
        val weeks = results(*BooleanArray(12) { true })
        val atTheGuide = weeks.flatMap { week ->
            week.mix.flatMap { unit -> week.days.map { onDay(it, unit.unitsPerDay, unit.copy(unitsPerDay = 1)) } }
        }
        val record = planRecord(weeks, atTheGuide, PLAN_START.plusWeeks(12).plusDays(1))
        for (achievement in listOf(Achievement.FIVE_DAYS_UNDER, Achievement.THREE_WEEKS_UNDER, Achievement.WEEK_10_PERCENT_UNDER)) {
            assertFalse(AchievementRules.holds(achievement, record), "${achievement.key} can't come from weeks at zero")
        }
        assertTrue(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, record))
    }

    @Test
    fun `a week with a day not logged is met, but isn't under`() {
        // Week 1's allowance is 168 mg. Six days at the guide of 6 pieces is 144 mg, over 10% under on paper.
        val dayLeftOut = planRecord(results(true), week(PLAN_START, 6, 6, 6, 0, 6, 6, 6))
        assertFalse(AchievementRules.holds(Achievement.WEEK_10_PERCENT_UNDER, dayLeftOut))
        assertFalse(AchievementRules.holds(Achievement.WEEK_10_PERCENT_UNDER, planRecord(results(true))), "nothing logged")
        assertTrue(AchievementRules.holds(Achievement.WEEK_10_PERCENT_UNDER, planRecord(results(true), week(PLAN_START, 6, 6, 6, 1, 6, 6, 6))))
        // Three weeks under in a row, but the middle one with a day not logged.
        val third = week(PLAN_START.plusWeeks(2), 8, 8, 8, 8, 8, 8, 7, unit = gum2(1))
        val gap = week(PLAN_START, 6, 6, 6, 6, 6, 6, 3) + week(PLAN_START.plusWeeks(1), 4, 4, 4, 0, 4, 4, 4) + third
        assertFalse(AchievementRules.holds(Achievement.THREE_WEEKS_UNDER, planRecord(results(true, true, true), gap)))
        val everyDay = week(PLAN_START, 6, 6, 6, 6, 6, 6, 3) + week(PLAN_START.plusWeeks(1), 4, 4, 4, 1, 4, 4, 4) + third
        assertTrue(AchievementRules.holds(Achievement.THREE_WEEKS_UNDER, planRecord(results(true, true, true), everyDay)))
    }

    @Test
    fun `habits leave patches out and need a week with its result in and 5 days of doses`() {
        val fourDays = habits(listOf("07:00", "12:00"), listOf("09:00", "12:00"), weekDays = 4)
        assertFalse(AchievementRules.holds(Achievement.LATER_FIRST_DOSE, fourDays))
        val fiveDays = habits(listOf("07:00", "12:00"), listOf("09:00", "12:00"))
        assertTrue(AchievementRules.holds(Achievement.LATER_FIRST_DOSE, fiveDays))
        val waiting = habits(listOf("07:00", "12:00"), listOf("09:00", "12:00"), resultIn = false)
        assertFalse(AchievementRules.holds(Achievement.LATER_FIRST_DOSE, waiting), "week 1 is over, but its late logs can still come")
        val earlyPatches = days(PLAN_START, 7).map { dose("${it}T06:00", patch(21)) }
        assertTrue(
            AchievementRules.holds(Achievement.LATER_FIRST_DOSE, fiveDays.copy(entries = fiveDays.entries + earlyPatches)),
            "a patch at 06:00 isn't a first dose",
        )
    }

    @Test
    fun `tagging less never reads as the top trigger falling`() {
        assertFalse(AchievementRules.holds(Achievement.TOP_TRIGGER_DOWN, trigger(weekCoffee = 0, weekOther = 0)), "nothing tagged")
        // 6 of 7 tagged doses are coffee, as 12 of 14 were: the same share, tagged half as often.
        assertFalse(AchievementRules.holds(Achievement.TOP_TRIGGER_DOWN, trigger(weekCoffee = 6, weekOther = 1)), "tagged half as often")
        assertFalse(AchievementRules.holds(Achievement.TOP_TRIGGER_DOWN, trigger(weekCoffee = 0, weekOther = 4)), "too few tagged to say")
        assertTrue(AchievementRules.holds(Achievement.TOP_TRIGGER_DOWN, trigger(weekCoffee = 0, weekOther = 5)))
        // 4 of 7 tagged is down by a third against 12 of 14, with the other 7 untagged.
        assertTrue(AchievementRules.holds(Achievement.TOP_TRIGGER_DOWN, trigger(weekCoffee = 4, weekOther = 3)))
    }

    @Test
    fun `only weeks with a hit of their own count as weeks met, so overtime doesn't`() {
        // 8 weeks: weeks 1 to 7 met, week 8 missed, overtime weeks 9 and 10 missed, and week 11 met.
        val run = WeekRun(example2Plan(8))
        repeat(7) { run.result(met = true) }
        repeat(3) { run.result(met = false) }
        run.result(met = true)
        val overtime = planRecord(run.weeks)
        assertEquals(11, run.weeks.size)
        assertEquals(3, run.weeks.count { it.kind == GameWeek.Kind.OVERTIME })
        assertTrue(AchievementRules.holds(Achievement.WEEKS_MET_6, overtime))
        assertFalse(AchievementRules.holds(Achievement.WEEKS_MET_10, overtime), "8 weeks squared, as when every week is met")
    }

    @Test
    fun `an abandoned programme keeps its HP, and its days stop where it ended`() {
        // Weeks 1 to 3 met and their hits spent, then abandoned on day 4 of week 4.
        val weeks = results(true, true, true)
        var ledger = HitLedger(120).withEarned(programmeOf(weeks).earnedHits)
        repeat(3) { ledger = ledger.hit(resultDue(weeks[2]).plusSeconds(it * 60L)) }
        val endedOn = PLAN_START.plusWeeks(3).plusDays(3)
        val abandoned = GameProgramme(GameBaseline(EXAMPLE_2_USUAL_DAY, BASELINE_START), weeks, ledger, endedOn)
        // Later, one piece of 2 mg gum a day: under every guide the plan had left, up to week 10's.
        val today = PLAN_START.plusWeeks(10)
        val later = days(endedOn, 46).map { onDay(it, 1, gum2(1)) }
        val record = record(today, later, listOf(abandoned))
        val running = record.copy(programmes = listOf(abandoned.copy(endedOn = null)))

        assertEquals(120, abandoned.maxHp)
        assertEquals(90, abandoned.ledger.hp)
        assertFalse(AchievementRules.holds(Achievement.DEMON_DEFEATED, record))
        for (achievement in listOf(Achievement.FINAL_TAPER_WEEK, Achievement.FIVE_DAYS_UNDER, Achievement.FIRST_DAY_IN_GUIDE)) {
            assertTrue(AchievementRules.holds(achievement, running), "${achievement.key} holds while the programme runs")
            assertFalse(AchievementRules.holds(achievement, record), "${achievement.key} doesn't come after the end")
        }
        assertFalse(Gear.FIRE_STAFF in Gear.unlockedWith(AchievementRules.unlocked(record), record))

        // Days before the end still count.
        val before = record.copy(entries = later + week(PLAN_START.plusWeeks(3), 5, 5, 5, unit = gum2(1)))
        assertTrue(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, before))
        assertFalse(AchievementRules.holds(Achievement.FIVE_DAYS_UNDER, before), "3 days under before the end")

        assertThrows<IllegalArgumentException> { GameProgramme(abandoned.baseline, weeks, ledger, PLAN_START.plusWeeks(2)) }
    }

    @Test
    fun `a rematch over an abandoned programme's dates is held to its own guides`() {
        // The first programme is abandoned in week 2. A rematch from 4 pieces of 2 mg a day starts in week 3.
        val first = programmeOf(results(true)).copy(endedOn = PLAN_START.plusDays(10))
        val rematchStart = PLAN_START.plusWeeks(2)
        val rematch = GameProgramme(
            GameBaseline(listOf(gum2(4))),
            WeekRun(listOf(listOf(gum2(3)), listOf(gum2(2)), listOf(gum2(1)), emptyList(), emptyList()), rematchStart).weeks,
        )
        // 4 pieces of 2 mg a day: over the rematch's guide of 3, though under the 8 of 2 mg the first plan had.
        val logs = days(rematchStart, 7).map { onDay(it, 4, gum2(1)) }
        val record = record(rematchStart.plusDays(8), logs, listOf(first, rematch))
        assertFalse(AchievementRules.holds(Achievement.FIVE_DAYS_UNDER, record))
        assertFalse(AchievementRules.holds(Achievement.FIRST_DAY_IN_GUIDE, record))
        val notEnded = record.copy(programmes = listOf(first.copy(endedOn = null), rematch))
        assertTrue(AchievementRules.holds(Achievement.FIVE_DAYS_UNDER, notEnded))
    }

    @Test
    fun `a baseline abandoned on the way isn't done`() {
        val logged = loggedOn(days(BASELINE_START, 7))
        val abandoned = programmeOf(emptyList()).copy(endedOn = BASELINE_START.plusDays(3))
        assertFalse(AchievementRules.holds(Achievement.BASELINE_DONE, record(BASELINE_START.plusDays(7), logged, listOf(abandoned))))
        assertTrue(
            AchievementRules.holds(Achievement.BASELINE_DONE, record(BASELINE_START.plusDays(7), logged, listOf(abandoned.copy(endedOn = null)))),
        )
    }

    @Test
    fun `a lower strength is a weaker unit of the same kind`() {
        assertTrue(AchievementRules.isDownAStrength(listOf(gum4(8)), listOf(gum2(8))))
        assertTrue(AchievementRules.isDownAStrength(listOf(patch(21), gum2(4)), listOf(patch(14), gum2(4))))
        assertFalse(AchievementRules.isDownAStrength(listOf(gum4(8)), listOf(gum4(4))))
        assertFalse(AchievementRules.isDownAStrength(listOf(gum4(4), gum2(4)), listOf(gum4(2), gum2(4))), "still on 4 mg gum")
        assertFalse(AchievementRules.isDownAStrength(listOf(patch(21), gum4(2)), listOf(gum4(2))), "the patch came off")
    }

    @Test
    fun `counts carry over from one programme to the next`() {
        val first = programmeOf(results(true, true))
        val rematch = GameProgramme(GameBaseline(listOf(gum2(6))), WeekRun(EXAMPLE_2_PLAN, PLAN_START.plusWeeks(20)).apply { result(true) }.weeks)
        val record = record(PLAN_START.plusWeeks(22), programmes = listOf(first, rematch))
        assertTrue(AchievementRules.holds(Achievement.WEEKS_MET_3, record))
    }
}
