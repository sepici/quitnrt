package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.insights.InsightWindow
import app.thelasthit.nrt.domain.insights.Triggers
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.TagGroup
import java.time.LocalDate

/**
 * The logical days advice looks at, wherever it shows: the 14 full days before today. Today is
 * left out, so the advice on Today's card is settled by the date and doesn't change with each
 * dose logged.
 */
object AdviceWindow {
    const val DAYS = 14

    fun of(today: LocalDate): InsightWindow = InsightWindow(today.minusDays(DAYS.toLong()), today.minusDays(1))
}

/** A tag that doses were logged with in the advice window: its group, its doses and their share. */
data class TopTrigger(
    val key: String,
    val group: TagGroup,
    val doses: Int,
    /** Of all doses in the window, so shares don't add up to 100% when a dose has two tags. */
    val share: Double,
)

/**
 * What doses were tagged with over the advice window, most doses first, with patches left out.
 * This is what puts someone's own triggers first in the advice.
 */
data class TopTriggers(
    val window: InsightWindow,
    /** Doses in the window, patches left out. */
    val doses: Int,
    /** Logical days in the window with at least one dose. */
    val days: Int,
    /**
     * Every tag with at least [MIN_TAG_DOSES] doses and a group, most doses first, ties by key.
     * Tags with no group (one that's been deleted, say) can't be matched, so they're left out.
     */
    val ranked: List<TopTrigger>,
) {
    /** Whether enough is logged to say what someone's triggers are. */
    val enoughData: Boolean get() = doses >= MIN_DOSES && days >= MIN_DAYS

    /** The first [TOP] triggers, or none when there's too little data. */
    val top: List<TopTrigger> get() = if (enoughData) ranked.take(TOP) else emptyList()

    companion object {
        /** How many triggers advice puts first. */
        const val TOP = 3

        /** Too little data below these: 10 doses on 3 different days. */
        const val MIN_DOSES = 10
        const val MIN_DAYS = 3

        /** A tag needs this many doses in the window to count as a trigger. */
        const val MIN_TAG_DOSES = 3

        /**
         * The triggers in [entries] over the advice window ending before [today]. Entries outside
         * the window are ignored, so the caller can pass more. [groupOf] gives a tag's group,
         * built-in or added, and null for a tag to leave out.
         */
        fun of(entries: List<Entry>, today: LocalDate, groupOf: (String) -> TagGroup?): TopTriggers {
            val window = AdviceWindow.of(today)
            val doses = entries.filter { !it.isBackground && it.logicalDate in window }
            val ranked = Triggers.of(doses, previous = null, groupOf).tags
                .filter { it.doses >= MIN_TAG_DOSES }
                .mapNotNull { share -> groupOf(share.key)?.let { TopTrigger(share.key, it, share.doses, share.share) } }
            return TopTriggers(window, doses.size, doses.map { it.logicalDate }.distinct().size, ranked)
        }
    }
}
