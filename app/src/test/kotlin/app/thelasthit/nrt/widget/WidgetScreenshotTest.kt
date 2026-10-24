package app.thelasthit.nrt.widget

import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.R
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.quicklog.WidgetMoment
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.PHONE
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The widget as a launcher would show it, in each of its states and sizes, on a plain wallpaper.
 * Glance builds the remote views and Robolectric draws them. Dark mode comes from the phone's
 * own setting, as on a home screen, so it's a test of its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class WidgetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val nbsp = Char(0xA0)

    private val favourites = listOf(
        Favourite(GUM_2, "Gum 2${nbsp}mg", R.drawable.ic_form_gum, "Log Nicorette gum 2${nbsp}mg"),
        Favourite(GUM_4, "Gum 4${nbsp}mg", R.drawable.ic_form_gum, "Log Nicorette gum 4${nbsp}mg"),
        Favourite(QUICKMIST, "Mouth spray 1${nbsp}mg", R.drawable.ic_form_spray, "Log Nicorette QuickMist 1${nbsp}mg"),
    )

    private fun model(today: Int = 8, doses: Int = 3, guide: Int? = 14, moment: WidgetMoment? = null) =
        WidgetModel(true, Dose.mg(today), doses, guide?.let(Dose::mg), favourites, moment, asksForTags = false)

    private val logged = WidgetMoment.Logged(
        Entry(
            id = "e1",
            productId = GUM_2,
            productName = "Nicorette gum",
            form = ProductForm.GUM,
            unit = DoseUnit.PIECE,
            perUnit = Dose.mg(2),
            quantity = 1,
            total = Dose.mg(2),
            occurredAt = Instant.parse("2026-10-23T14:05:00Z"),
            zone = ZoneId.of("Europe/London"),
            logicalDate = LocalDate.parse("2026-10-23"),
        ),
        "Nicorette gum",
        warning = null,
    )

    /** Each state at the sizes a phone's home screen gives a 4 by 2 widget, and a short and a narrow one. */
    private val shown = listOf(
        DpSize(330.dp, 230.dp) to model(),
        DpSize(330.dp, 150.dp) to model(today = 17),
        DpSize(330.dp, 150.dp) to model(moment = logged),
        DpSize(250.dp, 110.dp) to model(guide = null),
    )

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun capture() {
        val dark = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val large = app.resources.configuration.fontScale > 1f
        val views = shown.map { (size, model) -> size to runBlocking { GlanceRemoteViews().compose(app, size) { WidgetContent(model) } }.remoteViews }
        compose.setContent {
            Column(
                Modifier.fillMaxSize().background(if (dark) Color(0xFF1F2A26) else Color(0xFF8FA39A)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                views.forEach { (size, remote) ->
                    AndroidView(factory = { context -> remote.apply(context, FrameLayout(context)) }, modifier = Modifier.size(size.width, size.height))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/widget-${if (dark) "dark" else "light"}${if (large) "-large" else ""}.png")
    }

    @Test
    fun light() = capture()

    @Test @Config(qualifiers = "+night")
    fun dark() = capture()

    @Test @Config(fontScale = LARGE_FONT)
    fun lightLarge() = capture()
}
