package app.thelasthit.nrt.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.quicklog.WidgetMoment
import app.thelasthit.nrt.ui.format.labelNoteShortText
import app.thelasthit.nrt.ui.format.labelNoteText
import app.thelasthit.nrt.ui.format.mgText
import kotlin.math.ceil

/** The widget's measures, shared by the layout and the drawing. */
internal object WidgetSizes {
    val SIDE = 14.dp

    /** Every tap target on the widget, as Today's pills are. */
    val PILL_HEIGHT = 48.dp

    /** Between pills. */
    val GAP = 6.dp

    /** The least room between two labels on one line, and between the amount and what's beside it. */
    val LABEL_GAP = 8.dp

    val METER = 22.dp
    val METER_GAP = 6.dp

    /** Above the last dose, under the roomy hero. */
    val LAST_DOSE_GAP = 12.dp
    val TICK = 16.dp
    val TICK_GAP = 5.dp

    /** Wide enough for a third favourite, at the normal font size. */
    val THREE_WIDE = 300.dp

    /** A favourite this wide has room for its form's icon as well as its label. */
    val ICON_PILL = 130.dp

    /** The small text: labels, the moment and the pills. */
    const val SMALL = 13f

    /** Today's amount, and the sizes it steps down through when a short widget has less room. */
    const val AMOUNT = 34f
    const val AMOUNT_LARGE = 42f
    val AMOUNT_STEPS = listOf(AMOUNT, 30f, 26f, 22f)

    /** The "mg" beside the amount, in proportion to it. */
    fun unitSize(amount: Float): Float = if (amount == AMOUNT_LARGE) 20f else amount * 18f / AMOUNT

    /**
     * How much a block of text can be cut at its edges without losing ink: the font's padding
     * below the descenders is a fraction of a dp.
     */
    val TEXT_SLACK = 0.5.dp
}

/**
 * The widget's padding and gaps, which give a little back as it gets shorter, so the 48 dp
 * pills leave the hero, or two lines of a moment, room above them.
 */
internal data class Spacing(val padding: Dp, val bottomGap: Dp, val momentGap: Dp) {
    companion object {
        fun of(height: Dp): Spacing = when {
            height < 130.dp -> Spacing(padding = 8.dp, bottomGap = 6.dp, momentGap = 3.dp)
            height < 160.dp -> Spacing(padding = 10.dp, bottomGap = 8.dp, momentGap = 6.dp)
            else -> Spacing(padding = 12.dp, bottomGap = 8.dp, momentGap = 6.dp)
        }
    }
}

/** What the hero part of the widget shows, from none to the most. */
internal enum class Hero {
    /** A moment takes the whole widget, as there isn't room for both without squeezing. */
    NONE,

    /** The amount, with what's left and the guide, or the doses, beside it. */
    SHORT,

    /** [SHORT] with the meter under it. */
    METER,

    /** Today's hero: the label, a large amount, the meter, and the guide's label under its marker. */
    ROOMY,
}

/**
 * How the widget lays out at its size and font scale. Glance can't measure text, so this works
 * out what fits from the text as the phone will draw it ([TextRoom]), and leaves out or moves
 * what doesn't, rather than letting it squeeze or clip.
 */
