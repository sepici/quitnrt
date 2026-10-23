package app.thelasthit.nrt.domain.game

/**
 * Gear changes how a hit looks and sounds, and nothing else (PLAN 8.2). In unlock order, from the
 * stick everyone starts with. The [key] names its icon in `icons/gear.json` and is what gets stored.
 *
 * Each piece after the stick comes at a point on the plan's own path, one anyone who keeps to the
 * plan reaches. The early pieces come with a badge. From the war hammer on they come with points
 * in the programme's own run rather than counts of weeks, so they arrive in this order whatever
 * the programme's length: half its hits earned, its final taper week reached, then met, and the
 * defeat. The lightning bow is in hand for the last hits, and the legendary blade is there to
 * carry into a rematch.
 */
enum class Gear(val key: String) {
    WOODEN_STICK("wooden_stick"),

    /** With the first day within the daily guide. */
    WOODEN_SWORD("wooden_sword"),

    /** With the first week met. */
    IRON_SWORD("iron_sword"),

    /** With 3 weeks met. */
    AXE("axe"),

    /** Half of a programme's hits earned. */
    WAR_HAMMER("war_hammer"),

    /** With the final taper week reached. */
    FIRE_STAFF("fire_staff"),

    /** A programme's final taper week met, or caught up. */
    LIGHTNING_BOW("lightning_bow"),

    /** With the demon defeated. */
    LEGENDARY_BLADE("legendary_blade");

    /** The stick is there from the start, so it's never news. */
    val isStarter: Boolean get() = this == WOODEN_STICK

    /** The badge it comes with, or null for the stick and for a piece that comes with a point in a programme. */
    val badge: Achievement?
        get() = when (this) {
            WOODEN_SWORD -> Achievement.FIRST_DAY_IN_GUIDE
            IRON_SWORD -> Achievement.FIRST_WEEK_MET
            AXE -> Achievement.WEEKS_MET_3
            FIRE_STAFF -> Achievement.FINAL_TAPER_WEEK
            LEGENDARY_BLADE -> Achievement.DEMON_DEFEATED
            WOODEN_STICK, WAR_HAMMER, LIGHTNING_BOW -> null
        }

    /** Whether it's unlocked, given the badges [achievements] and the [record]. */
    private fun isUnlocked(achievements: Set<Achievement>, record: GameRecord): Boolean = when (this) {
        WOODEN_STICK -> true
        WAR_HAMMER -> record.programmes.any { it.hitsOnOffer > 0 && 2 * it.earnedHits.size >= it.hitsOnOffer }
        LIGHTNING_BOW -> record.programmes.any { it.finalTaperWeek?.isSquared == true }
        else -> badge in achievements
    }

    companion object {
        fun fromKey(key: String?): Gear? = entries.firstOrNull { it.key == key }

        /**
         * The gear unlocked: the stick, each piece whose badge is in [achievements], and each piece
         * whose point in a programme of [record] has been reached. [achievements] should be every
         * badge stored as well as those that hold now, so editing old logs never takes a weapon
         * away. The points in a programme come from results, which don't change once they're in.
         */
        fun unlockedWith(achievements: Set<Achievement>, record: GameRecord): Set<Gear> =
            entries.filterTo(LinkedHashSet()) { it.isUnlocked(achievements, record) }
    }
}

/**
 * The gear unlocked and the piece equipped. Only unlocked gear can be equipped; a stored choice
 * that isn't unlocked, or that this version doesn't know, falls back to the stick.
 */
data class Loadout(val unlocked: Set<Gear>, val equipped: Gear) {
    init {
        require(Gear.WOODEN_STICK in unlocked) { "The stick is always there" }
        require(equipped in unlocked) { "${equipped.key} isn't unlocked" }
    }

    fun canEquip(gear: Gear): Boolean = gear in unlocked

    fun equip(gear: Gear): Loadout {
        require(canEquip(gear)) { "${gear.key} isn't unlocked yet" }
        return copy(equipped = gear)
    }

    companion object {
        /**
         * [storedGear] is the keys of the gear stored as unlocked, which [NewUnlocks] adds to, and
         * [equippedKey] is the stored setting. Keys this version doesn't know are ignored.
         */
        fun of(storedGear: Set<String>, equippedKey: String?): Loadout {
            val unlocked = Gear.entries.filterTo(LinkedHashSet()) { it.isStarter || it.key in storedGear }
            val equipped = Gear.fromKey(equippedKey)?.takeIf { it in unlocked } ?: Gear.WOODEN_STICK
            return Loadout(unlocked, equipped)
        }
    }
}
