package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.FormWord
import app.thelasthit.nrt.domain.product.word
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How far back Insights looks. 7 days is always there, and each longer range unlocks once
 * there's more history than the range below it covers: 30 days after more than 7, and so on.
 */
enum class InsightRange(val days: Int) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_60(60),
    DAYS_90(90);

    fun isUnlocked(historyDays: Int): Boolean = ordinal == 0 || historyDays > entries[ordinal - 1].days

    /**
     * The days this range covers, ending [today]. It never starts before [firstDay], so a chart
     * never shows zeros from before logging began. Null when nothing has been logged.
     */
    fun window(today: LocalDate, firstDay: LocalDate?): InsightWindow? {
        if (firstDay == null || firstDay > today) return null
        val start = today.minusDays(days - 1L)
        return InsightWindow(if (start < firstDay) firstDay else start, today)
    }

    companion object {
        /** Logical days from the first one with a dose up to [today], both counted. */
        fun historyDays(today: LocalDate, firstDay: LocalDate?): Int =
            if (firstDay == null || firstDay > today) 0 else ChronoUnit.DAYS.between(firstDay, today).toInt() + 1
    }
}

/** A run of logical days, both ends included. */
data class InsightWindow(val start: LocalDate, val end: LocalDate) {
    init {
        require(start <= end) { "$start is after $end" }
    }

    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    val dates: List<LocalDate> get() = (0 until days).map { start.plusDays(it.toLong()) }

    operator fun contains(date: LocalDate): Boolean = date in start..end

    /** The same number of days just before this one, for the change against the previous period. */
    val previous: InsightWindow get() = InsightWindow(start.minusDays(days.toLong()), start.minusDays(1))

    /** Whether [previous] is all logged, so a comparison never counts days from before [firstDay]. */
    fun hasFullPrevious(firstDay: LocalDate): Boolean = previous.start >= firstDay
}

/**
 * A kind of product to narrow Insights to, by the word its pills use: gum, patch and so on.
 * Products with no word of their own share the null kind.
 */
data class ProductType(val word: FormWord?) {
    fun matches(entry: Entry): Boolean = entry.form.word == word

    companion object {
        fun of(entry: Entry): ProductType = ProductType(entry.form.word)

        /** The kinds in [entries], most mg first, for the filter row. */
        fun inUse(entries: List<Entry>): List<ProductType> = entries.groupBy(::of).entries
            .sortedByDescending { (_, list) -> list.map { it.total }.sum() }
            .map { it.key }
    }
}

/** These entries narrowed to [type], or all of them when [type] is null. */
fun List<Entry>.ofType(type: ProductType?): List<Entry> = if (type == null) this else filter(type::matches)
