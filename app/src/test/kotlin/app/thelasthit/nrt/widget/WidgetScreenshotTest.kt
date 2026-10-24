package app.thelasthit.nrt.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.quicklog.WidgetMoment
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
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
import kotlin.math.roundToInt

/**
 * The widget as a launcher would show it, in each of its states and sizes, on a plain wallpaper.
 * Glance builds the remote views and Robolectric draws them. Dark mode comes from the phone's
 * own setting, as on a home screen, so it's a test of its own.
 *
 * `widget-preview-*` is the picture the launcher's widget picker shows. After a change to the
 * widget, record it and copy it to `res/drawable-nodpi/widget_preview.png` (light) and
 * `res/drawable-night-nodpi/widget_preview.png` (dark).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FULL_PAGE)
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

    private val entry = Entry(
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
    )

    private val logged = WidgetMoment.Logged(entry, "Nicorette gum", warning = null)
    private val labelNote = WidgetMoment.Logged(entry, "Nicorette gum", warning = LabelWarning.OverDaily(units = 16, limit = 15))
    private val duplicate = WidgetMoment.Duplicate(LogRequest(GUM_2), entry)

    private val small = DpSize(250.dp, 110.dp)
    private val fourByTwo = DpSize(330.dp, 150.dp)

    /**
     * Each state at the sizes a phone's home screen gives a 4 by 2 widget, the smallest it can be
     * resized to, with and without a plan, and a tall narrow one, where the guide's label has the
     * least room beside what's left.
     */
    private val states = listOf(
        DpSize(330.dp, 230.dp) to model(),
        fourByTwo to model(today = 17),
        small to model(),
        small to model(guide = null),
        DpSize(250.dp, 230.dp) to model(),
    )

    /** After a tap, at the smallest size and at 4 by 2: "Logged", a label's note and the duplicate question. */
    private val moments = listOf(small, fourByTwo).flatMap { size ->
        listOf(logged, labelNote, duplicate).map { size to model(moment = it) }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun capture(name: String, shown: List<Pair<DpSize, WidgetModel>>) {
        val dark = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val large = app.resources.configuration.fontScale > 1f
        val views = shown.map { (size, model) -> size to runBlocking { GlanceRemoteViews().compose(app, size) { WidgetContent(model) } }.remoteViews }
        compose.setContent {
            Column(
                Modifier.testTag(WIDGETS).background(if (dark) Color(0xFF1F2A26) else Color(0xFF8FA39A)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                views.forEach { (size, remote) ->
                    AndroidView(factory = { context -> remote.apply(context, FrameLayout(context)) }, modifier = Modifier.size(size.width, size.height))
                }
            }
        }
        compose.onNodeWithTag(WIDGETS).captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}${if (large) "-large" else ""}.png")
    }

    @Test
    fun light() = capture("widget", states)

    @Test @Config(qualifiers = "+night")
    fun dark() = capture("widget", states)

    @Test @Config(fontScale = LARGE_FONT)
    fun lightLarge() = capture("widget", states)

    @Test
    fun momentsLight() = capture("widget-moments", moments)

    @Test @Config(qualifiers = "+night")
    fun momentsDark() = capture("widget-moments", moments)

    @Test @Config(fontScale = LARGE_FONT)
    fun momentsLightLarge() = capture("widget-moments", moments)

    /** The 4 by 2 widget on its own, with its rounded corners clear, for the widget picker. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun capturePreview() {
        val dark = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val remote = runBlocking { GlanceRemoteViews().compose(app, fourByTwo) { WidgetContent(model()) } }.remoteViews
        val density = app.resources.displayMetrics.density
        val width = (fourByTwo.width.value * density).roundToInt()
        val height = (fourByTwo.height.value * density).roundToInt()
        val view = remote.apply(app, FrameLayout(app))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.captureRoboImage("build/outputs/roborazzi/widget-preview-${if (dark) "dark" else "light"}.png")
    }

    @Test
    fun previewLight() = capturePreview()

    @Test @Config(qualifiers = "+night")
    fun previewDark() = capturePreview()

    private companion object {
        const val WIDGETS = "widgets"
    }
}