internal data class WidgetLayout(
    val spacing: Spacing,
    val hero: Hero,
    /** Today's "You've had" above the amount, where there's room. */
    val labelled: Boolean,
    /** The amount's size in the short layouts. */
    val amountSize: Float,
    /** "6 mg left today" or "2 mg over the guide", while a plan runs. */
    val left: String?,
    val leftLines: Int,
    /** "14 mg guide", while a plan runs. */
    val guide: String?,
    val guideLines: Int,
    /** The short layouts show the guide's label under what's left, when there's room. */
    val showGuide: Boolean,
    /** The roomy layout puts the guide's label on a line of its own when it would run into what's left. */
    val guideOwnLine: Boolean,
    /** Where the guide's label starts in the roomy layout, so it sits centred under its marker. */
    val guideStart: Dp,
    /** "3 doses so far", without a plan. */
    val doses: String?,
    val dosesLines: Int,
    /** "Logged 2 mg of Nicorette gum", the label's note or the duplicate question. */
    val moment: String?,
    val momentLines: Int,
    val favourites: Int,
    val withIcons: Boolean,
    /** What a screen reader says for the hero, in Today's words: "8 mg used. Daily guide 14 mg. 6 mg left today." */
    val heroDescription: String = "",
    /** The last dose under the roomy hero, where the widget is tall enough to leave a gap above the favourites. */
    val lastDose: String? = null,
    val lastDoseLines: Int = 1,
) {
    /** How the short layout's column beside the amount fits, at one size of the amount. */
    private data class Beside(val leftLines: Int, val guideLines: Int, val dosesLines: Int, val left: Dp, val guide: Dp, val doses: Dp)

    companion object {

        fun of(model: WidgetModel, size: DpSize, context: Context, room: TextRoom = TextRoom(context)): WidgetLayout {
            val resources = context.resources
            val s = WidgetSizes
            val spacing = Spacing.of(size.height)
            val width = size.width - s.SIDE * 2
            val content = size.height - spacing.padding * 2
            val guide = model.guide

            // What's at the bottom, and so how much room the hero has.
            val warning = (model.moment as? WidgetMoment.Logged)?.warning?.takeIf { model.setUp }
            val moment = model.moment.takeIf { model.setUp }?.let { moment ->
                when (moment) {
                    is WidgetMoment.Logged -> moment.warning?.let { resources.labelNoteText(it) }
                        ?: resources.getString(R.string.today_logged, moment.entry.total.mgText(), moment.name)
                    is WidgetMoment.Duplicate -> resources.getString(R.string.log_duplicate_title)
                }
            }
            val momentNeeds = moment?.let { room.lines(it, s.SMALL, bold = true, width) } ?: 0
            val bottom = when {
                moment != null -> spacing.bottomGap + room.height(s.SMALL, bold = true, momentNeeds) + spacing.momentGap + s.PILL_HEIGHT
                !model.setUp || model.favourites.isEmpty() -> spacing.bottomGap + room.height(s.SMALL, bold = false, lines = 2)
                else -> spacing.bottomGap + s.PILL_HEIGHT
            }
            val heroRoom = content - bottom

            // The words beside the amount.
            val within = guide != null && model.today <= guide
            val tick = if (within) s.TICK + s.TICK_GAP else 0.dp
            val left = guide?.let {
                when {
                    guide == Dose.ZERO && within -> resources.getString(R.string.widget_nrt_free)
                    within -> resources.getString(R.string.widget_left, Dose(guide.micrograms - model.today.micrograms).mgText())
                    else -> resources.getString(R.string.widget_over, Dose(model.today.micrograms - guide.micrograms).mgText())
                }
            }
            val guideText = guide?.let { resources.getString(R.string.widget_guide, it.mgText()) }
            val doses = when {
                guide != null -> null
                model.doses == 0 -> resources.getString(R.string.today_nothing_yet)
                else -> resources.getQuantityString(R.plurals.today_doses_so_far, model.doses, model.doses)
            }
            val mg = resources.getString(R.string.unit_mg)
            fun beside(amount: Float): Beside {
                val room0 = width - room.width(model.today.mgText(), amount, bold = true) - 4.dp -
                    room.width(mg, s.unitSize(amount), bold = true) - s.LABEL_GAP
                val leftLines = left?.let { room.lines(it, s.SMALL, bold = true, room0 - tick).coerceIn(1, 2) } ?: 1
                val guideLines = guideText?.let { room.lines(it, s.SMALL, bold = false, room0).coerceIn(1, 2) } ?: 1
                val dosesLines = doses?.let { room.lines(it, s.SMALL, bold = false, room0).coerceIn(1, 2) } ?: 1
                return Beside(
                    leftLines, guideLines, dosesLines,
                    left = maxOf(s.TICK, room.height(s.SMALL, bold = true, leftLines)),
                    guide = room.height(s.SMALL, bold = false, guideLines),
                    doses = room.height(s.SMALL, bold = false, dosesLines),
                )
            }
            /** The amount's ink, down to the "mg" beside it: a clipped bottom padding loses nothing. */
            fun amountInk(amount: Float): Dp = room.baseline(amount, bold = true) + room.descent(s.unitSize(amount), bold = true)

            val full = beside(s.AMOUNT)
            val fullBeside = if (guide == null) full.doses else full.left + full.guide
            val short = maxOf(amountInk(s.AMOUNT), fullBeside)
            val withMeter = maxOf(room.height(s.AMOUNT, bold = true, 1), fullBeside) + s.METER_GAP + s.METER
            val label = room.height(s.SMALL, bold = false, 1)

            // The roomy layout: the guide's label centred under its marker, and on a line of its
            // own if it would run into what's left.
            val mark = if (guide == null) 0.dp else width * MeterMarks.markAt(model.today, guide)
            val guideWidth = guideText?.let { room.width(it, s.SMALL, bold = false) } ?: 0.dp
            val guideStart = (mark - guideWidth / 2).coerceIn(0.dp, (width - guideWidth).coerceAtLeast(0.dp))
            val leftWidth = left?.let { tick + room.width(it, s.SMALL, bold = true) } ?: 0.dp
            val guideOwnLine = leftWidth + s.LABEL_GAP > guideStart
            val leftLine = maxOf(s.TICK, room.height(s.SMALL, bold = true, 1))
            val guideLine = room.height(s.SMALL, bold = false, 1)
            val guideRow = if (guideOwnLine) guideLine + leftLine else maxOf(leftLine, guideLine)
            val roomy = label + room.height(s.AMOUNT_LARGE, bold = true, 1) + 4.dp + s.METER + 2.dp + guideRow

            // The most the hero has room for. During a moment it shows in full or not at all, so
            // the moment never squeezes it; otherwise a short widget steps the amount down.
            var amountSize = s.AMOUNT
            var shown = full
            var showGuide = guide != null
            val hero = when {
                guide != null && heroRoom >= roomy -> Hero.ROOMY
                guide != null && heroRoom >= withMeter -> Hero.METER
                heroRoom + s.TEXT_SLACK >= short -> Hero.SHORT
                moment != null -> Hero.NONE
                else -> {
                    amountSize = s.AMOUNT_STEPS.firstOrNull { amountInk(it) <= heroRoom } ?: s.AMOUNT_STEPS.last()
                    shown = beside(amountSize)
                    showGuide = guide != null && shown.left + shown.guide <= heroRoom + s.TEXT_SLACK
                    Hero.SHORT
                }
            }
            val labelled = when (hero) {
                Hero.ROOMY -> true
                Hero.METER -> heroRoom >= label + withMeter
                Hero.SHORT -> guide == null && heroRoom >= label + withMeter
                Hero.NONE -> false
            }

            // With the hero gone, the moment has the whole widget, and shows what fits of it.
            var shownMoment = moment
            var momentLines = if (hero == Hero.NONE) {
                val textRoom = content - spacing.momentGap - s.PILL_HEIGHT
                (1..momentNeeds).lastOrNull { room.height(s.SMALL, bold = true, it) <= textRoom + s.TEXT_SLACK } ?: 1
            } else {
                momentNeeds
            }
            // The pack's note is the one thing that must be read in full, so where the whole of it
            // won't fit, the shorter one takes its place rather than being cut off.
            if (warning != null && momentLines < momentNeeds) {
                val short = resources.labelNoteShortText(warning)
                shownMoment = short
                momentLines = room.lines(short, s.SMALL, bold = true, width).coerceAtMost(momentLines)
            }

            // A tall widget's roomy hero leaves a gap above the favourites: the last dose fills it, as
            // on Today, or just its time where the whole line won't fit.
            val spare = heroRoom - roomy - s.LAST_DOSE_GAP
            fun fits(text: String?): Int? = text?.let { room.lines(it, s.SMALL, bold = false, width) }
                ?.takeIf { lines -> lines <= 2 && room.height(s.SMALL, bold = false, lines) <= spare + s.TEXT_SLACK }
            val (lastDose, lastDoseLines) = when {
                hero != Hero.ROOMY || moment != null -> null to 1
                fits(model.lastDose) != null -> model.lastDose to fits(model.lastDose)!!
                fits(model.lastDoseShort) != null -> model.lastDoseShort to fits(model.lastDoseShort)!!
                else -> null to 1
            }

            val heroDescription = if (guide == null) {
                resources.getString(R.string.widget_hero_description, model.today.mgText(), doses.orEmpty())
            } else {
                val status = when {
                    guide == Dose.ZERO && within -> resources.getString(R.string.today_plan_free_at)
                    guide == Dose.ZERO -> resources.getString(R.string.today_plan_free_over, model.today.mgText())
                    model.today < guide -> resources.getString(R.string.today_plan_left, Dose(guide.micrograms - model.today.micrograms).mgText())
                    within -> resources.getString(R.string.today_plan_at)
                    else -> resources.getString(R.string.today_plan_over, Dose(model.today.micrograms - guide.micrograms).mgText())
                }
                resources.getString(R.string.today_plan_meter_description, model.today.mgText(), guide.mgText(), status)
            }

            // Larger text needs a wider widget for a third favourite.
            val fontScale = resources.configuration.fontScale.coerceAtLeast(1f)
            val favourites = if (size.width >= s.THREE_WIDE * fontScale) 3 else 2
            return WidgetLayout(
                spacing = spacing,
                hero = hero,
                labelled = labelled,
                amountSize = amountSize,
                left = left,
                leftLines = if (hero == Hero.ROOMY) 1 else shown.leftLines,
                guide = guideText,
                guideLines = if (hero == Hero.ROOMY) 1 else shown.guideLines,
                showGuide = showGuide,
                guideOwnLine = guideOwnLine,
                guideStart = guideStart,
                doses = doses,
                dosesLines = shown.dosesLines,
                moment = shownMoment,
                momentLines = momentLines.coerceAtLeast(1),
                favourites = favourites,
                withIcons = (width - s.GAP * (favourites - 1)) / favourites >= s.ICON_PILL,
                heroDescription = heroDescription,
                lastDose = lastDose,
                lastDoseLines = lastDoseLines,
            )

        }
    }
}

