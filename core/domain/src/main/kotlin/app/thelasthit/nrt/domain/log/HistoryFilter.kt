package app.thelasthit.nrt.domain.log

import java.time.LocalDate

/**
 * History's filter: one product or one tag at a time, never both. Days with no match are left
 * out, and each day that's left keeps its count of every dose, so it can read "1 of 5 doses".
 */
sealed interface HistoryFilter {
    /**
     * Whether the filter keeps [entry]. This is the rule: the app asks the database for the
     * matches through EntryRepository.observeMatching, whose queries are tested against it.
     */
    fun matches(entry: Entry): Boolean

    data class Product(val productId: String) : HistoryFilter {
        override fun matches(entry: Entry): Boolean = entry.productId == productId
    }

    data class Tag(val tagKey: String) : HistoryFilter {
        override fun matches(entry: Entry): Boolean = tagKey in entry.tagKeys
    }

    companion object {
        /**
         * The products or tags worth offering: only those with a dose, most used first. Ties keep
         * their order in [items]. [uses] is key to the number of doses, across all of History.
         */
        fun <T> choices(items: List<T>, uses: Map<String, Int>, key: (T) -> String): List<T> =
            TagOrder.byUse(items.filter { (uses[key(it)] ?: 0) > 0 }, uses, key)

        /**
         * The days with a match, newest first, as [LoggedDay.groupByDay] makes them from
         * [matching]. Each day's [LoggedDay.doses] comes from [dosesOn], the count of every dose
         * that day, and never falls below its matches.
         */
        fun days(matching: List<Entry>, dosesOn: Map<LocalDate, Int>): List<LoggedDay> =
            LoggedDay.groupByDay(matching).map { day ->
                day.copy(doses = maxOf(day.entries.size, dosesOn[day.date] ?: 0))
            }
    }
}
