package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class InsightsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun show() {
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    InsightsScreen(snackbars = snackbars)
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    private fun waitFor(text: String) = compose.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    private fun waitForDescription(text: String) =
        compose.waitUntilExactlyOneExists(hasContentDescription(text, substring = true), timeoutMillis = 5_000)

    @Test
    fun `nothing logged yet says where the charts will come from`() {
        app.setUp()
        show()
        waitFor("Nothing logged yet")
        compose.onNodeWithText("Log a dose").assertExists()
    }

    @Test
    fun `a first week has only 7 days, and says when 30 opens and when the average starts`() {
        app.setUp()
        app.seedWeek()
        show()
        waitFor("30 days opens on 26 October, after a week of logging.")
        compose.onNode(hasText("7 days") and isSelectable()).assertIsSelected()
        compose.onNodeWithContentDescription("30 days, opens on 26 October").assertIsNotEnabled()
        // Monday to Thursday: 14, 13, 12 and 14 mg. Today's 8 mg is still partial.
        compose.onNodeWithText("13.3").assertExists()
        compose.onNodeWithText("Average of 4 full days. Today counts once it's over.").assertExists()
        compose.onNodeWithText("The 7-day average line starts on 26 October.").performScrollTo().assertExists()
    }

    @Test
    fun `with two months logged, 30 days is the default, 90 opens tomorrow, and there's a change`() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        show()
        waitFor("90 days opens tomorrow, after 60 days of logging.")
        compose.onNode(hasText("30 days") and isSelectable()).assertIsSelected()
        compose.waitUntilExactlyOneExists(hasText("than the 30 days before", substring = true) or hasText("The same as the 30 days before"))
    }

    @Test
    fun `a kind of product narrows the numbers`() {
        app.setUp()
        app.seedWeek()
        show()
        waitFor("13.3")
        compose.onNodeWithText("Patch").performClick()
        // Patches on Monday and Thursday: 28 mg over 4 full days.
        waitFor("7")
        compose.onNode(hasText("Patch") and isSelectable()).assertIsSelected()
    }

    @Test
    fun `the table lists every day`() {
        app.setUp()
        app.seedWeek()
        show()
        compose.waitUntilExactlyOneExists(hasContentDescription("Show each day as a table"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Show each day as a table").performClick()
        waitFor("Thursday 22 October")
        compose.onNodeWithText("8 mg so far").assertExists()
        compose.onNodeWithContentDescription("Show each day as a chart").assertExists()
    }

    @Test
    fun `the hour map names its busiest hour, and its table lists every hour with a dose`() {
        app.setUp()
        app.seedWeek()
        show()
        // Wednesday's three pieces at 08:15 are one dose, but the most mg of the 08:00 doses.
        waitForDescription("Most often Wednesdays from 08:00, 1 dose.")
        compose.onNodeWithText("Doses by hour and day of the week. Patches aren't counted.").assertExists()
        compose.onNodeWithContentDescription("Show when you use as a table").performScrollTo().performClick()
        waitFor("Wednesdays, 08:00 to 09:00")
        compose.onNodeWithText("1 dose, 12 mg").assertExists()
        compose.onNodeWithText("Tuesdays, 19:00 to 20:00").assertExists()
    }

    @Test
    fun `shading by mg names the hour with the most mg`() {
        app.setUp()
        app.seedWeek()
        show()
        waitForDescription("Most often Wednesdays from 08:00")
        compose.onNode(hasText("mg") and isSelectable()).performScrollTo().performClick()
        waitForDescription("The most is on Wednesdays from 08:00, 12 mg.")
    }

    @Test
    fun `tapping an hour shows its doses and mg`() {
        app.setUp()
        app.seedWeek()
        show()
        waitForDescription("Most often Wednesdays from 08:00")
        compose.tapHour(column = 2, row = 4)
        waitFor("Wednesdays, 08:00 to 09:00")
        compose.onNodeWithText("1 dose, 12 mg").assertExists()
        compose.onNodeWithText("Over the 5 days").assertExists()
    }

    @Test
    fun `the calendar shades from the lightest day to the heaviest, and dashes days before the first dose`() {
        app.setUp()
        app.seedWeek()
        show()
        // Monday to Thursday: 14, 13, 12 and 14 mg. Today's 8 mg so far doesn't set the scale.
        waitForDescription("from 12 mg on the lightest day with doses to 14 mg on the heaviest.")
        compose.onNodeWithText("Before your first dose").performScrollTo().assertExists()
        compose.onNodeWithText("Today, so far").assertExists()
        // The range starts on Saturday the 17th, so the first week row is the week of the 12th.
        compose.tapCalendarDay(column = 2, row = 1)
        waitFor("Wednesday 21 October")
        compose.onNodeWithText("12 mg").assertExists()
        compose.onNodeWithContentDescription("Show calendar as a table").performScrollTo().performClick()
        waitFor("Monday 19 October")
        compose.onNodeWithText("8 mg so far").assertExists()
    }

    @Test
    fun `with only patches, the hour map gives way to a note`() {
        app.setUp()
        app.seedWeek()
        show()
        waitFor("13.3")
        compose.onNodeWithText("Patch").performClick()
        waitFor("The hour map leaves out patches, since a patch releases nicotine steadily.")
        compose.onNodeWithText("When you use").assertDoesNotExist()
        compose.onNodeWithText("Calendar").assertExists()
    }

    @Test
    fun `60 days shows a column a week`() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        show()
        compose.waitUntilExactlyOneExists(hasText("60 days") and isSelectable(), timeoutMillis = 5_000)
        compose.onNode(hasText("60 days") and isSelectable()).performClick()
        waitFor("Each week")
        compose.onNodeWithText("Average a day, each week").performScrollTo().assertExists()
    }
}
