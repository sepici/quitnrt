package app.thelasthit.nrt.domain.game

import java.time.Instant

/**
 * One thing to show when unlocks come in (PLAN 8.3): a toast for a badge, a "new gear" card for
 * a piece of gear, or, when more than [NewUnlocks.MAX_TOASTS] badges arrive at once, one summary
 * card for all of them and their gear (ROADMAP P4-2).
 */
sealed interface UnlockNews {
    /** The pixel toast, with the badge and its name. */
    data class Badge(val achievement: Achievement) : UnlockNews

    /** The short "new gear" card. */
    data class NewGear(val gear: Gear) : UnlockNews

    /** Many badges at once, as when phase 4 first looks at weeks of history, in one card. */
    data class Summary(val achievements: List<Achievement>, val gear: List<Gear>) : UnlockNews
}

/**
 * What [NewUnlocks] shows, in order: each badge's toast, then each gear card, or one summary card
 * in place of them all when there are too many badges for toasts.
 */
val NewUnlocks.news: List<UnlockNews>
    get() = when {
        isEmpty -> emptyList()
        isSummary -> listOf(UnlockNews.Summary(achievements, gear))
        else -> achievements.map { UnlockNews.Badge(it) } + gear.map { UnlockNews.NewGear(it) }
    }

/**
 * The trophy shelf in the lair (PLAN 8.3): every badge in PLAN's groups and order, each with when
 * it was earned, or none for one still to come.
 */
data class TrophyShelf(val groups: List<Group>) {

    data class Badge(val achievement: Achievement, val earnedAt: Instant?) {
        val isEarned: Boolean get() = earnedAt != null
    }

    data class Group(val group: Achievement.Group, val badges: List<Badge>)

    val badges: List<Badge> get() = groups.flatMap { it.badges }

    val earned: Int get() = badges.count { it.isEarned }

    val total: Int get() = badges.size

    companion object {
        /**
         * The shelf from the stored achievement keys and when each was unlocked. Keys this version
         * doesn't know are left off.
         */
        fun of(stored: Map<String, Instant>): TrophyShelf = TrophyShelf(
            Achievement.entries.groupBy { it.group }.map { (group, achievements) ->
                Group(group, achievements.map { Badge(it, stored[it.key]) })
            },
        )
    }
}
