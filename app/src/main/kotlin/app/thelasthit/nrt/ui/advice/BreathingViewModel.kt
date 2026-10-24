package app.thelasthit.nrt.ui.advice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.repo.BreathingRepository
import app.thelasthit.nrt.data.settings.ReminderStore
import app.thelasthit.nrt.domain.advice.BreathingOutcome
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.advice.BreathingPlan
import app.thelasthit.nrt.domain.advice.BreathingRun
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Where the breathing timer is: choosing, breathing, or the end frame. */
sealed interface BreathingStage {
    data class SetUp(val pattern: BreathingPattern, val minutes: Int, val buzz: Boolean) : BreathingStage {
        val plan: BreathingPlan get() = BreathingPlan(pattern, minutes)
    }

    data class Running(val run: BreathingRun) : BreathingStage

    /** A session run to the end. [session] is its number among those that count, once it's saved. */
    data class Done(val plan: BreathingPlan, val session: Int?) : BreathingStage
}

/**
 * The breathing timer (PLAN §9). Everything it needs to carry on is in [saved], so it survives
 * the app being closed and opened again. A session is saved for the breathing badges when it
 * runs to the end, and when it's stopped part way, which doesn't count.
 *
 * Whether it buzzes at each change is kept with this phone's other settings in [prefs], out of
 * backups, and is on until it's switched off.
 */
