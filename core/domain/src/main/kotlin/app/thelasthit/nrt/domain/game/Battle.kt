package app.thelasthit.nrt.domain.game

import app.thelasthit.nrt.domain.creature.SpriteFrame

/** The two ways to spend banked hits on the demon in the lair (PLAN 7.2). */
enum class Attack(val hits: Int) {
    /** One hit, for 10 damage. */
    HIT(1),

    /** The 5× attack: five hits in one combo, for 50 damage. */
    COMBO(HitLedger.COMBO_HITS);

    /** The HP it takes off: the "−10" or "−50" that floats up from the demon. */
    val damage: Int get() = hits * HitLedger.HP_PER_HIT

    /** Whether it can be used with [banked] hits on a demon at [hp]. */
    fun canUse(banked: Int, hp: Int): Boolean = hits <= banked && damage <= hp
}

/**
 * The demon's flavour line in the lair (PLAN 7.3 and STYLE.md): short, dry, in character, and
 * about the demon, never about the person fighting it.
 */
enum class LairLine {
    /** Above two thirds of its HP. */
    FRESH,

    /** Above a third, at most two thirds. */
    ROUGH,

    /** A third of its HP or less. */
    BARELY,

    /** One hit from the end, with a hit banked to land it. */
    LAST_HIT,

    /** The latest week was missed and isn't paid back yet. Nothing resets: the next week met takes the hit back. */
    SMUG,

    /** Just took a hit. */
    STAGGERS,

    /** Just took the 5× attack. */
    REELS,

    /** Defeated: nothing left but a few pixels. */
    GONE;

    companion object {
        /**
         * The line for a demon at [hp] of [maxHp] with [banked] hits to spend. [attack] is the one
         * just landed in the lair, if any, which the demon answers to first. [lastWeekMissed] is
         * true when the latest result in is a miss.
         */
        fun of(hp: Int, maxHp: Int, banked: Int, lastWeekMissed: Boolean, attack: Attack? = null): LairLine = when {
            maxHp > 0 && hp <= 0 -> GONE
            attack == Attack.COMBO -> REELS
            attack == Attack.HIT -> STAGGERS
            lastWeekMissed -> SMUG
            hp == HitLedger.HP_PER_HIT && banked >= 1 -> LAST_HIT
            else -> when (SpriteFrame.forHealth(hp, maxHp.coerceAtLeast(1))) {
                SpriteFrame.IDLE_1 -> FRESH
                SpriteFrame.ROUGH -> ROUGH
                else -> BARELY
            }
        }
    }
}
