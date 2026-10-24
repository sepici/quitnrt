package app.thelasthit.nrt.widget

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/** The quick tag picker the widget opens: it logs from the widget, then saves the tags picked. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class QuickTagsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private var activity: ActivityController<QuickTagsActivity>? = null
    private val today = LocalDate.parse("2026-10-23")
    private val nbsp = Char(0xA0)

    @Before
    fun setUpApp() {
        app.setUp()
    }

    @After
    fun close() {
        activity?.pause()?.stop()?.destroy()
    }

    private fun open(key: String, value: String) {
        val intent = Intent(app, QuickTagsActivity::class.java).putExtra(key, value)
        activity = Robolectric.buildActivity(QuickTagsActivity::class.java, intent).setup()
    }

    /** Reads the database, so only call it once the screen shows the change. */
    private fun entriesToday(): List<Entry> = runBlocking { app.graph.entries.observeDay(today).first().entries }

    @Test
    fun aTapLogsFromTheWidgetThenSavesTheTagsPicked() {
        open(WidgetKeys.PRODUCT.name, GUM_2)
        compose.waitUntilExactlyOneExists(hasText("2${nbsp}mg of Nicorette gum at 15:05"), timeoutMillis = 5_000)
        assertEquals(EntrySource.WIDGET, entriesToday().single().source)

        compose.onNodeWithText("Stressed").performClick()
        compose.onNodeWithText("Save tags").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            activity?.get()?.isFinishing == true
        }
        val entry = entriesToday().single()
        assertEquals(setOf("stressed"), entry.tagKeys)
        assertEquals(EntrySource.WIDGET, entry.source)
    }

    @Test
    fun tagsFromTheLoggedMomentEditTheDoseAlreadyLogged() {
        val entry = runBlocking { app.graph.entries.log(LogRequest(GUM_2, source = EntrySource.WIDGET)) }
        val id = (entry as app.thelasthit.nrt.data.repo.LogResult.Saved).entry.id
        open(WidgetKeys.ENTRY.name, id)
        compose.waitUntilExactlyOneExists(hasText("Add tags"), timeoutMillis = 5_000)
        compose.onNodeWithText("Anxious").performClick()
        compose.onNodeWithText("Save tags").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            activity?.get()?.isFinishing == true
        }
        assertEquals(setOf("anxious"), entriesToday().single().tagKeys)
    }

    @Test
    fun undoTakesTheDoseBack() {
        open(WidgetKeys.PRODUCT.name, GUM_2)
        compose.waitUntilExactlyOneExists(hasText("Undo this dose") and hasClickAction(), timeoutMillis = 5_000)
        compose.onNodeWithText("Undo this dose").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            activity?.get()?.isFinishing == true
        }
        assertTrue(entriesToday().isEmpty())
    }

    @Test
    fun aDuplicateAsksFirst() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2)) }
        open(WidgetKeys.PRODUCT.name, GUM_2)
        compose.waitUntilExactlyOneExists(hasText("Logged twice in a minute. Keep both?"), timeoutMillis = 5_000)
        compose.onNodeWithText("Keep both").performClick()
        compose.waitUntilExactlyOneExists(hasText("Add tags"), timeoutMillis = 5_000)
        assertEquals(listOf(EntrySource.APP, EntrySource.WIDGET), entriesToday().map { it.source }.sorted())
    }
}
