package app.thelasthit.nrt.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assert
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.PATCH_14
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class SettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpApp() {
        app.setUp()
    }

    private fun show(screen: @Composable (SnackbarHostState) -> Unit) {
        compose.setContent {
            LastHitTheme {
                val snackbars = remember { SnackbarHostState() }
                Box(Modifier.fillMaxSize()) {
                    screen(snackbars)
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    private fun waitFor(text: String) = compose.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** Reads the database once the screen shows the change. Never poll with it (see TodayTest). */
    private fun <T> read(block: suspend () -> T): T = runBlocking { block() }

    @Test
    fun `hiding a product moves it down and Show brings it back`() {
        show { ProductsScreen(onBack = {}, onAddFromCatalogue = {}) }
        waitFor("Nicorette gum 2 mg")
        compose.onNodeWithContentDescription("Hide Nicorette gum 2 mg").performClick()

        waitFor("Hidden")
        compose.onNodeWithContentDescription("Show Nicorette gum 2 mg").performScrollTo().assertExists()
        assertTrue(read { app.graph.products.get(GUM_2) }!!.isHidden)
        assertEquals(listOf(GUM_4, QUICKMIST, PATCH_14), read { app.graph.products.quickLog.first() }.map { it.id })

        compose.onNodeWithContentDescription("Show Nicorette gum 2 mg").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Hidden")).fetchSemanticsNodes().isEmpty() }
        assertEquals(GUM_2, read { app.graph.products.quickLog.first() }.first().id)
    }

    @Test
    fun `a product added by hand keeps its strength and limit`() {
        show { ProductsScreen(onBack = {}, onAddFromCatalogue = {}) }
        waitFor("Add by hand")
        compose.onNodeWithText("Add by hand").performClick()
        waitFor("For anything the catalogue doesn't list")

        // Each field is named after the title above it, which a screen reader then doesn't hear twice.
        fun field(name: String) = compose.onNode(androidx.compose.ui.test.hasSetTextAction() and hasContentDescription(name))
        field("Name").performTextInput("Holiday gum")
        field("Strength").performTextInput("1.5")
        field("Pack limit").performTextInput("12")
        compose.onNodeWithText("Add product").performClick()

        waitFor("Holiday gum 1.5\u00A0mg")
        val added = read { app.graph.products.all.first() }.single { it.name == "Holiday gum" }
        assertEquals(Dose(1_500), added.perUnit)
        assertEquals(12, added.label.maxUnitsPerDay)
        assertTrue(added.id in read { app.graph.products.quickLog.first() }.map { it.id })
    }

    @Test
    fun `editing a product renames it`() {
        val custom = read { app.graph.products.addCustom("Holiday gum", ProductForm.GUM, Dose.mg(2)) }
        show { ProductsScreen(onBack = {}, onAddFromCatalogue = {}) }
        waitFor("Holiday gum 2\u00A0mg")
        compose.onNodeWithText("Holiday gum 2\u00A0mg").performScrollTo().performClick()
        waitFor("Edit product")

        compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextReplacement("Duty free gum")
        compose.onNodeWithText("Save product").performClick()

        waitFor("Duty free gum 2\u00A0mg")
        assertEquals("Duty free gum", read { app.graph.products.get(custom.id) }!!.name)
    }

    @Test
    fun `the catalogue adds a product`() {
        show { CatalogueScreen(onBack = {}) }
        compose.waitUntilExactlyOneExists(hasContentDescription("Nicorette Cools 4\u00A0mg"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Nicorette Cools 4\u00A0mg").performScrollTo().performClick()

        compose.waitUntilExactlyOneExists(hasContentDescription("Nicorette Cools 4\u00A0mg") and isOn(), timeoutMillis = 5_000)
        assertTrue(read { app.graph.products.get("nicorette-cools-uk-4mg") } != null)
    }

    @Test
    fun `the quick log reorders, loses and gains products`() {
        show { QuickLogScreen(onBack = {}) }
        waitFor("On the quick log")
        // Each row says where it is in the order, and says it again after a move.
        compose.onNodeWithText("Gum 2\u00A0mg").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Position 1 of 4"))

        compose.onNodeWithText("Gum 2\u00A0mg").performCustomAccessibilityActionWithLabel("Move down")
        // Wait on the screen, not the database: Gum 4 mg's row moves above Gum 2 mg's.
        compose.waitUntil(5_000) { top("Gum 4\u00A0mg") < top("Gum 2\u00A0mg") }
        compose.onNodeWithText("Gum 2\u00A0mg").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Position 2 of 4"))

        compose.onNodeWithContentDescription("Take Patch 14\u00A0mg off the quick log").performClick()
        waitFor("Add")
        assertEquals(listOf(GUM_4, GUM_2, QUICKMIST), read { app.graph.products.quickLog.first() }.map { it.id })

        compose.onNodeWithContentDescription("Add NiQuitin patch 14 mg to the quick log").performClick()
        waitFor("Every product is on the quick log.")
        assertEquals(PATCH_14, read { app.graph.products.quickLog.first() }.last().id)
    }

    @Test
    fun `a tag can be renamed and one added`() {
        show { TagsScreen(onBack = {}) }
        compose.waitUntilExactlyOneExists(hasContentDescription("Bored"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Bored").performClick()
        waitFor("Edit tag")
        compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextReplacement("Nothing on")
        compose.onNodeWithText("Save tag").performClick()
        compose.waitUntilExactlyOneExists(hasContentDescription("Nothing on"), timeoutMillis = 5_000)

        compose.onNodeWithText("Add a tag").performClick()
        waitFor("It shows when you log a dose, in the group you pick")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction() and hasContentDescription("Name")).performTextInput("School run")
        compose.onNode(hasText("Routine") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.onNodeWithText("Add tag").performClick()
        compose.waitUntilExactlyOneExists(hasContentDescription("School run, added by you"), timeoutMillis = 5_000)

        val tags = read { app.graph.tags.all.first() }
        assertEquals("Nothing on", tags.single { it.key == "bored" }.customName)
        assertEquals(TagGroup.ROUTINE, tags.single { it.customName == "School run" }.group)
    }

    @Test
    fun `a later day start moves early doses to the day before`() {
        read { app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T04:15"))) }
        var left = false
        show { DayStartScreen(onBack = {}, onSaved = { left = true }) }
        compose.waitUntilExactlyOneExists(hasContentDescription("Start 30\u00A0minutes later"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Start 30\u00A0minutes later").performClick()
        compose.waitUntilExactlyOneExists(hasText("04:30"))
        compose.onNodeWithText("Save day start").performClick()

        // A plain flag never syncs with Compose, so let the UI settle each time round.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            left
        }
        assertEquals(LocalTime.of(4, 30), read { app.graph.settings.current() }.dayStart)
        assertEquals(1, read { app.graph.entries.observeDay(LocalDate.parse("2026-10-22")).first() }.entries.size)
    }

    @Test
    fun `restoring can replace everything after a warning`() {
        app.seedWeek()
        val backup = read { app.graph.backups.export() }.encode()
        read { app.graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T14:50"))) }
        val viewModel = SettingsViewModel(app.graph)
        show { snackbars -> SettingsScreen(snackbars, onBack = {}, links = NoLinks, viewModel = viewModel) }
        waitFor("What you use")

        viewModel.openBackup("thelasthit-backup-2026-10-23.json", backup)
        waitFor("How to restore it")
        compose.onNodeWithText("Replace everything").performClick()
        // The button now names the choice too, and the warning then confirms it.
        compose.onNode(hasText("Replace everything") and isButton).performClick()
        waitFor("Replace everything on this phone?")
        compose.onNodeWithText(
            "This deletes 10\u00A0doses, 4\u00A0products and your tag changes on this phone. " +
                "The backup from 23 October 2026 takes their place, along with its day start.",
        ).assertExists()
        // The sheet counts as a dialog too, so find the warning's button by the warning's title.
        val warning = hasAnyAncestor(hasAnyDescendant(hasText("Replace everything on this phone?")))
        compose.onNode(hasText("Replace everything") and isButton and warning).performClick()

        waitFor("Backup restored")
        assertEquals(9, read { app.graph.backups.counts() }.entries)
    }

    @Test
    fun `merging adds only what's missing`() {
        // A backup from another phone: a second, empty database with two doses in it.
        val other = DataGraph.inMemory(app, app.clock)
        val backup = runBlocking {
            other.tags.ensureBuiltIns()
            other.products.addFromCatalogue(GUM_2)
            other.entries.log(LogRequest(GUM_2, at = london("2026-10-22T09:00")))
            other.entries.log(LogRequest(GUM_2, at = london("2026-10-22T18:00")))
            other.backups.export().encode()
        }
        val viewModel = SettingsViewModel(app.graph)
        show { snackbars -> SettingsScreen(snackbars, onBack = {}, links = NoLinks, viewModel = viewModel) }
        waitFor("What you use")

        viewModel.openBackup("backup.json", backup)
        waitFor("How to restore it")
        compose.onNodeWithText("Add from backup").performClick()

        waitFor("Added 2\u00A0doses from the backup")
        assertEquals(2, read { app.graph.backups.counts() }.entries)
        assertEquals(4, read { app.graph.backups.counts() }.products)
    }

    @Test
    fun `a file that isn't a backup says so`() {
        val viewModel = SettingsViewModel(app.graph)
        show { snackbars -> SettingsScreen(snackbars, onBack = {}, links = NoLinks, viewModel = viewModel) }
        waitFor("What you use")

        viewModel.openBackup("notes.json", """{"hello": "world"}""")
        waitFor("That file isn't a backup from this app.")
        compose.onNodeWithText("How to restore it").assertDoesNotExist()
    }

    @Test
    fun `about shows the font licences`() {
        var opened: FontLicence? = null
        show { AboutScreen(onBack = {}, onLicence = { opened = it }) }
        waitFor("Disclaimer")
        compose.onNodeWithText("Silkscreen").performScrollTo().performClick()
        compose.waitUntil(5_000) { opened == FontLicence.SILKSCREEN }
    }

    @Test
    fun `a licence shows its text`() {
        show { LicenceScreen(FontLicence.SILKSCREEN, onBack = {}) }
        compose.waitUntilAtLeastOneExists(hasText("SIL Open Font License", substring = true), timeoutMillis = 5_000)
    }

    private val NoLinks = SettingsLinks({}, {}, {}, {}, {})

    private fun top(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
}
