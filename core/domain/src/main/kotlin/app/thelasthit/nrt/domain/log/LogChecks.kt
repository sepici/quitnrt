package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.DoseMath
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.RollingLimit
import java.time.Duration
import java.time.Instant

object LogChecks {

    val DUPLICATE_WINDOW: Duration = Duration.ofSeconds(60)

    /**
     * The same product logged twice within a minute from two different places (say the widget
     * and Gemini) is probably one dose logged twice, so the app asks before keeping both.
     */
    fun possibleDuplicateOf(
        productId: String,
        at: Instant,
        source: EntrySource,
        existing: List<Entry>,
    ): Entry? = existing.firstOrNull { other ->
        other.productId == productId &&
            other.source != source &&
            Duration.between(other.occurredAt, at).abs() <= DUPLICATE_WINDOW
    }

    /**
     * A label limit expressed in the unit the product is logged in. Labels count inhalator
     * cartridges, but the inhalator is usually logged by session or puff.
     */
    fun limitInLoggedUnit(
        label: LabelLimit,
        labelUnit: DoseUnit,
        loggedUnit: DoseUnit,
        puffsPerCartridge: Int? = null,
    ): LabelLimit {
        val factor = when {
            labelUnit == loggedUnit -> 1
            labelUnit == DoseUnit.CARTRIDGE && loggedUnit == DoseUnit.SESSION -> DoseMath.SESSIONS_PER_CARTRIDGE
            labelUnit == DoseUnit.CARTRIDGE && loggedUnit == DoseUnit.PUFF -> puffsPerCartridge ?: return LabelLimit.NONE
            else -> return LabelLimit.NONE
        }
        return LabelLimit(
            maxUnitsPerDay = label.maxUnitsPerDay?.let { it * factor },
            window = label.window?.let { RollingLimit(it.maxUnits * factor, it.span) },
        )
    }

    /**
     * Whether logging [quantity] more of a product at [at] would go over its label limit.
     * [sameProductToday] holds that product's entries for the logical day. [sameProductRecent]
     * holds its entries that could fall inside the rolling window, which can reach into yesterday.
     */
    fun labelWarning(
        limit: LabelLimit,
        quantity: Int,
        at: Instant,
        sameProductToday: List<Entry>,
        sameProductRecent: List<Entry> = sameProductToday,
    ): LabelWarning? {
        val today = sameProductToday.sumOf { it.quantity } + quantity
        if (limit.isOverDaily(today)) return LabelWarning.OverDaily(today, limit.maxUnitsPerDay!!)
        val window = limit.window ?: return null
        val from = at.minus(window.span)
        val inWindow = sameProductRecent
            .filter { it.occurredAt.isAfter(from) && !it.occurredAt.isAfter(at) }
            .sumOf { it.quantity } + quantity
        if (limit.isOverWindow(inWindow)) return LabelWarning.OverWindow(inWindow, window)
        return null
    }
}

sealed interface LabelWarning {
    data class OverDaily(val units: Int, val limit: Int) : LabelWarning
    data class OverWindow(val units: Int, val limit: RollingLimit) : LabelWarning
}
