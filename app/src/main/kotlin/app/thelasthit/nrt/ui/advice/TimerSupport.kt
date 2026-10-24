package app.thelasthit.nrt.ui.advice

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.ui.graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration

/** The timers' buzzes. */
interface Buzzer {
    /** A short tap at each change of breath. */
    fun tick()

    /** Two taps, as a breathing session ends. */
    fun finish()

    /** The delay timer's end: three long buzzes, as an alarm, which Android lets through from an app in the background. */
    fun alarm()
}

/** The phone's vibration motor. Nothing happens on a phone without one. */
class VibratorBuzzer(context: Context) : Buzzer {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    override fun tick() = vibrate(VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE), alarm = false)

    override fun finish() = vibrate(VibrationEffect.createWaveform(FINISH, -1), alarm = false)

    override fun alarm() = vibrate(VibrationEffect.createWaveform(ALARM, -1), alarm = true)

    private fun vibrate(effect: VibrationEffect, alarm: Boolean) {
        val motor = vibrator?.takeIf { it.hasVibrator() } ?: return
        when {
            !alarm -> motor.vibrate(effect)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                motor.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            else -> {
                @Suppress("DEPRECATION")
                motor.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
    }

    companion object {
        const val TICK_MS = 40L
        val FINISH = longArrayOf(0, 60, 120, 60)
        val ALARM = longArrayOf(0, 400, 250, 400, 250, 400)
    }
}

/**
 * Keeps the phone's processor running while the delay timer counts, so its end buzzes on time
 * with the screen off. Without it, a locked phone sleeps and the countdown stops with it.
 */
interface WakeHold {
    /** Keeps the processor awake for at most [timeout]. Holding again starts the timeout over. */
    fun hold(timeout: Duration)

    fun release()
}

/** A partial wake lock: the screen can go off, and the processor stays on until it's released or times out. */
class PartialWakeLock(context: Context) : WakeHold {

    private val lock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        ?.apply { setReferenceCounted(false) }

    override fun hold(timeout: Duration) {
        lock?.acquire(timeout.toMillis().coerceAtLeast(1))
    }

    override fun release() {
        lock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val TAG = "LastHit:delay"
    }
}

/**
 * The timers that want the screen kept on. While any holds it, the app's window stays awake,
 * whichever page is showing: a delay keeps it on while breathing runs on top of it.
 */
object ScreenOnHolds {
    private val holders = mutableSetOf<Any>()
    private val _held = MutableStateFlow(false)

    val held: StateFlow<Boolean> = _held.asStateFlow()

    fun hold(holder: Any) = update { holders += holder }

    fun release(holder: Any) = update { holders -= holder }

    private fun update(change: () -> Unit) = synchronized(holders) {
        change()
        _held.value = holders.isNotEmpty()
    }
}

/** Keeps the window awake while a timer holds the screen on. The shell has one, and so does each timer page. */
@Composable
fun KeepScreenOnWhileTimersRun() {
    val view = LocalView.current
    LaunchedEffect(view) {
        ScreenOnHolds.held.collect { view.keepScreenOn = it }
    }
}

/**
 * Calls [onTick] while [running] and the page is on screen: every frame when [everyFrame], for
 * an animation, otherwise a few times a second. A UI test stops endless frame loops, so there
 * it ticks on a short delay instead, and the test clock moving shows on screen.
 */
@Composable
fun TimerTicks(running: Boolean, everyFrame: Boolean, onTick: () -> Unit) {
    val tick by rememberUpdatedState(onTick)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(running && visible, everyFrame) {
        if (!running || !visible) return@LaunchedEffect
        val underTest = coroutineContext[InfiniteAnimationPolicy] != null
        while (true) {
            tick()
            if (everyFrame && !underTest) withFrameNanos { } else delay(if (everyFrame) FRAME_STEP_MS else STEP_MS)
        }
    }
}

private const val STEP_MS = 200L
private const val FRAME_STEP_MS = 50L

/** A timer's ViewModel, built from the app's [DataGraph] with its saved state, which survives the app being closed. */
@Composable
inline fun <reified VM : ViewModel> timerViewModel(crossinline create: (DataGraph, SavedStateHandle, Context) -> VM): VM =
    viewModel(
        factory = viewModelFactory {
            initializer { create(graph, createSavedStateHandle(), checkNotNull(this[APPLICATION_KEY])) }
        },
    )
