package app.thelasthit.nrt.ui.format

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Elapsed
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.FormWord
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.product.ProductForm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A dose in mg for display, to one decimal place at most: 8, 1.5, 1.9 for an inhalator session.
 * Only for showing. Sums and checks stay in whole micrograms.
 */
fun Dose.mgText(): String {
    val tenths = (micrograms + 50) / 100
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}

/** Times follow the phone's 12 or 24 hour setting. */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    return remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.UK) }
}

fun DateTimeFormatter.formatAt(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = format(instant.atZone(zone))

/** "Friday 23 October", as the Today title shows it. */
val DayTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

/** "22 October", and with the year for another year, as History's day headers show them. */
val DayMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)
val DayMonthYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

/** "Wed 21 Oct", for a picked time on the log sheet. */
val ShortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

@Composable
fun elapsedText(elapsed: Elapsed): String = when {
    elapsed.isJustNow -> stringResource(R.string.elapsed_just_now)
    elapsed.days > 0 && elapsed.hours > 0 ->
        pluralStringResource(R.plurals.elapsed_days_hours, elapsed.days.toInt(), elapsed.days, elapsed.hours)
    elapsed.days > 0 -> pluralStringResource(R.plurals.elapsed_days, elapsed.days.toInt(), elapsed.days)
    elapsed.hours > 0 && elapsed.minutes > 0 -> stringResource(R.string.elapsed_hours_minutes, elapsed.hours, elapsed.minutes)
    elapsed.hours > 0 -> stringResource(R.string.elapsed_hours, elapsed.hours)
    else -> stringResource(R.string.elapsed_minutes, elapsed.minutes)
}

/** [elapsedText] in words, for screen readers, which would read "h" as a letter. */
@Composable
fun elapsedSpoken(elapsed: Elapsed): String {
    val resources = LocalResources.current
    fun part(plural: Int, count: Long) = resources.getQuantityString(plural, count.toInt(), count)
    val days = part(R.plurals.spoken_days, elapsed.days)
    val hours = part(R.plurals.spoken_hours, elapsed.hours.toLong())
    val minutes = part(R.plurals.spoken_minutes, elapsed.minutes.toLong())
    return when {
        elapsed.isJustNow -> resources.getString(R.string.elapsed_just_now)
        elapsed.days > 0 && elapsed.hours > 0 -> resources.getString(R.string.spoken_ago_two, days, hours)
        elapsed.days > 0 -> resources.getString(R.string.spoken_ago, days)
        elapsed.hours > 0 && elapsed.minutes > 0 -> resources.getString(R.string.spoken_ago_two, hours, minutes)
        elapsed.hours > 0 -> resources.getString(R.string.spoken_ago, hours)
        else -> resources.getString(R.string.spoken_ago, minutes)
    }
}

@Composable
fun pillLabelText(label: PillLabel): String = when (label) {
    is PillLabel.ByForm -> stringResource(R.string.pill_label, stringResource(label.word.text), label.strength.mgText())
    is PillLabel.ByName -> stringResource(R.string.pill_label, label.name, label.strength.mgText())
}

@get:StringRes
val FormWord.text: Int
    get() = when (this) {
        FormWord.GUM -> R.string.form_word_gum
        FormWord.LOZENGE -> R.string.form_word_lozenge
        FormWord.MINI_LOZENGE -> R.string.form_word_mini_lozenge
        FormWord.PATCH -> R.string.form_word_patch
        FormWord.MOUTH_SPRAY -> R.string.form_word_mouth_spray
        FormWord.NASAL_SPRAY -> R.string.form_word_nasal_spray
        FormWord.INHALATOR -> R.string.form_word_inhalator
        FormWord.MICROTAB -> R.string.form_word_microtab
        FormWord.STRIP -> R.string.form_word_strip
        FormWord.POUCH -> R.string.form_word_pouch
    }

