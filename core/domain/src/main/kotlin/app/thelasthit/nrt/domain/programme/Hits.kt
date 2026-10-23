package app.thelasthit.nrt.domain.programme

import java.time.Instant

/** Each programme week puts 10 HP on the demon, and each hit takes 10 off. */
const val HP_PER_HIT = 10

/**
 * A hit earned by a week's result, for the hit ledger. [week] is the week it pays for.
 * [earnedBy] is the met week whose result brought it: the same week, or a later one for a
 * catch-up. [at] is when that result came in.
 */
data class EarnedHit(val kind: Kind, val week: Int, val earnedBy: Int, val at: Instant) {
    enum class Kind {
        /** The week was met. */
        WEEK_MET,

        /** The week was missed, then paid back by meeting a later one. */
        CATCH_UP,
    }
}
