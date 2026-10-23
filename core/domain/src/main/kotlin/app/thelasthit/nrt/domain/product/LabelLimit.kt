package app.thelasthit.nrt.domain.product

/**
 * The most a pack's label allows, e.g. 15 pieces a day, or 64 sprays a day with no more than
 * 4 an hour. Reaching the limit is fine; going over it is what the app points out.
 */
data class LabelLimit(
    val maxUnitsPerDay: Int? = null,
    val maxUnitsPerHour: Int? = null,
) {
    init {
        require(maxUnitsPerDay == null || maxUnitsPerDay > 0) { "Daily limit must be positive: $maxUnitsPerDay" }
        require(maxUnitsPerHour == null || maxUnitsPerHour > 0) { "Hourly limit must be positive: $maxUnitsPerHour" }
    }

    fun isOverDaily(unitsToday: Int): Boolean = maxUnitsPerDay != null && unitsToday > maxUnitsPerDay

    fun isOverHourly(unitsInLastHour: Int): Boolean = maxUnitsPerHour != null && unitsInLastHour > maxUnitsPerHour
}
