package app.thelasthit.nrt.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.insights.ByProduct
import app.thelasthit.nrt.domain.insights.TimeOfDay
import app.thelasthit.nrt.domain.insights.Triggers
import app.thelasthit.nrt.ui.components.IconTile
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.plural
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.format.unitsName
import app.thelasthit.nrt.ui.theme.LastHit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign

/** Below this many doses, a part of the day reads as a count ("3 of 5"), since a share of so few says little. */
private const val FEW_DOSES = 10

/** How many tags the list shows, most used first. */
private const val TOP_TAGS = 5

/**
 * Triggers: the most used tags with their change against the period before, each group's share,
 * the top tag in each part of the day, and how many doses had no tag. Patches are left out.
 */
@Composable
internal fun TriggersSection(state: InsightsState) {
    val triggers = state.triggers ?: return
    val period = periodBefore(state.range)
    val attribute = state.type?.let { typeAttribute(it) }
    val title = if (attribute == null) stringResource(R.string.insights_triggers_title) else stringResource(R.string.insights_triggers_title_type, attribute).sentence()
    Section(title = title, action = {}) {
        Meta(
            listOfNotNull(
                when {
                    attribute == null && state.comparesFrom == null -> stringResource(R.string.insights_triggers_meta, period)
                    attribute == null -> stringResource(R.string.insights_triggers_meta_plain)
                    state.comparesFrom == null -> stringResource(R.string.insights_triggers_meta_type, attribute, period)
                    else -> stringResource(R.string.insights_triggers_meta_plain_type, attribute)
                },
                if (state.patchesLeftOut) stringResource(R.string.insights_patches_not_counted) else null,
                state.comparesFrom?.let { stringResource(R.string.insights_changes_against, period, keptTogether(it)) },
            ).joinToString(" "),
        )
        when {
            triggers.doses == 0 -> Meta(stringResource(R.string.insights_table_none), top = 14.dp)
            triggers.tags.isEmpty() -> Meta(stringResource(R.string.insights_triggers_none), top = 14.dp)
            else -> {
                Column(Modifier.padding(top = 8.dp)) {
                    triggers.tags.take(TOP_TAGS).forEachIndexed { index, tag ->
                        if (index > 0) HorizontalDivider(color = LastHit.colors.line)
                        TagRow(tag, tagName(state, tag.key))
                    }
                }
                SubHeading(stringResource(R.string.insights_by_group))
                Groups(triggers.groups)
                SubHeading(stringResource(R.string.insights_by_time_of_day))
                // One format for the whole list: counts when any part has too few doses for a share to mean much.
                val counts = TimeOfDay.entries.any { (triggers.partDoses[it] ?: 0) in 1 until FEW_DOSES }
                Column(Modifier.padding(top = 4.dp)) {
                    TimeOfDay.entries.forEachIndexed { index, part ->
                        if (index > 0) HorizontalDivider(color = LastHit.colors.line)
                        PartRow(state, triggers, part, counts)
                    }
                }
                Untagged(triggers)
            }
        }
    }
}

@Composable
private fun tagName(state: InsightsState, key: String): String = state.tags[key]?.let { tagText(it) } ?: tagText(key)

/** A tag's name and share of doses, with a bar and its change in points when there's one. */
@Composable
private fun TagRow(tag: Triggers.TagShare, name: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 13.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, style = rowTitle, color = LastHit.colors.text, modifier = Modifier.weight(1f).alignByBaseline())
            Text(percentText(tag.share), style = figure, color = LastHit.colors.text, modifier = Modifier.alignByBaseline())
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ShareBar(tag.share, Modifier.weight(1f))
            tag.change?.let { change ->
                val points = pluralStringResource(R.plurals.insights_points, abs(change), abs(change))
                Delta(
                    direction = change.sign,
                    text = if (change == 0) stringResource(R.string.insights_no_change) else points,
                    spoken = when {
                        change > 0 -> stringResource(R.string.insights_up, points)
                        change < 0 -> stringResource(R.string.insights_down, points)
                        else -> stringResource(R.string.insights_no_change)
                    },
                    modifier = Modifier.widthIn(min = 92.dp),
                )
            }
        }
    }
}

