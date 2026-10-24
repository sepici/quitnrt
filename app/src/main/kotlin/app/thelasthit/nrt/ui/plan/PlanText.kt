package app.thelasthit.nrt.ui.plan

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.programme.Mix
import app.thelasthit.nrt.domain.programme.MixItem
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.plural
import app.thelasthit.nrt.ui.format.unitsName
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// How the Plan tab puts a programme into words.

/** "Tuesday". */
internal val DayName: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.UK)

// A day's number and its month are joined by a no-break space, as in the mockups, so a date
// that wraps never leaves the month on a line of its own.

/** "Saturday 24 October". */
internal val LongDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d\u00A0MMMM", Locale.UK)

/** "24 Oct", as the plan's table starts each week. */
internal val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d\u00A0MMM", Locale.UK)

private val DayOnly = DateTimeFormatter.ofPattern("EEEE d", Locale.UK)
private val Number = DateTimeFormatter.ofPattern("d", Locale.UK)
private val NumberMonth = DateTimeFormatter.ofPattern("d\u00A0MMMM", Locale.UK)

/** A number and the unit after it, as catalogue names end: "Nicorette gum 2 mg". */
private val NumberUnit = Regex("(\\d) (mg|h)\\b")

/** "a, b and c". */
internal fun Resources.listText(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> getString(R.string.plan_list_and, items.dropLast(1).joinToString(", "), items.last())
}

/** "a, b or c". */
internal fun Resources.orText(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> getString(R.string.plan_list_or, items.dropLast(1).joinToString(", "), items.last())
}

/** Week numbers with runs of three or more joined up: "1, 2 and 4 to 6". */
internal fun Resources.weeksText(numbers: List<Int>): String {
    val sorted = numbers.distinct().sorted()
    val items = mutableListOf<String>()
    var start = 0
    while (start < sorted.size) {
        var end = start
        while (end + 1 < sorted.size && sorted[end + 1] == sorted[end] + 1) end++
        if (end - start >= 2) {
            items += getString(R.string.plan_range_to, sorted[start], sorted[end])
        } else {
            for (index in start..end) items += sorted[index].toString()
        }
        start = end + 1
    }
    return listText(items)
}

/** "week 3", or "weeks 1 and 3", inside a sentence. */
internal fun Resources.weekRef(numbers: List<Int>): String =
    if (numbers.size == 1) getString(R.string.plan_week_ref_one, numbers.single()) else getString(R.string.plan_week_ref_many, weeksText(numbers))

/** "Friday 16 to Thursday 22 October", with the month once when both days share it. */
internal fun Resources.longRange(start: LocalDate, end: LocalDate): String {
    val first = if (start.month == end.month && start.year == end.year) DayOnly else LongDate
    return getString(R.string.plan_range, first.format(start), LongDate.format(end))
}

/** "24 to 30 October", or "31 October to 6 November". */
internal fun Resources.shortRange(start: LocalDate, end: LocalDate): String {
    val first = if (start.month == end.month && start.year == end.year) Number else NumberMonth
    return getString(R.string.plan_range, first.format(start), NumberMonth.format(end))
}

/** "From Tuesday to Thursday", or "From Tuesday" for one day. */
internal fun Resources.dayNames(start: LocalDate, end: LocalDate): String =
    if (start == end) DayName.format(start) else getString(R.string.plan_range, DayName.format(start), DayName.format(end))

/**
 * A product as lists show it: catalogue names carry their strength, and one added by hand gets it
 * added. The strength's number keeps to its unit with a no-break space, as the plan's own text does.
 */
internal fun Resources.productTitle(product: UserProduct): String {
    val name = product.name.replace(NumberUnit, "$1\u00A0$2")
    return if (product.catalogueId == null) getString(R.string.dose_named, name, product.perUnit.mgText()) else name
}

