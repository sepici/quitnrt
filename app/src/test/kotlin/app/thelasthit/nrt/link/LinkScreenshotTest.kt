package app.thelasthit.nrt.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.R
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.matcher.LogLinkRequest
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.captureLooks
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.log.LogSheet
import app.thelasthit.nrt.ui.log.LogSheetViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LinkScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = PHONE)
    fun linkChoice() = captureLinkChoice()

    @Test
    @Config(qualifiers = PHONE, fontScale = LARGE_FONT)
    fun linkChoiceLarge() = captureLinkChoice()

    /** The log sheet a link opens when its product could be either strength of gum. */
    private fun captureLinkChoice() {
        app.setUp()
        val choices = runBlocking {
            listOf(GUM_2, GUM_4).map { id -> LogLinkRequest(requireNotNull(app.graph.products.get(id)), 2, null, setOf("stressed")) }
        }
        compose.captureLooks(
            "log-sheet-link-choice",
            ready = hasText("The link fits more than one of your products. Pick the one you meant."),
        ) {
            val viewModel = graphViewModel(key = "link-sheet") { LogSheetViewModel(it) }
            LaunchedEffect(viewModel) { viewModel.openFromLink(EntrySource.DEEP_LINK, choices) }
            LogSheet(viewModel, onDismiss = {}, onSaved = {})
        }
    }

    /** "Log a dose", then each form's shortcut icon, masked to a circle as most launchers do. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    @Config(qualifiers = PHONE)
    fun shortcutIcons() {
        val shortcuts = QuickLogShortcuts(app)
        val icons = ProductForm.entries.map { it.icon }.distinct().map { shortcuts.iconBitmap(it).asImageBitmap() }
        compose.captureLooks("shortcut-icons") {
            FlowRow(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Masked {
                    Image(
                        painterResource(R.drawable.ic_shortcut_log_glyph),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(108.dp).background(colorResource(R.color.shortcut_tile)),
                    )
                }
                icons.forEach { icon -> Masked { Image(icon, contentDescription = null, modifier = Modifier.requiredSize(108.dp)) } }
            }
        }
    }

    /** An adaptive icon's middle 72 dp, the part a launcher shows. */
    @Composable
    private fun Masked(content: @Composable () -> Unit) {
        Box(Modifier.size(72.dp).clip(CircleShape)) { content() }
    }
}
