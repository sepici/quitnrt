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

/** What a row of the hit ledger records: a hit earned by a result, or hits spent on the demon. */
enum class LedgerKind(val hits: Int) {
    /** A week met: one hit earned. */
    WEEK_MET(1),

    /** A missed week paid back by a later one: one hit earned. */
    CATCH_UP(1),

    /** One hit spent, for 10 damage. */
    HIT(-1),

    /** The 5× attack: five hits spent at once, for 50 damage. */
    COMBO(-COMBO_HITS),
    ;

    companion object {
        fun of(kind: EarnedHit.Kind): LedgerKind = when (kind) {
            EarnedHit.Kind.WEEK_MET -> WEEK_MET
            EarnedHit.Kind.CATCH_UP -> CATCH_UP
        }
    }
}

/** The hits the 5× attack spends. */
const val COMBO_HITS = 5

/**
 * The hit ledger's sums for one demon: the hits results have [earned], and the hits [spent] on it.
 * The demon's HP and the banked hits both come from these, so they can always be worked out again.
 */
data class HitBalance(val maxHp: Int, val earned: Int, val spent: Int) {
    init {
        require(maxHp >= 0) { "Max HP can't be negative: $maxHp" }
        require(spent in 0..earned) { "Spent $spent of $earned hits" }
    }

    /** Hits earned and not spent yet. */
    val banked: Int get() = earned - spent

    /** Max HP less 10 for each hit spent. */
    val hp: Int get() = maxHp - HP_PER_HIT * spent

    /** Whether [hits] can be spent now: no more than are banked, and no more than the HP left. */
    fun canSpend(hits: Int): Boolean = hits in 1..banked && HP_PER_HIT * hits <= hp

    /** The balance once [hits] more are spent. */
    fun spending(hits: Int): HitBalance {
        require(canSpend(hits)) { "Can't spend $hits of $banked banked hits at $hp HP" }
        return copy(spent = spent + hits)
    }
}
