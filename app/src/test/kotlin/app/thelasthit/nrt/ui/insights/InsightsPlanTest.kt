package app.thelasthit.nrt.ui.insights

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
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
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.seedMockupPlan
import app.thelasthit.nrt.seedHistory
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Insights once a plan runs: the guide line, week by week, the calendar against the guide and the days within it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class InsightsPlanTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun show() {
        compose.setContent { LastHitTheme { InsightsScreen(snackbars = remember { SnackbarHostState() }) } }
    }

    @Test
    fun `the mockups' plan has a missed second week that the third paid back`() {
        app.setUp()
        app.seedMockupPlan()
        val plan = checkNotNull(runBlocking { app.graph.programmes.current() }?.plan)
        assertEquals(
            listOf(WeekResult.MET, WeekResult.CAUGHT_UP, WeekResult.MET, WeekResult.PENDING),
            plan.weeks.take(4).map { it.result },
        )
    }

    @Test
    fun `with a plan, each day has its guide, and the weeks are against their allowances`() {
        app.setUp()
        app.seedMockupPlan()
        show()

        compose.waitUntilAtLeastOneExists(hasText("Daily guide"), timeoutMillis = 5_000)
        // 25 finished days from Monday 28 September to yesterday have a guide.
        compose.onNode(hasText("At or under the guide on", substring = true) and hasText(" of 25 days.", substring = true)).assertExists()
        compose.onNodeWithText("Week by week").assertExists()
        compose.onNodeWithText("Each week of your plan against its allowance.").assertExists()
        compose.onNode(hasContentDescription("Week 2, ", substring = true) and hasContentDescription("of 126 mg, caught up in week 3.", substring = true))
            .assertExists()
        compose.onNode(hasContentDescription("of 112 mg, met, paid back week 2.", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Week 4, 61 of 98 mg, this week, so far.", substring = true)).assertExists()
        compose.onNodeWithText("Each day shaded by mg against its daily guide. Days before your plan are against your usual day of 24 mg.")
            .assertExists()
        compose.onNodeWithText("of the guide").assertExists()
    }

    @Test
    fun `week by week turns into a table`() {
        app.setUp()
        app.seedMockupPlan()
        show()

        compose.waitUntilAtLeastOneExists(hasContentDescription("Show week by week as a table"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Show week by week as a table").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("61 of 98 mg, this week, so far"), timeoutMillis = 5_000)
        compose.onNodeWithText("Week 4").assertExists()
    }

    @Test
    fun `a kind of product on its own has no guide`() {
        app.setUp()
        app.seedMockupPlan()
        show()

        compose.waitUntilAtLeastOneExists(hasText("Daily guide"), timeoutMillis = 5_000)
        compose.onNode(hasText("Gum") and isSelectable()).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Gum each day"), timeoutMillis = 5_000)
        compose.onNodeWithText("Daily guide").assertDoesNotExist()
        compose.onNodeWithText("Week by week").assertDoesNotExist()
        compose.onNode(hasText("At or under the guide on", substring = true)).assertDoesNotExist()
        compose.onNodeWithText("Each day shaded by mg from gum.").assertExists()
    }

    @Test
    fun `without a plan, Insights is as it was`() {
        app.setUp()
        app.seedHistory()
        app.seedWeek()
        show()

        compose.waitUntilAtLeastOneExists(hasText("Each day shaded by mg."), timeoutMillis = 5_000)
        compose.onNodeWithText("Daily guide").assertDoesNotExist()
        compose.onNodeWithText("Week by week").assertDoesNotExist()
    }
}
