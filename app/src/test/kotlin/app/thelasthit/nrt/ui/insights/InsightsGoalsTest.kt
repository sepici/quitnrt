package app.thelasthit.nrt.ui.insights

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.seedMockupPlan
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.plan.seedGoalWeeks
import app.thelasthit.nrt.ui.plan.seedWeekFour
import app.thelasthit.nrt.ui.plan.setUpForPlan
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The no-break space that keeps a count such as "3 of 4 days" on one line. */
private val NB = Char(0xA0).toString()

/** Insights' "Your goals", and Week by week for results from before the judged totals were kept. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class InsightsGoalsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun show() {
        compose.setContent { LastHitTheme { InsightsScreen(snackbars = remember { SnackbarHostState() }) } }
    }

    @Test
    fun `each goal shows this week so far beside last week`() {
        app.setUpForPlan()
        app.seedGoalWeeks()
        show()

        compose.waitUntilAtLeastOneExists(hasText("Your goals"), timeoutMillis = 10_000)
        compose.onNodeWithText("Your goals").performScrollTo()
        compose.onNodeWithText(
            "This week so far and last week. A day counts once it's over, and goals never change whether a week is met.",
        ).assertExists()
        // The gum cap came down to 6 in week 4, so last week's line says its own.
        compose.onNode(hasText("gum a day", substring = true) and hasText("Kept on 3${NB}of${NB}4", substring = true) and hasText("Week 3, at most 8", substring = true))
            .assertExists()
        compose.onNode(hasText("sprays a day", substring = true) and hasText("Kept on 4${NB}of${NB}4", substring = true) and hasText("Week 3: kept on 7${NB}of${NB}7", substring = true))
            .assertExists()
        compose.onNode(hasText("between doses", substring = true) and hasText("Kept for 26${NB}of${NB}28", substring = true) and hasText("Week 3: kept for 47${NB}of${NB}54", substring = true))
            .assertExists()
        compose.onNode(hasText("First dose from 09:00") and hasText("Kept on 3${NB}of${NB}4", substring = true) and hasText("Week 3, from 08:45: kept on 5${NB}of${NB}7", substring = true))
            .assertExists()
    }

    @Test
    fun `with every goal off there's no section`() {
        app.setUpForPlan()
        app.seedWeekFour()
        show()

        compose.waitUntilAtLeastOneExists(hasText("Week by week"), timeoutMillis = 10_000)
        compose.onNodeWithText("Your goals").assertDoesNotExist()
    }

    @Test
    fun `goals are about every product, so a kind of product picked leaves them out`() {
        app.setUpForPlan()
        app.seedGoalWeeks()
        show()

        compose.waitUntilAtLeastOneExists(hasText("Your goals"), timeoutMillis = 10_000)
        compose.onNode(hasText("Gum") and isSelectable()).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Gum triggers"), timeoutMillis = 5_000)
        compose.onNodeWithText("Your goals").assertDoesNotExist()
    }

    @Test
    fun `a result from before the judged totals were kept says it's the one that came in`() {
        app.setUp()
        app.seedMockupPlan()
        // As restored from a version 2 backup: the same results, with no totals kept.
        runBlocking {
            val backup = app.graph.backups.export()
            val old = backup.copy(
                version = 2,
                programmes = backup.programmes.map { programme ->
                    programme.copy(weeks = programme.weeks.map { it.copy(resultTotalMicrograms = null) })
                },
            )
            app.graph.backups.import(Backup.decode(old.encode()), ImportMode.REPLACE)
            // 40 mg forgotten on Tuesday 13 October, logged after week 3's result came in.
            app.graph.entries.log(LogRequest(GUM_4, quantity = 10, at = london("2026-10-13T21:00")))
        }
        show()

        compose.waitUntilAtLeastOneExists(
            hasContentDescription("mg, met when its result came in, paid back week 2.", substring = true),
            timeoutMillis = 10_000,
        )
    }
}
