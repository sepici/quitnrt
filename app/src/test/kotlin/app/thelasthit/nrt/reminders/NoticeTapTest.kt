package app.thelasthit.nrt.reminders

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import app.thelasthit.nrt.MainActivity
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Tapping a notice, on a cold start or into the open app: a weekly result opens the Plan tab, a badge the lair's shelf. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class NoticeTapTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private var activity: ActivityController<MainActivity>? = null

    @Before
    fun setUpApp() {
        app.setUp()
        app.allowNotifications()
    }

    @After
    fun close() {
        activity?.pause()?.stop()?.destroy()
        app.appScope.cancel()
    }

    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntilExactlyOneExists(matcher, timeoutMillis = 5_000)

    private fun waitFor(text: String) = waitFor(hasText(text))

    /** Week 1 missed, 180 mg against 168, with its result in on Sunday morning and told of. */
    private fun missWeekOne(): Intent {
        app.startGumPlan()
        app.setTime("2026-10-26T12:00")
        app.chew("2026-10-26T09:00", pieces = 45)
        app.setTime("2026-11-01T09:00")
        runBlocking { TestListenableWorkerBuilder<ResultWorker>(app).build().doWork() }
        val notice = app.notification(Notice.ID_RESULTS + 1)
        assertNotNull(notice)
        return notice!!.tapIntent()
    }

    /** Starts the app from cold with [intent], as tapping a notice does when it isn't running. */
    private fun launch(intent: Intent) {
        activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    /** Starts the app from its icon, then sends [intent] to it, as tapping a notice does while it's open. */
    private fun sendToOpenApp(intent: Intent) {
        val running = Robolectric.buildActivity(MainActivity::class.java).setup().also { activity = it }
        waitFor(hasContentDescription("Log a dose"))
        running.newIntent(intent)
    }

    @Test
    fun `a missed week's result opens the Plan tab, where its choice waits`() {
        launch(missWeekOne())
        waitFor("Missed this one")
    }

    @Test
    fun `a result tapped while the app is open goes to the Plan tab too`() {
        sendToOpenApp(missWeekOne())
        waitFor("Missed this one")
    }

    @Test
    fun `a badge opens the lair at its trophy shelf`() {
        runBlocking { app.reminders.achievementsUnlocked(listOf("First entry"), emptyList()) }
        launch(app.achievementNotices().single().tapIntent())

        waitFor("Trophy shelf")
        compose.waitUntil(5_000) {
            val heading = compose.onNode(hasText("Trophy shelf")).fetchSemanticsNode().boundsInRoot
            val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
            heading.top >= 0f && heading.bottom < screen.height / 2
        }
    }
}
