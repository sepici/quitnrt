package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.LONDON
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Friday 23 October, as in the app's tests. The advice window is 9 to 22 October. */
internal val TODAY: LocalDate = LocalDate.parse("2026-10-23")

internal val DAY_START: LocalTime = LocalTime.of(4, 0)

internal val builtInGroups: (String) -> TagGroup? = { BuiltInTags.find(it)?.group }

/**
 * One dose a day on each of the [times].size days before [TODAY], newest first: the first time
 * is yesterday's, the next the day before's, and so on.
 */
internal fun daily(
    vararg times: String,
    tags: Set<String> = emptySet(),
    form: ProductForm = ProductForm.GUM,
    perUnit: Dose = Dose.mg(2),
    zone: ZoneId = LONDON,
    skip: Int = 0,
): List<Entry> = times.mapIndexed { index, time ->
    entry("${TODAY.minusDays(1L + skip + index)}T$time", form = form, perUnit = perUnit, tags = tags, zone = zone)
}

/** The same time on each of the [days] days before [TODAY]. */
internal fun daily(
    time: String,
    days: Int,
    tags: Set<String> = emptySet(),
    form: ProductForm = ProductForm.GUM,
    zone: ZoneId = LONDON,
    skip: Int = 0,
): List<Entry> = daily(*Array(days) { time }, tags = tags, form = form, zone = zone, skip = skip)

/** A small library, for rules that shouldn't hang on the bundled text. */
internal fun library(vararg strategies: String): AdviceLibrary =
    AdviceLibrary.parse("""{"version": 1, "strategies": [${strategies.joinToString()}], "quitlines": []}""")

internal fun strategy(
    key: String,
    tags: List<String> = emptyList(),
    groups: List<TagGroup> = emptyList(),
    forms: List<ProductForm> = emptyList(),
    interactive: Interactive? = null,
): String {
    fun list(items: List<Any>) = items.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    val source = """{"publisher": "NHS", "title": "Page", "url": "https://www.nhs.uk/", "read": "2026-10-23"}"""
    val extra = (if (forms.isEmpty()) "" else """, "forms": ${list(forms)}""") +
        (if (interactive == null) "" else """, "interactive": "$interactive"""")
    return """{"key": "$key", "title": "Title", "body": "Body.", "steps": ["Step."], "tags": ${list(tags)},
        "groups": ${list(groups)}$extra, "sources": [$source]}"""
}

internal val EVERY_GROUP: List<TagGroup> = TagGroup.entries