/** Each group's share of doses in a row of five, divided by hairlines. */
@Composable
private fun Groups(groups: List<Triggers.GroupShare>) {
    val colors = LastHit.colors
    val measurer = rememberTextMeasurer()
    val figureStyle = LastHit.type.title.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
    val nameStyle = LastHit.type.caption
    val figures = groups.map { percentText(it.share) }
    val names = groups.map { stringResource(it.group.text) }
    val density = LocalDensity.current
    // The widest figure or name, plus a cell's padding, so no name ever breaks inside a word.
    val needed = with(density) {
        (figures.map { measurer.measure(it, figureStyle).size.width } + names.map { measurer.measure(it, nameStyle).size.width })
            .max().toDp() + 14.dp
    }
    BoxWithConstraints(Modifier.padding(top = 10.dp)) {
        // Five across as the mockups have them, or three then two when a large font needs the room.
        val perRow = listOf(groups.size, 3, 2, 1).first { it == 1 || maxWidth / it >= needed }
        Column {
            HorizontalDivider(color = colors.line)
            groups.indices.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    for (slot in 0 until perRow) {
                        val index = row.getOrNull(slot)
                        if (slot > 0 && index != null) VerticalDivider(color = colors.line)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = if (slot > 0) 10.dp else 0.dp, top = 10.dp, bottom = 11.dp, end = 4.dp)
                                .semantics(mergeDescendants = true) {},
                        ) {
                            if (index != null) {
                                Text(figures[index], style = figureStyle, color = colors.text, maxLines = 1)
                                Text(names[index], style = nameStyle, color = colors.text2, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.line)
            }
        }
    }
}

