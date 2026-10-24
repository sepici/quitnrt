package app.thelasthit.nrt.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
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
import app.thelasthit.nrt.reminders.chew
import app.thelasthit.nrt.reminders.setTime
import app.thelasthit.nrt.reminders.startGumPlan
import app.thelasthit.nrt.setUp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the widget shows, for each of its states, and what its taps do. */
@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val nbsp = Char(0xA0)

    @After
    fun stop() {
        // This test's app stops listening, so nothing from its logs lands in the next test.
        app.appScope.cancel()
    }

    private val favourites = listOf(
        Favourite(GUM_2, "Gum 2${nbsp}mg", R.drawable.ic_form_gum, "Log Nicorette gum 2${nbsp}mg"),
        Favourite(GUM_4, "Gum 4${nbsp}mg", R.drawable.ic_form_gum, "Log Nicorette gum 4${nbsp}mg"),
        Favourite(QUICKMIST, "Mouth spray 1${nbsp}mg", R.drawable.ic_form_spray, "Log Nicorette QuickMist 1${nbsp}mg"),
    )

    private fun model(today: Int = 8, doses: Int = 3, guide: Int? = 14, moment: WidgetMoment? = null, asksForTags: Boolean = false, setUp: Boolean = true) =
        WidgetModel(setUp, Dose.mg(today), doses, guide?.let(Dose::mg), favourites, moment, asksForTags)

    private fun show(model: WidgetModel, size: DpSize = DpSize(320.dp, 180.dp), checks: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(app)
            setAppWidgetSize(size)
            provideComposable { WidgetContent(model) }
            checks()
        }

    @Test
    fun todayAgainstTheGuide() = show(model()) {
        onNode(hasTextEqualTo("8")).assertExists()
        onNode(hasTextEqualTo("6${nbsp}mg left today")).assertExists()
        onNode(hasTextEqualTo("14${nbsp}mg guide")).assertExists()
    }

    @Test
    fun overTheGuideIsPlain() = show(model(today = 16)) {
        onNode(hasTextEqualTo("2${nbsp}mg over the guide")).assertExists()
    }

    @Test
    fun withoutAPlanItCountsTheDoses() = show(model(guide = null)) {
        onNode(hasTextEqualTo("3${nbsp}doses so far")).assertExists()
        onAllNodes(hasText("guide")).assertCountEquals(0)
    }

    @Test
    fun nothingYet() = show(model(today = 0, doses = 0, guide = null)) {
        onNode(hasTextEqualTo("Nothing logged yet today")).assertExists()
    }

    @Test
    fun anNrtFreeDayWithNothingLogged() = show(model(today = 0, doses = 0, guide = 0)) {
        onNode(hasTextEqualTo("NRT-free so far today")).assertExists()
    }

    @Test
    fun aTapOnAFavouriteLogsIt() = show(model()) {
        onNode(hasContentDescriptionEqualTo("Log Nicorette gum 2${nbsp}mg"))
            .assertHasRunCallbackClickAction<LogFavourite>(actionParametersOf(WidgetKeys.PRODUCT to GUM_2))
        onNode(hasTextEqualTo("Mouth spray 1${nbsp}mg")).assertExists()
    }

    @Test
    fun aNarrowWidgetHasTwoFavourites() = show(model(), size = DpSize(250.dp, 180.dp)) {
        onNode(hasTextEqualTo("Gum 4${nbsp}mg")).assertExists()
        onAllNodes(hasTextEqualTo("Mouth spray 1${nbsp}mg")).assertCountEquals(0)
    }

    @Test
    fun setToAskForTagsAFavouriteOpensThePicker() = show(model(asksForTags = true)) {
        onNode(hasContentDescriptionEqualTo("Log Nicorette gum 2${nbsp}mg"))
            .assertHasStartActivityClickAction<QuickTagsActivity>(actionParametersOf(WidgetKeys.PRODUCT to GUM_2))
    }

    @Test
    fun loggedWithUndoAndTags() {
        val entry = gumEntry()
        show(model(moment = WidgetMoment.Logged(entry, "Nicorette gum", warning = null))) {
            onNode(hasTextEqualTo("Logged 2${nbsp}mg of Nicorette gum")).assertExists()
            onNode(hasTextEqualTo("Undo")).assertExists()
            onNode(hasContentDescriptionEqualTo("Add tags to this dose"))
                .assertHasStartActivityClickAction<QuickTagsActivity>(actionParametersOf(WidgetKeys.ENTRY to entry.id))
            onAllNodes(hasTextEqualTo("Gum 2${nbsp}mg")).assertCountEquals(0)
        }
    }

    @Test
    fun theDuplicateQuestion() {
        val entry = gumEntry()
        show(model(moment = WidgetMoment.Duplicate(LogRequest(GUM_2), entry))) {
            onNode(hasTextEqualTo("Logged twice in a minute. Keep both?")).assertExists()
            onNode(hasTextEqualTo("Keep both")).assertExists()
            onNode(hasTextEqualTo("Discard")).assertExists()
        }
    }

    @Test
    fun beforeSetUp() = show(model(setUp = false)) {
        onNode(hasTextEqualTo("Finish setting up the app to log from here.")).assertExists()
    }

    @Test
    fun aShortWidgetLeavesTheMeterOutButKeepsTheGuide() = show(model(), size = DpSize(320.dp, 110.dp)) {
        onNode(hasTextEqualTo("6${nbsp}mg left today")).assertExists()
        onNode(hasTextEqualTo("Gum 2${nbsp}mg")).assertExists()
    }

    @Test
    fun aShortWidgetLetsTheMomentTakeTheHerosPlace() =
        show(model(moment = WidgetMoment.Duplicate(LogRequest(GUM_2), gumEntry())), size = DpSize(250.dp, 110.dp)) {
            onNode(hasTextEqualTo("Logged twice in a minute. Keep both?")).assertExists()
            onNode(hasTextEqualTo("Discard")).assertExists()
            onNode(hasTextEqualTo("Keep both")).assertExists()
            onAllNodes(hasTextEqualTo("8")).assertCountEquals(0)
        }

    @Test
    fun aRoomyWidgetKeepsTheHeroAboveTheMoment() =
        show(model(moment = WidgetMoment.Logged(gumEntry(), "Nicorette gum", warning = null)), size = DpSize(330.dp, 230.dp)) {
            onNode(hasTextEqualTo("8")).assertExists()
            onNode(hasTextEqualTo("Logged 2${nbsp}mg of Nicorette gum")).assertExists()
        }

    // How it lays out.

    private fun layout(model: WidgetModel, size: DpSize) = WidgetLayout.of(model, size, app)

    @Test
    fun everyTapTargetIsAtLeast48Dp() {
        assertTrue(WidgetSizes.PILL_HEIGHT >= 48.dp)
    }

    @Test
    fun aLabelsNoteIsShownInFullWhereItFits() {
        val note = WidgetMoment.Logged(gumEntry(), "Nicorette gum", warning = LabelWarning.OverDaily(units = 16, limit = 15))
        val shown = layout(model(moment = note), DpSize(330.dp, 150.dp))
        val text = "That's more than the pack's daily limit of 15. Worth a word with a pharmacist."
        assertEquals(text, shown.moment)
        assertEquals(TextRoom(app).lines(text, WidgetSizes.SMALL, bold = true, 302.dp), shown.momentLines)
    }

    @Test
    fun theGuidesLabelSitsUnderItsMarkerBesideWhatsLeft() {
        val shown = layout(model(), DpSize(330.dp, 230.dp))
        assertEquals(Hero.ROOMY, shown.hero)
        assertEquals(false, shown.guideOwnLine)
        // Centred under the marker, 70% along the 302 dp meter.
        val guideWidth = TextRoom(app).width("14${nbsp}mg guide", WidgetSizes.SMALL, bold = false)
        assertEquals((302 * 0.7f - guideWidth.value / 2), shown.guideStart.value, 0.5f)
    }

    @Test @Config(fontScale = 1.3f)
    fun withLargeTextOnANarrowWidgetTheGuidesLabelTakesALineOfItsOwn() {
        // Tall enough for the roomy layout even with the guide's label on a line of its own.
        val shown = layout(model(), DpSize(250.dp, 300.dp))
        assertEquals(Hero.ROOMY, shown.hero)
        assertEquals(true, shown.guideOwnLine)
        // Where there isn't the height for that, the guide's label goes under what's left, beside the amount.
        assertEquals(Hero.METER, layout(model(), DpSize(250.dp, 230.dp)).hero)
    }

    // The data behind it.

    @Test
    fun theModelFollowsTodayTheGuideAndTheQuickLog() {
        app.setUp()
        app.startGumPlan()
        app.setTime("2026-10-24T15:05")
        app.chew("2026-10-24T09:00", pieces = 2)
        val model = runBlocking { LastHitWidget.models(app, app.graph, app.quickLog).first { it.doses == 1 } }
        assertEquals(Dose.mg(8), model.today)
        assertEquals(Dose.mg(24), model.guide)
        assertEquals(listOf(GUM_2, GUM_4, QUICKMIST), model.favourites.map { it.productId })
        assertEquals("Gum 2${nbsp}mg", model.favourites.first().label)
        assertNull(model.moment)
    }

    private fun gumEntry() = Entry(
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
}