@get:DrawableRes
val ProductForm.icon: Int
    get() = when (this) {
        ProductForm.GUM -> R.drawable.ic_form_gum
        ProductForm.LOZENGE, ProductForm.MINI_LOZENGE -> R.drawable.ic_form_lozenge
        ProductForm.PATCH_16H, ProductForm.PATCH_24H -> R.drawable.ic_form_patch
        ProductForm.MOUTH_SPRAY -> R.drawable.ic_form_spray
        ProductForm.NASAL_SPRAY -> R.drawable.ic_form_nasal
        ProductForm.INHALATOR -> R.drawable.ic_form_inhalator
        ProductForm.MICROTAB -> R.drawable.ic_form_microtab
        ProductForm.ORAL_FILM -> R.drawable.ic_form_strip
        ProductForm.POUCH -> R.drawable.ic_form_pouch
        ProductForm.OTHER -> R.drawable.ic_form_other
    }

@get:PluralsRes
val DoseUnit.plural: Int
    get() = when (this) {
        DoseUnit.PIECE -> R.plurals.unit_piece
        DoseUnit.LOZENGE -> R.plurals.unit_lozenge
        DoseUnit.PATCH -> R.plurals.unit_patch
        DoseUnit.SPRAY -> R.plurals.unit_spray
        DoseUnit.SESSION -> R.plurals.unit_session
        DoseUnit.CARTRIDGE -> R.plurals.unit_cartridge
        DoseUnit.PUFF -> R.plurals.unit_puff
        DoseUnit.TABLET -> R.plurals.unit_tablet
        DoseUnit.STRIP -> R.plurals.unit_strip
        DoseUnit.POUCH -> R.plurals.unit_pouch
        DoseUnit.CUSTOM -> R.plurals.unit_custom
    }

@get:StringRes
val TagGroup.text: Int
    get() = when (this) {
        TagGroup.FEELINGS -> R.string.tag_group_feelings
        TagGroup.ROUTINE -> R.string.tag_group_routine
        TagGroup.PEOPLE -> R.string.tag_group_people
        TagGroup.BODY -> R.string.tag_group_body
        TagGroup.HABIT -> R.string.tag_group_habit
    }

/** The text for each built-in tag key. A tag the user renamed or added uses its own name instead. */
private val builtInTagText: Map<String, Int> = mapOf(
    "stressed" to R.string.tag_stressed,
    "anxious" to R.string.tag_anxious,
    "angry" to R.string.tag_angry,
    "low" to R.string.tag_low,
    "bored" to R.string.tag_bored,
    "lonely" to R.string.tag_lonely,
    "happy" to R.string.tag_happy,
    "excited" to R.string.tag_excited,
    "celebrating" to R.string.tag_celebrating,
    "relieved" to R.string.tag_relieved,
    "after_meal" to R.string.tag_after_meal,
    "coffee_tea" to R.string.tag_coffee_tea,
    "drink" to R.string.tag_drink,
    "woke_up" to R.string.tag_woke_up,
    "before_bed" to R.string.tag_before_bed,
    "commuting" to R.string.tag_commuting,
    "work_break" to R.string.tag_work_break,
    "after_exercise" to R.string.tag_after_exercise,
    "on_call" to R.string.tag_on_call,
    "screen_time" to R.string.tag_screen_time,
    "nicotine_users" to R.string.tag_nicotine_users,
    "socialising" to R.string.tag_socialising,
    "alone" to R.string.tag_alone,
    "after_argument" to R.string.tag_after_argument,
    "strong_craving" to R.string.tag_strong_craving,
    "withdrawal" to R.string.tag_withdrawal,
    "tired" to R.string.tag_tired,
    "hungry" to R.string.tag_hungry,
    "cant_focus" to R.string.tag_cant_focus,
    "autopilot" to R.string.tag_autopilot,
)

/** Every built-in key has a string. Checked by a test, so a new tag can't ship without text. */
val builtInTagKeysWithText: Set<String> get() = builtInTagText.keys

@Composable
fun tagText(tag: TagEntity): String = tag.customName ?: tagText(tag.key)

@Composable
fun tagText(key: String): String = builtInTagText[key]?.let { stringResource(it) } ?: key
