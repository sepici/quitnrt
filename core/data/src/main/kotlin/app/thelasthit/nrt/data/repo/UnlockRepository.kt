package app.thelasthit.nrt.data.repo

import androidx.room.withTransaction
import app.thelasthit.nrt.data.db.AchievementEntity
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.db.GearEntity
import app.thelasthit.nrt.data.db.gameRecordOf
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.db.toRecord
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.game.GameRecord
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.Loadout
import app.thelasthit.nrt.domain.game.NewUnlocks
import app.thelasthit.nrt.domain.game.TrophyShelf
import app.thelasthit.nrt.domain.game.UnlockNews
import app.thelasthit.nrt.domain.game.news
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Achievements and gear (PLAN §8): checking for new unlocks, storing them with the time they came,
 * the trophy shelf, and the gear equipped.
 *
 * The rules ([NewUnlocks]) read a [GameRecord], mapped from what's stored. [check] runs them and
 * stores what's new, and [follow] checks on start and again after every write to the logs, the
 * programme and its results, the hit ledger or the breathing sessions. What came in waits in
 * [news] until the app has shown it.
 */
class UnlockRepository(
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val results: ResultSettler,
    private val clock: Clock,
    /** The phone's current zone, for today and the day a programme was abandoned on. */
    private val zone: () -> ZoneId = { clock.zone },
) {
    private val game = database.game()

    /** One check at a time, so two can't both find the same badge new. */
    private val mutex = Mutex()

    private val waiting = MutableStateFlow<List<UnlockNews>>(emptyList())

    /** Toasts and cards for unlocks that came in and haven't been shown yet, oldest first. */
    val news: StateFlow<List<UnlockNews>> = waiting.asStateFlow()

    /** Takes [item] off [news], once it's been shown or closed. */
    fun shown(item: UnlockNews) {
        waiting.update { list -> list.toMutableList().apply { remove(item) } }
    }

    /** What the game's rules read, as stored now. */
    suspend fun record(): GameRecord {
        val boundary = settings.current().dayBoundary
        val zone = zone()
        val products = database.products().allIncludingHidden().associate { it.id to it.toDomain() }
        // A row that doesn't read as a programme is left out rather than stopping every unlock.
        val programmes = database.programmes().all().mapNotNull { runCatching { it.toRecord(products) }.getOrNull() }
        return gameRecordOf(
            today = boundary.logicalDate(clock.instant(), zone),
            entries = database.entries().allLiveWithTags(),
            programmes = programmes,
            ledger = game.allLedger(),
            breathing = database.breathing().all(),
            boundary = boundary,
            zone = zone,
        )
    }

    /**
     * Settles the results that are due, then stores every badge and piece of gear that's newly
     * unlocked, with the time now, and puts them on [news]. Returns what it stored.
     */
    suspend fun check(): NewUnlocks = mutex.withLock {
        results.settleDue()
        val found = withContext(Dispatchers.Default) {
            NewUnlocks.since(game.achievements().map { it.key }.toSet(), game.gear().map { it.key }.toSet(), record())
        }
        if (found.isEmpty) return@withLock NewUnlocks.NONE
        val at = clock.millis()
        val stored = database.withTransaction {
            val badges = game.insertAchievements(found.achievements.map { AchievementEntity(it.key, at) })
            val gear = game.insertGear(found.gear.map { GearEntity(it.key, at) })
            NewUnlocks(
                found.achievements.filterIndexed { index, _ -> badges[index] != -1L },
                found.gear.filterIndexed { index, _ -> gear[index] != -1L },
            )
        }
        waiting.update { it + stored.news }
        stored
    }

    /**
     * Checks now, then after every write to the tables the rules read, until it's cancelled. The
     * app follows while it's on screen, and checks again each time it comes back, so a result
     * that fell due or a dose logged from the widget meanwhile is found then.
     */
    suspend fun follow() {
        database.invalidationTracker.createFlow(*WATCHED).conflate().collect {
            // Rows the rules can't read are skipped where they can be. Anything else waits for
            // the next write rather than stopping the app.
            try {
                check()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (broken: IllegalStateException) {
                Unit
            } catch (broken: IllegalArgumentException) {
                Unit
            }
        }
    }

    // The shelf and the gear.

    /** Every badge, earned or still to come, with when each was earned. */
    val shelf: Flow<TrophyShelf> = game.observeAchievements().map { rows ->
        TrophyShelf.of(rows.associate { it.key to Instant.ofEpochMilli(it.unlockedAt) })
    }.distinctUntilChanged()

    /** The gear unlocked and the piece equipped. */
    val loadout: Flow<Loadout> = combine(game.observeGear(), settings.settings) { rows, current ->
        Loadout.of(rows.map { it.key }.toSet(), current.equippedGear)
    }.distinctUntilChanged()

    /** Equips [gear], through the [Loadout]. False when it isn't unlocked yet. */
    suspend fun equip(gear: Gear): Boolean {
        val loadout = Loadout.of(game.gear().map { it.key }.toSet(), settings.current().equippedGear)
        if (!loadout.canEquip(gear)) return false
        settings.setEquippedGear(loadout.equip(gear).equipped.key)
        return true
    }

    private companion object {
        /** The tables the rules read. Achievements and gear aren't among them, so storing them doesn't check again. */
        val WATCHED = arrayOf(
            "entries", "entry_tags", "programmes", "baseline_units", "programme_weeks", "programme_week_units",
            "demons", "hit_ledger", "breathing_sessions",
        )
    }
}