/** A part of the day and its hours, beside its top tag and how much of that part it covers, as a count or a share. */
@Composable
private fun PartRow(state: InsightsState, triggers: Triggers, part: TimeOfDay, counts: Boolean) {
    val colors = LastHit.colors
    val timeFormat = rememberTimeFormatter()
    val next = TimeOfDay.entries[(part.ordinal + 1) % TimeOfDay.entries.size]
    val doses = triggers.partDoses[part] ?: 0
    val top = triggers.byTimeOfDay[part]?.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(stringResource(part.text), style = rowTitle, color = colors.text)
            Text(
                stringResource(R.string.insights_time_range, timeFormat.format(part.from), timeFormat.format(next.from)),
                style = LastHit.type.caption,
                color = colors.text2,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            when {
                doses == 0 -> Text(stringResource(R.string.insights_part_none), style = LastHit.type.caption, color = colors.text2, textAlign = TextAlign.End)
                top == null -> Text(stringResource(R.string.insights_part_untagged), style = LastHit.type.caption, color = colors.text2, textAlign = TextAlign.End)
                else -> {
                    Text(tagName(state, top.key), style = rowTitle, color = colors.text, textAlign = TextAlign.End)
                    Text(
                        if (counts) {
                            pluralStringResource(part.count, doses, top.doses, doses)
                        } else {
                            stringResource(part.share, percentText(top.share))
                        },
                        style = LastHit.type.caption,
                        color = colors.text2,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** "11% of doses had no tag.", with the figure in bold. */
@Composable
private fun Untagged(triggers: Triggers) {
    val colors = LastHit.colors
    val style = LastHit.type.label
    val text: AnnotatedString = when {
        triggers.untagged == 0 -> AnnotatedString(stringResource(R.string.insights_all_tagged))
        triggers.doses < FEW_DOSES -> AnnotatedString(
            pluralStringResource(R.plurals.insights_untagged_count, triggers.doses, triggers.untagged, triggers.doses),
        )
        else -> boldFirst(stringResource(R.string.insights_untagged_share, PLACEHOLDER), percentText(triggers.untaggedShare), colors.text)
    }
    Text(text, style = style, color = colors.text2, modifier = Modifier.padding(top = 18.dp))
}

private const val PLACEHOLDER = "\u0000"

/** [template] with its [PLACEHOLDER] swapped for [figure], set in extra bold and [colour]. */
private fun boldFirst(template: String, figure: String, colour: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val at = template.indexOf(PLACEHOLDER)
    append(template.substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = colour)) { append(figure) }
    append(template.substring(at + PLACEHOLDER.length))
}

/**
 * Habits: when the first dose comes, and the average and longest gaps between doses, each with
 * its change against the period before. Patches are left out.
 */
@Composable
internal fun HabitsSection(state: InsightsState) {
    val habits = state.habits ?: return
    val period = periodBefore(state.range)
    val timeFormat = rememberTimeFormatter()
    val attribute = state.type?.let { typeAttribute(it) }
    val title = if (attribute == null) stringResource(R.string.insights_habits_title) else stringResource(R.string.insights_habits_title_type, attribute).sentence()
    Section(title = title, action = {}) {
        Meta(
            listOfNotNull(
                if (state.comparesFrom == null) stringResource(R.string.insights_habits_against, period) else null,
                when {
                    attribute != null -> stringResource(R.string.insights_habits_gaps_type, attribute)
                    state.patchesLeftOut -> stringResource(R.string.insights_habits_gaps_patches)
                    else -> stringResource(R.string.insights_habits_gaps)
                },
                state.comparesFrom?.let { stringResource(R.string.insights_changes_against, period, keptTogether(it)) },
            ).joinToString(" "),
        )
        val first = habits.firstDose
        if (first == null) {
            Meta(stringResource(R.string.insights_table_none), top = 14.dp)
        } else {
            val gap = habits.averageGap
            val firstValue = AnnotatedString(timeFormat.format(first))
            val gapValue = gap?.let { durationValue(it.toMinutes().toInt()) }
            val firstChange = habits.firstDoseChange?.let { timeChangeText(it, later = R.string.insights_later, earlier = R.string.insights_earlier, same = R.string.insights_same_time) }
            val gapChange = habits.averageGapChange?.let { timeChangeText(it, later = R.string.insights_longer, earlier = R.string.insights_shorter, same = R.string.insights_no_change) }
            val measurer = rememberTextMeasurer()
            val changeStyle = changeText
            val density = LocalDensity.current
            // The widest figure or change, with an arrow and the tile's padding, so a change never wraps away from its arrow.
            val needed = with(density) {
                val values = listOfNotNull(firstValue, gapValue).map { measurer.measure(it, tileValue).size.width.toDp() }
                val changes = listOfNotNull(firstChange, gapChange).map { measurer.measure(it, changeStyle).size.width.toDp() + 16.sp.toDp() + 4.dp }
                (values + changes).max() + 32.dp
            }
            BoxWithConstraints(Modifier.padding(top = 14.dp)) {
                // Side by side as the mockups have them, or one a row when a large font needs the room.
                val stacked = needed > (maxWidth - 8.dp) / 2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val firstTile: @Composable (Modifier) -> Unit = { modifier ->
                        Tile(stringResource(R.string.insights_first_dose), modifier) {
                            TileValue(firstValue)
                            habits.firstDoseChange?.let { TimeChange(it, later = R.string.insights_later, earlier = R.string.insights_earlier, same = R.string.insights_same_time) }
                        }
                    }
                    val gapTile: @Composable (Modifier) -> Unit = { modifier ->
                        Tile(stringResource(R.string.insights_average_gap), modifier) {
                            if (gap == null || gapValue == null) {
                                Text(
                                    stringResource(R.string.insights_no_gaps),
                                    style = tileValue.copy(fontSize = 20.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
                                    color = LastHit.colors.text2,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            } else {
                                TileValue(gapValue, durationSpoken(gap.toMinutes().toInt()))
                                habits.averageGapChange?.let { TimeChange(it, later = R.string.insights_longer, earlier = R.string.insights_shorter, same = R.string.insights_no_change) }
                            }
                        }
                    }
                    if (stacked) {
                        firstTile(Modifier.fillMaxWidth())
                        gapTile(Modifier.fillMaxWidth())
                    } else {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            firstTile(Modifier.weight(1f).fillMaxHeight())
                            gapTile(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    habits.longestGap?.let { longest ->
                        Tile(stringResource(R.string.insights_longest_gap), Modifier.fillMaxWidth()) {
                            val minutes = longest.length.toMinutes().toInt()
                            TileValue(durationValue(minutes), durationSpoken(minutes))
                            Text(
                                stringResource(R.string.insights_gap_when, DayTitleFormatter.format(longest.date), timeFormat.format(longest.from), timeFormat.format(longest.to)),
                                style = LastHit.type.label,
                                color = LastHit.colors.text,
                            )
                            habits.longestGapChange?.let { TimeChange(it, later = R.string.insights_longer, earlier = R.string.insights_shorter, same = R.string.insights_no_change) }
                        }
                    }
                }
            }
        }
    }
}

/** A habit's figure in a white tile with a hairline edge, its name above. */
@Composable
private fun Tile(label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 15.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.text2)
        content()
    }
}

@Composable
private fun TileValue(value: AnnotatedString, spoken: String? = null) {
    Text(
        value,
        style = tileValue,
        color = LastHit.colors.text,
        modifier = Modifier
            .padding(top = 2.dp)
            .then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier),
    )
}

/** "4 min later", "No change": a change in minutes, later or longer when positive. */
@Composable
private fun timeChangeText(minutes: Int, later: Int, earlier: Int, same: Int): String {
    val amount = abs(minutes)
    return when {
        minutes > 0 -> stringResource(later, durationText(amount))
        minutes < 0 -> stringResource(earlier, durationText(amount))
        else -> stringResource(same)
    }
}

/** A change in minutes: later or longer when positive, with an arrow, and [same] for none. */
@Composable
private fun TimeChange(minutes: Int, later: Int, earlier: Int, same: Int) {
    val amount = abs(minutes)
    Delta(
        direction = minutes.sign,
        text = timeChangeText(minutes, later, earlier, same),
        spoken = when {
            minutes > 0 -> stringResource(later, durationSpoken(amount))
            minutes < 0 -> stringResource(earlier, durationSpoken(amount))
            else -> stringResource(same)
        },
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** "1 h 15 min", "2 h" or "40 min". */
@Composable
private fun durationText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> stringResource(R.string.insights_hours_minutes, hours, rest)
        hours > 0 -> stringResource(R.string.insights_hours, hours)
        else -> stringResource(R.string.insights_minutes, rest)
    }
}

/** [durationText] in words, for screen readers, which would read "h" as a letter. */
@Composable
private fun durationSpoken(minutes: Int): String {
    val resources = LocalResources.current
    val hours = minutes / 60
    val rest = minutes % 60
    return listOfNotNull(
        if (hours > 0) resources.getQuantityString(R.plurals.spoken_hours, hours, hours) else null,
        if (rest > 0 || hours == 0) resources.getQuantityString(R.plurals.spoken_minutes, rest, rest) else null,
    ).joinToString(" ")
}

/** A duration as a tile shows it: big numbers with the units a size down, "3 h 30 min". */
@Composable
private fun durationValue(minutes: Int): AnnotatedString {
    val hours = minutes / 60
    val rest = minutes % 60
    val small = SpanStyle(fontSize = 18.sp, letterSpacing = (-0.3).sp)
    val h = stringResource(R.string.insights_unit_h)
    val min = stringResource(R.string.insights_unit_min)
    return buildAnnotatedString {
        if (hours > 0) {
            append(hours.toString())
            append(' ')
            withStyle(small) { append(h) }
        }
        if (rest > 0 || hours == 0) {
            if (hours > 0) append(' ')
            append(rest.toString())
            append(' ')
            withStyle(small) { append(min) }
        }
    }
}

/**
 * By product: each product's share of the mg and how much of it a day, most mg first. Patches
 * count here, and say on how many days they were used.
 */
@Composable
internal fun ByProductSection(state: InsightsState) {
    val byProduct = state.byProduct ?: return
    val type = state.type
    val title = when {
        type == null -> stringResource(R.string.insights_products_title)
        // "Other product products" would say it twice.
        type.word == null -> stringResource(R.string.insights_products_title_other)
        else -> stringResource(R.string.insights_products_title_type, typeAttribute(type)).sentence()
    }
    Section(title = title, action = {}) {
        // With one product its share is all of it, so the row says only how much a day.
        val single = byProduct.products.size == 1
        val noun = type?.let { typeNoun(it) }
        Meta(
            when {
                single -> stringResource(R.string.insights_products_meta_single)
                noun == null -> stringResource(R.string.insights_products_meta)
                else -> stringResource(R.string.insights_products_meta_type, noun)
            },
        )
        if (byProduct.products.isEmpty()) {
            Meta(stringResource(R.string.insights_table_none), top = 14.dp)
        } else {
            Column(Modifier.padding(top = 8.dp)) {
                byProduct.products.forEachIndexed { index, product ->
                    if (index > 0) HorizontalDivider(color = LastHit.colors.line)
                    ProductRow(product, state.productDays, showShare = !single)
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: ByProduct.Share, days: Int, showShare: Boolean) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(product.form.icon, size = 40.dp, dose = true, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(product.name, style = rowTitle, color = colors.text, modifier = Modifier.weight(1f).alignByBaseline())
                if (showShare) Text(percentText(product.share), style = figure, color = colors.text, modifier = Modifier.alignByBaseline())
            }
            Text(unitsText(product, days), style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            if (showShare) ShareBar(product.share, Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * How much a day: "1.3 pieces a day", "1 spray a day", or for a patch, one a day on the days it's
 * on, "Used on 7 of 29 days". With today the only day so far, what's been used so far today.
 */
@Composable
private fun unitsText(product: ByProduct.Share, days: Int): String {
    if (days == 0) return stringResource(R.string.insights_so_far_today, pluralStringResource(product.unit.plural, product.units, product.units))
    val tenths = (product.unitsPerDay * 10).roundToLong()
    val number = if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    val period = pluralStringResource(R.plurals.insights_days, days, days)
    return when {
        product.form.isBackground -> stringResource(R.string.insights_used_on, product.days, period)
        tenths == 0L -> stringResource(R.string.insights_units_in, pluralStringResource(product.unit.plural, product.units, product.units), period)
        tenths == 10L -> stringResource(R.string.insights_unit_a_day, pluralStringResource(product.unit.plural, 1, 1))
        else -> stringResource(R.string.insights_units_a_day, number, stringResource(unitsName(product.unit)))
    }
}

/** A thin bar of a share, in the stone of nicotine used, on a sunk track. */
@Composable
private fun ShareBar(share: Double, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(colors.sunk),
    ) {
        Box(
            Modifier
                .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(colors.used),
        )
    }
}

/**
 * A change: an arrow up or down, or a dash for none, in the text colour, then [text]. Never
 * green or red, since fewer doses with a tag isn't a score. Screen readers hear [spoken].
 */
@Composable
private fun Delta(direction: Int, text: String, spoken: String, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
    ) {
        ChangeArrow(
            when {
                direction > 0 -> R.drawable.ic_arrow_up
                direction < 0 -> R.drawable.ic_arrow_down
                else -> R.drawable.ic_minus
            },
        )
        Text(text, style = changeText, color = colors.text2)
    }
}

private val changeText
    @Composable get() = LastHit.type.label.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
        color = LastHit.colors.text2,
        modifier = Modifier.padding(top = 24.dp),
    )
}

/** "23%", or "Under 1%" for a share that rounds to nothing but isn't. */
@Composable
private fun percentText(share: Double): String {
    val whole = (share * 100).roundToInt()
    return if (share > 0 && whole == 0) stringResource(R.string.insights_under_one_percent, 1) else stringResource(R.string.insights_percent, whole)
}

private val rowTitle
    @Composable get() = LastHit.type.title.copy(fontWeight = FontWeight.Bold)

private val figure
    @Composable get() = LastHit.type.title.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)

private val tileValue
    @Composable get() = LastHit.type.headline.copy(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-1).sp)

private val TimeOfDay.text: Int
    get() = when (this) {
        TimeOfDay.MORNING -> R.string.insights_morning
        TimeOfDay.AFTERNOON -> R.string.insights_afternoon
        TimeOfDay.EVENING -> R.string.insights_evening
        TimeOfDay.NIGHT -> R.string.insights_night
    }

private val TimeOfDay.share: Int
    get() = when (this) {
        TimeOfDay.MORNING -> R.string.insights_morning_share
        TimeOfDay.AFTERNOON -> R.string.insights_afternoon_share
        TimeOfDay.EVENING -> R.string.insights_evening_share
        TimeOfDay.NIGHT -> R.string.insights_night_share
    }

private val TimeOfDay.count: Int
    get() = when (this) {
        TimeOfDay.MORNING -> R.plurals.insights_morning_count
        TimeOfDay.AFTERNOON -> R.plurals.insights_afternoon_count
        TimeOfDay.EVENING -> R.plurals.insights_evening_count
        TimeOfDay.NIGHT -> R.plurals.insights_night_count
    }
