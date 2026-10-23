package app.thelasthit.nrt.domain.product

import java.time.Duration

/**
 * The most a pack's label allows. Some labels give a daily figure only ("15 pieces a day"),
 * some add a rolling window ("no more than 4 sprays an hour", "no more than 5 in any 6 hours").
 * Reaching a limit is fine; going over it is what the app points out.
 */
data class LabelLimit(
    val maxUnitsPerDay: Int? = null,
    val window: RollingLimit? = null,
) {
    init {
        require(maxUnitsPerDay == null || maxUnitsPerDay > 0) { "Daily limit must be positive: $maxUnitsPerDay" }
    }

    fun isOverDaily(unitsToday: Int): Boolean = maxUnitsPerDay != null && unitsToday > maxUnitsPerDay

    fun isOverWindow(unitsInWindow: Int): Boolean = window != null && unitsInWindow > window.maxUnits

    companion object {
        val NONE = LabelLimit()
    }
}

data class RollingLimit(val maxUnits: Int, val span: Duration) {
    init {
        require(maxUnits > 0) { "Window limit must be positive: $maxUnits" }
        require(!span.isNegative && !span.isZero) { "Window must be longer than zero: $span" }
    }
}