class BreathingViewModel(
    private val clock: Clock,
    private val saved: SavedStateHandle,
    private val breathing: BreathingRepository,
    private val buzzer: Buzzer,
    private val prefs: ReminderStore?,
) : ViewModel() {

    private val _stage = MutableStateFlow(restore())
    val stage: StateFlow<BreathingStage> = _stage.asStateFlow()

    private val _now = MutableStateFlow(clock.instant())
    val now: StateFlow<Instant> = _now.asStateFlow()

    /** Sessions that count so far, for the badge line on the set-up. */
    val sessions: StateFlow<Int?> = breathing.completedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The step the last tick saw, so each change buzzes once. */
    private var lastPhase = -1

    /** The buzz setting kept on this phone, once it's been read. */
    private var keptBuzz: Boolean? = null

    init {
        prefs?.let { store ->
            viewModelScope.launch {
                val on = store.timers.first().breathingBuzz
                keptBuzz = on
                // The set-up shows the kept setting, unless the switch was changed here before it was read.
                if (saved.get<Boolean>(BUZZ_CHANGED) != true) setUp { it.copy(buzz = on) }
            }
        }
        if (_stage.value is BreathingStage.Running) ScreenOnHolds.hold(this)
        // "Breathe for 1 minute" on the tip after logging starts at once, the first time only.
        if (saved.get<Boolean>(START) == true && saved.get<Boolean>(STARTED) != true) {
            saved[STARTED] = true
            start()
        }
    }

    fun pickPattern(pattern: BreathingPattern) = setUp { it.copy(pattern = pattern) }

    fun pickMinutes(minutes: Int) = setUp { it.copy(minutes = minutes.coerceIn(BreathingPlan.MINUTES)) }

    fun setBuzz(on: Boolean) {
        saved[BUZZ_CHANGED] = true
        keptBuzz = on
        prefs?.let { store -> viewModelScope.launch(NonCancellable) { store.setBreathingBuzz(on) } }
        setUp { it.copy(buzz = on) }
    }

    fun start() {
        val setUp = _stage.value as? BreathingStage.SetUp ?: return
        begin(setUp.plan)
    }

    /** From the end frame: another minute of the same pattern. */
    fun another() {
        val done = _stage.value as? BreathingStage.Done ?: return
        begin(BreathingPlan(done.plan.pattern, 1))
    }

    fun pause() = running { it.pause(clock.instant()) }

    fun resume() = running { it.resume(clock.instant()) }

    /** Called on each frame while a session runs: buzzes at each change and ends the session on time. */
    fun tick() {
        val now = clock.instant()
        _now.value = now
        val run = (_stage.value as? BreathingStage.Running)?.run ?: return
        if (run.isPaused) return
        val moment = run.moment(now)
        if (!moment.finished && moment.phaseNumber != lastPhase && buzz) buzzer.tick()
        lastPhase = moment.phaseNumber
        if (moment.finished) finish(run, now)
    }

    /** Stops a session part way, as the cross or Back does. What was breathed is kept, and doesn't count. */
    fun stop() {
        val run = (_stage.value as? BreathingStage.Running)?.run ?: return
        val outcome = run.outcome(clock.instant())
        if (outcome.duration > Duration.ZERO) save(outcome) {}
        ScreenOnHolds.release(this)
        set(BreathingStage.SetUp(run.plan.pattern, run.plan.minutes, buzz))
    }

    override fun onCleared() {
        ScreenOnHolds.release(this)
    }

    private val buzz: Boolean get() = saved.get<Boolean>(BUZZ) ?: keptBuzz ?: true

    private fun begin(plan: BreathingPlan) {
        val now = clock.instant()
        lastPhase = -1
        ScreenOnHolds.hold(this)
        set(BreathingStage.Running(BreathingRun(plan, now)))
        tick()
    }

    private fun finish(run: BreathingRun, now: Instant) {
        ScreenOnHolds.release(this)
        set(BreathingStage.Done(run.plan, session = null))
        if (buzz) buzzer.finish()
        save(run.outcome(now)) { count ->
            if (_stage.value == BreathingStage.Done(run.plan, null)) set(BreathingStage.Done(run.plan, count))
        }
    }

    /** Saves [outcome], even if the page closes straight after, then passes on the count that counts. */
    private fun save(outcome: BreathingOutcome, then: (Int) -> Unit) {
        viewModelScope.launch(NonCancellable) {
            breathing.record(outcome.pattern, outcome.startedAt, outcome.duration, outcome.rounds, outcome.ranToEnd)
            then(breathing.completedCount())
        }
    }

    private fun setUp(change: (BreathingStage.SetUp) -> BreathingStage.SetUp) {
        val setUp = _stage.value as? BreathingStage.SetUp ?: return
        set(change(setUp))
    }

    private fun running(change: (BreathingRun) -> BreathingRun) {
        val running = _stage.value as? BreathingStage.Running ?: return
        set(BreathingStage.Running(change(running.run)))
        _now.value = clock.instant()
    }

    private fun set(stage: BreathingStage) {
        _stage.value = stage
        when (stage) {
            is BreathingStage.SetUp -> {
                saved[STAGE] = SET_UP
                saved[PATTERN] = stage.pattern.name
                saved[MINUTES] = stage.minutes
                saved[BUZZ] = stage.buzz
            }
            is BreathingStage.Running -> {
                val run = stage.run
                saved[STAGE] = RUNNING
                saved[PATTERN] = run.plan.pattern.name
                saved[MINUTES] = run.plan.minutes
                saved[RUN_STARTED] = run.startedAt.toEpochMilli()
                saved[RUN_BEFORE] = run.before.toMillis()
                saved[RUN_RESUMED] = run.resumedAt?.toEpochMilli() ?: -1L
            }
            is BreathingStage.Done -> {
                saved[STAGE] = DONE
                saved[PATTERN] = stage.plan.pattern.name
                saved[MINUTES] = stage.plan.minutes
                saved[SESSION] = stage.session ?: -1
            }
        }
    }

    private fun restore(): BreathingStage {
        val pattern = saved.get<String>(PATTERN)?.let { name -> BreathingPattern.entries.firstOrNull { it.name == name } } ?: BreathingPattern.BOX
        val minutes = (saved.get<Int>(MINUTES) ?: 1).coerceIn(BreathingPlan.MINUTES)
        val plan = BreathingPlan(pattern, minutes)
        return when (saved.get<String>(STAGE)) {
            RUNNING -> {
                val started = saved.get<Long>(RUN_STARTED)?.let(Instant::ofEpochMilli) ?: return setUpOf(pattern, minutes)
                val resumed = saved.get<Long>(RUN_RESUMED)?.takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
                BreathingStage.Running(BreathingRun(plan, started, Duration.ofMillis(saved.get<Long>(RUN_BEFORE) ?: 0), resumed))
            }
            DONE -> BreathingStage.Done(plan, saved.get<Int>(SESSION)?.takeIf { it > 0 })
            else -> setUpOf(pattern, minutes)
        }
    }

    private fun setUpOf(pattern: BreathingPattern, minutes: Int) = BreathingStage.SetUp(pattern, minutes, buzz)

    companion object {
        // The route's arguments, which the saved state starts from.
        const val PATTERN = AdviceRoutes.PATTERN
        const val MINUTES = AdviceRoutes.MINUTES
        const val START = AdviceRoutes.START

        private const val STARTED = "started"
        private const val STAGE = "stage"
        private const val BUZZ = "buzz"
        private const val BUZZ_CHANGED = "buzzChanged"
        private const val RUN_STARTED = "runStarted"
        private const val RUN_BEFORE = "runBefore"
        private const val RUN_RESUMED = "runResumed"
        private const val SESSION = "session"

        private const val SET_UP = "setUp"
        private const val RUNNING = "running"
        private const val DONE = "done"
    }
}
