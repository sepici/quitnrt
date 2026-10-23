package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.TagGroup

/**
 * What doses were tagged with, with patches left out. Shares are of doses, and one dose can carry
 * several tags, so they don't add up to 100%.
 */
data class Triggers(
    val doses: Int,
    /** Most used first. */
    val tags: List<TagShare>,
    /** Every group, most used first. A dose counts once for a group however many of its tags it has. */
    val groups: List<GroupShare>,
    /** Each part of the day's tags, as shares of that part's doses. */
    val byTimeOfDay: Map<TimeOfDay, List<TagShare>>,
    /** Doses with no tag at all. */
    val untagged: Int,
) {
    /** [change] is in percentage points against the previous period, when there's one to compare with. */
    data class TagShare(val key: String, val doses: Int, val share: Double, val change: Int? = null)

    data class GroupShare(val group: TagGroup, val doses: Int, val share: Double)

    val untaggedShare: Double get() = ratio(untagged, doses)

    companion object {
        /**
         * [previous] is the period just before, or null when it isn't all logged, so there's no
         * fair comparison. [groupOf] gives a tag's group; tags it doesn't know stay out of groups.
         */
        fun of(entries: List<Entry>, previous: List<Entry>?, groupOf: (String) -> TagGroup?): Triggers {
            val doses = entries.filterNot { it.isBackground }
            val before = previous?.filterNot { it.isBackground }?.takeIf { it.isNotEmpty() }
                ?.let { shares(it).associate { share -> share.key to share.share } }
            val tags = shares(doses).map { share ->
                share.copy(change = before?.let { points(share.share, it[share.key] ?: 0.0) })
            }
            val groups = TagGroup.entries.map { group ->
                val count = doses.count { dose -> dose.tagKeys.any { groupOf(it) == group } }
                GroupShare(group, count, ratio(count, doses.size))
            }.sortedByDescending { it.doses }
            return Triggers(
                doses = doses.size,
                tags = tags,
                groups = groups,
                byTimeOfDay = TimeOfDay.entries.associateWith { part -> shares(doses.filter { TimeOfDay.of(it) == part }) },
                untagged = doses.count { it.tagKeys.isEmpty() },
            )
        }

        private fun shares(doses: List<Entry>): List<TagShare> = doses.flatMap { it.tagKeys }.groupingBy { it }.eachCount()
            .map { (key, count) -> TagShare(key, count, ratio(count, doses.size)) }
            .sortedWith(compareByDescending<TagShare> { it.doses }.thenBy { it.key })

        private fun ratio(part: Int, whole: Int): Double = if (whole == 0) 0.0 else part.toDouble() / whole

        private fun points(now: Double, before: Double): Int = Math.round((now - before) * 100).toInt()
    }
}
