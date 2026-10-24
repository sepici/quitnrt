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
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.DarkColors
import app.thelasthit.nrt.ui.theme.LastHitColors
import app.thelasthit.nrt.ui.theme.LightColors
import app.thelasthit.nrt.widget.WidgetSizes.AMOUNT_LARGE
import app.thelasthit.nrt.widget.WidgetSizes.GAP
import app.thelasthit.nrt.widget.WidgetSizes.METER
import app.thelasthit.nrt.widget.WidgetSizes.METER_GAP
import app.thelasthit.nrt.widget.WidgetSizes.PILL_HEIGHT
import app.thelasthit.nrt.widget.WidgetSizes.SIDE
import app.thelasthit.nrt.widget.WidgetSizes.SMALL
import app.thelasthit.nrt.widget.WidgetSizes.TICK
import app.thelasthit.nrt.widget.WidgetSizes.TICK_GAP

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

/**
 * The widget at its size: Today's hero above, as much of it as fits, and the favourites below,
 * or for a moment after a tap, what came of it. [WidgetLayout] works out what fits.
 */
@Composable
internal fun WidgetContent(model: WidgetModel?) {
    val size = LocalSize.current
    val layout = model?.let { WidgetLayout.of(it, size, LocalContext.current) }
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .tinted(R.drawable.widget_shape_hero, Tones.hero)
            .padding(horizontal = SIDE, vertical = (layout?.spacing ?: Spacing.of(size.height)).padding),
    ) {
        if (model == null || layout == null) return@Column
        val width = size.width - SIDE * 2
        if (layout.hero != Hero.NONE) {
            Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Top(model, layout, width)
            }
        }
        Bottom(model, layout)
    }
}

/** Today's hero, as much of it as the widget has room for. */
@Composable
private fun Top(model: WidgetModel, layout: WidgetLayout, width: Dp) {
    val guide = model.guide
    if (layout.hero == Hero.ROOMY && guide != null) {
        // As Today's hero has it: the label, the amount, the meter, and the guide's label under
        // its marker, beside what's left, or under it when they'd meet.
        Label()
        Amount(model.today, AMOUNT_LARGE)
        Spacer(GlanceModifier.height(4.dp))
        Meter(model.today, guide, width)
        Spacer(GlanceModifier.height(2.dp))
        val guideLabel = @Composable {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(GlanceModifier.width(layout.guideStart))
                GuideText(layout.guide.orEmpty(), lines = 1, end = false)
            }
        }
        if (layout.guideOwnLine) {
            guideLabel()
            Left(model.today <= guide, layout.left.orEmpty(), lines = 1, end = false)
        } else {
            Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Left(model.today <= guide, layout.left.orEmpty(), lines = 1, end = false)
                guideLabel()
            }
        }
        return
    }
    if (layout.labelled) Label()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Amount(model.today, layout.amountSize)
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(WidgetSizes.LABEL_GAP))
        Column(horizontalAlignment = Alignment.End) {
            if (guide == null) {
                Text(
                    layout.doses.orEmpty(),
                    style = small(Tones.onHero2, bold = false).copy(textAlign = TextAlign.End),
                    maxLines = layout.dosesLines,
                )
            } else {
                Left(model.today <= guide, layout.left.orEmpty(), layout.leftLines, end = true)
                if (layout.showGuide) GuideText(layout.guide.orEmpty(), layout.guideLines, end = true)
            }
        }
    }
    if (guide != null && layout.hero == Hero.METER) {
        Spacer(GlanceModifier.height(METER_GAP))
        Meter(model.today, guide, width)
    }
}

@Composable
private fun Label() {
    Text(LocalContext.current.getString(R.string.today_youve_had), style = small(Tones.onHero2, bold = false), maxLines = 1)
}

/**
 * "8 mg" at [size]. The "mg" sits on the amount's baseline, lined up from the top, so a short
 * widget that runs into the amount's bottom padding never moves it.
 */
@Composable
private fun Amount(today: Dose, size: Float) {
    val room = TextRoom(LocalContext.current)
    val unit = WidgetSizes.unitSize(size)
    Row(verticalAlignment = Alignment.Top) {
        Text(
            today.mgText(),
            style = TextStyle(color = Tones.onHero, fontSize = size.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            LocalContext.current.getString(R.string.unit_mg),
            style = TextStyle(color = Tones.onHero, fontSize = unit.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 4.dp, top = (room.baseline(size, bold = true) - room.baseline(unit, bold = true)).coerceAtLeast(0.dp)),
        )
    }
}

@Composable
private fun GuideText(text: String, lines: Int, end: Boolean) {
    Text(text, style = small(Tones.onHero2, bold = false).copy(textAlign = if (end) TextAlign.End else TextAlign.Start), maxLines = lines)
}

/** "6 mg left today" with the tick, which is the only green: at or under the guide. Over it is plain. */
@Composable
private fun Left(within: Boolean, text: String, lines: Int, end: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (within) {
            Box(GlanceModifier.size(TICK).tinted(R.drawable.widget_shape_dot, Tones.successOnHero), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_check), contentDescription = null, modifier = GlanceModifier.size(12.dp), colorFilter = ColorFilter.tint(Tones.hero))
            }
            Spacer(GlanceModifier.width(TICK_GAP))
        }
        Text(text, style = small(Tones.onHero, bold = true).copy(textAlign = if (end) TextAlign.End else TextAlign.Start), maxLines = lines)
    }
}

