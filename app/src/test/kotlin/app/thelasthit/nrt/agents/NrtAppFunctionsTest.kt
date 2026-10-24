package app.thelasthit.nrt.agents

import android.os.Build
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.testing.AppFunctionTestRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.PATCH_14
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.R
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.setUp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.time.LocalDate
import java.time.LocalDateTime
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The five functions as an agent calls them, through the AppFunctions library's test rule: the
 * generated service, its parameters and the shapes that come back. What they do is in NrtAgentTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.BAKLAVA])
class NrtAppFunctionsTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.parse("2026-10-23")

    @get:Rule
    val appFunctions = AppFunctionTestRule(app)

    private val manager by lazy { appFunctions.getAppFunctionManager() }

    @Before
    fun setUpApp() {
        app.setUp()
    }

    private fun all(): List<AppFunctionMetadata> = runBlocking {
        manager.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(app.packageName)))
    }

    private fun call(name: String, parameters: AppFunctionData.Builder.() -> Unit = {}): ExecuteAppFunctionResponse = runBlocking {
        val function = all().single { it.id == "$ID#$name" }
        val data = AppFunctionData.Builder(function.parameters, function.components).apply(parameters).build()
        manager.executeAppFunction(ExecuteAppFunctionRequest(app.packageName, function.id, data))
    }

    private fun result(response: ExecuteAppFunctionResponse): AppFunctionData {
        val success = response as? ExecuteAppFunctionResponse.Success
            ?: throw AssertionError("failed: ${(response as ExecuteAppFunctionResponse.Error).error}")
        return requireNotNull(success.returnValue.getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE))
    }

    private fun error(response: ExecuteAppFunctionResponse) =
        (response as? ExecuteAppFunctionResponse.Error)?.error ?: throw AssertionError("succeeded: $response")

    private fun entriesToday() = runBlocking { app.graph.entries.observeDay(today).first().entries }

    @Test
    fun theFiveFunctionsAreThereWithTheirDescriptions() {
        val functions = all()
        assertEquals(
            setOf("logNrtUsage", "listNrtProducts", "getNrtSummary", "undoLastNrtEntry", "getNrtAdvice").map { "$ID#$it" }.toSet(),
            functions.map { it.id }.toSet(),
        )
        val log = functions.single { it.id.endsWith("#logNrtUsage") }
        assertEquals(
            listOf("productDescription", "quantity", "time", "tags", "cravingStrength", "keepDuplicate"),
            log.parameters.map { it.name },
        )
        assertEquals(listOf("productDescription"), log.parameters.filter { it.isRequired }.map { it.name })
    }

    @Test
    fun theFunctionListTheSystemIndexesDescribesEveryFunctionAndParameter() {
        // The compiler writes it as assets/nrt_app_functions.xml, from the KDoc.
        val xml = requireNotNull(javaClass.classLoader?.getResourceAsStream("assets/nrt_app_functions.xml")) { "no function list" }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val functions = document.documentElement.childElements("appfunction")
        assertEquals(5, functions.size)
        for (function in functions) {
            val id = function.childElements("id").single().textContent
            assertTrue("$id has no description", function.childElements("description").single().textContent.isNotBlank())
            for (parameter in function.childElements("parameters")) {
                val name = parameter.childElements("name").single().textContent
                assertTrue("$id: $name has no description", parameter.childElements("description").singleOrNull()?.textContent.orEmpty().isNotBlank())
            }
            assertTrue("$id: no description of what it returns", function.childElements("response").single().childElements("description").single().textContent.isNotBlank())
        }
    }

    @Test
    fun theAppDescribesItsFunctionsToAgentsAndTheUser() {
        val parser = app.resources.getXml(R.xml.app_functions_metadata)
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()
        assertEquals("AppFunctionAppMetadata", parser.name)
        val namespace = "http://schemas.android.com/apk/res-auto"
        assertTrue(parser.getAttributeValue(namespace, "instructions").contains("never suggest taking more nicotine"))
        val display = parser.getAttributeResourceValue(namespace, "displayDescription", 0)
        assertEquals(app.getString(R.string.agents_display_description), app.getString(display))
    }

    private fun Element.childElements(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    @Test
    fun logNrtUsageLogsAsAnAppFunction() {
        val result = result(
            call("logNrtUsage") {
                setString("productDescription", "two 4 mg gums")
                setStringList("tags", listOf("Stressed"))
                setAppFunctionData("time", AppFunctionData.serialize(LocalDateTime.parse("2026-10-23T14:30"), LocalDateTime::class.java))
            },
        )
        assertTrue(result.getBoolean("saved"))
        assertTrue(result.getString("message")!!.startsWith("Logged 8\u00A0mg of Nicorette gum at 14:30."))
        val entry = requireNotNull(result.getAppFunctionData("entry"))
        assertEquals(GUM_4, entry.getString("productId"))
        assertEquals(2, entry.getInt("quantity"))
        assertEquals(8000L, entry.getLong("totalMicrograms"))
        assertEquals(listOf("stressed"), entry.getStringList("tags"))
        assertEquals(8000L, result.getAppFunctionData("summary")!!.getLong("todayMicrograms"))

        val saved = entriesToday().single()
        assertEquals(EntrySource.APP_FUNCTION, saved.source)
        assertEquals(TestApp.london("2026-10-23T14:30"), saved.occurredAt)
    }

    private fun AppFunctionData.product(): AppFunctionData = requireNotNull(getAppFunctionData("product"))

    /** The call the KDoc asks for once the user picks a candidate: its product's ID and its quantity. */
    private fun logCandidate(candidate: AppFunctionData): AppFunctionData = result(
        call("logNrtUsage") {
            setString("productDescription", requireNotNull(candidate.product().getString("id")))
            setInt("quantity", candidate.getInt("quantity"))
        },
    )

    @Test
    fun anAmbiguousProductReturnsItsCandidates() {
        val result = result(call("logNrtUsage") { setString("productDescription", "nicorette gum") })
        assertFalse(result.getBoolean("saved"))
        val candidates = result.getAppFunctionDataList("candidates")!!
        assertEquals(listOf(GUM_2, GUM_4), candidates.map { it.product().getString("id") })
        assertEquals(listOf(1, 1), candidates.map { it.getInt("quantity") })
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun aCandidateLoggedAsTheKdocSaysKeepsTheCountFromTheDescription() {
        val asked = result(call("logNrtUsage") { setString("productDescription", "two nicorette gums") })
        val candidates = asked.getAppFunctionDataList("candidates")!!
        assertEquals(listOf(GUM_2, GUM_4), candidates.map { it.product().getString("id") })
        assertEquals(listOf(2, 2), candidates.map { it.getInt("quantity") })

        val entry = requireNotNull(logCandidate(candidates.last()).getAppFunctionData("entry"))
        assertEquals(GUM_4, entry.getString("productId"))
        assertEquals(2, entry.getInt("quantity"))
        assertEquals(8000L, entry.getLong("totalMicrograms"))
    }

    @Test
    fun aCandidateLoggedAsTheKdocSaysKeepsTheInhalatorsUnit() {
        runBlocking {
            app.graph.products.addFromCatalogue(INHALATOR_10)
            app.graph.products.addFromCatalogue(INHALATOR_15)
        }
        val asked = result(call("logNrtUsage") { setString("productDescription", "inhalator cartridge") })
        val candidates = asked.getAppFunctionDataList("candidates")!!
        assertEquals(listOf(INHALATOR_10, INHALATOR_15), candidates.map { it.product().getString("id") })
        assertEquals(listOf("session", "session"), candidates.map { it.product().getString("unit") })
        // A cartridge is 8 sessions on an inhalator logged by session.
        assertEquals(listOf(8, 8), candidates.map { it.getInt("quantity") })

        val entry = requireNotNull(logCandidate(candidates.last()).getAppFunctionData("entry"))
        assertEquals(INHALATOR_15, entry.getString("productId"))
        assertEquals(8, entry.getInt("quantity"))
        assertEquals(15000L, entry.getLong("totalMicrograms"))
    }

    @Test
    fun anUnknownTagIsRefusedWithTheAppsWords() {
        val error = error(
            call("logNrtUsage") {
                setString("productDescription", "2 mg gum")
                setStringList("tags", listOf("Grumpy"))
            },
        )
        assertTrue(error is AppFunctionInvalidArgumentException)
        assertEquals("None of your tags matches “Grumpy”. Nothing was logged.", error.errorMessage)
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun theOtherFourAnswer() {
        result(call("logNrtUsage") { setString("productDescription", "2 mg gum") })

        val products = (call("listNrtProducts") as ExecuteAppFunctionResponse.Success).returnValue
            .getAppFunctionDataList(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)!!
        assertEquals(listOf(GUM_2, GUM_4, QUICKMIST, PATCH_14), products.map { it.getString("id") })
        assertEquals("Nicorette gum 2\u00A0mg", products.first().getString("label"))
        assertEquals("gum", products.first().getString("form"))

        val summary = result(call("getNrtSummary"))
        assertEquals(2000L, summary.getLong("todayMicrograms"))
        assertEquals("none", summary.getString("plan"))

        val advice = result(call("getNrtAdvice") { setString("tag", "Bored") })
        assertEquals("busy_hands", advice.getString("key"))
        assertEquals("bored", advice.getString("tag"))
        val quitlines = advice.getAppFunctionDataList("quitlines")!!
        assertEquals(listOf("England", "Scotland", "Wales", "Northern Ireland"), quitlines.map { it.getString("region") })
        assertEquals("0300 123 1044", quitlines.first().getString("phone"))
        assertTrue(advice.getString("note")!!.endsWith("For more help, call a free quitline."))

        val undone = result(call("undoLastNrtEntry"))
        assertEquals(GUM_2, undone.getAppFunctionData("deleted")!!.getString("productId"))
        assertTrue(entriesToday().isEmpty())
        assertTrue(error(call("undoLastNrtEntry")) is AppFunctionElementNotFoundException)
    }

    private companion object {
        const val ID = "app.thelasthit.nrt.agents.NrtAppFunctions"

        /** Two strengths of one inhalator, both logged by session, which "inhalator cartridge" can't tell apart. */
        const val INHALATOR_10 = "generic-inhalator-10mg"
        const val INHALATOR_15 = "generic-inhalator-15mg"
    }
}
