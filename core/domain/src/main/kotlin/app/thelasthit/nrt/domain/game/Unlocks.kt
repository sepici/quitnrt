package app.thelasthit.nrt.domain.game

/**
 * What a record has unlocked that isn't stored yet: the badges, in PLAN's order, and the gear
 * they bring. The caller stores [achievements] and [gear] by key, so they're never taken away.
 *
 * Usually that's a toast or two. When phase 4 first looks at weeks of history, or after a long
 * spell away, many arrive at once, and they show in one summary card instead of a flood of
 * toasts (ROADMAP P4-2).
 */
data class NewUnlocks(val achievements: List<Achievement>, val gear: List<Gear>) {

    val isEmpty: Boolean get() = achievements.isEmpty() && gear.isEmpty()

    /** More than [MAX_TOASTS] badges at once go in one summary card. */
    val isSummary: Boolean get() = achievements.size > MAX_TOASTS

    companion object {
        /** About what one weekly result can bring together. */
        const val MAX_TOASTS = 3

        val NONE = NewUnlocks(emptyList(), emptyList())

        /**
         * What [record] unlocks beyond the keys already stored. Stored keys this version doesn't
         * know are ignored. Gear follows every badge ever stored, not only those that hold now,
         * so editing old logs never takes a weapon away. The stick is there from the start and
         * never counts as new.
         */
        fun since(storedAchievements: Set<String>, storedGear: Set<String>, record: GameRecord): NewUnlocks {
            val stored = storedAchievements.mapNotNullTo(mutableSetOf()) { Achievement.fromKey(it) }
            val newBadges = AchievementRules.unlocked(record).filterNot { it in stored }
            val gear = Gear.unlockedWith(stored + newBadges)
                .filterNot { it.isStarter || it.key in storedGear }
            return NewUnlocks(newBadges, gear)
        }
    }
}