/**
 * The meter, as on Today: the used amount on the track, and the guide as a marker standing on
 * it, never a fill. The guide sits 70% along, or the track stretches for a day over it.
 */
@Composable
private fun Meter(today: Dose, guide: Dose, width: Dp) {
    val used = width * MeterMarks.usedAt(today, guide)
    val mark = width * MeterMarks.markAt(today, guide)
    Box(GlanceModifier.fillMaxWidth().height(METER), contentAlignment = Alignment.CenterStart) {
        Box(GlanceModifier.fillMaxWidth().height(12.dp).tinted(R.drawable.widget_shape_bar, Tones.heroTrack)) {}
        if (today > Dose.ZERO) {
            Box(GlanceModifier.width(maxOf(used, 12.dp)).height(12.dp).tinted(R.drawable.widget_shape_bar, Tones.heroUsed)) {}
        }
        Row(GlanceModifier.fillMaxWidth().height(METER)) {
            // A ring of the hero green sets the marker apart from the bar under it.
            Spacer(GlanceModifier.width(maxOf(mark - 4.dp, 0.dp)))
            Box(GlanceModifier.width(8.dp).height(METER).tinted(R.drawable.widget_shape_mark, Tones.hero), contentAlignment = Alignment.Center) {
                Box(GlanceModifier.width(4.dp).height(18.dp).tinted(R.drawable.widget_shape_mark, Tones.onHero)) {}
            }
        }
    }
}

/** The favourites, or for a moment after a tap, what came of it. */
@Composable
private fun ColumnScope.Bottom(model: WidgetModel, layout: WidgetLayout) {
    val context = LocalContext.current
    val modifier = GlanceModifier.fillMaxWidth().padding(top = layout.spacing.bottomGap)
    val moment = model.moment
    when {
        !model.setUp -> Note(context.getString(R.string.widget_set_up), modifier)
        moment is WidgetMoment.Logged -> Moment(layout) {
            Pill(
                context.getString(R.string.widget_tags),
                actionStartActivity<QuickTagsActivity>(actionParametersOf(WidgetKeys.ENTRY to moment.entry.id)),
                description = context.getString(R.string.widget_tags_description),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(GAP))
            Pill(
                context.getString(R.string.action_undo),
                actionRunCallback<UndoWidgetLog>(actionParametersOf(WidgetKeys.ENTRY to moment.entry.id)),
                filled = true,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        moment is WidgetMoment.Duplicate -> Moment(layout) {
            Pill(context.getString(R.string.widget_discard), actionRunCallback<DiscardWidgetLog>(), modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(GAP))
            Pill(context.getString(R.string.log_duplicate_keep), actionRunCallback<KeepBothWidgetLogs>(), filled = true, modifier = GlanceModifier.defaultWeight())
        }
        model.favourites.isEmpty() -> Note(context.getString(R.string.widget_no_favourites), modifier)
        else -> Row(modifier) {
            model.favourites.take(layout.favourites).forEachIndexed { index, favourite ->
                if (index > 0) Spacer(GlanceModifier.width(GAP))
                val parameters = actionParametersOf(WidgetKeys.PRODUCT to favourite.productId)
                val action = if (model.asksForTags) actionStartActivity<QuickTagsActivity>(parameters) else actionRunCallback<LogFavourite>(parameters)
                Pill(
                    favourite.label,
                    action,
                    icon = favourite.icon.takeIf { layout.withIcons },
                    description = favourite.description,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

/**
 * A moment: its text across the full width, and its two pills under it, where the favourites
 * were. When the widget has no room for the hero as well, the moment takes its place.
 */
@Composable
private fun ColumnScope.Moment(layout: WidgetLayout, pills: @Composable RowScope.() -> Unit) {
    val text = @Composable { modifier: GlanceModifier ->
        Text(layout.moment.orEmpty(), style = small(Tones.onHero, bold = true), maxLines = layout.momentLines, modifier = modifier)
    }
    if (layout.hero == Hero.NONE) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
            text(GlanceModifier.fillMaxWidth())
        }
    } else {
        text(GlanceModifier.fillMaxWidth().padding(top = layout.spacing.bottomGap))
    }
    Spacer(GlanceModifier.height(layout.spacing.momentGap))
    Row(GlanceModifier.fillMaxWidth()) { pills() }
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
            style = TextStyle(color = ink, fontSize = SMALL.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 2,
        )
    }
}

private fun small(color: ColorProvider, bold: Boolean) =
    TextStyle(color = color, fontSize = SMALL.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)

/** A rounded shape in [tone]: corners that also show on phones before Android 12. */
private fun GlanceModifier.tinted(@DrawableRes shape: Int, tone: ColorProvider): GlanceModifier =
    background(ImageProvider(shape), colorFilter = ColorFilter.tint(tone))
