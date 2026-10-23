package app.thelasthit.nrt.domain.log

import java.time.Duration

/** The log sheet puts your most used tags first, so the ones you reach for are near the top. */
object TagOrder {

    /** Recent use counts, so the order follows a change in habits rather than the whole history. */
    val USE_WINDOW: Duration = Duration.ofDays(30)

    /**
     * [tags] sorted by [uses] (tag key to number of entries), most used first. Ties, including
     * tags with no uses, keep their order in [tags], which is the user's own order.
     */
    fun <T> byUse(tags: List<T>, uses: Map<String, Int>, key: (T) -> String): List<T> =
        tags.sortedByDescending { uses[key(it)] ?: 0 }
}
