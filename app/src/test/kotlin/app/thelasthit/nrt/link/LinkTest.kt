package app.thelasthit.nrt.link

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.MainActivity
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.matcher.LogLinkError
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.link.linkErrorText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `lasthit://log` links, opened the way Android opens them: on a cold start, or into the open app. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class LinkTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private var activity: ActivityController<MainActivity>? = null
    private val today = LocalDate.parse("2026-10-23")

    /** The sheet's "Log" button. Today's Log pill behind it is read as "Log a dose". */
    private val logButton = hasText("Log") and hasClickAction()

    @Before
    fun setUpApp() {
        app.setUp()
    }

    @After
    fun close() {
        activity?.pause()?.stop()?.destroy()
    }

    private fun linkIntent(link: String, shortcut: Boolean = false): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link), app, MainActivity::class.java).apply {
            if (shortcut) putExtra(LogLinks.EXTRA_SOURCE, LogLinks.SOURCE_SHORTCUT)
        }

    /** Starts the app from cold with [intent], as a link from Tasker or a shortcut does. */
    private fun launch(intent: Intent) {
        activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    /** Starts the app from its icon, then sends [intent] to it, as a link that arrives while it's open. */
    private fun sendToOpenApp(intent: Intent) {
        val running = activity ?: Robolectric.buildActivity(MainActivity::class.java).setup().also {
            activity = it
            waitFor(hasContentDescription("Log a dose"))
        }
        running.newIntent(intent)
    }

    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntilExactlyOneExists(matcher, timeoutMillis = 5_000)

    private fun waitFor(text: String) = waitFor(hasText(text))

    /** Reads the database, so only call it once the screen shows the change (see TodayTest). */
    private fun entriesToday(): List<Entry> = runBlocking { app.graph.entries.observeDay(today).first().entries }

    @Test
    fun `a link logs one dose with its source, and Undo takes it back`() {
        launch(linkIntent("lasthit://log?product=nicorette-gum-uk-2mg&tags=stressed,after-meal"))

        waitFor("Logged 2 mg of Nicorette gum")
        val entry = entriesToday().single()
        assertEquals(GUM_2, entry.productId)
        assertEquals(1, entry.quantity)
        assertEquals(TestApp.NOW, entry.occurredAt)
        assertEquals(setOf("stressed", "after_meal"), entry.tagKeys)
        assertEquals(EntrySource.DEEP_LINK, entry.source)

        compose.onNodeWithText("Undo").performClick()
        waitFor("Nothing logged yet today")
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun `a link that arrives while the app is open logs too`() {
        sendToOpenApp(linkIntent("lasthit://log?product=two-4mg-gums&at=2026-10-23T14:50"))

        waitFor("Logged 8 mg of Nicorette gum")
        val entry = entriesToday().single()
        assertEquals(GUM_4, entry.productId)
        assertEquals(2, entry.quantity)
        assertEquals(TestApp.london("2026-10-23T14:50"), entry.occurredAt)
        assertEquals(EntrySource.DEEP_LINK, entry.source)
    }

    @Test
    fun `a duplicate asks first`() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2)) }
        launch(linkIntent("lasthit://log?product=nicorette-gum-uk-2mg"))

        waitFor("Logged twice in a minute. Keep both?")
        assertEquals(1, entriesToday().size)
        compose.onNodeWithText("Keep both").performClick()

        waitFor("Logged 2 mg of Nicorette gum")
        assertEquals(listOf(EntrySource.APP, EntrySource.DEEP_LINK), entriesToday().map { it.source }.sorted())
    }

    @Test
    fun `a duplicate can be discarded`() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2)) }
        sendToOpenApp(linkIntent("lasthit://log?product=nicorette-gum-uk-2mg"))

        waitFor("Logged twice in a minute. Keep both?")
        compose.onNodeWithText("Discard this one").performClick()

        compose.waitUntilDoesNotExist(hasText("Logged twice in a minute. Keep both?"), timeoutMillis = 5_000)
        assertEquals(EntrySource.APP, entriesToday().single().source)
    }

    @Test
    fun `a product that could be several opens the sheet on those`() {
        launch(linkIntent("lasthit://log?product=nicorette+gum&qty=2&tags=stressed"))

        waitFor("The link fits more than one of your products. Pick the one you meant.")
        compose.onNodeWithText("2 mg").assertExists()
        compose.onNodeWithText("4 mg").assertExists()
        // QuickMist doesn't fit, so it waits behind More.
        compose.onNodeWithText("1 mg").assertDoesNotExist()
        compose.onNode(logButton).assertIsNotEnabled()

        compose.onNodeWithText("4 mg").performClick()
        waitFor("8 mg at 15:05")
        compose.onNode(logButton).performClick()

        waitFor("Logged 8 mg of Nicorette gum")
        val entry = entriesToday().single()
        assertEquals(GUM_4, entry.productId)
        assertEquals(2, entry.quantity)
        assertEquals(setOf("stressed"), entry.tagKeys)
        assertEquals(EntrySource.DEEP_LINK, entry.source)
    }

    @Test
    fun `the Log a dose shortcut opens the sheet, and the dose says where it came from`() {
        launch(linkIntent("lasthit://log", shortcut = true))

        waitFor("Log a dose")
        waitFor("2 mg at 15:05")
        compose.onNode(logButton).performClick()

        waitFor("Logged 2 mg of Nicorette gum")
        assertEquals(EntrySource.SHORTCUT, entriesToday().single().source)
    }

    @Test
    fun `an error shows its message and logs nothing`() {
        launch(linkIntent("lasthit://log?product=nicorette-gum-6mg"))

        waitFor("None of your products in use matches “nicorette-gum-6mg”.")
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun `an error in a link to the open app shows its message too`() {
        sendToOpenApp(linkIntent("lasthit://log?product=nicorette-gum-uk-2mg&tags=stressed,vaping"))

        waitFor("None of your tags matches “vaping”.")
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun `every error has its own plain message`() {
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
        val messages = listOf(
            LogLinkError.NotALogLink to "That isn't a log link. Log links start lasthit://log.",
            LogLinkError.BadEncoding to "Part of the link couldn't be read. Check how it's encoded.",
            LogLinkError.UnknownParameter("quantity") to "Links don't use “quantity”. They take product, qty, at and tags.",
            LogLinkError.RepeatedParameter("qty") to "The link gives “qty” twice. Give it once.",
            LogLinkError.MissingProduct to "The link doesn't say which product to log.",
            LogLinkError.UnknownProduct("gum-6") to "None of your products in use matches “gum-6”.",
            LogLinkError.BadQuantity("0") to "The link's qty needs to be a whole number from 1 to 99.",
            LogLinkError.ConflictingQuantity(inProduct = 2, qty = 3) to "The link says 2 in the product and 3 in qty. Give the number once.",
            LogLinkError.TooMany(104) to "That would be 104 at once, and a link logs up to 99.",
            LogLinkError.BadTime("yesterday") to "The link's time needs to look like 2026-10-23T15:00.",
            LogLinkError.TimeInFuture(LocalDateTime.parse("2026-10-24T09:00")) to
                "The link's time is Sat 24 Oct at 09:00, which hasn't come yet.",
            LogLinkError.UnknownTags(listOf("vaping")) to "None of your tags matches “vaping”.",
            LogLinkError.UnknownTags(listOf("vaping", "after meals")) to "None of your tags matches these: vaping, after meals.",
            LogLinkError.AmbiguousTags(mapOf("Gym" to listOf("custom_a", "custom_b"))) to
                "More than one of your tags is called “Gym”. Rename one in Settings.",
        )
        messages.forEach { (error, message) -> assertEquals(message, app.resources.linkErrorText(error, time)) }
    }

    @Test
    fun `only lasthit links are taken, and not again from Recents`() {
        assertEquals(IncomingLink("lasthit://log?product=gum-2", EntrySource.DEEP_LINK), LogLinks.from(linkIntent("lasthit://log?product=gum-2")))
        assertEquals(EntrySource.SHORTCUT, LogLinks.from(linkIntent("lasthit://log", shortcut = true))?.source)
        assertNull(LogLinks.from(Intent(Intent.ACTION_MAIN)))
        assertNull(LogLinks.from(linkIntent("https://thelasthit.app/log?product=gum-2")))
        assertNull(LogLinks.from(linkIntent("lasthit://log?product=gum-2").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
    }
}
