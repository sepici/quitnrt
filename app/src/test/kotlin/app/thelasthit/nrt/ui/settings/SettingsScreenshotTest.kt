package app.thelasthit.nrt.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.seedWeek
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each Settings page as the mockups show it, with a hidden product, one added by hand and changed tags. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun setUpLikeTheMockups() {
        app.setUp()
        runBlocking {
            app.graph.products.addFromCatalogue("nicorette-cools-uk-4mg", addToQuickLog = false)
            app.graph.products.setHidden("nicorette-cools-uk-4mg", true)
            app.graph.products.addCustom("Holiday gum", ProductForm.GUM, Dose.mg(2), addToQuickLog = false)
            val tags = app.graph.tags.all.first()
            app.graph.tags.edit(tags.first { it.key == "celebrating" }, "", TagGroup.FEELINGS, hidden = true)
            app.graph.tags.edit(tags.first { it.key == "on_call" }, "", TagGroup.ROUTINE, hidden = true)
            app.graph.tags.addCustom("School run", TagGroup.ROUTINE)
            app.graph.settings.acceptDisclaimer(TestApp.london("2026-10-23T09:12"))
        }
    }

    private val links = SettingsLinks({}, {}, {}, {}, {})

    @Test @Config(qualifiers = FULL_PAGE)
    fun settings() = captureMain()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun settingsLarge() = captureMain()

    private fun captureMain() {
        setUpLikeTheMockups()
        compose.captureLooks("settings", ready = hasText("What you use")) {
            SettingsScreen(remember { SnackbarHostState() }, onBack = {}, links = links)
        }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun products() = captureProducts()

    @Test @Config(qualifiers = FULL_PAGE, fontScale = LARGE_FONT)
    fun productsLarge() = captureProducts()

    private fun captureProducts() {
        setUpLikeTheMockups()
        compose.captureLooks("settings-products", ready = hasText("Holiday gum", substring = true)) {
            ProductsScreen(onBack = {}, onAddFromCatalogue = {})
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun productByHand() {
        setUpLikeTheMockups()
        compose.captureLooks(
            "settings-product-by-hand",
            ready = hasText("Add by hand"),
            before = {
                onNodeWithText("Add by hand").performClick()
                waitUntil(5_000) { onAllNodes(hasText("For anything the catalogue doesn't list")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { ProductsScreen(onBack = {}, onAddFromCatalogue = {}) }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun catalogue() {
        setUpLikeTheMockups()
        compose.captureLooks("settings-catalogue", ready = hasText("Show other countries")) { CatalogueScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun quickLog() {
        setUpLikeTheMockups()
        compose.captureLooks("settings-quick-log", ready = hasText("On the quick log")) { QuickLogScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun tags() {
        setUpLikeTheMockups()
        compose.captureLooks("settings-tags", ready = hasText("Feelings")) { TagsScreen(onBack = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun dayStart() {
        setUpLikeTheMockups()
        compose.captureLooks("settings-day-start", ready = hasText("Save day start")) { DayStartScreen(onBack = {}, onSaved = {}) }
    }

    @Test @Config(qualifiers = FULL_PAGE)
    fun about() {
        setUpLikeTheMockups()
        compose.captureLooks("settings-about", ready = hasText("Disclaimer")) { AboutScreen(onBack = {}, onLicence = {}) }
    }

    @Test @Config(qualifiers = PHONE)
    fun restore() {
        setUpLikeTheMockups()
        app.seedWeek()
        val backup = runBlocking { app.graph.backups.export() }.encode()
        val viewModel = SettingsViewModel(app.graph)
        compose.captureLooks(
            "settings-restore",
            ready = hasText("What you use"),
            before = {
                viewModel.openBackup("thelasthit-backup-2026-10-23.json", backup)
                waitUntil(5_000) { onAllNodes(hasText("How to restore it")).fetchSemanticsNodes().isNotEmpty() }
            },
        ) { SettingsScreen(remember { SnackbarHostState() }, onBack = {}, links = links, viewModel = viewModel) }
    }
}
