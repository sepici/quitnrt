package app.thelasthit.nrt.domain.game

/**
 * Gear changes how a hit looks and sounds, and nothing else (PLAN 8.2). In unlock order, from the
 * stick everyone starts with. The [key] names its icon in `icons/gear.json` and is what gets stored.
 *
 * Each piece after the stick comes with an achievement on the plan's own path, one anyone who
 * keeps to the plan reaches, so the gear arrives in order across a 12-week programme and the
 * lightning bow is in hand for the last hits. The legendary blade comes with the defeat, to carry
 * into a rematch.
 */
enum class Gear(val key: String, val unlockedBy: Achievement?) {
    WOODEN_STICK("wooden_stick", null),
    WOODEN_SWORD("wooden_sword", Achievement.FIRST_DAY_IN_GUIDE),
    IRON_SWORD("iron_sword", Achievement.FIRST_WEEK_MET),
    AXE("axe", Achievement.WEEKS_MET_3),
    WAR_HAMMER("war_hammer", Achievement.WEEKS_MET_6),
    FIRE_STAFF("fire_staff", Achievement.FINAL_TAPER_WEEK),
    LIGHTNING_BOW("lightning_bow", Achievement.WEEKS_MET_10),
    LEGENDARY_BLADE("legendary_blade", Achievement.DEMON_DEFEATED);

    /** The stick is there from the start, so it's never news. */
    val isStarter: Boolean get() = unlockedBy == null

    companion object {
        fun fromKey(key: String?): Gear? = entries.firstOrNull { it.key == key }

        /** The gear that comes with [achievements]: the stick, and each piece whose achievement is in. */
        fun unlockedWith(achievements: Set<Achievement>): Set<Gear> =
            entries.filterTo(LinkedHashSet()) { it.unlockedBy == null || it.unlockedBy in achievements }
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
        /** [equippedKey] is the stored setting. */
        fun of(achievements: Set<Achievement>, equippedKey: String?): Loadout {
            val unlocked = Gear.unlockedWith(achievements)
            val equipped = Gear.fromKey(equippedKey)?.takeIf { it in unlocked } ?: Gear.WOODEN_STICK
            return Loadout(unlocked, equipped)
        }
    }
}