/** Where the meter puts its marks, as a share of its width. */
internal object MeterMarks {
    /** The guide sits 70% along, or the track stretches for a day over it. */
    private fun scale(today: Dose, guide: Dose): Long = maxOf(guide.micrograms * 10 / 7, today.micrograms, 1)

    fun usedAt(today: Dose, guide: Dose): Float = today.micrograms.toFloat() / scale(today, guide)

    fun markAt(today: Dose, guide: Dose): Float = guide.micrograms.toFloat() / scale(today, guide)
}

/**
 * Text as the phone will draw it on the widget: its size after the font scale, and the default
 * font's widths and line heights, with the padding a TextView keeps above and below.
 */
internal class TextRoom(context: Context) {
    private val metrics = context.resources.displayMetrics

    private fun px(sp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)

    private fun dp(px: Float): Dp = (px / metrics.density).dp

    private fun paint(sp: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = px(sp)
        typeface = if (bold) Typeface.DEFAULT_BOLD else MEDIUM
    }

    /** How wide [text] is on one line. */
    fun width(text: String, sp: Float, bold: Boolean): Dp {
        val size = px(sp)
        val measured = paint(sp, bold).measureText(text)
        // Where the font can't be measured, as in tests that don't draw, an average letter. No
        // letter of a real font is this narrow, not even a space.
        return dp(if (measured < text.length * size * 0.12f) text.length * size * AVERAGE_LETTER else measured)
    }

