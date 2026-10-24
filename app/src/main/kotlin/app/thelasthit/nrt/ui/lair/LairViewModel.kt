package app.thelasthit.nrt.ui.lair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.DemonState
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.data.repo.SpendResult
import app.thelasthit.nrt.data.settings.Settings
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.game.Attack
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.Victory
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Where the programme is, for the bar across the top of the stage. */
sealed interface WeekLabel {
    /** The plan is built and its first week starts on [on]. */
    data class Starts(val on: LocalDate) : WeekLabel

    data class Week(val number: Int, val of: Int) : WeekLabel

    /** NRT-free weeks after a missed final week. */
    data class Overtime(val number: Int) : WeekLabel

    data object Done : WeekLabel
}

/** The lair: the demon lurking before a plan starts, then the battle. */
sealed interface LairState {
    /** The name picked, or null for the app's default. */
    val name: String?
    val creature: Creature

    /** The colours picked, or null for the creature's own. */
    val variant: String?

    /** Before the plan starts: a shadow on the rock, with no HP to show. */
    data class Lurking(override val name: String?, override val creature: Creature, override val variant: String?) : LairState

    /** The plan's demon, with its HP and the hits banked to spend on it. */
    data class Battle(
        override val name: String?,
        override val creature: Creature,
        override val variant: String?,
        val hp: Int,
        val maxHp: Int,
        val banked: Int,
        val week: WeekLabel,
        /** The logical day it is, from the day start. */
        val today: LocalDate,
        /** The latest result in is a miss, so the demon looks smug. */
        val lastWeekMissed: Boolean,
        /** Once the final hit has landed: what the victory screen sums up. */
        val victory: Victory? = null,
        /** Tags by key, for naming the triggers in the victory. */
        val tags: Map<String, TagEntity> = emptyMap(),
    ) : LairState {
        val isDefeated: Boolean get() = maxHp > 0 && hp <= 0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LairViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val strikes = MutableStateFlow<Strike?>(null)
    private val landed = MutableStateFlow<Attack?>(null)
    private var nextStrike = 1L

    val state: StateFlow<LairState?> =
        combine(graph.settings.settings, graph.game.demon, graph.programmes.observeCurrent()) { settings, demon, programme ->
            Triple(settings, demon, programme)
        }.mapLatest { (settings, demon, programme) -> build(settings, demon, programme) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The strike playing now, from the tap to the end of its animation. Taps wait for it. */
    val strike: StateFlow<Strike?> = strikes.asStateFlow()

    /** The attack that landed last on this visit, which the demon answers to. */
    val lastAttack: StateFlow<Attack?> = landed.asStateFlow()

    val soundOn: StateFlow<Boolean> = graph.settings.settings.map { it.soundOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The gear the hits are made with. */
    val equipped: StateFlow<Gear> = graph.unlocks.loadout.map { it.equipped }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Gear.WOODEN_STICK)

    /**
     * Spends hits on the demon. The strike starts at once, so the wind-up plays while the ledger
     * is written; it's refused while another strike plays or when there aren't the hits for it.
     * The final hit marks the demon defeated.
     */
    fun attack(attack: Attack) {
        val battle = state.value as? LairState.Battle ?: return
        if (strikes.value != null || battle.isDefeated || !attack.canUse(battle.banked, battle.hp)) return
        val strike = Strike(nextStrike++, attack, battle.hp, battle.maxHp)
        strikes.value = strike
        viewModelScope.launch {
            try {
                val result = when (attack) {
                    Attack.HIT -> graph.game.spendHit()
                    Attack.COMBO -> graph.game.comboAttack()
                }
                if (result is SpendResult.Spent) {
                    if (result.balance.hp <= 0) graph.game.markDefeated()
                    // The strike ends on the HP it took the demon to, so wait for the state to show it.
                    state.first { (it as? LairState.Battle)?.let { now -> now.hp <= strike.hpAfter } ?: true }
                }
            } finally {
                finish(strike, written = true)
            }
        }
    }

    /** The strike's animation is over. It ends once the ledger shows it too. */
    fun strikeDone(strike: Strike) = finish(strike, animated = true)

    private val written = mutableSetOf<Long>()
    private val animated = mutableSetOf<Long>()

    /** Ends [strike] once it's both written and played: the screen then shows the ledger as it is. */
    private fun finish(strike: Strike, written: Boolean = false, animated: Boolean = false) {
        if (written) this.written += strike.id
        if (animated) this.animated += strike.id
        if (strike.id !in this.written || strike.id !in this.animated || strikes.value?.id != strike.id) return
        landed.value = strike.attack
        strikes.value = null
    }

    /** A new name or look: for the demon fighting now, or the one the plan will start with. */
    fun restyle(name: String, creature: Creature, variant: String) {
        viewModelScope.launch { graph.game.restyleDemon(name.trim(), creature, variant) }
    }

    private suspend fun build(settings: Settings, demon: DemonState?, programme: ProgrammeRecord?): LairState {
        if (demon == null) return LairState.Lurking(settings.demonName, settings.demonCreature, settings.demonVariant)
        val balance = demon.balance
        val plan = programme?.plan
        val today = graph.programmes.clock().today
        val week = when {
            programme == null || plan == null -> WeekLabel.Done
            programme.status == ProgrammeStatus.COMPLETE -> WeekLabel.Done
            today < plan.startDate -> WeekLabel.Starts(plan.startDate)
            programme.status == ProgrammeStatus.OVERTIME -> WeekLabel.Overtime(plan.weekOn(today)?.number ?: plan.weeks.size)
            else -> WeekLabel.Week(plan.weekOn(today)?.number ?: plan.weeks.size, plan.weeks.size)
        }
        val battle = LairState.Battle(
            name = demon.demon.name,
            creature = demon.demon.creature,
            variant = demon.demon.variant,
            hp = balance.hp,
            maxHp = balance.maxHp,
            banked = balance.banked,
            week = week,
            today = today,
            lastWeekMissed = plan?.weeks?.lastOrNull { it.result != WeekResult.PENDING }?.result == WeekResult.MISSED,
        )
        if (!battle.isDefeated) return battle
        val record = graph.unlocks.record()
        val victory = record.programmes.lastOrNull()?.let { Victory.of(it, record.entries, settings.dayBoundary, zone()) }
        return battle.copy(victory = victory, tags = graph.tags.all.first().associateBy { it.key })
    }
}
