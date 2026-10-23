package app.thelasthit.nrt.data.repo

import androidx.room.withTransaction
import app.thelasthit.nrt.data.db.AchievementEntity
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.db.DemonWithLedger
import app.thelasthit.nrt.data.db.GearEntity
import app.thelasthit.nrt.data.db.HitLedgerEntity
import app.thelasthit.nrt.data.db.balance
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.programme.HitBalance
import app.thelasthit.nrt.domain.programme.LedgerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The current demon with the hit ledger's sums: its HP and the banked hits. */
data class DemonState(val demon: Demon, val balance: HitBalance)

sealed interface SpendResult {
    /** The hits were spent, leaving [balance]. */
    data class Spent(val balance: HitBalance) : SpendResult

    /** Fewer hits are banked than the attack needs, so nothing was spent. */
    data class NotEnough(val balance: HitBalance) : SpendResult

    /** There's no demon to hit until a plan starts. */
    data object NoDemon : SpendResult
}

/**
 * The game layer's storage (PLAN §7 and §8): the hit ledger, the demon, achievements and gear.
 * Hits are earned by results, which [ProgrammeRepository] writes. Spending them here is a plain
 * ledger write; the battle's rules come with phase 4.
 *
 * Results are worked out whenever the app looks (ROADMAP P3-3), so reading the demon and
 * spending hits settle the results that are due first, through [results].
 */
class GameRepository(
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val results: ResultSettler,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val game = database.game()

    // The demon and the ledger.

    /**
     * The current programme's demon, with its HP and banked hits. Null until a plan starts.
     * Collecting it settles whatever results are due first, then it follows every change.
     */
    val demon: Flow<DemonState?> = game.observeCurrentDemon().map { it?.toState() }.onStart { results.settleDue() }

    /** The current demon now, with the results that are due settled first. */
    suspend fun demon(): DemonState? {
        results.settleDue()
        return game.currentDemon()?.toState()
    }

    /** Spends one hit on the demon, for 10 damage. */
    suspend fun spendHit(): SpendResult = spend(LedgerKind.HIT)

    /** The 5× attack: spends five hits at once, for 50 damage. */
    suspend fun comboAttack(): SpendResult = spend(LedgerKind.COMBO)

    /** Spends what [kind] costs, once the hits that are due are in, so none earned is refused. */
    private suspend fun spend(kind: LedgerKind): SpendResult {
        results.settleDue()
        return database.withTransaction {
            val current = game.currentDemon() ?: return@withTransaction SpendResult.NoDemon
            val balance = current.balance()
            val hits = -kind.hits
            if (!balance.canSpend(hits)) return@withTransaction SpendResult.NotEnough(balance)
            game.insertLedger(
                HitLedgerEntity(
                    id = newId(),
                    programmeId = current.demon.programmeId,
                    kind = kind,
                    hits = kind.hits,
                    week = null,
                    earnedBy = null,
                    createdAt = clock.millis(),
                ),
            )
            SpendResult.Spent(balance.spending(hits))
        }
    }

    /**
     * A new name or look for the demon: the current one once a plan has started, or the one the
     * next plan starts with in settings before that. A blank name shows the app's default.
     */
    suspend fun restyleDemon(name: String, creature: Creature, variant: String) {
        val current = game.currentDemon()
        if (current == null) {
            settings.setDemon(name, creature, variant)
        } else {
            game.restyle(current.demon.id, name.trim().ifBlank { null }, creature, variant)
        }
    }

    /**
     * Marks the current demon defeated once its HP is 0. Safe to repeat: the first time stands.
     * False when there's no demon or it still has HP.
     */
    suspend fun markDefeated(at: Instant = clock.instant()): Boolean = database.withTransaction {
        val current = game.currentDemon() ?: return@withTransaction false
        if (current.balance().hp > 0) return@withTransaction false
        game.setDefeated(current.demon.id, at.toEpochMilli())
        true
    }

    // Achievements and gear.

    /** Achievement keys with when each was unlocked, in the order they came. */
    val achievements: Flow<Map<String, Instant>> = game.observeAchievements().map { list ->
        list.associate { it.key to Instant.ofEpochMilli(it.unlockedAt) }
    }

    /** Unlocks achievement [key]. True the first time; after that it keeps its first time. */
    suspend fun unlockAchievement(key: String, at: Instant = clock.instant()): Boolean =
        game.insertAchievements(listOf(AchievementEntity(key, at.toEpochMilli()))).single() != -1L

    /** Gear keys with when each was unlocked, in the order they came. */
    val gear: Flow<Map<String, Instant>> = game.observeGear().map { list ->
        list.associate { it.key to Instant.ofEpochMilli(it.unlockedAt) }
    }

    /** Unlocks gear [key]. True the first time; after that it keeps its first time. */
    suspend fun unlockGear(key: String, at: Instant = clock.instant()): Boolean =
        game.insertGear(listOf(GearEntity(key, at.toEpochMilli()))).single() != -1L

    /** Equips gear [key], or none for null. False for gear that isn't unlocked. */
    suspend fun equip(key: String?): Boolean {
        if (key != null && !game.hasGear(key)) return false
        settings.setEquippedGear(key)
        return true
    }

    private fun DemonWithLedger.toState() = DemonState(demon.toDomain(), balance())
}
