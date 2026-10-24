package app.thelasthit.nrt.agents

import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.agent.AgentLogAsk
import app.thelasthit.nrt.domain.agent.AgentRefusal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Before set-up there are no products, so every function says to finish it first. */
@RunWith(RobolectricTestRunner::class)
class NrtAgentBeforeSetUpTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val agent by lazy { NrtAgent(app.graph, tagNames = { listOfNotNull(it.customName) }) }

    @Test
    fun everyFunctionWaitsForSetUp() = runBlocking {
        val notSetUp = AgentRefusal.NotSetUp
        assertEquals(AgentLogOutcome.Refused(notSetUp), agent.log(AgentLogAsk("2 mg gum")))
        assertEquals(AgentAnswer.Refused(notSetUp), agent.products())
        assertEquals(AgentAnswer.Refused(notSetUp), agent.summary())
        assertEquals(AgentUndoOutcome.Refused(notSetUp), agent.undoLast())
        assertEquals(AgentAnswer.Refused(notSetUp), agent.advice(null))
        val text = AgentText(app.resources, is24Hour = true, tagName = { it })
        assertEquals("The Last Hit isn't set up yet. Open the app to finish set-up first.", text.refusal(notSetUp, logging = true))
    }
}