    /** From the top of a TextView to its first baseline. */
    fun baseline(sp: Float, bold: Boolean): Dp {
        val size = px(sp)
        val top = -paint(sp, bold).fontMetrics.top
        return dp(if (top > size * 0.8f) top else size * TOP)
    }

    /** How far the letters reach below the baseline. */
    fun descent(sp: Float, bold: Boolean): Dp {
        val size = px(sp)
        val descent = paint(sp, bold).fontMetrics.descent
        return dp(if (descent > size * 0.1f) descent else size * DESCENT)
    }

    /** The height of a TextView of [lines] lines. */
    fun height(sp: Float, bold: Boolean, lines: Int): Dp {
        val size = px(sp)
        val metrics = paint(sp, bold).fontMetrics
        val first = (metrics.bottom - metrics.top).takeIf { it > size } ?: (size * (TOP + BOTTOM))
        val next = (metrics.descent - metrics.ascent).takeIf { it > size } ?: (size * LINE)
        return dp(first + next * (lines - 1).coerceAtLeast(0))
    }

    /** How many lines [text] takes at [width], wrapped at spaces as a TextView wraps it. */
    fun lines(text: String, sp: Float, bold: Boolean, width: Dp): Int {
        if (width <= 0.dp) return text.length
        val space = width(" ", sp, bold)
        var lines = 1
        var line = 0.dp
        for (word in text.split(' ')) {
            val w = width(word, sp, bold)
            when {
                line == 0.dp -> line = w
                line + space + w <= width -> line += space + w
                else -> {
                    lines++
                    line = w
                }
            }
            // A word longer than the line breaks within itself.
            if (line > width) {
                lines += ceil(line / width).toInt() - 1
                line = width * ((line / width) % 1f)
            }
        }
        return lines
    }

    private companion object {
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        // Roboto's own proportions, for when the font can't be measured.
        const val AVERAGE_LETTER = 0.56f
        const val TOP = 1.06f
        const val BOTTOM = 0.27f
        const val DESCENT = 0.24f
        const val LINE = 1.17f
    }
}
