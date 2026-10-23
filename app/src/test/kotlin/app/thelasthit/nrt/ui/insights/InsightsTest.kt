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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

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
        waitFor("The hour map, triggers and habits leave out patches, since a patch releases nicotine steadily.")
        compose.onNode(hasText("When you use", substring = true)).assertDoesNotExist()
        compose.onNodeWithText("Patch triggers").assertDoesNotExist()
        compose.onNodeWithText("Patch habits").assertDoesNotExist()
        compose.onNodeWithText("Patch calendar").assertExists()
        compose.onNodeWithText("Patch products").assertExists()
    }

    @Test
    fun `triggers show each tag's share, the top tag in each part of the day, and when changes start`() {
        app.setUp()
        app.seedWeek()
        show()
        // 7 doses besides the patches, 3 of them tagged once each.
        waitFor("Share of doses with each tag. Patches aren't counted. Changes against the week before start on 1 November.")
        compose.onAllNodesWithText("With coffee or tea").assertCountEquals(2)
        compose.onNodeWithText("1 of 4 morning doses").assertExists()
        compose.onNodeWithText("1 of 2 afternoon doses").assertExists()
        compose.onNodeWithText("No tags").assertExists()
        compose.onNodeWithText("4 of 7 doses had no tag.").performScrollTo().assertExists()
        compose.onNodeWithText("Routine").assertExists()
    }

    @Test
    fun `habits show the first dose and the gaps, with the longest one's day and times`() {
        app.setUp()
        app.seedWeek()
        show()
        waitFor("Gaps only count within a day, and patches aren't counted. Changes start on 1 November.")
        // First doses at 08:00, 08:15 and 08:10. Gaps of 5 h and 6 h on Tuesday, 2 h 45 min twice today.
        compose.onNodeWithText("08:08").assertExists()
        compose.onNode(hasContentDescription("4 hours 8 minutes")).assertExists()
        compose.onNode(hasContentDescription("6 hours")).assertExists()
        compose.onNodeWithText("Tuesday 20 October, 13:00 to 19:00").performScrollTo().assertExists()
    }

    @Test
    fun `by product shares the finished days' mg, and says how much a day`() {
        app.setUp()
        app.seedWeek()
        show()
        // Monday to Thursday: 28 mg of patches, 24 mg of 4 mg gum and 1 mg of spray.
        waitFor("0.5 a day, on 2 of 4\u00A0days")
        compose.onNodeWithText("53%").assertExists()
        compose.onNodeWithText("1.5\u00A0pieces a day").assertExists()
        compose.onNodeWithText("0.3\u00A0sprays a day").assertExists()
    }

    @Test
    fun `with a full period before, triggers and habits compare with it`() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        show()
        waitFor("Share of doses with each tag, against the 30\u00A0days before. Patches aren't counted.")
        compose.onNodeWithText("Against the 30\u00A0days before. Gaps only count within a day, and patches aren't counted.").assertExists()
        compose.onNodeWithText("Gum").performClick()
        // Gum alone has no patches to leave out, and every section names it.
        waitFor("Share of gum doses with each tag, against the 30\u00A0days before.")
        compose.onNodeWithText("Gum each day").assertExists()
        compose.onNodeWithContentDescription("Show gum each day as a table").assertExists()
        compose.onNodeWithText("When you use gum").assertExists()
        compose.onNodeWithText("Gum doses by hour and day of the week.").assertExists()
        compose.onNodeWithContentDescription("Show when you use gum as a table").assertExists()
        compose.onNodeWithText("Gum triggers").assertExists()
        compose.onNodeWithText("Gum habits").assertExists()
        compose.onNodeWithText("Against the 30\u00A0days before. Gaps only count within a day, between gum doses.").assertExists()
        compose.onNodeWithText("Gum calendar").assertExists()
        compose.onNodeWithText("Each day shaded by gum mg.").assertExists()
        compose.onNodeWithContentDescription("Show gum calendar as a table").assertExists()
        compose.onNodeWithText("Gum products").assertExists()
        compose.onNodeWithText("Share of gum mg, and how much a day.").assertExists()
    }

    @Test
    fun `with the week before all logged, changes show in points and minutes`() {
        app.setUp()
        fun log(at: String, vararg tags: String) = runBlocking {
            app.graph.entries.log(LogRequest(GUM_2, at = london(at), tagKeys = tags.toSet()))
        }
        // The week from Saturday the 10th: 1 of 4 doses with coffee, and every first dose at 08:00.
        log("2026-10-10T08:00", "coffee_tea")
        log("2026-10-11T08:00")
        log("2026-10-12T08:00")
        log("2026-10-12T12:00")
        // This week: 2 of 4 with coffee, and every first dose at 08:30.
        log("2026-10-20T08:30", "coffee_tea")
        log("2026-10-20T13:30", "coffee_tea")
        log("2026-10-21T08:30")
        log("2026-10-22T08:30")
        show()
        // 30 days is the default with two weeks logged, and it has no full period before.
        compose.waitUntilExactlyOneExists(hasText("7\u00A0days") and isSelectable(), timeoutMillis = 5_000)
        compose.onNode(hasText("7\u00A0days") and isSelectable()).performClick()
        waitFor("Share of doses with each tag, against the week before.")
        compose.onNode(hasText("more than the week before", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Up 25 points")).assertExists()
        compose.onNodeWithText("Against the week before. Gaps only count within a day.").assertExists()
        compose.onNode(hasContentDescription("30 minutes later")).assertExists()
    }

    @Test
    fun `the hour map's rows follow the day start from settings, even on the half hour`() {
        app.setUp()
        runBlocking {
            app.graph.settings.setDayStart(LocalTime.of(0, 30))
            app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-22T08:40")))
            // Before 00:30, so the last of Thursday.
            app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T00:15")))
        }
        show()
        waitFor("Each column is a whole day from 00:30, so a dose at 00:15 on Friday sits at the bottom of Thursday.")
        compose.onNodeWithContentDescription("Show when you use as a table").performScrollTo().performClick()
        waitFor("Thursdays, 23:30 to 00:30")
        compose.onNodeWithText("Thursdays, 08:30 to 09:30").assertExists()
        compose.onAllNodesWithText("1 dose, 2\u00A0mg").assertCountEquals(2)
    }

    @Test
    fun `tags show their own name, and count toward the group they're in`() {
        app.setUp()
        app.seedWeek()
        runBlocking {
            val tags = app.graph.tags.all.first().associateBy { it.key }
            app.graph.tags.rename(tags.getValue("coffee_tea"), "Morning brew")
            app.graph.tags.edit(tags.getValue("work_break"), name = "", group = TagGroup.FEELINGS, hidden = false)
        }
        show()
        waitFor("4 of 7 doses had no tag.")
        // In the list and as the morning's top tag.
        compose.onAllNodesWithText("Morning brew").assertCountEquals(2)
        compose.onNodeWithText("With coffee or tea").assertDoesNotExist()
        // Coffee and the meal are routine, 2 of 7 doses, and the break is now a feeling, 1 of 7.
        compose.onNode(hasText("29%") and hasText("Routine")).assertExists()
        compose.onNode(hasText("14%") and hasText("Feelings")).assertExists()
    }

    @Test
    fun `a kind first used today shows what's been used so far by product`() {
        app.setUp()
        runBlocking {
            app.graph.entries.log(LogRequest(GUM_4, at = london("2026-10-20T08:00")))
            app.graph.entries.log(LogRequest(QUICKMIST, quantity = 2, at = london("2026-10-23T09:00")))
        }
        show()
        compose.waitUntilExactlyOneExists(hasText("Mouth spray") and isSelectable(), timeoutMillis = 5_000)
        compose.onNode(hasText("Mouth spray") and isSelectable()).performClick()
        waitFor("Mouth spray products")
        compose.onNodeWithText("2\u00A0sprays so far today").performScrollTo().assertExists()
        compose.onNodeWithText("No doses in this period.").assertDoesNotExist()
    }

    @Test
    fun `on a calendar one week tall, a tapped day's tip sits over it`() {
        app.setUp()
        app.seedWeek()
        // Sunday, so the 7 days are Monday to Sunday, one row.
        app.clock.now = london("2026-10-25T15:05")
        show()
        waitForDescription("Calendar of mg each day")
        compose.tapCalendarDay(column = 3, row = 0)
        waitFor("Thursday 22 October")
        val calendar = compose.onNode(hasContentDescription("Calendar of mg each day", substring = true)).getUnclippedBoundsInRoot()
        val tip = compose.onNodeWithText("Thursday 22 October").getUnclippedBoundsInRoot()
        // Above the row of days, which starts under the day names, never over the legend below.
        assertTrue(tip.bottom < calendar.top + 18.dp)
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
