package app.thelasthit.nrt.domain.creature

/**
 * The creatures a user can pick to stand for their addiction. Only drawn creatures can be
 * picked; the rest arrive as their sprites are made.
 */
enum class Creature(val key: String, val isDrawn: Boolean) {
    DRAGON("dragon", isDrawn = true),
    IMP("imp", isDrawn = false),
    BEAR("bear", isDrawn = false),
    KRAKEN("kraken", isDrawn = false),
    GOBLIN("goblin", isDrawn = false),
    SLIME("slime", isDrawn = false),
    WRAITH("wraith", isDrawn = false),
    SERPENT("serpent", isDrawn = false);

    companion object {
        fun fromKey(key: String?): Creature = entries.firstOrNull { it.key == key } ?: DRAGON
    }
}

/** The frames every creature has. The white hit flash is generated, not drawn. */
enum class SpriteFrame(val key: String) {
    IDLE_1("idle1"),
    IDLE_2("idle2"),
    HURT("hurt"),
    ROUGH("rough"),
    BARELY("barely");

    companion object {
        /**
         * Fresh above two thirds of its HP, roughed up above one third, barely standing at a third
         * or below. Compared in whole numbers, so 80 of 120 is exactly two thirds.
         */
        fun forHealth(hp: Int, maxHp: Int): SpriteFrame {
            require(maxHp > 0) { "Max HP must be positive: $maxHp" }
            val thirds = hp.coerceIn(0, maxHp) * 3
            return when {
                thirds > maxHp * 2 -> IDLE_1
                thirds > maxHp -> ROUGH
                else -> BARELY
            }
        }
    }
}
