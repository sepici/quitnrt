package app.thelasthit.nrt.agents

import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.PATCH_14
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.agent.AgentAdviceAnswer
import app.thelasthit.nrt.domain.agent.AgentLogAsk
import app.thelasthit.nrt.domain.agent.AgentRefusal
import app.thelasthit.nrt.domain.agent.PlanStage
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.format.builtInTagName
import app.thelasthit.nrt.ui.format.mgText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the agent functions do, through [NrtAgent] on the app's in-memory data, and the words
 * [AgentText] gives back. The AppFunctions library's side is in NrtAppFunctionsTest.
 */
@RunWith(RobolectricTestRunner::class)
class NrtAgentTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.parse("2026-10-23")

    private val agent by lazy {
        NrtAgent(app.graph, tagNames = { tag -> listOfNotNull(tag.customName, app.resources.builtInTagName(tag.key)) })
    }

    private fun text(): AgentText = runBlocking {
        val tags = app.graph.tags.all.first().associate { it.key to (it.customName ?: app.resources.builtInTagName(it.key) ?: it.key) }
        val names = app.graph.products.shortNames.first()
        AgentText(
            app.resources,
            is24Hour = true,
            tagName = { tags[it] ?: it },
            shortName = { names[it.productId] ?: it.productName },
            now = app.clock::instant,
        )
    }

    @Before
    fun setUpApp() {
        app.setUp()
    }

    private fun log(ask: AgentLogAsk, keepDuplicate: Boolean = false) = runBlocking { agent.log(ask, keepDuplicate) }

    private fun failWith(message: String): Nothing = throw AssertionError(message)

    private fun logged(ask: AgentLogAsk, keepDuplicate: Boolean = false): AgentLogOutcome.Logged {
        val outcome = log(ask, keepDuplicate)
        return outcome as? AgentLogOutcome.Logged ?: failWith("not logged: $outcome")
    }

    private fun refused(ask: AgentLogAsk): AgentRefusal {
        val outcome = log(ask)
        return (outcome as? AgentLogOutcome.Refused)?.reason ?: failWith("not refused: $outcome")
    }

    private fun entriesToday() = runBlocking { app.graph.entries.observeDay(today).first().entries }

    @Test
    fun aDoseIsLoggedThroughTheNormalPathAsAnAppFunction() {
        val outcome = logged(AgentLogAsk("a 2 mg nicotine gum", tags = listOf("Stressed"), cravingStrength = 4))

        val entry = outcome.entry
        assertEquals(GUM_2, entry.productId)
        assertEquals(EntrySource.APP_FUNCTION, entry.source)
        assertEquals(setOf("stressed"), entry.tagKeys)
        assertEquals(4, entry.cravingStrength)
        assertEquals(TestApp.NOW, entry.occurredAt)
        assertEquals(listOf(entry), entriesToday())
        assertEquals(
            "Logged 2\u00A0mg of Nicorette gum at 15:05. 2\u00A0mg today so far, from 1 dose. This week so far: 2\u00A0mg. " +
                "Last dose at 15:05.",
            text().logged(outcome),
        )
    }

    @Test
    fun aDoseAtAnEarlierTimeSaysItsDay() {
        val outcome = logged(AgentLogAsk("two 4 mg gums", time = LocalDateTime.parse("2026-10-22T20:30")))
        assertEquals(london("2026-10-22T20:30"), outcome.entry.occurredAt)
        assertEquals(2, outcome.entry.quantity)
        assertTrue(text().logged(outcome).startsWith("Logged 8\u00A0mg of Nicorette gum on Thu 22 Oct at 20:30. Nothing logged today so far."))
    }

    @Test
    fun aRenamedTagIsFoundByItsNewName() {
        runBlocking {
            val bored = app.graph.tags.all.first().single { it.key == "bored" }
            app.graph.tags.rename(bored, "Restless")
        }
        assertEquals(setOf("bored"), logged(AgentLogAsk("QuickMist", tags = listOf("restless"))).entry.tagKeys)
    }

    @Test
    fun theSameProductLoggedAMinuteBeforeFromTheAppIsAPossibleDuplicate() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, at = TestApp.NOW.minusSeconds(40))) }

        val outcome = log(AgentLogAsk("2mg gum")) as? AgentLogOutcome.PossibleDuplicate ?: failWith("no duplicate question")
        assertEquals(EntrySource.APP, outcome.existing.source)
        assertEquals(1, entriesToday().size)
        assertEquals(
            "Nicorette gum 2\u00A0mg was logged at 15:04, within a minute of this one. Nothing's saved yet. Keep both?",
            text().possibleDuplicate(outcome),
        )

        val kept = logged(AgentLogAsk("2mg gum"), keepDuplicate = true)
        assertEquals(EntrySource.APP_FUNCTION, kept.entry.source)
        assertEquals(2, entriesToday().size)
    }

    @Test
    fun anAmbiguousProductListsItsCandidatesAndLogsNothing() {
        val outcome = log(AgentLogAsk("two nicorette gums")) as? AgentLogOutcome.Ambiguous ?: failWith("not ambiguous")
        assertEquals(listOf(GUM_2, GUM_4), outcome.choices.map { it.product.id })
        assertEquals(listOf(2, 2), outcome.choices.map { it.quantity })
        assertTrue(entriesToday().isEmpty())
        assertEquals(
            "“two nicorette gums” could be Nicorette gum 2\u00A0mg or Nicorette gum 4\u00A0mg. Nothing's saved yet. Which one?",
            text().ambiguous(outcome),
        )
        // The agent asks, then logs the one chosen as the KDoc says: by its ID, with its quantity.
        val chosen = outcome.choices.last().toCandidate(text())
        val entry = logged(AgentLogAsk(chosen.product.id, quantity = chosen.quantity)).entry
        assertEquals(GUM_4, entry.productId)
        assertEquals(Dose.mg(8), entry.total)
    }

    @Test
    fun aDoseAfterMidnightSaysWhichDayItCountsTowards() {
        // 01:20 on Friday, logged at 15:05 on Friday, counts towards Thursday.
        val outcome = logged(AgentLogAsk("2 mg gum", time = LocalDateTime.parse("2026-10-23T01:20")))
        assertEquals(LocalDate.parse("2026-10-22"), outcome.entry.logicalDate)
        assertEquals(
            "Logged 2\u00A0mg of Nicorette gum at 01:20, which counts towards Thursday. Nothing logged today so far. " +
                "This week so far: 2\u00A0mg. Last dose at 01:20, which counts towards Thursday.",
            text().logged(outcome),
        )

        // On a day before today, it names the day it was taken as well.
        app.clock.now = app.clock.now.plusSeconds(30)
        val earlier = logged(AgentLogAsk("4 mg gum", time = LocalDateTime.parse("2026-10-22T02:10")))
        assertTrue(
            text().logged(earlier).startsWith("Logged 4\u00A0mg of Nicorette gum on Thu 22 Oct at 02:10, which counts towards Wednesday."),
        )
        val undone = runBlocking { agent.undoLast() } as? AgentUndoOutcome.Undone ?: failWith("nothing undone")
        assertTrue(
            text().undone(undone).startsWith("Deleted 4\u00A0mg of Nicorette gum, logged on Thu 22 Oct at 02:10, which counted towards Wednesday."),
        )
        val first = runBlocking { agent.undoLast() } as? AgentUndoOutcome.Undone ?: failWith("nothing undone")
        assertTrue(text().undone(first).startsWith("Deleted 2\u00A0mg of Nicorette gum, logged at 01:20, which counted towards Thursday."))
    }

    @Test
    fun aDoseAfterMidnightBeforeTheDayStartsIsToday() {
        // At 02:00 on Saturday it's still Friday, so 01:00 and 23:30 are both today.
        app.clock.now = london("2026-10-24T02:00")
        val late = logged(AgentLogAsk("2 mg gum", time = LocalDateTime.parse("2026-10-24T01:00")))
        assertTrue(text().logged(late).startsWith("Logged 2\u00A0mg of Nicorette gum at 01:00. 2\u00A0mg today so far"))
        val evening = logged(AgentLogAsk("4 mg gum", time = LocalDateTime.parse("2026-10-23T23:30")))
        assertTrue(text().logged(evening).startsWith("Logged 4\u00A0mg of Nicorette gum on Fri 23 Oct at 23:30. 6\u00A0mg today so far"))
    }

    @Test
    fun aFutureTimeOrAnUnknownTagIsRefusedWithAClearMessage() {
        val future = refused(AgentLogAsk("2 mg gum", time = LocalDateTime.parse("2026-10-24T09:00")))
        assertEquals("Sat 24 Oct at 09:00 hasn't come yet. Nothing was logged.", text().refusal(future, logging = true))

        val unknown = refused(AgentLogAsk("2 mg gum", tags = listOf("Grumpy")))
        assertEquals("None of your tags matches “Grumpy”. Nothing was logged.", text().refusal(unknown, logging = true))

        val product = refused(AgentLogAsk("boots lozenge"))
        assertEquals("None of your products in use matches “boots lozenge”. Nothing was logged.", text().refusal(product, logging = true))

        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun productsComeQuickLogFirstWithoutHiddenOnes() {
        runBlocking { app.graph.products.setHidden(QUICKMIST, true) }
        val products = (runBlocking { agent.products() } as AgentAnswer.Given).value
        assertEquals(listOf(GUM_2, GUM_4, PATCH_14), products.map { it.id })
        assertEquals("Nicorette gum 2\u00A0mg", text().productLabel(products.first()))
    }

    @Test
    fun theSummaryWithNoPlan() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_4, at = london("2026-10-21T09:00"), quantity = 2)) }
        logged(AgentLogAsk("2 mg gum"))
        val summary = (runBlocking { agent.summary() } as AgentAnswer.Given).value
        assertEquals(PlanStage.NONE, summary.stage)
        assertNull(summary.dailyGuide)
        assertEquals(Dose.mg(10), summary.week.total)
        assertEquals(
            "2\u00A0mg today so far, from 1 dose. This week so far: 10\u00A0mg. Last dose at 15:05.",
            text().summary(summary),
        )
    }

    @Test
    fun theSummaryWhileAPlanRuns() {
        val plan = runBlocking {
            val gum4 = requireNotNull(app.graph.products.get(GUM_4))
            app.graph.programmes.keepUsualDay(UsualDay.typed(gum4 to 5))
            val built = requireNotNull(app.graph.programmes.buildPlan(startDate = today))
            app.graph.programmes.startPlan(built)
            built
        }
        logged(AgentLogAsk("4 mg gum", quantity = 2))

        val summary = (runBlocking { agent.summary() } as AgentAnswer.Given).value
        val first = plan.week(1)
        assertEquals(PlanStage.ACTIVE, summary.stage)
        assertEquals(1, summary.week.number)
        assertEquals(first.target.dailyGuide, summary.dailyGuide)
        assertEquals(first.target.weeklyAllowance, summary.week.allowance)
        assertEquals(plan.maxHp, summary.demon?.maxHp)

        val guide = first.target.dailyGuide.mgText()
        val allowance = first.target.weeklyAllowance.mgText()
        assertEquals(
            "8\u00A0mg today so far, within the daily guide of $guide\u00A0mg. Week 1 so far: 8\u00A0mg of its $allowance\u00A0mg allowance. " +
                "Last dose at 15:05. Gary has ${plan.maxHp} of ${plan.maxHp}\u00A0HP.",
            text().summary(summary),
        )
    }

    @Test
    fun undoTakesBackTheEntryWrittenLast() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_4, at = london("2026-10-23T14:00"))) }
        app.clock.now = app.clock.now.plusSeconds(30)
        // Written after, though taken earlier.
        val backdated = logged(AgentLogAsk("2 mg gum", time = LocalDateTime.parse("2026-10-23T10:00"))).entry

        val undone = runBlocking { agent.undoLast() } as? AgentUndoOutcome.Undone ?: failWith("nothing undone")
        assertEquals(backdated.id, undone.entry.id)
        assertEquals(listOf(GUM_4), entriesToday().map { it.productId })
        assertTrue(text().undone(undone).startsWith("Deleted 2\u00A0mg of Nicorette gum, logged at 10:00. 4\u00A0mg today so far, from 1 dose."))
    }

    @Test
    fun undoReachesBackOneDay() {
        logged(AgentLogAsk("2 mg gum"))
        app.clock.now = app.clock.now.plus(Duration.ofHours(25))
        val outcome = runBlocking { agent.undoLast() }
        assertEquals(AgentUndoOutcome.Refused(AgentRefusal.NothingToUndo), outcome)
        assertEquals(
            "Nothing's been logged in the last 24\u00A0hours, so there's nothing to undo.",
            text().refusal(AgentRefusal.NothingToUndo),
        )
    }

    @Test
    fun undoLeavesOutWhatARestoreBroughtBack() {
        runBlocking {
            app.graph.entries.log(LogRequest(GUM_2, at = london("2026-09-20T09:00")))
            app.graph.entries.log(LogRequest(GUM_4, at = london("2026-10-01T09:00")))
        }
        val backup = runBlocking { app.graph.backups.export() }
        app.clock.now = app.clock.now.plus(Duration.ofDays(2))
        for (mode in ImportMode.entries) {
            runBlocking { app.graph.backups.import(backup, mode) }
            // A restore writes every entry at once, but none of them was just logged.
            assertEquals(AgentUndoOutcome.Refused(AgentRefusal.NothingToUndo), runBlocking { agent.undoLast() })
        }

        app.clock.now = app.clock.now.plusSeconds(30)
        val after = logged(AgentLogAsk("2 mg gum")).entry
        val undone = runBlocking { agent.undoLast() } as? AgentUndoOutcome.Undone ?: failWith("nothing undone")
        assertEquals(after.id, undone.entry.id)
        assertEquals(AgentUndoOutcome.Refused(AgentRefusal.NothingToUndo), runBlocking { agent.undoLast() })
        assertEquals(2, runBlocking { app.graph.entries.observeRange(LocalDate.parse("2026-09-01"), today.plusDays(2)).first() }.size)
    }

    @Test
    fun adviceComesWithTheQuitlinesForTheUsersCountry() {
        val answer = (runBlocking { agent.advice("strong_craving") } as AgentAnswer.Given).value
        val advice = answer.toResponse(text())
        assertEquals(
            listOf("Smokefree National Helpline", "Quit Your Way Scotland", "Help Me Quit", "Local stop smoking services"),
            advice.quitlines.map { it.name },
        )
        assertEquals("England", advice.quitlines.first().region)
        assertEquals("0300 123 1044", advice.quitlines.first().phone)
        assertEquals("https://www.stopsmokingni.info/ways-quit/local-help-and-support", advice.quitlines.last().web)
        assertEquals("This is general information, not medical advice. For more help, call a free quitline.", advice.note)

        // Where the app has no quitlines, it says nothing about them.
        runBlocking { app.graph.settings.setCountry("FR") }
        val elsewhere = (runBlocking { agent.advice("strong_craving") } as AgentAnswer.Given).value.toResponse(text())
        assertTrue(elsewhere.quitlines.isEmpty())
        assertEquals("This is general information, not medical advice.", elsewhere.note)
    }

    @Test
    fun adviceForATagAskedAbout() {
        val answer = (runBlocking { agent.advice("Stressed") } as AgentAnswer.Given).value
        assertEquals("breathing_timer", answer.strategy.key)
        assertEquals(AgentAdviceAnswer.Reason.AskedTag("stressed"), answer.reason)
        assertEquals("For doses tagged “Stressed”.", text().adviceReason(answer))

        val unknown = (runBlocking { agent.advice("Grumpy") } as AgentAnswer.Refused).reason
        assertEquals(AgentRefusal.UnknownTags(listOf("Grumpy")), unknown)
    }

    @Test
    fun adviceForTheTopTrigger() {
        runBlocking {
            for (day in 10..21) {
                app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-${day}T11:00"), tagKeys = setOf("bored")))
            }
        }
        val answer = (runBlocking { agent.advice(null) } as AgentAnswer.Given).value
        assertEquals(AgentAdviceAnswer.Reason.ForTopTrigger::class, answer.reason::class)
        assertEquals("busy_hands", answer.strategy.key)
        assertEquals("Your top trigger in the last 14\u00A0days is “Bored”, on 100% of doses.", text().adviceReason(answer))
    }
}
