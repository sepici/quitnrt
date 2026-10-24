package app.thelasthit.nrt.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.thelasthit.nrt.MainActivity
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.quicklog.WidgetMoment
import app.thelasthit.nrt.ui.format.labelNoteText
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.DarkColors
import app.thelasthit.nrt.ui.theme.LastHitColors
import app.thelasthit.nrt.ui.theme.LightColors

/**
 * The B tokens for the widget, each a light and dark pair, so the phone's own mode picks. The
 * widget is the hero block from Today: forest green, with the used amount in [heroUsed], a neutral
 * stone, never green or marigold, and the guide as a marker.
 */
private object Tones {
    private fun pair(pick: (LastHitColors) -> Color): ColorProvider = ColorProvider(day = pick(LightColors), night = pick(DarkColors))

    val hero = pair { it.hero }
    val onHero = pair { it.onHero }
    val onHero2 = pair { it.onHero2 }
    val heroTrack = pair { it.heroTrack }
    val heroUsed = pair { it.heroUsed }
    val heroChip = pair { it.heroChip }
    val successOnHero = pair { it.successOnHero }
}

/** Heights at which the widget has room for more: the meter, then the hero's own layout. */
private val METER_HEIGHT = 140.dp
private val ROOMY_HEIGHT = 220.dp

/** Wide enough for a third favourite. */
private val THREE_WIDE = 300.dp

/** A favourite this wide has room for its form's icon as well as its label. */
private val ICON_PILL = 130.dp

private val SIDE = 14.dp
private val GAP = 6.dp
private val PILL_HEIGHT = 44.dp

@Composable
internal fun WidgetContent(model: WidgetModel?) {
    val size = LocalSize.current
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .tinted(R.drawable.widget_shape_hero, Tones.hero)
            .padding(horizontal = SIDE, vertical = 12.dp),
    ) {
        if (model == null) return@Column
        val width = size.width - SIDE * 2
        Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            val guide = model.guide
            if (size.height >= ROOMY_HEIGHT && guide != null) {
                // As Today's hero has it: the label, the amount, the meter, then what's left.
                Label()
                Amount(model.today, large = true)
                Spacer(GlanceModifier.height(4.dp))
                Meter(model.today, guide, width)
                Spacer(GlanceModifier.height(2.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Left(model.today, guide)
                    Spacer(GlanceModifier.defaultWeight())
                    GuideText(guide)
                }
            } else {
                if (size.height >= ROOMY_HEIGHT) Label()
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Amount(model.today, large = false)
                    Spacer(GlanceModifier.defaultWeight())
                    Column(horizontalAlignment = Alignment.End) {
                        if (guide == null) {
                            Doses(model.doses)
                        } else {
                            Left(model.today, guide)
                            GuideText(guide)
                        }
                    }
                }
                if (guide != null && size.height >= METER_HEIGHT) {
                    Spacer(GlanceModifier.height(6.dp))
                    Meter(model.today, guide, width)
                }
            }
        }
        // Larger text needs a wider widget for a third.
        val fontScale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
        val favourites = if (size.width >= THREE_WIDE * fontScale) 3 else 2
        Bottom(model, favourites, withIcons = (width - GAP * (favourites - 1)) / favourites >= ICON_PILL)
    }
}

@Composable
private fun Label() {
    Text(LocalContext.current.getString(R.string.today_youve_had), style = small(Tones.onHero2, bold = false))
}

/** "8 mg", larger when the widget has the room. */
@Composable
private fun Amount(today: Dose, large: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            today.mgText(),
            style = TextStyle(color = Tones.onHero, fontSize = if (large) 42.sp else 34.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            LocalContext.current.getString(R.string.unit_mg),
            style = TextStyle(color = Tones.onHero, fontSize = if (large) 20.sp else 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 4.dp, bottom = if (large) 7.dp else 5.dp),
        )
    }
}

/** Without a plan there's nothing to measure against, so it counts the doses, as Today does. */
@Composable
private fun Doses(doses: Int) {
    val context = LocalContext.current
    val text = if (doses == 0) {
        context.getString(R.string.today_nothing_yet)
    } else {
        context.resources.getQuantityString(R.plurals.today_doses_so_far, doses, doses)
    }
    Text(text, style = small(Tones.onHero2, bold = false), maxLines = 2)
}

@Composable
private fun GuideText(guide: Dose) {
    Text(LocalContext.current.getString(R.string.widget_guide, guide.mgText()), style = small(Tones.onHero2, bold = false), maxLines = 1)
}

/** "6 mg left today" with the tick, which is the only green: at or under the guide. Over it is plain. */
@Composable
private fun Left(today: Dose, guide: Dose) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        val within = today <= guide
        if (within) {
            Box(GlanceModifier.size(16.dp).tinted(R.drawable.widget_shape_dot, Tones.successOnHero), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_check), contentDescription = null, modifier = GlanceModifier.size(12.dp), colorFilter = ColorFilter.tint(Tones.hero))
            }
            Spacer(GlanceModifier.width(5.dp))
        }
        val text = when {
            guide == Dose.ZERO && within -> context.getString(R.string.widget_nrt_free)
            within -> context.getString(R.string.widget_left, Dose(guide.micrograms - today.micrograms).mgText())
            else -> context.getString(R.string.widget_over, Dose(today.micrograms - guide.micrograms).mgText())
        }
        Text(text, style = small(Tones.onHero, bold = true), maxLines = 1)
    }
}

