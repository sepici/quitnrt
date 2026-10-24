package app.thelasthit.nrt.ui.advice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.domain.advice.DelayRun
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** Where the delay timer is: choosing a length, counting down, or the end frame. */
sealed interface DelayStage {
    data class SetUp(val minutes: Int) : DelayStage

    data class Running(val run: DelayRun) : DelayStage

    data class Done(val run: DelayRun) : DelayStage
}

/**
 * The delay timer (PLAN §9). It counts from its start by the clock, and its start is in [saved],
 * so it's right whenever it's looked at again, even after the app was closed.
 *
 * While it runs it keeps the screen on, and [awake] keeps the processor running until just after
 * the end, so it buzzes on time under the breathing timer, behind another app or with the phone
 * locked. If Android closes or freezes the app in the meantime, there's no buzz, and the timer
 * shows as done when it's opened again. A notification comes with reminders in phase 6.
 */
class DelayViewModel(
    private val clock: Clock,
    private val saved: SavedStateHandle,
    private val buzzer: Buzzer,
    private val awake: WakeHold,
) : ViewModel() {

    private val _stage = MutableStateFlow(restore())
    val stage: StateFlow<DelayStage> = _stage.asStateFlow()

    private val _now = MutableStateFlow(clock.instant())
    val now: StateFlow<Instant> = _now.asStateFlow()

    private var ending: Job? = null

    init {
        keepAwake(_stage.value)
        (_stage.value as? DelayStage.Running)?.let { watch(it.run) }
        tick()
    }

    fun less() = setUp { it.copy(minutes = (it.minutes - 1).coerceIn(DelayRun.MINUTES)) }

    fun more() = setUp { it.copy(minutes = (it.minutes + 1).coerceIn(DelayRun.MINUTES)) }

    fun start() {
        val minutes = when (val stage = _stage.value) {
            is DelayStage.SetUp -> stage.minutes
            is DelayStage.Done -> stage.run.minutes
            is DelayStage.Running -> return
        }
        val run = DelayRun(clock.instant(), minutes)
        set(DelayStage.Running(run))
        watch(run)
        tick()
    }

    /** Stops the countdown and goes back to choosing a length. */
    fun stop() {
        val running = _stage.value as? DelayStage.Running ?: return
        ending?.cancel()
        set(DelayStage.SetUp(running.run.minutes))
    }

    /** Looks at the clock: the page calls this a few times a second while it shows. */
    fun tick() {
        val now = clock.instant()
        _now.value = now
        val running = _stage.value as? DelayStage.Running ?: return
        if (running.run.isDone(now)) {
            ending?.cancel()
            set(DelayStage.Done(running.run))
            if (running.run.buzzesAt(now)) buzzer.alarm()
        }
    }

    override fun onCleared() {
        ScreenOnHolds.release(this)
        awake.release()
    }

    /** Ends the timer on time while the app runs, whatever page is showing. */
    private fun watch(run: DelayRun) {
        ending?.cancel()
        ending = viewModelScope.launch {
            while (true) {
                val left = run.remaining(clock.instant())
                if (left.isZero) break
                delay(left.toMillis())
            }
            tick()
        }
    }

    private fun setUp(change: (DelayStage.SetUp) -> DelayStage.SetUp) {
        val setUp = _stage.value as? DelayStage.SetUp ?: return
        set(change(setUp))
    }

    private fun set(stage: DelayStage) {
        _stage.value = stage
        keepAwake(stage)
        when (stage) {
            is DelayStage.SetUp -> {
                saved[STAGE] = SET_UP
                saved[MINUTES] = stage.minutes
            }
            is DelayStage.Running -> {
                saved[STAGE] = RUNNING
                saved[MINUTES] = stage.run.minutes
                saved[STARTED_AT] = stage.run.startedAt.toEpochMilli()
            }
            is DelayStage.Done -> {
                saved[STAGE] = DONE
                saved[MINUTES] = stage.run.minutes
                saved[STARTED_AT] = stage.run.startedAt.toEpochMilli()
            }
        }
    }

    /** Keeps the screen and the processor on while [stage] runs, the processor until a little after the end. */
    private fun keepAwake(stage: DelayStage) {
        if (stage is DelayStage.Running) {
            ScreenOnHolds.hold(this)
            awake.hold(stage.run.remaining(clock.instant()) + DelayRun.BUZZ_WINDOW)
        } else {
            ScreenOnHolds.release(this)
            awake.release()
        }
    }

    private fun restore(): DelayStage {
        val minutes = (saved.get<Int>(MINUTES) ?: DelayRun.DEFAULT_MINUTES).coerceIn(DelayRun.MINUTES)
        val run = saved.get<Long>(STARTED_AT)?.let { DelayRun(Instant.ofEpochMilli(it), minutes) }
        return when (saved.get<String>(STAGE)) {
            RUNNING -> run?.let { DelayStage.Running(it) }
            DONE -> run?.let { DelayStage.Done(it) }
            else -> null
        } ?: DelayStage.SetUp(minutes)
    }

    companion object {
        const val STAGE = "stage"
        const val MINUTES = "minutes"
        const val STARTED_AT = "startedAt"

        const val SET_UP = "setUp"
        const val RUNNING = "running"
        const val DONE = "done"
    }
}
