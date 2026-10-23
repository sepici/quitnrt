package app.thelasthit.nrt.domain.game

import java.time.Instant

/**
 * A demon's hit ledger (PLAN 7.2 and 17): every hit its weeks have earned and every hit spent on
 * it, in the order they were written. Its HP and the banked hits come from these rows alone, so
 * they can always be worked out again and checked.
 *
 * - Each week met earns a hit, and each missed week it pays back earns one more (catch-up).
 * - A [hit] spends 1 for 10 HP. The [combo], the 5× attack, spends 5 at once for 50, once 5 are
 *   banked.
 * - Spending never goes past the banked hits, and the hits on offer match the HP blocks, so
 *   spending every hit the weeks earn takes the demon to exactly 0. That's the defeat.
 *
 * Only weeks earn hits. Achievements have no row kind here, so they can never deal damage.
 */
data class HitLedger(val maxHp: Int, val rows: List<Row> = emptyList()) {

    /** One ledger row. [week] is the week an earned hit pays for, and null for a spend. */
    data class Row(val kind: Kind, val at: Instant, val week: Int? = null) {
        init {
            require((week != null) == kind.isEarned) { "$kind ${if (kind.isEarned) "needs" else "has no"} week: $week" }
        }

        /** Positive when earned, negative when spent. */
        val hits: Int get() = kind.hits
    }

    enum class Kind(val hits: Int) {
        /** A week met earns its own hit. */
        WEEK_MET(1),

        /** A missed week, paid back by meeting a later one. */
        CATCH_UP(1),

        /** One hit spent: 10 damage. */
        HIT(-1),

        /** The 5× attack: five hits spent in one combo, 50 damage. */
        COMBO(-COMBO_HITS);

        val isEarned: Boolean get() = hits > 0
    }

    init {
        require(maxHp >= 0 && maxHp % HP_PER_HIT == 0) { "Max HP comes in blocks of $HP_PER_HIT: $maxHp" }
        var banked = 0
        rows.forEach { row ->
            banked += row.hits
            require(banked >= 0) { "${row.kind} at ${row.at} spends more than the hits banked" }
        }
        val weeks = rows.mapNotNull { it.week }
        require(weeks.size == weeks.toSet().size) { "A week earns one hit at most: $weeks" }
        require(HP_PER_HIT * weeks.size <= maxHp) { "${weeks.size} hits earned against $maxHp HP" }
    }

    val earned: Int get() = rows.filter { it.kind.isEarned }.sumOf { it.hits }

    val spent: Int get() = -rows.filterNot { it.kind.isEarned }.sumOf { it.hits }

    /** Earned and not spent yet. */
    val banked: Int get() = earned - spent

    /** The demon's HP: max HP less 10 for each hit spent. */
    val hp: Int get() = maxHp - HP_PER_HIT * spent

    /** HP blocks of 10 left, for the HP bar. */
    val hpBlocks: Int get() = hp / HP_PER_HIT

    /** The final hit has landed. A demon with no weeks yet has no HP to lose, so it isn't defeated. */
    val isDefeated: Boolean get() = maxHp > 0 && hp == 0

    /** When the final hit landed. */
    val defeatedAt: Instant?
        get() {
            if (!isDefeated) return null
            var spentSoFar = 0
            for (row in rows) {
                if (row.kind.isEarned) continue
                spentSoFar -= row.hits
                if (HP_PER_HIT * spentSoFar == maxHp) return row.at
            }
            return null
        }

    val canHit: Boolean get() = banked >= 1

    /** The 5× attack is there once 5 hits are banked. */
    val canCombo: Boolean get() = banked >= COMBO_HITS

    /** Spends one banked hit for 10 damage. */
    fun hit(at: Instant): HitLedger {
        require(canHit) { "No hits banked" }
        return copy(rows = rows + Row(Kind.HIT, at))
    }

    /** Spends five banked hits in one combo for 50 damage. */
    fun combo(at: Instant): HitLedger {
        require(canCombo) { "The 5× attack needs $COMBO_HITS hits banked, not $banked" }
        return copy(rows = rows + Row(Kind.COMBO, at))
    }

    /** The ledger with each of [hits] that isn't in yet. Adding the same hits again changes nothing. */
    fun withEarned(hits: List<Row>): HitLedger {
        require(hits.all { it.kind.isEarned }) { "Only earned hits can be added this way" }
        val recorded = rows.mapNotNull { it.week }.toSet()
        val new = hits.filter { it.week !in recorded }
        return if (new.isEmpty()) this else copy(rows = rows + new)
    }

    companion object {
        /** Each programme week puts 10 HP on the demon, and each hit takes 10 off. */
        const val HP_PER_HIT = 10

        /** Hits the 5× attack spends at once. */
        const val COMBO_HITS = 5
    }
}
