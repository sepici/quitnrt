package app.thelasthit.nrt

import android.os.Looper
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.UnlockNews
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.game.BADGE_TOAST_MILLIS
import app.thelasthit.nrt.ui.game.badgeName as badgeText
import app.thelasthit.nrt.ui.game.gearName as gearText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

/**
 * The whole journey through the real screens, with the clock moved on a day at a time: set-up, a
 * week of logging from the quick log and the log sheet, "Use your last 7 days", an 8-week plan
 * from that usual day, every week of it (one missed, carried on from and caught up), the
 * NRT-free weeks, the hits spent in the lair until the demon falls, the victory, and a rematch.
 *
 * At each week's result it checks that Today, the Plan tab, Insights, the lair's HP and the
 * badges tell the same story, so the game and the programme agree.
 *
 * It runs the real [MainActivity], so the results and the badges come in as the app finds them
 * itself: results as the app comes back or the minute ticks over, and badges from the unlock
 * checks the activity follows while it's on screen. The test only waits for them to show.
 *
 * The usual day is 6 pieces of 2 mg gum, 12 mg. Over 8 weeks that's 6 taper weeks of 5, 4, 3, 2,
 * 1 and 1 pieces a day (70, 56, 42, 28, 14 and 14 mg a week), then 2 NRT-free weeks: 8 hits on
 * Gary's 80 HP.
 *
 * Repository calls run off the main thread while the test waits on the screen: blocking the main
 * thread on one could stop the work Room posts back to it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class JourneyTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    /** Every badge and piece of gear the toasts and cards have shown so far, by name. */
    private val shown = mutableSetOf<String>()

    @Test
    fun `from set-up to a defeated demon and a rematch`() {
        try {
            setUp()
            baselineWeek()
            startThePlan()
            weekOne()
            weekTwoMissed()
            weekThreeCatchesUp()
            weeksFourToSix()
            nrtFreeWeeks()
            theLastHits()
            aRematch()
        } catch (failure: Throwable) {
            println(compose.onRoot().printToString(maxDepth = Int.MAX_VALUE))
            throw failure
        }
    }

    // The chapters.

    private fun setUp() {
        click("Accept and set up")
        click("Skip")
        waitFor("Your phone's region")
        click("Next: your products")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Nicorette gum 2\u00A0mg"), timeoutMillis = 5_000)
        compose.onNode(hasContentDescription("Nicorette gum 2\u00A0mg")).performScrollTo().performClick()
        waitFor("1 picked")
        click("Next: name your demon")
        compose.waitUntilAtLeastOneExists(hasSetTextAction(), timeoutMillis = 5_000)
        compose.onNode(hasSetTextAction()).performTextInput("Gary")
        compose.waitUntilAtLeastOneExists(hasText("Next: when your day starts") and isEnabled(), timeoutMillis = 5_000)
        click("Next: when your day starts")
        click("Next: your reminders")
        click("Skip")
        waitForDescription("Gary is lurking. It shows itself when your plan starts.")
        click("Start logging")

        waitFor("Nothing logged yet today")
        waitForDescription("Gary, lurking until your plan starts. HP unknown. Opens the lair.")
    }

    /**
     * Friday 23 to Thursday 29 October, 6 pieces a day from the quick log and the log sheet,
     * except Sunday, the day the clocks go back, with nothing logged. The first dose brings a
     * badge, and so does the first on a third day.
     */
    private fun baselineWeek() {
        for (day in listOf(23, 24, 26, 27, 28, 29)) {
            if (day != 23) nextVisit("2026-10-${day}T20:00")
            waitFor("Nothing logged yet today")
            quickLog(expectTodayMg = 2)
            when (day) {
                23 -> drainUnlocks(listOf("First entry"))
                26 -> drainUnlocks(listOf("Logged on 3\u00A0days"))
            }
            quickLog(expectTodayMg = 4)
            logSheet(pieces = 4, expectTodayMg = 12)
            waitFor("3\u00A0doses so far")
        }
        assertEquals(18, entries().size)
        drainUnlocks()
    }

    /** Friday 30 October: the last 7 days are the baseline, kept as they are, and an 8-week plan starts tomorrow. */
    private fun startThePlan() {
        nextVisit("2026-10-30T09:00")
        tab("Plan")
        waitFor("6 of the 7 days have doses, so they can be your baseline.")
        click("Use your last 7 days")

        waitFor("From your logs, Friday 23 to Thursday 29\u00A0October.")
        waitFor("6\u00A0pieces, 12\u00A0mg a day")
        click("Use this usual day")

        waitFor("Week by week")
        click("8 weeks")
        waitFor("8 weeks, Saturday 31\u00A0October to Friday 25\u00A0December")
        compose.onNode(hasContentDescription("Week 1, from Saturday 31\u00A0October: 5\u00A0pieces of 2\u00A0mg gum", substring = true)).performScrollTo()
        compose.onNode(hasContentDescription("Week 6, from Saturday 5\u00A0December: 1\u00A0piece of 2\u00A0mg gum", substring = true)).performScrollTo()
        click("Start the plan")

        waitFor("Starts tomorrow")
        val record = checkNotNull(background { app.graph.programmes.current() })
        assertEquals(ProgrammeStatus.ACTIVE, record.status)
        assertEquals(LocalDate.parse("2026-10-23")..LocalDate.parse("2026-10-29"), record.baselineDays)
        assertEquals(listOf(70, 56, 42, 28, 14, 14, 0, 0).map { Dose.mg(it) }, checkNotNull(record.plan).weeks.map { it.target.weeklyAllowance })
        assertEquals(80, record.demon?.maxHp)
        drainUnlocks(listOf("Baseline done"))

        tab("Today")
        waitFor("Your plan starts tomorrow.")
        waitForDescription("Gary, lurking until your plan starts. HP unknown. Opens the lair.")
    }

    /** Week 1, 31 October to 6 November: 4 pieces a day, 56 of its 70 mg. */
    private fun weekOne() {
        nextVisit("2026-10-31T10:00")
        tab("Today")
        waitForDescription("Daily guide 10\u00A0mg. 10\u00A0mg left today.")
        waitFor("Week 1 of 8, day 1. 70\u00A0mg left this week.")
        waitForDescription("Gary, 80 of 80 HP. Opens the lair.")
        for (mg in listOf(2, 4, 6, 8)) quickLog(expectTodayMg = mg)
        waitForDescription("Daily guide 10\u00A0mg. 2\u00A0mg left today.")
        waitFor("Week 1 of 8, day 1. 62\u00A0mg left this week.")

        logDays("2026-11-01", days = 6, pieces = 4)

        // Saturday: week 2 has started, and week 1's result is still to come.
        nextVisit("2026-11-07T10:00")
        tab("Today")
        waitFor("Week 2 of 8, day 1. 56\u00A0mg left this week.")
        tab("Plan")
        waitFor("Week 1's result comes in after today, so late logs still count.")
        compose.onNode(hasContentDescription("Week 1, 31\u00A0October to 6\u00A0November: 5\u00A0pieces of 2\u00A0mg gum. 56\u00A0mg used of the 70\u00A0mg allowance, Result after today")).performScrollTo()
        logDays("2026-11-07", days = 1, pieces = 5)

        // Sunday: week 1 is met.
        nextVisit("2026-11-08T10:00")
        checkWeek(
            number = 1,
            plan = "Week 1, 31\u00A0October to 6\u00A0November: 5\u00A0pieces of 2\u00A0mg gum. 56\u00A0mg used of the 70\u00A0mg allowance, Met. 1 hit for Gary",
            insights = "Week 1, 56 of 70\u00A0mg, met.",
            hp = 80,
            banked = 1,
            badges = listOf("First day within the guide", "First week met", "Wooden sword", "Iron sword"),
        )

        // A hit in the lair.
        tab("Lair")
        waitForHp(80)
        click("Hit")
        waitFor("Gary staggers.")
        waitForHp(70)
        waitFor("No hits banked")
        tab("Today")
        waitForDescription("Gary, 70 of 80 HP. Opens the lair.")
    }

    /** Week 2, 7 to 13 November: 5 pieces a day, 70 mg against 56. Missed, and carried on from. */
    private fun weekTwoMissed() {
        logDays("2026-11-08", days = 6, pieces = 5)
        // Week 3's first day, before week 2's result comes in.
        logDays("2026-11-14", days = 1, pieces = 3)
        nextVisit("2026-11-15T10:00")
        tab("Plan")
        waitFor("Missed this one")
        waitFor("Week 2 came to 70\u00A0mg, 14\u00A0mg over its 56\u00A0mg allowance.")
        // The option, already picked, and the button that confirms it.
        compose.onAllNodes(hasText("Carry on") and hasClickAction())[1].performClick()
        waitFor("You carried on after week 2.")
        compose.onNode(
            hasContentDescription("Week 2, 7 to 13\u00A0November: 4\u00A0pieces of 2\u00A0mg gum. 70\u00A0mg used of the 56\u00A0mg allowance, Missed, back if week\u00A03 is met"),
        ).performScrollTo()
        assertEquals(WeekResult.MISSED, background { checkNotNull(app.graph.programmes.current()?.plan).week(2).result })
        // Nothing for the programme comes from a missed week.
        drainUnlocks()
        assertTrue("$shown", shown.none { it == "Back in step" || it == "3\u00A0weeks met" })
        waitForDescription("Gary, 70 of 80 HP. Opens the lair.")

        tab("Today")
        waitFor("Week 3 of 8, day 2.")
        waitForDescription("Gary, 70 of 80 HP. Opens the lair.")
        tab("Insights")
        waitForDescription("Week 2, 70 of 56\u00A0mg, missed, back if week 3 is met.")
        tab("Lair")
        waitFor("Gary looks smug. Meet next week and take that hit back.")
        waitForHp(70)
    }

    /**
     * Week 3, 14 to 20 November: 3 pieces a day, right at its 42 mg. Met, and it pays week 2
     * back. Its result comes in with the app left open from Saturday night into Sunday morning.
     */
    private fun weekThreeCatchesUp() {
        logDays("2026-11-15", days = 6, pieces = 3)
        logDays("2026-11-21", days = 1, pieces = 2)
        nextVisit("2026-11-21T21:30")
        tab("Plan")
        waitFor("Week 3's result comes in after today, so late logs still count.")
        // Still open at 10:00 on Sunday, six hours after the result came due. Nothing is logged,
        // so the result comes in only because the app looks while it's open.
        app.clock.now = london("2026-11-22T10:00")
        aMinutePasses()
        waitForDescription("42\u00A0mg used of the 42\u00A0mg allowance, Met, paid back week\u00A02. 2 hits for Gary")
        compose.onNode(hasText("Week 3's result comes in after today", substring = true)).assertDoesNotExist()
        checkWeek(
            number = 3,
            plan = "Week 3, 14 to 20\u00A0November: 3\u00A0pieces of 2\u00A0mg gum. 42\u00A0mg used of the 42\u00A0mg allowance, Met, paid back week\u00A02. 2 hits for Gary",
            insights = "Week 3, 42 of 42\u00A0mg, met, paid back week 2.",
            hp = 70,
            banked = 2,
            badges = listOf("3\u00A0weeks met", "Back in step", "Halfway", "Axe"),
        )
        tab("Plan")
        compose.onNode(hasContentDescription("Week 2, 7 to 13\u00A0November: 4\u00A0pieces of 2\u00A0mg gum. 70\u00A0mg used of the 56\u00A0mg allowance, Caught up in week\u00A03")).performScrollTo()
        tab("Insights")
        waitForDescription("Week 2, 70 of 56\u00A0mg, caught up in week 3.")
    }

    /** Weeks 4 to 6, each met at its guide: 2, 1 and 1 pieces a day. Then the 5× attack. */
    private fun weeksFourToSix() {
        logDays("2026-11-22", days = 6, pieces = 2)
        logDays("2026-11-28", days = 1, pieces = 1)
        nextVisit("2026-11-29T10:00")
        checkWeek(
            number = 4,
            plan = "Week 4, 21 to 27\u00A0November: 2\u00A0pieces of 2\u00A0mg gum. 28\u00A0mg used of the 28\u00A0mg allowance, Met. 1 hit for Gary",
            insights = "Week 4, 28 of 28\u00A0mg, met.",
            hp = 70,
            banked = 3,
            // Half the programme's 8 hits are in: weeks 1, 3 and 4, and week 2 paid back.
            badges = listOf("War hammer"),
        )

        logDays("2026-11-29", days = 6, pieces = 1)
        logDays("2026-12-05", days = 1, pieces = 1)
        nextVisit("2026-12-06T10:00")
        checkWeek(
            number = 5,
            plan = "Week 5, 28\u00A0November to 4\u00A0December: 1\u00A0piece of 2\u00A0mg gum. 14\u00A0mg used of the 14\u00A0mg allowance, Met. 1 hit for Gary",
            insights = "Week 5, 14 of 14\u00A0mg, met.",
            hp = 70,
            banked = 4,
            // Week 6, the final taper week, started yesterday.
            badges = listOf("Final taper week", "Fire staff"),
        )

        logDays("2026-12-06", days = 6, pieces = 1)
        nextVisit("2026-12-13T10:00")
        checkWeek(
            number = 6,
            plan = "Week 6, 5 to 11\u00A0December: 1\u00A0piece of 2\u00A0mg gum. 14\u00A0mg used of the 14\u00A0mg allowance, Met. 1 hit for Gary",
            insights = "Week 6, 14 of 14\u00A0mg, met.",
            hp = 70,
            banked = 5,
            badges = listOf("6\u00A0weeks met", "Lightning bow"),
        )

        tab("Lair")
        waitForDescription("5 hits banked")
        click("5× attack")
        waitFor("Gary reels. It didn't see the 5× coming.")
        waitForHp(20)
        waitFor("No hits banked")
    }

    /** Weeks 7 and 8, 12 to 25 December, with nothing at all. */
    private fun nrtFreeWeeks() {
        nextVisit("2026-12-14T10:00")
        tab("Today")
        waitForDescription("Daily guide 0\u00A0mg. At the 0\u00A0mg guide.")
        waitFor("Week 7 of 8, day 3. An NRT-free week.")

        nextVisit("2026-12-20T10:00")
        checkWeek(
            number = 7,
            plan = "Week 7, 12 to 18\u00A0December: 0\u00A0mg used of the 0\u00A0mg allowance, Met, NRT-free. 1 hit for Gary",
            insights = "Week 7, 0 of 0\u00A0mg, met.",
            hp = 20,
            banked = 1,
        )

        nextVisit("2026-12-27T10:00")
        tab("Plan")
        waitFor("Programme done")
        waitFor("8 earned")
        waitFor("Gary still has 20\u00A0HP. Once the last hits land in the lair, a new programme can start.")
        tab("Today")
        waitForDescription("Gary, 20 of 80 HP, 2 hits ready. Opens the lair.")
        tab("Insights")
        waitForDescription("Week 8, 0 of 0\u00A0mg, met.")
        drainUnlocks()
    }

    /** The last two hits, the defeat and the victory. */
    private fun theLastHits() {
        tab("Lair")
        waitFor("Programme done")
        waitForHp(20)
        waitForDescription("2 hits banked")
        click("Hit")
        waitFor("Gary staggers.")
        waitForHp(10)
        waitForDescription("1 hit banked")
        click("Hit")
        waitFor("Victory")
        waitForHp(0)
        waitFor("Nothing left of Gary but a few stray pixels.")
        waitFor("6\u00A0pieces of 2\u00A0mg gum, down to none")
        waitFor("8, the last one today")
        val demon = checkNotNull(background { app.graph.game.demon() })
        assertEquals(0, demon.balance.hp)
        assertEquals(0, demon.balance.banked)
        assertTrue(demon.demon.defeatedAt != null)
        drainUnlocks(listOf("Demon defeated", "Legendary blade"))
        checkShelf(listOf("Demon defeated"))
    }

    /** Back on the Plan tab: a new programme with Gary back for round two, from a new baseline. */
    private fun aRematch() {
        tab("Today")
        waitForDescription("Gary, 0 of 80 HP. Opens the lair.")
        tab("Plan")
        waitFor("Programme done")
        waitFor("8, the last one today")
        waitForDescription("Gary, defeated, 0 of 80 HP. Opens the lair.")
        waitFor("Start a new programme")
        click("Gary, round two")
        waitFor("Baseline, day 1 of 7")
        val record = checkNotNull(background { app.graph.programmes.current() })
        assertEquals(ProgrammeStatus.BASELINE, record.status)
        assertEquals(LocalDate.parse("2026-12-27"), record.baselineStartedOn)
        assertEquals(2, background { app.graph.backups.counts() }.programmes)
        tab("Lair")
        waitFor("Gary watches from the rocks until your plan starts.")
        tab("Today")
        waitFor("Nothing logged yet today")
        waitForDescription("Gary, lurking until your plan starts. HP unknown. Opens the lair.")
        compose.onNode(hasContentDescription("Daily guide", substring = true)).assertDoesNotExist()
        tab("Insights")
        waitFor("Each day")
        compose.onNode(hasText("Week by week")).assertDoesNotExist()
        // Badges carry over.
        assertTrue(Achievement.DEMON_DEFEATED.key in background { app.graph.game.achievements.first() }.keys)
    }

    // A week's result, as every screen tells it.

    /**
     * Checks week [number]'s result as every screen tells it: the Plan tab's schedule and band,
     * Today's band, Insights' week by week, and the lair's HP, hits and trophy shelf, with Gary at
     * [hp] of 80 and [banked] hits ready.
     *
     * The result has to come in by itself first, as the app came back, so the Plan tab's row
     * shows it before the test does anything. Then [badges] (badges and gear, by name) have to
     * show in the toasts and cards, as the app finds them.
     */
    private fun checkWeek(number: Int, plan: String, insights: String, hp: Int, banked: Int, badges: List<String> = emptyList()) {
        val ready = if (banked == 1) "1 hit ready" else "$banked hits ready"
        val band = "Gary, $hp of 80 HP, $ready. Opens the lair."

        tab("Plan")
        val row = hasContentDescription(plan)
        compose.waitUntilAtLeastOneExists(row, timeoutMillis = 10_000)
        compose.onNode(row).performScrollTo()
        drainUnlocks(badges)
        waitForDescription(band)
        tab("Today")
        waitFor("Week ${number + 1} of 8, day 2.")
        waitForDescription(band)
        tab("Insights")
        waitFor("Week by week")
        // The chart is one picture to a screen reader, which hears every week in turn.
        val week = hasContentDescription(insights, substring = true)
        compose.waitUntilAtLeastOneExists(week, timeoutMillis = 10_000)
        compose.onNode(week).performScrollTo()
        tab("Lair")
        waitForHp(hp)
        waitForDescription(if (banked == 1) "1 hit banked" else "$banked hits banked")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Lair, $ready"), timeoutMillis = 5_000)
        checkShelf(badges)

        // What every screen shows is what's stored.
        val demon = checkNotNull(background { app.graph.game.demon() })
        assertEquals(hp, demon.balance.hp)
        assertEquals(banked, demon.balance.banked)
    }

    /** The lair's trophy shelf shows every badge stored, [badges] among them. */
    private fun checkShelf(badges: List<String>) {
        val stored = badgesEarned()
        waitForDescription("${stored.size} of 33 badges earned")
        for (badge in badges.filter { it in stored }) waitForDescription("$badge, earned")
        assertTrue("$badges, stored $stored", badges.filter { name -> Achievement.entries.any { badgeName(it) == name } }.all { it in stored })
    }

    // Steps.

    /**
     * The phone put down, and picked up again at [at]: the app goes to the background, the clock
     * moves on, and the app comes back, which settles the results due, as on a phone. The main
     * looper runs on a minute, so each screen's clock ticks over to the new time.
     */
    private fun nextVisit(at: String) {
        val scenario = compose.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED)
        app.clock.now = london(at)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    /**
     * A minute on with the app open: the main looper's clock, which the screens' minute ticks
     * wait on, and Compose's, which the shell's settling waits on.
     */
    private fun aMinutePasses() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        compose.mainClock.advanceTimeBy(61_000)
        compose.waitForIdle()
    }

    /**
     * Doses on [days] days from [from]: [pieces] at 09:00 on each, logged at 21:00 that day
     * through the repository, without looking at the screens.
     */
    private fun logDays(from: String, days: Int, pieces: Int) {
        for (offset in 0 until days) {
            val day = LocalDate.parse(from).plusDays(offset.toLong())
            app.clock.now = london("${day}T21:00")
            if (pieces == 0) continue
            val result = background { app.graph.entries.log(LogRequest(GUM_2, pieces, at = london("${day}T09:00"))) }
            assertTrue("$result", result is LogResult.Saved)
        }
    }

    /** A tap on the quick-log pill, which logs a piece now, leaving [expectTodayMg] today. */
    private fun quickLog(expectTodayMg: Int) {
        // The pill says its own label first, then the product: "Log Gum 2 mg, Nicorette gum".
        compose.onNode(hasContentDescription("Log Gum 2\u00A0mg,", substring = true)).performClick()
        waitForTodayMg(expectTodayMg)
    }

    /** The log sheet from Today's Log button: [pieces] of the gum, an hour ago. */
    private fun logSheet(pieces: Int, expectTodayMg: Int) {
        compose.onNode(hasContentDescription("Log a dose")).performClick()
        waitFor("Log a dose")
        repeat(pieces - 1) { compose.onNode(hasContentDescription("One more")).performClick() }
        waitFor("$pieces\u00A0pieces")
        compose.onNode(hasText("1\u00A0h ago")).performScrollTo().performClick()
        compose.onNode(hasText("Log") and hasText(" at ", substring = true)).performClick()
        waitForTodayMg(expectTodayMg)
    }

    /**
     * Lets every toast and card the app puts up for new badges and gear show and go, reading
     * each name off the toast or card itself into [shown]. The app finds them on its own, so
     * this waits up to 10 seconds at a time for each of [expect] still to show, then takes
     * whatever else is up, and checks that [expect] were all shown.
     */
    private fun drainUnlocks(expect: List<String> = emptyList()) {
        val news = app.graph.unlocks.news
        while (true) {
            val next = if (expect.any { it !in shown }) {
                awaitNews()
            } else {
                compose.waitForIdle()
                news.value.firstOrNull()
            } ?: break
            val names = when (next) {
                is UnlockNews.Badge -> {
                    val name = badgeName(next.achievement)
                    val toast = hasText(app.getString(R.string.unlock_badge_label)) and hasText(name) and hasClickAction()
                    listOf(name).takeIf { showsUp(next, toast) }
                }
                is UnlockNews.NewGear -> {
                    val name = gearName(next.gear)
                    listOf(name).takeIf { showsUp(next, hasText(app.getString(R.string.unlock_gear_label)) and hasText(name)) }
                }
                is UnlockNews.Summary -> {
                    // The card says how many, and shows the badges as pictures only.
                    val count = next.achievements.size
                    val title = app.resources.getQuantityString(R.plurals.unlock_summary_title, count, count)
                    (next.achievements.map { badgeName(it) } + next.gear.map { gearName(it) }).takeIf { showsUp(next, hasText(title)) }
                }
            }
            if (names != null) {
                shown += names
                when (next) {
                    is UnlockNews.Badge -> compose.mainClock.advanceTimeBy(BADGE_TOAST_MILLIS + 1_000)
                    is UnlockNews.NewGear -> click("Not now")
                    is UnlockNews.Summary -> click("Close")
                }
            }
            compose.waitUntil(10_000) {
                compose.waitForIdle()
                news.value.firstOrNull() != next
            }
        }
        assertTrue("Waited for $expect, shown $shown", shown.containsAll(expect))
    }

    /** The first unlock waiting to be shown, after up to 10 seconds for one to come in. */
    private fun awaitNews(): UnlockNews? {
        val news = app.graph.unlocks.news
        try {
            compose.waitUntil(10_000) {
                compose.waitForIdle()
                news.value.isNotEmpty()
            }
        } catch (_: ComposeTimeoutException) {
            return null
        }
        return news.value.firstOrNull()
    }

    /** Whether [item]'s toast or card, found by [card], shows. False if it went by unseen. */
    private fun showsUp(item: UnlockNews, card: SemanticsMatcher): Boolean {
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() || app.graph.unlocks.news.value.firstOrNull() != item
        }
        return compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty()
    }

    private fun badgeName(achievement: Achievement): String = app.getString(badgeText(achievement))

    private fun gearName(gear: Gear): String = app.getString(gearText(gear))

    /** The badges stored so far, by name. */
    private fun badgesEarned(): List<String> =
        background { app.graph.game.achievements.first() }.keys
            .mapNotNull { key -> Achievement.entries.firstOrNull { it.key == key } }
            .map { badgeName(it) }

    private fun entries() = background { app.graph.entries.observeRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2027-01-31")).first() }

    // Finding things on screen.

    /** A tab in the tab bar. */
    private fun tab(name: String) {
        val tab = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.waitUntilAtLeastOneExists(tab, timeoutMillis = 5_000)
        compose.onNode(tab).performClick()
        compose.waitForIdle()
    }

    private fun waitFor(text: String) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis = 10_000)

    private fun waitForDescription(text: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(text, substring = true), timeoutMillis = 10_000)

    /** The lair's HP, as its whole description, so 10 HP can't pass for 0. At 0 it also says the defeat. */
    private fun waitForHp(hp: Int) =
        compose.waitUntilAtLeastOneExists(
            hasContentDescription(if (hp == 0) "0 of 80 HP. Gary is defeated." else "$hp of 80 HP"),
            timeoutMillis = 10_000,
        )

    /** Today's total, as its whole description, so 12 mg can't pass for 2. */
    private fun waitForTodayMg(mg: Int) =
        compose.waitUntilAtLeastOneExists(hasContentDescription("${mg}\u00A0mg today"), timeoutMillis = 10_000)

    /** Taps the first control showing [text], scrolling to it in a scrolling page. */
    private fun click(text: String) {
        val control = hasText(text) and hasClickAction()
        compose.waitUntilAtLeastOneExists(control, timeoutMillis = 10_000)
        val scrolling = control and hasAnyAncestor(hasScrollAction())
        if (compose.onAllNodes(scrolling).fetchSemanticsNodes().isNotEmpty()) compose.onAllNodes(scrolling).onFirst().performScrollTo()
        compose.onAllNodes(control).onFirst().performClick()
    }

    /**
     * [block]'s answer, from another thread while the main thread keeps idling: a save the screen
     * started may hold a lock until it finishes there.
     */
    private fun <T> background(block: suspend () -> T): T {
        val result = CompletableFuture.supplyAsync { runBlocking { block() } }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            result.isDone
        }
        return result.get()
    }
}
