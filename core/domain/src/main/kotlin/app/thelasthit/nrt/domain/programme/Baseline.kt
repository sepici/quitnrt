package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.average
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import java.time.LocalDate

/** Where a usual day came from: measured from the logs, or typed in product by product. */
enum class BaselineSource { MEASURED, MANUAL }

/**
 * The usual day a programme steps down from: whole units a day of each product, such as 8 pieces
 * of 4 mg gum and 3 sprays, which is 35 mg. The plan needs the mix, not only the total, because
 * it steps down through the products (PLAN §6.1).
 *
 * [measured] is the average mg a day the logs showed, for the confirm screen. It can differ from
 * [total], which comes from the whole units.
 */
data class UsualDay(val mix: Mix, val source: BaselineSource, val measured: Dose? = null) {
    init {
        require(!mix.isEmpty) { "A usual day needs some nicotine" }
    }

    val total: Dose get() = mix.total

    val weekly: Dose get() = total * WeekTarget.DAYS

    companion object {
        /** The skip option: a usual day typed in, product by product, to start straight away. */
        fun typed(mix: Mix): UsualDay = UsualDay(mix, BaselineSource.MANUAL)

        /** Units a day of each product. A product with none is left out. */
        fun typed(vararg units: Pair<UserProduct, Int>): UsualDay = typed(Mix.of(*units))
    }
}

/**
 * Seven logical days measured for a baseline. A day counts once it's over, and a baseline needs
 * all seven over with at least [MIN_DAYS_WITH_DOSES] of them logged. The usual day is the average
 * on the days with doses, since a day with none is usually a day that wasn't logged.
 */
data class BaselineDays(
    val days: List<Day>,
    /** What each product came to on the days counted so far, most mg first. */
    val uses: List<ProductUse>,
) {
    init {
        require(days.size == DAYS) { "A baseline is $DAYS days: ${days.size}" }
    }

    /** [doses] counts entries. A day [isOver] once a later logical day has begun. */
    data class Day(val date: LocalDate, val total: Dose, val doses: Int, val isOver: Boolean) {
        val counts: Boolean get() = isOver && doses > 0
    }

    /**
     * One product's units and mg on the days counted. [latest] is its most recent entry there,
     * which stands in for the product if it's no longer in the user's list.
     */
    data class ProductUse(val productId: String, val units: Int, val total: Dose, val latest: Entry)

    val start: LocalDate get() = days.first().date
    val end: LocalDate get() = days.last().date

    val daysWithDoses: Int get() = days.count { it.counts }

    val isComplete: Boolean get() = days.all { it.isOver }

    val isReady: Boolean get() = isComplete && daysWithDoses >= MIN_DAYS_WITH_DOSES

    /** The average mg a day on the days counted so far, for showing the baseline as it builds. */
    val averageSoFar: Dose? get() = days.filter { it.counts }.map { it.total }.average()

    /**
     * The measured usual day, to confirm or edit, once [isReady]. Each product is its average
     * units a day on the days with doses, rounded half up. A product that rounds to none drops
     * out, but the product with the most mg always keeps at least one unit.
     *
     * [products] are the user's own, hidden ones too. A product that's gone from the list is
     * taken from its latest entry.
     */
    fun usualDay(products: List<UserProduct>): UsualDay? {
        if (!isReady) return null
        val byId = products.associateBy { it.id }
        val days = daysWithDoses.toLong()
        val items = uses.mapIndexedNotNull { index, use ->
            val perDay = ((use.units * 2L + days) / (2 * days)).toInt()
            val units = if (index == 0) maxOf(perDay, 1) else perDay
            if (units == 0) return@mapIndexedNotNull null
            MixItem(byId[use.productId] ?: standIn(use.latest), units)
        }
        return UsualDay(Mix.of(items), BaselineSource.MEASURED, averageSoFar)
    }

    private fun standIn(entry: Entry) = UserProduct(
        id = entry.productId,
        catalogueId = null,
        name = entry.productName,
        shortName = entry.productName,
        form = entry.form,
        unit = entry.unit,
        perUnit = entry.perUnit,
    )

    companion object {
        const val DAYS = 7
        const val MIN_DAYS_WITH_DOSES = 5

        /**
         * The days to measure on [today]. The last [DAYS] full days come first: if enough of them
         * are logged, they're the baseline at once, even from before a baseline was started on
         * [startedOn]. Otherwise a baseline started less than a week ago builds over its own
         * seven days. Once those are over, the window moves on a day at a time until enough of
         * it is logged.
         */
        fun measure(today: LocalDate, entries: List<Entry>, startedOn: LocalDate? = null): BaselineDays {
            val recent = over(today.minusDays(DAYS.toLong()), today, entries)
            if (startedOn == null || recent.isReady || startedOn <= recent.start) return recent
            return over(startedOn, today, entries)
        }

        private fun over(start: LocalDate, today: LocalDate, entries: List<Entry>): BaselineDays {
            val end = start.plusDays(DAYS - 1L)
            val inWindow = entries.filter { it.logicalDate in start..end }
            val byDay = inWindow.groupBy { it.logicalDate }
            val days = (0 until DAYS).map { offset ->
                val date = start.plusDays(offset.toLong())
                val onDay = byDay[date].orEmpty()
                Day(date, onDay.map { it.total }.sum(), onDay.size, isOver = date < today)
            }
            val uses = inWindow.filter { it.logicalDate < today }
                .groupBy { it.productId }
                .map { (id, list) ->
                    ProductUse(id, list.sumOf { it.quantity }, list.map { it.total }.sum(), list.maxBy { it.occurredAt })
                }
                .sortedWith(compareByDescending<ProductUse> { it.total }.thenBy { it.productId })
            return BaselineDays(days, uses)
        }
    }
}
