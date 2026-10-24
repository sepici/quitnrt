package app.thelasthit.nrt.ui.lair

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.game.checkUnlocks
import app.thelasthit.nrt.ui.game.meetFirstWeek
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class LairTrophiesTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun showLair() = compose.setContent { LastHitTheme { LairScreen() } }

    @Test
    fun `the shelf shows the badges earned, and the rest as outlines still to come`() {
        app.setUp()
        runBlocking { app.graph.entries.log(LogRequest(GUM_4)) }
        app.checkUnlocks()
        showLair()

        compose.waitUntilExactlyOneExists(hasText("Trophy shelf"), timeoutMillis = 5_000)
        compose.onNodeWithText("1 of 33").assertExists()
        compose.onNodeWithContentDescription("First entry, earned 23 October").assertExists()
        compose.onNodeWithContentDescription("Logged on 3\u00A0days, not earned yet").assertExists()
        compose.onNodeWithContentDescription("Demon defeated, not earned yet").assertExists()
        for (group in listOf("Logging", "Matching the plan", "Beating the plan", "Habits", "The end")) {
            compose.onNodeWithText(group).assertExists()
        }
    }

    @Test
    fun `a badge earned before the day starts is dated the day before, as History has it`() {
        app.setUp()
        // A dose at 02:30 on Saturday counts toward Friday 23 October, and so does its badge.
        app.clock.now = london("2026-10-24T02:30")
        runBlocking { app.graph.entries.log(LogRequest(GUM_4)) }
        app.checkUnlocks()
        showLair()

        val first = hasContentDescription("First entry, earned 23 October")
        compose.waitUntilExactlyOneExists(first, timeoutMillis = 5_000)
        compose.onNode(first).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("Earned 23 October"), timeoutMillis = 5_000)
    }

    @Test
    fun `just after midnight at new year, a badge from the year that's ending leaves its year off`() {
        app.setUp()
        // 01:00 on 1 January still counts toward 31 December, so it's still this year.
        app.clock.now = london("2027-01-01T01:00")
        runBlocking { app.graph.entries.log(LogRequest(GUM_4)) }
        app.checkUnlocks()
        showLair()

        compose.waitUntilExactlyOneExists(hasContentDescription("First entry, earned 31 December"), timeoutMillis = 5_000)
    }

    @Test
    fun `a tap on a badge says what it's for and when it was earned`() {
        app.setUp()
        runBlocking { app.graph.entries.log(LogRequest(GUM_4)) }
        app.checkUnlocks()
        showLair()

        val first = hasContentDescription("First entry, earned 23 October")
        compose.waitUntilExactlyOneExists(first, timeoutMillis = 5_000)
        compose.onNode(first).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("Your first dose logged."), timeoutMillis = 5_000)
        // The tile reads as expanded, and the detail under the grid is read out as it opens.
        compose.onNode(first).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        compose.onNode(hasText("Your first dose logged."))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Earned 23 October").assertExists()

        // A badge still to come says what it takes.
        val later = hasContentDescription("Tagged 100\u00A0doses, not earned yet")
        compose.onNode(later).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("100\u00A0doses logged with a tag."), timeoutMillis = 5_000)
        compose.onNodeWithText("Not earned yet").assertExists()
        compose.onNodeWithText("Your first dose logged.").assertDoesNotExist()
    }

    @Test
    fun `the gear row shows the stick until there's more, and equipping goes through the sheet`() {
        app.setUp()
        app.meetFirstWeek()
        app.checkUnlocks()
        showLair()

        val row = hasText("Wooden stick") and hasClickAction()
        compose.waitUntilExactlyOneExists(row, timeoutMillis = 5_000)
        compose.onNode(row).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("Gear changes how your hits look and sound, and nothing else."), timeoutMillis = 5_000)

        // Gear still to come can't be picked.
        val axe = hasContentDescription("Axe, still to come. For 3\u00A0weeks met.")
        compose.onNode(axe).assertExists()
        compose.onNode(axe).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        compose.onNode(hasText("Iron sword") and hasClickAction()).performClick()
        compose.waitUntilDoesNotExist(hasText("Gear changes how your hits look and sound, and nothing else."), timeoutMillis = 5_000)
        compose.waitUntilExactlyOneExists(hasText("Iron sword") and hasClickAction(), timeoutMillis = 5_000)
        compose.onNode(hasText("Wooden stick")).assertDoesNotExist()
        assertEquals("iron_sword", runBlocking { app.graph.settings.current().equippedGear })
    }
}
