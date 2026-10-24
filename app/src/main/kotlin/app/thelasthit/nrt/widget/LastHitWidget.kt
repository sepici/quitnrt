package app.thelasthit.nrt.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import java.util.Locale
import java.time.format.DateTimeFormatter
import android.text.format.DateFormat
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
import app.thelasthit.nrt.ui.format.pillDescription
import app.thelasthit.nrt.ui.format.pillLabelText
import app.thelasthit.nrt.ui.format.mgText
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
    /** "Last dose at 14:25, 2 mg of Nicorette gum", as Today has it, for a widget with room to spare. */
    val lastDose: String? = null,
    /** "Last dose at 14:25", for one with less. */
    val lastDoseShort: String? = null,
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
                    val label = labels.getValue(product.id)
                    Favourite(
                        productId = product.id,
                        label = resources.pillLabelText(label),
                        icon = product.form.icon,
                        // The words on the pill first, as on Today, then the product.
                        description = resources.pillDescription(label, names[product.id] ?: product.shortName),
                    )
                }
            }
            return today(graph).flatMapLatest { date ->
                combine(
                    combine(graph.entries.observeDay(date), graph.products.shortNames, ::Pair),
                    graph.programmes.observeCurrent().map { record -> TodayGuide.on(record?.plan, record?.status, date) },
                    combine(favourites, graph.settings.settings.map { it.onboardingComplete }, ::Pair),
                    graph.reminders.quickLog.map { it.widgetAsksForTags },
                    quickLog.moment,
                ) { (day, names), guide, (favourites, setUp), asksForTags, moment ->
                    val lastEntry = day.entries.maxByOrNull { it.occurredAt }
                    val at = lastEntry?.let { entry ->
                        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.UK)
                            .format(entry.occurredAt.atZone(entry.zone))
                    }
                    val last = lastEntry?.let { entry ->
                        resources.getString(R.string.today_last_dose, at, entry.total.mgText(), names[entry.productId] ?: entry.productName)
                    }
                    val lastShort = at?.let { resources.getString(R.string.widget_last_dose_short, it) }
                    WidgetModel(setUp, day.total, day.entries.size, guide, favourites, moment, asksForTags, last, lastShort)
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