/**
 * The meter, as on Today: the used amount on the track, and the guide as a marker standing on
 * it, never a fill. The guide sits 70% along, or the track stretches for a day over it.
 */
@Composable
private fun Meter(today: Dose, guide: Dose, width: Dp) {
    val scale = maxOf(guide.micrograms * 10 / 7, today.micrograms, 1)
    val used = width * (today.micrograms.toFloat() / scale)
    val mark = width * (guide.micrograms.toFloat() / scale)
    Box(GlanceModifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
        Box(GlanceModifier.fillMaxWidth().height(12.dp).tinted(R.drawable.widget_shape_bar, Tones.heroTrack)) {}
        if (today > Dose.ZERO) {
            Box(GlanceModifier.width(maxOf(used, 12.dp)).height(12.dp).tinted(R.drawable.widget_shape_bar, Tones.heroUsed)) {}
        }
        Row(GlanceModifier.fillMaxWidth().height(22.dp)) {
            // A ring of the hero green sets the marker apart from the bar under it.
            Spacer(GlanceModifier.width(maxOf(mark - 4.dp, 0.dp)))
            Box(GlanceModifier.width(8.dp).height(22.dp).tinted(R.drawable.widget_shape_mark, Tones.hero), contentAlignment = Alignment.Center) {
                Box(GlanceModifier.width(4.dp).height(18.dp).tinted(R.drawable.widget_shape_mark, Tones.onHero)) {}
            }
        }
    }
}

/** The favourites, or for a moment after a tap, what came of it. */
@Composable
private fun Bottom(model: WidgetModel, favourites: Int, withIcons: Boolean) {
    val context = LocalContext.current
    val modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)
    val moment = model.moment
    when {
        !model.setUp -> Note(context.getString(R.string.widget_set_up), modifier)
        moment is WidgetMoment.Logged -> {
            val text = moment.warning?.let { context.resources.labelNoteText(it) }
                ?: context.getString(R.string.today_logged, moment.entry.total.mgText(), moment.name)
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = small(Tones.onHero, bold = true), maxLines = 3, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(GAP))
                Pill(
                    context.getString(R.string.widget_tags),
                    actionStartActivity<QuickTagsActivity>(actionParametersOf(WidgetKeys.ENTRY to moment.entry.id)),
                    description = context.getString(R.string.widget_tags_description),
                )
                Spacer(GlanceModifier.width(GAP))
                Pill(context.getString(R.string.action_undo), actionRunCallback<UndoWidgetLog>(actionParametersOf(WidgetKeys.ENTRY to moment.entry.id)), filled = true)
            }
        }
        moment is WidgetMoment.Duplicate -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.log_duplicate_title), style = small(Tones.onHero, bold = true), maxLines = 3, modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(GAP))
            Pill(context.getString(R.string.widget_discard), actionRunCallback<DiscardWidgetLog>())
            Spacer(GlanceModifier.width(GAP))
            Pill(context.getString(R.string.log_duplicate_keep), actionRunCallback<KeepBothWidgetLogs>(), filled = true)
        }
        model.favourites.isEmpty() -> Note(context.getString(R.string.widget_no_favourites), modifier)
        else -> Row(modifier) {
            model.favourites.take(favourites).forEachIndexed { index, favourite ->
                if (index > 0) Spacer(GlanceModifier.width(GAP))
                val parameters = actionParametersOf(WidgetKeys.PRODUCT to favourite.productId)
                val action = if (model.asksForTags) actionStartActivity<QuickTagsActivity>(parameters) else actionRunCallback<LogFavourite>(parameters)
                Pill(
                    favourite.label,
                    action,
                    icon = favourite.icon.takeIf { withIcons },
                    description = favourite.description,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String, modifier: GlanceModifier) {
    Text(text, style = small(Tones.onHero2, bold = false), maxLines = 3, modifier = modifier.clickable(actionStartActivity<MainActivity>()))
}

/**
 * A pill from Today's hero: forest on forest, or [filled] light for the main answer, as the Log
 * pill is. A label that doesn't fit goes onto two lines, so "Gum 2 mg" and "Gum 4 mg" never
 * shorten to the same "Gum".
 */
@Composable
private fun Pill(
    label: String,
    action: Action,
    @DrawableRes icon: Int? = null,
    filled: Boolean = false,
    description: String? = null,
    modifier: GlanceModifier = GlanceModifier,
) {
    val ink = if (filled) Tones.hero else Tones.onHero
    Row(
        modifier
            .height(PILL_HEIGHT)
            .tinted(R.drawable.widget_shape_pill, if (filled) Tones.onHero else Tones.heroChip)
            .clickable(action)
            .padding(horizontal = 10.dp)
            .then(if (description != null) GlanceModifier.semantics { contentDescription = description } else GlanceModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(ink))
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(
            label,
            style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 2,
        )
    }
}

private fun small(color: ColorProvider, bold: Boolean) =
    TextStyle(color = color, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)

/** A rounded shape in [tone]: corners that also show on phones before Android 12. */
private fun GlanceModifier.tinted(@DrawableRes shape: Int, tone: ColorProvider): GlanceModifier =
    background(ImageProvider(shape), colorFilter = ColorFilter.tint(tone))
