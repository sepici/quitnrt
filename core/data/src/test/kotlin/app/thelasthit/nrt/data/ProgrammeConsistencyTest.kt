package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.db.HitLedgerEntity
import app.thelasthit.nrt.data.repo.DemonState
import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.data.repo.SpendResult
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.HP_PER_HIT
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.domain.programme.WeekTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * Many seeded random runs of a programme against the real repositories, to show the game and
 * the programme stay in step whatever happens: logs, edits, backdated doses, deletes, backups
 * merged and replaced, repeated weeks, overtime and rematches. After every step:
 *
 * - the demon's HP is its max less 10 for each hit spent, from the ledger as stored
 * - the banked hits are never negative
 * - the hits on offer match the demon's HP blocks, and the ledger holds the hits the results earned
 * - a result never changes once it's in, apart from a missed week being caught up
 * - allowances never go up from one week to the next, and a week's target is fixed once it starts
 *
 * A failure names its seed and the steps that led to it, so the run can be played again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgrammeConsistencyTest {

    @Test
    fun randomRunsKeepTheGameAndTheProgrammeInStep() = runTest(timeout = 10.minutes) {
        val tally = Tally()
        for (seed in 1..RUNS) Run(seed, tally).play()
        tally.assertEverythingHappened()
    }

    /** How often each kind of step came up across the runs, so none can quietly stop happening. */
    private class Tally {
        val counts = mutableMapOf<String, Int>()

        fun count(what: String) {
            counts[what] = (counts[what] ?: 0) + 1
        }

        fun assertEverythingHappened() {
            val needed = listOf(
                "log", "backdate into a week whose result is in", "backdate into a week still to come in",
                "edit", "delete", "undo a delete", "hit", "5x attack", "spend refused",
                "missed", "caught up", "carry on", "repeat", "carry on, then repeat", "overtime",
                "merge onto itself", "replace onto itself", "merge onto a new phone", "replace onto a new phone",
                "merge onto a new phone with a baseline", "week edited", "day start changed", "defeated", "rematch", "abandoned",
            )
            val missing = needed.filter { (counts[it] ?: 0) == 0 }
            assertTrue("Never happened: $missing. Counts: $counts", missing.isEmpty())
        }
    }

    /** What a phone holds that a backup carries: compared before and after a round trip. */
    private data class Snapshot(
        val current: ProgrammeRecord?,
        val ledger: List<HitLedgerEntity>,
        val demon: DemonState?,
        val entries: Set<Entry>,
        val programmes: Int,
    )

    /** What a week's doses aim for. */
    private enum class Aim { MEET, MISS }

    /**
     * A week's target by its step and units of each product, since the products' other details,
     * such as their place on the quick log, can change while the target stays the same.
     */
    private data class TargetSignature(val step: Int?, val units: Map<String, Int>)

    private data class WeekSignature(
        val number: Int,
        val startDate: LocalDate,
        val kind: WeekKind,
        val target: TargetSignature,
        val result: WeekResult,
        val resultAt: Instant?,
        val resultTotal: Dose?,
    )

    private companion object Signatures {
        fun WeekTarget.signature() = TargetSignature(step, mix.items.associate { it.product.id to it.units })

        fun ProgrammeWeek.signature() = WeekSignature(number, startDate, kind, target.signature(), result, resultAt, resultTotal)
    }

    private class Run(private val seed: Int, private val tally: Tally) {
        private val random = Random(seed)
        private var data = TestData()

        /** The last steps taken, for the failure message. */
        private val trail = ArrayDeque<String>()

        /** Each result as it first came in, by programme and week number. */
        private val results = mutableMapOf<Pair<String, Int>, WeekSignature>()

        /** Each week's target once it has started, by programme and start date. */
        private val started = mutableMapOf<Pair<String, LocalDate>, TargetSignature>()

        /** The ledger of each programme that has closed, as it closed. */
        private val closed = mutableMapOf<String, List<HitLedgerEntity>>()

        /** The doses logged and still live, and those deleted, by ID. */
        private val live = mutableListOf<String>()
        private val deleted = ArrayDeque<String>()

        /** The choices made so far in the run. */
        private val chosen = mutableSetOf<String>()

        /** What each week's doses aim for, by programme and start date. */
        private val aims = mutableMapOf<Pair<String, LocalDate>, Aim>()

        // Seeds that make sure the rarer paths come up.
        private val repeatFirstMiss = seed % 3 == 0
        private val missFinalWeek = seed % 4 == 1
        private val rounds = if (seed % 2 == 0) 2 else 1
        private val abandonFirst = seed % 7 == 3

        /** How rarely the run spends: some spend most days, some save up for the 5× attack. */
        private val spendOneIn = listOf(2, 4, 12, 40)[seed % 4]

        suspend fun play() {
            try {
                data.settings.setCountry("GB")
                listOf(GUM_4, GUM_2, QUICKMIST).forEach { data.products.addFromCatalogue(it) }
                for (round in 0 until rounds) playProgramme(round)
            } catch (failure: Throwable) {
                throw AssertionError("Seed $seed failed. Last steps:\n" + trail.joinToString("\n"), failure)
            } finally {
                data.close()
            }
        }

        // A programme from its usual day to its end.

        private suspend fun playProgramme(round: Int) {
            if (round > 0) {
                step("start a new programme") { data.programmes.startNew() }
                tally.count("rematch")
            }
            val usual = usualDay()
            step("keep a usual day of ${usual.mix}") { data.programmes.keepUsualDay(usual) }
            val length = ProgrammeLength.entries[weighted(4, 4, 2, 1)]
            var plan = checkNotNull(data.programmes.buildPlan(length))
            // A week or two edited before the plan starts.
            repeat(random.nextInt(3)) {
                val number = 1 + random.nextInt(plan.weeks.size)
                val steps = plan.editableSteps(number, today()) ?: return@repeat
                plan = plan.edit(number, steps.random(random), today())
            }
            step("start a $length plan") { data.programmes.startPlan(plan) }

            val abandonOn = if (abandonFirst && round == 0) plan.startDate.plusDays(7L * (1 + random.nextInt(plan.weeks.size - 1))) else null
            var day = plan.startDate
            while (true) {
                val record = checkNotNull(data.programmes.current())
                if (record.status == ProgrammeStatus.COMPLETE) break
                if (day == abandonOn) {
                    abandon(record)
                    return
                }
                live(day)
                day = day.plusDays(1)
                check(day < plan.startDate.plusWeeks(plan.weeks.size + 30L)) { "The programme never ended" }
            }
            finish()
        }

        /** One day of the programme: the day's doses, then whatever else happens on it. */
        private suspend fun live(day: LocalDate) {
            val late = random.nextInt(8) == 0
            setTime(if (late) day.plusDays(1).atTime(2, 30) else day.atTime(19 + random.nextInt(4), random.nextInt(60)))
            val record = checkNotNull(data.programmes.current())
            val plan = checkNotNull(record.plan)
            plan.weekOn(day)?.let { week -> logDay(record, plan, week, day) }

            if (random.nextInt(6) == 0) backdate()
            if (random.nextInt(8) == 0) editADose()
            if (random.nextInt(10) == 0) deleteADose()
            if (deleted.isNotEmpty() && random.nextInt(4) == 0) undoADelete()
            if (random.nextInt(spendOneIn) == 0) spend()
            choose()
            if (random.nextInt(20) == 0) editAWeek()
            if (random.nextInt(40) == 0) changeDayStart()
            if (random.nextInt(25) == 0) roundTrip()
        }

        private suspend fun logDay(record: ProgrammeRecord, plan: Programme, week: ProgrammeWeek, day: LocalDate) {
            val aim = aims.getOrPut(record.id to week.startDate) { aimFor(plan, week) }
            val items = week.target.mix.items
            if (aim == Aim.MEET) {
                // At the guide most days, a little under on some.
                for (item in items) {
                    val units = if (random.nextInt(4) == 0) item.units - 1 else item.units
                    if (units > 0) log(item.product.id, units, day.atTime(9, random.nextInt(60)))
                }
            } else {
                // Over the guide every day, so over the week's allowance.
                for (item in items) log(item.product.id, item.units + 1, day.atTime(9, random.nextInt(60)))
                if (items.isEmpty() && (day == week.startDate || random.nextInt(3) == 0)) log(GUM_2, 1, day.atTime(12, 0))
            }
        }

        private fun aimFor(plan: Programme, week: ProgrammeWeek): Aim = when {
            week.kind == WeekKind.OVERTIME -> if (week.number == plan.weeks.size && random.nextInt(3) == 0) Aim.MISS else Aim.MEET
            missFinalWeek && week.number == plan.weeks.size -> Aim.MISS
            random.nextInt(5) == 0 -> Aim.MISS
            else -> Aim.MEET
        }

        // The steps.

        private suspend fun log(productId: String, units: Int, at: LocalDateTime) {
            val result = step("log $units of $productId at $at") {
                data.entries.log(LogRequest(productId, units, at = at.atZone(LONDON).toInstant(), allowDuplicate = true))
            }
            if (result is LogResult.Saved) {
                live += result.entry.id
                tally.count("log")
            }
        }

        /** A dose forgotten on one of the last 16 days, logged now. */
        private suspend fun backdate() {
            val day = today().minusDays(1L + random.nextInt(16))
            val record = data.programmes.current()
            val week = record?.plan?.weekOn(day)
            if (week != null) {
                val into = if (week.result == WeekResult.PENDING) "a week still to come in" else "a week whose result is in"
                tally.count("backdate into $into")
            }
            log(listOf(GUM_2, GUM_4, QUICKMIST).random(random), 1, day.atTime(12 + random.nextInt(8), random.nextInt(60)))
        }

        private suspend fun editADose() {
            val id = live.randomOrNull(random) ?: return
            val entry = data.entries.get(id) ?: return
            // A different amount, or the time moved by up to a day and a half either way.
            val request = if (random.nextBoolean()) {
                LogRequest(entry.productId, maxOf(1, entry.quantity + listOf(-1, 1, 2).random(random)), allowDuplicate = true)
            } else {
                val at = entry.occurredAt.plusSeconds(3_600L * random.nextInt(-36, 36))
                LogRequest(entry.productId, entry.quantity, at = at, allowDuplicate = true)
            }
            step("edit $id to ${request.quantity} at ${request.at}") { data.entries.edit(id, request) }
            tally.count("edit")
        }

        private suspend fun deleteADose() {
            val id = live.randomOrNull(random) ?: return
            step("delete $id") { data.entries.delete(id) }
            live -= id
            deleted.addLast(id)
            tally.count("delete")
        }

        private suspend fun undoADelete() {
            val id = deleted.removeLast()
            step("undo the delete of $id") { data.entries.undoDelete(id) }
            live += id
            tally.count("undo a delete")
        }

        /** A hit or a 5× attack, when there are hits to spend, or a try with none. */
        private suspend fun spend() {
            val banked = data.game.demon()?.balance?.banked ?: return
            val result = when {
                banked >= 5 && random.nextBoolean() ->
                    step("5x attack with $banked banked") { data.game.comboAttack() }.also { tally.count("5x attack") }
                banked >= 1 -> step("hit with $banked banked") { data.game.spendHit() }.also { tally.count("hit") }
                else -> step("hit with none banked") { data.game.spendHit() }.also { tally.count("spend refused") }
            }
            if (banked == 0) assertTrue("Spent with nothing banked: $result", result !is SpendResult.Spent)
        }

        /** The choice after a missed week, once its result is in, or no answer at all. */
        private suspend fun choose() {
            val missed = data.programmes.awaitingChoice() ?: return
            val repeat = (repeatFirstMiss && "repeat" !in chosen) || random.nextInt(4) == 0
            when {
                repeat -> {
                    choice(missed, MissedWeekChoice.REPEAT)
                    tally.count("repeat")
                }
                random.nextInt(5) == 0 -> Unit
                else -> {
                    choice(missed, MissedWeekChoice.CARRY_ON)
                    tally.count("carry on")
                    if (random.nextInt(4) == 0) {
                        // Changed to a repeat later that day.
                        setTime(data.clock.now.atZone(LONDON).toLocalDateTime().plusMinutes(30))
                        if (choice(missed, MissedWeekChoice.REPEAT)) tally.count("carry on, then repeat")
                    }
                }
            }
        }

        private suspend fun choice(missed: ProgrammeWeek, choice: MissedWeekChoice): Boolean {
            val record = checkNotNull(data.programmes.current())
            // A repeat gives the week running the missed week's target: the one change a started
            // week can have.
            if (choice == MissedWeekChoice.REPEAT) started.remove(record.id to missed.endDate.plusDays(1))
            val made = step("choose $choice after week ${missed.number}") { data.programmes.chooseAfterMiss(missed.number, choice) }
            if (made && choice == MissedWeekChoice.REPEAT) {
                chosen += "repeat"
            }
            return made
        }

        /** A week still to come moved up or down its ladder. */
        private suspend fun editAWeek() {
            val plan = data.programmes.current()?.plan ?: return
            val number = 1 + random.nextInt(plan.weeks.size)
            val steps = plan.editableSteps(number, today()) ?: return
            step("edit week $number") { data.programmes.editWeek(number, steps.random(random)) }
            tally.count("week edited")
        }

        private suspend fun changeDayStart() {
            val time = listOf(LocalTime.of(0, 0), LocalTime.of(3, 0), LocalTime.of(4, 0), LocalTime.of(5, 30)).random(random)
            step("day start to $time") { data.entries.changeDayStart(time) }
            tally.count("day start changed")
        }

        /**
         * A backup taken now, read back from its text, then merged or replaced onto this phone,
         * or onto a new one that the run then carries on with. Nothing changes either way.
         */
        private suspend fun roundTrip() {
            val before = snapshot(data)
            val backup = Backup.decode(data.backups.export().encode())
            val mode = if (random.nextBoolean()) ImportMode.MERGE else ImportMode.REPLACE
            val where = when (random.nextInt(5)) {
                0, 1 -> "onto itself"
                2, 3 -> "onto a new phone"
                else -> if (mode == ImportMode.MERGE) "onto a new phone with a baseline" else "onto a new phone"
            }
            val onto = if (where == "onto itself") {
                data
            } else {
                // A new phone, set up as onboarding would: its country and day start.
                TestData(start = data.clock.now).also { phone ->
                    phone.settings.setCountry("GB")
                    phone.settings.setDayStart(data.settings.current().dayStart)
                    if (where.endsWith("baseline")) phone.programmes.startBaseline()
                }
            }
            step("${mode.name.lowercase()} a backup $where") { onto.backups.import(backup, mode) }
            // A plan in the backup takes over from the new phone's baseline, which closes as abandoned.
            val expected = if (where.endsWith("baseline")) before.copy(programmes = before.programmes + 1) else before
            assertEquals("A ${mode.name.lowercase()} $where changed what's stored", expected, snapshot(onto))
            if (onto !== data) {
                data.close()
                data = onto
                verify("the move to the new phone")
            }
            // Deleted doses aren't in a backup, so they're gone from a phone it replaced or started.
            if (mode == ImportMode.REPLACE || where != "onto itself") deleted.clear()
            tally.count("${mode.name.lowercase()} $where")
        }

        private suspend fun abandon(record: ProgrammeRecord) {
            step("abandon") { data.programmes.abandon() }
            closed[record.id] = data.database.game().allLedger().filter { it.programmeId == record.id }
            tally.count("abandoned")
        }

        /** Every hit left spent, so the demon falls, and the programme ends. */
        private suspend fun finish() {
            while (true) {
                val banked = checkNotNull(data.game.demon()).balance.banked
                if (banked == 0) break
                if (banked >= 5 && random.nextBoolean()) {
                    step("5x attack to finish") { data.game.comboAttack() }
                    tally.count("5x attack")
                } else {
                    step("hit to finish") { data.game.spendHit() }
                }
            }
            val record = checkNotNull(data.programmes.current())
            assertEquals("Every hit spent leaves the demon at 0 HP", 0, data.game.demon()?.balance?.hp)
            if (record.plan?.weeks?.any { it.result == WeekResult.CAUGHT_UP } == true) tally.count("caught up")
            if (record.plan?.weeks?.any { it.kind == WeekKind.OVERTIME } == true) tally.count("overtime")
            if (record.plan?.weeks?.any { it.result == WeekResult.CAUGHT_UP || it.result == WeekResult.MISSED } == true) tally.count("missed")
            assertTrue(step("end the programme") { data.programmes.end() })
            closed[record.id] = data.database.game().allLedger().filter { it.programmeId == record.id }
            tally.count("defeated")
        }

        // Checking.

        /** Runs [block] as a step of the run, then checks everything still holds. */
        private suspend fun <T> step(what: String, block: suspend () -> T): T {
            trail.addLast("${data.clock.now.atZone(LONDON).toLocalDateTime()}: $what")
            if (trail.size > 40) trail.removeFirst()
            val result = block()
            verify(what)
            return result
        }

        private suspend fun verify(after: String) {
            val record = data.programmes.current()
            val ledger = data.database.game().allLedger()
            for ((id, rows) in closed) {
                assertEquals("After $after, a closed programme's ledger changed", rows, ledger.filter { it.programmeId == id })
            }
            val plan = record?.plan ?: return
            val demon = checkNotNull(data.database.game().demonOf(record.id)) { "A plan without its demon" }
            val rows = ledger.filter { it.programmeId == record.id }
            val earnedRows = rows.filter { it.hits > 0 }
            val earned = earnedRows.sumOf { it.hits }
            val spent = -rows.filter { it.hits < 0 }.sumOf { it.hits }
            val state = checkNotNull(data.game.demon())

            assertTrue("After $after, banked hits went negative: $earned earned, $spent spent", spent <= earned)
            assertEquals("After $after, HP isn't max less 10 for each hit spent", demon.maxHp - HP_PER_HIT * spent, state.balance.hp)
            assertEquals("After $after, the banked hits don't match the ledger", earned - spent, state.balance.banked)
            assertEquals("After $after, the demon's HP blocks don't match the hits on offer", HP_PER_HIT * plan.hitsOnOffer, demon.maxHp)
            assertEquals("After $after, the hits on offer don't add up", plan.hitsOnOffer, earned + plan.hitsStillOnOffer)
            assertEquals(
                "After $after, the ledger's earned hits aren't the ones the results earned",
                plan.earnedHits.map { LedgerKind.of(it.kind) to it.week }.toSet(),
                earnedRows.map { it.kind to it.week }.toSet(),
            )
            assertEquals("After $after, a week earned more than one hit", earnedRows.size, earnedRows.map { it.week }.toSet().size)
            if (record.status == ProgrammeStatus.COMPLETE) assertEquals("A complete programme has hits still on offer", 0, plan.hitsStillOnOffer)

            // Results never change once in, apart from a missed week being caught up.
            for (week in plan.weeks) {
                val key = record.id to week.number
                val first = results[key]
                if (first != null) {
                    assertEquals("After $after, week ${week.number}'s result changed", first, week.signature().copy(result = first.result))
                    val caughtUp = first.result == WeekResult.MISSED && week.result == WeekResult.CAUGHT_UP
                    assertTrue("After $after, week ${week.number} went from ${first.result} to ${week.result}", week.result == first.result || caughtUp)
                }
                if (week.result != WeekResult.PENDING) results[key] = week.signature()
            }
            assertTrue(
                "After $after, a result went back to pending",
                results.keys.filter { it.first == record.id }
                    .all { (_, number) -> plan.weeks.getOrNull(number - 1)?.result != WeekResult.PENDING },
            )

            // Allowances never go up, and a week's target is fixed once it starts.
            plan.weeks.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    "After $after, week ${b.number}'s allowance ${b.target.weeklyAllowance} is above week ${a.number}'s ${a.target.weeklyAllowance}",
                    b.target.weeklyAllowance <= a.target.weeklyAllowance,
                )
            }
            val today = today()
            for (week in plan.weeks.filter { it.startDate <= today }) {
                val key = record.id to week.startDate
                val first = started.getOrPut(key) { week.target.signature() }
                assertEquals("After $after, week ${week.number}'s target changed once it had started", first, week.target.signature())
            }
        }

        private suspend fun snapshot(phone: TestData) = Snapshot(
            current = phone.programmes.current(),
            ledger = phone.database.game().allLedger(),
            demon = phone.game.demon(),
            entries = phone.entries.observeRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2030-01-01")).first().toSet(),
            programmes = phone.database.programmes().count(),
        )

        // Helpers.

        private suspend fun today(): LocalDate = data.programmes.clock().today

        /** The clock to [at], never backwards. */
        private fun setTime(at: LocalDateTime) {
            val instant = at.atZone(LONDON).toInstant()
            if (instant > data.clock.now) data.clock.now = instant
        }

        private suspend fun usualDay(): UsualDay {
            val gum4 = checkNotNull(data.products.get(GUM_4))
            val gum2 = checkNotNull(data.products.get(GUM_2))
            val spray = checkNotNull(data.products.get(QUICKMIST))
            return when (random.nextInt(4)) {
                0 -> UsualDay.typed(gum4 to 8)
                1 -> UsualDay.typed(gum2 to 6 + random.nextInt(7))
                2 -> UsualDay.typed(gum4 to 4 + random.nextInt(5), spray to 1 + random.nextInt(4))
                else -> UsualDay.typed(gum2 to 3, spray to 2)
            }
        }

        /** An index picked with the given weights. */
        private fun weighted(vararg weights: Int): Int {
            var pick = random.nextInt(weights.sum())
            weights.forEachIndexed { index, weight ->
                if (pick < weight) return index
                pick -= weight
            }
            error("Unreachable")
        }
    }
}

/** Seeded runs: enough for every path to come up many times. */
private const val RUNS = 16

private const val QUICKMIST = "nicorette-quickmist-uk-1mg"
