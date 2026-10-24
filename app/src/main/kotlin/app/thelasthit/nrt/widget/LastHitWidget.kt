package app.thelasthit.nrt.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import app.thelasthit.nrt.LastHitApp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.reminders.TodayGuide
import app.thelasthit.nrt.quicklog.QuickLog
import app.thelasthit.nrt.quicklog.WidgetMoment
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.pillLabelText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate

/** One favourite on the widget: a quick-log product with its pill label. */
data class Favourite(val productId: String, val label: String, @param:DrawableRes val icon: Int, val description: String)

/** Everything the widget shows. */
data class WidgetModel(
    /** Set-up is finished, so there's something to log. */
    val setUp: Boolean,
    val today: Dose,
    val doses: Int,
    /** Today's guide while a plan runs: a marker on the meter, never a fill. */
    val guide: Dose?,
    /** The quick log's first few products, in order. */
    val favourites: List<Favourite>,
    val moment: WidgetMoment?,
    /** A tap on a favourite opens the tag picker after logging. */
    val asksForTags: Boolean,
)

/**
 * The home-screen widget (PLAN §11): today's mg against the guide, and 2 or 3 favourites that log
 * at a tap. It's drawn from the same data as Today, and follows the phone's light or dark mode in
 * the app's colours.
 */
class LastHitWidget : GlanceAppWidget() {

    /** The widget knows its exact size, so the meter and favourites fit it. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as LastHitApp
        val models = models(context, app.graph, app.quickLog)
        // Loaded before the first frame, so the widget never shows an empty block while it reads.
        val first = models.first()
        provideContent {
            val model by remember { models }.collectAsState(initial = first)
            WidgetContent(model)
        }
    }

    companion object {

        /** Draws every placed widget again. */
        suspend fun update(context: Context) {
            if (placed(context)) LastHitWidget().updateAll(context)
        }

        /**
         * Draws the widgets again whenever what they show changes, from any way in. Runs as long
         * as the app does.
         */
        suspend fun follow(context: Context, graph: DataGraph) {
            val app = context.applicationContext as LastHitApp
            models(context, graph, app.quickLog).distinctUntilChanged().drop(1).collect { update(context) }
        }

        /** Whether any widget is on a home screen, so nothing is drawn for none. */
        private fun placed(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LastHitWidgetReceiver::class.java)).isNotEmpty()

        /** How many favourites a widget this wide has room for. */
        const val MAX_FAVOURITES = 3

        @OptIn(ExperimentalCoroutinesApi::class)
        fun models(context: Context, graph: DataGraph, quickLog: QuickLog): Flow<WidgetModel> {
            val resources = context.resources
            val favourites = combine(graph.products.quickLog, graph.products.shortNames) { products, names ->
                val labels = PillLabel.of(products)
                products.take(MAX_FAVOURITES).map { product ->
                    val named = resources.getString(R.string.dose_named, names[product.id] ?: product.shortName, product.perUnit.mgText())
                    Favourite(
                        productId = product.id,
                        label = resources.pillLabelText(labels.getValue(product.id)),
                        icon = product.form.icon,
                        description = resources.getString(R.string.today_quick_log_description, named),
                    )
                }
            }
            return today(graph).flatMapLatest { date ->
                combine(
                    graph.entries.observeDay(date),
                    graph.programmes.observeCurrent().map { record -> TodayGuide.on(record?.plan, record?.status, date) },
                    combine(favourites, graph.settings.settings.map { it.onboardingComplete }, ::Pair),
                    graph.reminders.quickLog.map { it.widgetAsksForTags },
                    quickLog.moment,
                ) { day, guide, (favourites, setUp), asksForTags, moment ->
                    WidgetModel(setUp, day.total, day.entries.size, guide, favourites, moment, asksForTags)
                }
            }
        }

        /** Today's logical day in the phone's zone, moving on at each day start while it's watched. */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun today(graph: DataGraph): Flow<LocalDate> =
            graph.settings.settings.map { it.dayBoundary.dayStart }.distinctUntilChanged().flatMapLatest {
                flow {
                    while (true) {
                        val clock = graph.programmes.clock()
                        emit(clock.today)
                        val left = Duration.between(clock.now, clock.endOf(clock.today))
                        delay(left.toMillis().coerceAtLeast(MIN_WAIT_MS))
                    }
                }
            }.distinctUntilChanged()

        private const val MIN_WAIT_MS = 1_000L
    }
}

/** Puts [LastHitWidget] on the home screen. */
class LastHitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastHitWidget()
}
