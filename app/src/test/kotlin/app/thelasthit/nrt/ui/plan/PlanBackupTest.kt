package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.settings.SettingsLinks
import app.thelasthit.nrt.ui.settings.SettingsScreen
import app.thelasthit.nrt.ui.settings.SettingsViewModel
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The backup's follow-ups for the programme: what replacing deletes, and a merge that leaves a plan out. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class PlanBackupTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Before
    fun setUpApp() {
        app.setUp()
    }

    private fun show(viewModel: SettingsViewModel) {
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    SettingsScreen(snackbars, onBack = {}, links = SettingsLinks({}, {}, {}, {}, {}), viewModel = viewModel)
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("What you use"), timeoutMillis = 5_000)
    }

    @Test
    fun `replacing everything names the plan and the demon it deletes`() {
        app.seedWeekFour()
        val backup = runBlocking { app.graph.backups.export() }.encode()
        val viewModel = SettingsViewModel(app.graph)
        show(viewModel)

        viewModel.openBackup("thelasthit-backup-2026-11-18.json", backup)
        compose.waitUntilAtLeastOneExists(hasText("How to restore it"), timeoutMillis = 5_000)
        compose.onNodeWithText("Replace everything").performClick()
        compose.onNode(hasText("Replace everything") and isButton).performClick()

        compose.waitUntilAtLeastOneExists(
            hasText("4\u00A0products, your current plan, Gary at 110 of 120\u00A0HP and your tag changes on this phone.", substring = true),
            timeoutMillis = 5_000,
        )
    }

    @Test
    fun `a merge that leaves out the backup's plan says so, and how to bring it in`() {
        app.startMockupPlan()
        // Another phone, with a plan of its own running and two doses.
        val other = DataGraph.inMemory(app, app.clock)
        val backup = runBlocking {
            other.tags.ensureBuiltIns()
            other.settings.setCountry("GB")
            val gum = other.products.addFromCatalogue(GUM_4)
            other.programmes.keepUsualDay(UsualDay.typed(gum to 6))
            other.programmes.startPlan(checkNotNull(other.programmes.buildPlan()))
            other.entries.log(LogRequest(GUM_4, at = london("2026-10-23T09:00")))
            other.entries.log(LogRequest(GUM_4, at = london("2026-10-23T12:00")))
            other.backups.export().encode()
        }
        val viewModel = SettingsViewModel(app.graph)
        show(viewModel)

        viewModel.openBackup("backup.json", backup)
        compose.waitUntilAtLeastOneExists(hasText("How to restore it"), timeoutMillis = 5_000)
        compose.onNodeWithText("Add from backup").performClick()

        compose.waitUntilAtLeastOneExists(
            hasText(
                "Added 2\u00A0doses from the backup. Its programme wasn't added, since this phone has one of its own. " +
                    "Restore with Replace everything to bring it in.",
            ),
            timeoutMillis = 5_000,
        )
    }
}
