package app.thelasthit.nrt.domain.game

/**
 * Gear changes how a hit looks and sounds, and nothing else (PLAN 8.2). In unlock order, from the
 * stick everyone starts with. The [key] names its icon in `icons/gear.json` and is what gets stored.
 */
enum class Gear(val key: String) {
    WOODEN_STICK("wooden_stick"),
    WOODEN_SWORD("wooden_sword"),
    IRON_SWORD("iron_sword"),
    AXE("axe"),
    WAR_HAMMER("war_hammer"),
    FIRE_STAFF("fire_staff"),
    LIGHTNING_BOW("lightning_bow"),
    LEGENDARY_BLADE("legendary_blade");

    companion object {
        fun fromKey(key: String?): Gear? = entries.firstOrNull { it.key == key }
    }
}
