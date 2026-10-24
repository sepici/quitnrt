package app.thelasthit.nrt.ui.plan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.data.repo.StoredCounts
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.util.concurrent.CompletableFuture

/** The Plan tab's states and flows, from the first baseline to a rematch. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class PlanTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpApp() {
        app.setUpForPlan()
    }

    /**
     * [block]'s answer, read once the screen shows a change has landed. The read runs off the
     * main thread while the test keeps the main thread idling: a save the screen started may
     * still hold the programme's lock until it finishes there, so reading on the main thread
     * could wait for it forever.
     */
    private fun <T> read(block: suspend () -> T): T {
        val read = CompletableFuture.supplyAsync { runBlocking { block() } }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            read.isDone
        }
        return read.get()
    }

    /** The programme as stored. */
    private fun current(): ProgrammeRecord? = read { app.graph.programmes.current() }

    /** What's stored, with every programme, the current one and those closed. */
    private fun counts(): StoredCounts = read { app.graph.backups.counts() }

    /** Two taps on the control showing [text], too quick for the first to land before the second. */
    private fun doubleTap(text: String) {
        waitFor(text)
        val control = hasText(text) and hasClickAction()
        if (compose.onAllNodes(control and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(control).onFirst().performScrollTo()
        }
        compose.onAllNodes(control).onFirst().performTouchInput {
            click()
            click()
        }
    }

    private fun mixOf(record: ProgrammeRecord?): Map<String, Int> =
        checkNotNull(record?.usualDay).mix.items.associate { it.product.id to it.units }

    private fun waitForDescription(text: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(text, substring = true), timeoutMillis = 10_000)

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis = 10_000)

    /** Taps the first control showing [text], scrolling to it when it's in a scrolling page. */
    private fun click(text: String) {
        waitFor(text)
        val control = hasText(text) and hasClickAction()
        val scrolling = compose.onAllNodes(control and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()
        if (scrolling) compose.onAllNodes(control and hasAnyAncestor(hasScrollAction())).onFirst().performScrollTo()
        compose.onAllNodes(control).onFirst().performClick()
    }

    private fun openPlanTab() {
        compose.setContent { LastHitTheme { AppShell() } }
        compose.waitUntilAtLeastOneExists(planTab, timeoutMillis = 5_000)
        compose.onNode(planTab).performClick()
    }

    // Before a programme.

    @Test
    fun `with nothing logged, a 7-day baseline starts from today`() {
        openPlanTab()
        waitFor("Two ways to start")
        compose.onNodeWithText("Use your last 7 days").assertDoesNotExist()
        click("Start a 7-day baseline")

        waitFor("Baseline, day 1 of 7")
        waitFor("No full day yet.")
        val record = checkNotNull(current())
        assertEquals(ProgrammeStatus.BASELINE, record.status)
        assertEquals(java.time.LocalDate.parse("2026-10-23"), record.baselineStartedOn)
    }

    @Test
    fun `two quick taps start one baseline`() {
        openPlanTab()
        doubleTap("Start a 7-day baseline")
        waitFor("Baseline, day 1 of 7")
        assertEquals(1, counts().programmes)
        assertEquals(ProgrammeStatus.BASELINE, current()?.status)
    }

    /**
     * With the last 7 days enough, "Start a 7-day baseline" uses them at once, as "Use your last
     * 7 days" does, so a fresh week can't be measured. This pins that until the owner decides.
     */
    @Test
    fun `with the last 7 days enough, starting a baseline uses them at once`() {
        app.seedLastSevenDays()
        openPlanTab()
        waitFor("Use your last 7 days")
        click("Start a 7-day baseline")
        waitFor("Check your usual day")
        compose.onNodeWithText("Baseline, day 1 of 7").assertDoesNotExist()
        assertEquals(java.time.LocalDate.parse("2026-10-23"), current()?.baselineStartedOn)
    }

    @Test
    fun `a baseline as it builds shows the usual day so far and when it ends`() {
        app.seedBaselineBuilding()
        openPlanTab()
        waitFor("Baseline, day 4 of 7")
        compose.onNodeWithText("8\u00A0pieces of 4\u00A0mg gum and 3\u00A0sprays").assertExists()
        waitFor("The baseline runs to the end of Monday 26\u00A0October. On Tuesday")
        click("Say what a usual day is instead")
        waitFor("How many of each product you use on a normal day.")
    }

    @Test
    fun `the last 7 days can be the baseline at once, then the plan is built from them`() {
        app.seedLastSevenDays()
        openPlanTab()
        waitFor("6 of the 7 days have doses, so they can be your baseline.")
        click("Use your last 7 days")

        waitFor("From your logs, Friday 16 to Thursday 22\u00A0October.")
        waitFor("35.7\u00A0mg a day")
        compose.onNodeWithText("Why Sunday doesn't count").performScrollTo().assertExists()
        click("Use this usual day")

        waitFor("Build your plan")
        assertEquals(mapOf(GUM_4 to 8, QUICKMIST to 3), mixOf(current()))
        assertNotNull(current()?.baselineDays)
    }

    @Test
    fun `the measured usual day can be changed before it's used`() {
        runBlocking { app.graph.products.addFromCatalogue(GUM_2) }
        app.seedLastSevenDays()
        runBlocking { app.graph.programmes.startBaseline() }
        var kept = false
        compose.setContent { LastHitTheme { UsualDayScreen(UsualDayMode.MEASURED, onBack = {}, onKept = { kept = true }, onOpenCatalogue = {}) } }
        waitFor("8\u00A0pieces, 32\u00A0mg a day")

        compose.onAllNodes(hasContentDescription("One piece fewer")).onFirst().performClick()
        waitFor("7\u00A0pieces, 28\u00A0mg a day")
        // Another of the user's own products joins the day.
        click("Add a product")
        click("Nicorette gum 2\u00A0mg")
        waitFor("1\u00A0piece, 2\u00A0mg a day")
        click("Use this usual day")

        // Nothing on screen changes once it's kept, so this waits for the page to move on.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            kept
        }
        assertEquals(mapOf(GUM_4 to 7, QUICKMIST to 3, GUM_2 to 1), mixOf(current()))
    }

    @Test
    fun `a usual day can be typed in, product by product`() {
        openPlanTab()
        click("Say what a usual day is")
        waitFor("How many of each product you use on a normal day.")
        compose.onNodeWithText("Use this usual day").assertExists()
        val more = hasContentDescription("One piece more")
        repeat(6) { compose.onAllNodes(more).onFirst().performClick() }
        waitFor("6\u00A0pieces, 24\u00A0mg a day")
        click("Use this usual day")

        waitFor("Week by week")
        val record = checkNotNull(current())
        assertEquals(ProgrammeStatus.BASELINE, record.status)
        assertNull(record.baselineStartedOn)
        assertEquals(1, mixOf(record).size)
    }

    @Test
    fun `the plan is built from the usual day, then started`() {
        app.keepMockupUsualDay()
        openPlanTab()
        click("Build your plan")
        waitFor("Week by week")
        // The switch to 2 mg gum gets a week of its own, and the new product is called out.
        waitFor(
            "2\u00A0mg gum from week 3, Saturday 7\u00A0November. " +
                "Nicorette gum 2\u00A0mg is added to your products, so get some before then.",
        )
        compose.onNodeWithText("8 weeks").performClick()
        waitFor("8 weeks, Saturday 24\u00A0October to Friday 18\u00A0December")
        assertNull(read { app.graph.products.get(GUM_2) })
        click("Start the plan")

        waitFor("Starts tomorrow")
        val record = checkNotNull(current())
        assertEquals(ProgrammeStatus.ACTIVE, record.status)
        val plan = checkNotNull(record.plan)
        assertEquals(ProgrammeLength.WEEKS_8, plan.length)
        assertEquals(80, record.demon?.maxHp)
        // Starting the plan added the 2 mg gum, shown, so the weeks after the switch can log it.
        val gum = checkNotNull(read { app.graph.products.get(GUM_2) })
        assertFalse(gum.isHidden)
    }

    @Test
    fun `two quick taps on "Start the plan" start it once`() {
        app.keepMockupUsualDay()
        openPlanTab()
        click("Build your plan")
        waitFor("Week by week")
        doubleTap("Start the plan")

        waitFor("Starts tomorrow")
        assertEquals(ProgrammeStatus.ACTIVE, current()?.status)
        assertEquals(1, counts().programmes)
    }

    @Test
    fun `a week of the plan being built can be changed`() {
        app.keepMockupUsualDay()
        compose.setContent { LastHitTheme { BuildScreen(onBack = {}, onStarted = {}) } }
        waitFor("Week by week")
        compose.openWeek(4)
        waitFor("6\u00A0pieces of 2\u00A0mg gum and 3\u00A0sprays")
        compose.pickStep("1\u00A0piece of 2\u00A0mg gum and 3\u00A0sprays")
        waitFor("Weeks 5 and 6 come down to 1\u00A0piece of 2\u00A0mg gum and 3\u00A0sprays too.")
        click("Save weeks 4 to 6")

        compose.waitUntilAtLeastOneExists(hasContentDescription("Week 6, from Saturday 28\u00A0November: 1\u00A0piece of 2\u00A0mg gum", substring = true), timeoutMillis = 5_000)
    }

    // During a programme.

    @Test
    fun `this week shows the meter, the mix and the schedule, and a later week can be changed`() {
        app.seedWeekFour()
        openPlanTab()
        waitFor("Week 4 of 12")
        waitForDescription("45\u00A0mg left this week")
        waitFor("Meet this week and you get week 3's hit back too.")
        compose.onNodeWithContentDescription("Gary, 110 of 120 HP, 1 hit ready. Opens the lair.").performScrollTo().assertExists()
        compose.onNode(hasContentDescription("Week 1, 24 to 30\u00A0October", substring = true)).performScrollTo()
        compose.onNode(hasContentDescription("Caught up in week\u00A02", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Missed, back if week\u00A04 is met", substring = true)).assertExists()

        val week5 = hasContentDescription("Week 5, 21 to 27\u00A0November", substring = true)
        compose.onNode(week5).performScrollTo().performClick()
        waitFor("Daily mix")
        compose.pickStep("2\u00A0pieces of 2\u00A0mg gum and 3\u00A0sprays")
        click("Save week 5")
        waitForDescription("Week 5, 21 to 27\u00A0November: 2\u00A0pieces of 2\u00A0mg gum and 3\u00A0sprays")
        assertEquals(7_000L, current()?.plan?.week(5)?.target?.dailyGuide?.micrograms)
    }

    @Test
    fun `a week's result still to come says so`() {
        app.seedWeekFivePending()
        openPlanTab()
        waitFor("Week 4's result comes in after today, so late logs still count.")
        waitFor("Day 1 of 7, ending Friday 27\u00A0November.")
    }

    @Test
    fun `after a missed week, repeating it shows its HP cost before it's confirmed`() {
        app.seedWeekSixMissed()
        openPlanTab()
        waitFor("Missed this one")
        waitFor("Week 6 came to 53\u00A0mg, 4\u00A0mg over its 49\u00A0mg allowance.")
        // The option, and the button that confirms it.
        assertEquals(2, compose.onAllNodes(hasText("Carry on") and hasClickAction()).fetchSemanticsNodes().size)
        click("Repeat this week")
        waitFor("Gary gets 10 more HP, so there's still a hit for every week.")
        compose.onNodeWithContentDescription("90 of 130 HP after a repeat").assertExists()
        // The button names the choice.
        compose.onAllNodes(hasText("Repeat this week") and hasClickAction()).fetchSemanticsNodes().let { assertEquals(2, it.size) }
        compose.onAllNodes(hasText("Repeat this week") and hasClickAction())[1].performClick()

        waitForDescription("repeating week 6")
        assertEquals(MissedWeekChoice.REPEAT, current()?.choices?.get(6)?.choice)
        val plan = checkNotNull(current()?.plan)
        assertEquals(WeekKind.REPEAT, plan.week(7).kind)
        assertEquals(13, plan.weeks.size)
        assertEquals(130, current()?.demon?.maxHp)
    }

    @Test
    fun `closing the missed-week sheet counts as carrying on, which can still become a repeat today`() {
        app.seedWeekSixMissed()
        openPlanTab()
        waitFor("Missed this one")
        compose.onNodeWithContentDescription("Close, and carry on").performClick()

        waitFor("You carried on after week 6.")
        assertEquals(MissedWeekChoice.CARRY_ON, current()?.choices?.get(6)?.choice)
        compose.onNodeWithText("Missed this one").assertDoesNotExist()
        click("Repeat week 6 instead")
        waitFor("Missed this one")
        compose.onAllNodes(hasText("Repeat this week") and hasClickAction())[1].performClick()
        waitForDescription("repeating week 6")
        assertEquals(MissedWeekChoice.REPEAT, current()?.choices?.get(6)?.choice)
    }

    @Test
    fun `a missed final week runs on in overtime`() {
        app.seedOvertime()
        openPlanTab()
        waitFor("Overtime")
        waitFor("Week 12 went over, so the plan runs on")
        waitFor("Meet this one and it pays back week\u00A012.")
        assertEquals(ProgrammeStatus.OVERTIME, current()?.status)
        // In overtime the schedule starts at the last few weeks.
        click("Show all 13 weeks")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Week 1, 24 to 30\u00A0October", substring = true), timeoutMillis = 5_000)
    }

    @Test
    fun `the extra goals are stored as they're set, from this week on`() {
        app.seedWeekFour()
        compose.setContent { LastHitTheme { GoalsScreen(onBack = {}) } }
        waitFor("Each one starts off.")
        compose.onNode(hasText("First dose not before", substring = true) and isToggleable()).performScrollTo().performClick()
        waitFor("This week, week 4")
        compose.onNodeWithContentDescription("15 minutes later").performScrollTo().performClick()
        waitFor("09:15")
        assertEquals(LocalTime.of(9, 15), current()?.plan?.week(4)?.goals?.firstDoseNotBefore)
        assertEquals(LocalTime.of(9, 30), current()?.plan?.week(5)?.goals?.firstDoseNotBefore)
        assertNull(current()?.plan?.week(3)?.goals?.firstDoseNotBefore)

        compose.onNode(hasText("Most units a day", substring = true) and isToggleable()).performScrollTo().performClick()
        waitForDescription("Nicorette gum 2\u00A0mg: 6\u00A0pieces a day")
        assertEquals(mapOf(GUM_2 to 6, QUICKMIST to 3), current()?.plan?.week(4)?.goals?.maxUnitsPerDay)
        compose.onNode(hasText("Gap between doses", substring = true) and isToggleable()).performScrollTo().performClick()
        waitFor("1\u00A0h 30\u00A0min")
        assertEquals(90, current()?.plan?.week(4)?.goals?.minGapMinutes)
    }

    @Test
    fun `quick steps on a goal each count`() {
        app.seedWeekFour()
        compose.setContent { LastHitTheme { GoalsScreen(onBack = {}) } }
        waitFor("Each one starts off.")
        compose.onNode(hasText("Gap between doses", substring = true) and isToggleable()).performScrollTo().performClick()
        waitFor("1\u00A0h 30\u00A0min")
        // Two taps on "15 minutes longer" before the first has been stored.
        compose.onNodeWithContentDescription("15 minutes longer").performScrollTo().performTouchInput {
            click()
            click()
        }
        compose.waitUntilAtLeastOneExists(hasText("2\u00A0h"), timeoutMillis = 10_000)
        assertEquals(120, current()?.plan?.week(4)?.goals?.minGapMinutes)
    }

    // After it.

    @Test
    fun `the end of the programme starts a rematch with the same demon`() {
        app.seedDone()
        openPlanTab()
        waitFor("Programme done")
        waitFor("12, plus 1 of overtime")
        waitFor("After a meal, Just woke up")
        val before = checkNotNull(current())
        assertEquals(ProgrammeStatus.COMPLETE, before.status)
        // Tapped twice in quick succession, it still starts one programme.
        doubleTap("Gary, round two")

        // A new baseline starts today. The weeks of overtime had no doses, so it builds from here.
        waitFor("Baseline, day 1 of 7")
        val after = checkNotNull(current())
        assertEquals(ProgrammeStatus.BASELINE, after.status)
        assertFalse(after.id == before.id)
        assertEquals(2, counts().programmes)
        assertEquals("Gary", runBlocking { app.graph.settings.current() }.demonName)
    }

    @Test
    fun `a rematch can bring a new demon`() {
        app.seedDone()
        openPlanTab()
        click("A new demon")
        waitFor("It stands for the habit")
        compose.onNode(hasSetTextAction()).performTextReplacement("Mildred")
        doubleTap("Start a new programme")

        waitFor("Baseline, day 1 of 7")
        assertEquals("Mildred", runBlocking { app.graph.settings.current() }.demonName)
        assertEquals(ProgrammeStatus.BASELINE, current()?.status)
        assertEquals(2, counts().programmes)
    }
}