/** One product's units a day in words: "8 pieces of 4 mg gum", "3 sprays". */
internal fun Resources.itemText(item: MixItem): String {
    val product = item.product
    val units = item.units
    val mg = product.perUnit.mgText()
    val plural = when (product.form) {
        ProductForm.GUM -> R.plurals.plan_mix_gum
        ProductForm.LOZENGE -> R.plurals.plan_mix_lozenge
        ProductForm.MINI_LOZENGE -> R.plurals.plan_mix_mini_lozenge
        ProductForm.PATCH_16H, ProductForm.PATCH_24H -> R.plurals.plan_mix_patch
        ProductForm.MOUTH_SPRAY -> R.plurals.plan_mix_spray
        ProductForm.NASAL_SPRAY -> R.plurals.plan_mix_nasal_spray
        else -> null
    }
    return if (plural != null) {
        getQuantityString(plural, units, units, mg)
    } else {
        getString(R.string.plan_mix_other, getQuantityString(product.unit.plural, units, units), productTitle(product))
    }
}

/** A day's mix in words: "8 pieces of 4 mg gum and 3 sprays", or "None". */
internal fun Resources.mixText(mix: Mix): String = if (mix.isEmpty) getString(R.string.plan_mix_none) else listText(mix.items.map { itemText(it) })

@Composable
internal fun mixText(mix: Mix): String = LocalResources.current.mixText(mix)

/** A lower strength as the plan brings it in: "2 mg gum", "14 mg patches". */
internal fun Resources.kindText(product: UserProduct): String {
    val mg = product.perUnit.mgText()
    return when (product.form) {
        ProductForm.GUM -> getString(R.string.plan_kind_gum, mg)
        ProductForm.LOZENGE -> getString(R.string.plan_kind_lozenge, mg)
        ProductForm.MINI_LOZENGE -> getString(R.string.plan_kind_mini_lozenge, mg)
        ProductForm.PATCH_16H, ProductForm.PATCH_24H -> getString(R.string.plan_kind_patch, mg)
        ProductForm.MOUTH_SPRAY, ProductForm.NASAL_SPRAY -> getString(R.string.plan_kind_spray, mg)
        else -> productTitle(product)
    }
}

/** A logging unit on its own, as in "One piece fewer". */
@StringRes
internal fun unitWord(unit: DoseUnit): Int = when (unit) {
    DoseUnit.PIECE -> R.string.plan_unit_piece
    DoseUnit.LOZENGE -> R.string.plan_unit_lozenge
    DoseUnit.PATCH -> R.string.plan_unit_patch
    DoseUnit.SPRAY -> R.string.plan_unit_spray
    DoseUnit.SESSION -> R.string.plan_unit_session
    DoseUnit.CARTRIDGE -> R.string.plan_unit_cartridge
    DoseUnit.PUFF -> R.string.plan_unit_puff
    DoseUnit.TABLET -> R.string.plan_unit_tablet
    DoseUnit.STRIP -> R.string.plan_unit_strip
    DoseUnit.POUCH -> R.string.plan_unit_pouch
    DoseUnit.CUSTOM -> R.string.plan_unit_custom
}

/** "pieces and sprays", for "rounded to whole pieces and sprays". */
internal fun Resources.unitsText(mix: Mix): String = listText(mix.products.map { it.unit }.distinct().map { getString(unitsName(it)) })

/** "piece or spray", for "one piece or spray fewer". */
internal fun Resources.unitWordsText(mix: Mix): String = orText(mix.products.map { it.unit }.distinct().map { getString(unitWord(it)) })

/** How far apart two doses are, always positive: what's left of an allowance, or how far over it a week went. */
internal fun Dose.apartFrom(other: Dose): Dose = Dose(kotlin.math.abs(micrograms - other.micrograms))

/** "35 mg". */
internal fun Resources.mg(dose: Dose): String = getString(R.string.plan_mg, dose.mgText())

/**
 * [template] with [bold] set in bold where it appears: for a line such as "**3 full days** so
 * far", where the bold part is one of the arguments. In a quiet line, [colour] gives the bold part
 * the text colour, so the figure stands out as it does in the mockups.
 */
internal fun boldIn(template: String, bold: String, colour: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    val at = template.indexOf(bold)
    if (bold.isEmpty() || at < 0) {
        append(template)
    } else {
        append(template.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colour)) { append(bold) }
        append(template.substring(at + bold.length))
    }
}
