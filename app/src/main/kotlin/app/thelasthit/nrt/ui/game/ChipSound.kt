package app.thelasthit.nrt.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.thelasthit.nrt.domain.game.Gear
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The lair's sound effects (PLAN 7.3). Each is made in code from square and noise waves, so the app carries no audio files. */
enum class GameSound {
    /** The wind-up before a hit. */
    SWING,

    /** A hit landing. */
    IMPACT,

    /** One of the 5× attack's quick strikes. */
    STRIKE,

    /** The 5× attack's heavy finisher. */
    FINISHER,

    /** The final hit knocks the demon out. */
    KNOCKOUT,

    /** The demon breaks apart into pixels. */
    BREAK,

    /** The victory jingle. */
    VICTORY,
}

/**
 * How a piece of gear sounds when it hits (PLAN 8.2): [pitch] raises or lowers the tone, [duty]
 * is the square wave's width (thinner is brighter and buzzier), and [noise] how much crunch.
 */
data class HitVoice(val pitch: Double, val duty: Double, val noise: Double) {
    companion object {
        val PLAIN = HitVoice(pitch = 1.0, duty = 0.5, noise = 0.5)

        fun of(gear: Gear): HitVoice = when (gear) {
            Gear.WOODEN_STICK -> HitVoice(pitch = 0.8, duty = 0.5, noise = 0.6)
            Gear.WOODEN_SWORD -> PLAIN
            Gear.IRON_SWORD -> HitVoice(pitch = 1.3, duty = 0.25, noise = 0.4)
            Gear.AXE -> HitVoice(pitch = 0.9, duty = 0.25, noise = 0.8)
            Gear.WAR_HAMMER -> HitVoice(pitch = 0.6, duty = 0.5, noise = 1.0)
            Gear.FIRE_STAFF -> HitVoice(pitch = 1.1, duty = 0.125, noise = 0.9)
            Gear.LIGHTNING_BOW -> HitVoice(pitch = 1.7, duty = 0.125, noise = 0.5)
            Gear.LEGENDARY_BLADE -> HitVoice(pitch = 1.5, duty = 0.25, noise = 0.6)
        }
    }
}

/**
 * The 8-bit synth: square waves with a pitch sweep and noise from a shift register, as the old
 * consoles made them, mixed into 16-bit mono at [RATE]. Plain Kotlin, so it's tested on the JVM.
 */
object ChipSynth {
    const val RATE = 22_050

    /** Loudest a sample gets, below full scale so mixed waves never clip. */
    private const val PEAK = 0.8

    /** [sound] as 16-bit samples. The hit sounds take the gear's [voice]; the rest are the same for all. */
    fun render(sound: GameSound, voice: HitVoice = HitVoice.PLAIN): ShortArray {
        val p = voice.pitch
        val samples = when (sound) {
            GameSound.SWING -> noise(ms = 90, holdFrom = 2, holdTo = 9, volume = 0.22, swell = true)
            GameSound.IMPACT -> mix(
                square(ms = 100, from = 260 * p, to = 90 * p, duty = voice.duty, volume = 0.34),
                noise(ms = 80, holdFrom = 1, holdTo = 4, volume = 0.18 + 0.22 * voice.noise),
            )
            GameSound.STRIKE -> mix(
                square(ms = 50, from = 560 * p, to = 380 * p, duty = voice.duty, volume = 0.24),
                noise(ms = 30, holdFrom = 1, holdTo = 2, volume = 0.08 + 0.12 * voice.noise),
            )
            GameSound.FINISHER -> mix(
                square(ms = 240, from = 200 * p, to = 45 * p, duty = voice.duty, volume = 0.36),
                noise(ms = 300, holdFrom = 1, holdTo = 12, volume = 0.22 + 0.24 * voice.noise),
            )
            GameSound.KNOCKOUT -> notes(listOf(392.0, 330.0, 262.0, 196.0), ms = 95, duty = 0.5, volume = 0.3)
            GameSound.BREAK -> noise(ms = 420, holdFrom = 2, holdTo = 26, volume = 0.3)
            GameSound.VICTORY -> notes(listOf(523.0, 659.0, 784.0), ms = 100, duty = 0.25, volume = 0.26) +
                square(ms = 300, from = 1047.0, to = 1047.0, duty = 0.25, volume = 0.26)
        }
        return ShortArray(samples.size) { i -> (samples[i].coerceIn(-PEAK, PEAK) * Short.MAX_VALUE).roundToInt().toShort() }
    }

    /** How long [sound] plays, in milliseconds. */
    fun lengthMillis(samples: ShortArray): Long = samples.size * 1_000L / RATE

    /** A square wave sweeping from [from] to [to] Hz, with a quick attack and a decay to silence. */
    private fun square(ms: Int, from: Double, to: Double, duty: Double, volume: Double): DoubleArray {
        val n = RATE * ms / 1_000
        var phase = 0.0
        return DoubleArray(n) { i ->
            val t = i.toDouble() / n
            phase = (phase + from * (to / from).pow(t) / RATE) % 1.0
            (if (phase < duty) 1.0 else -1.0) * volume * envelope(i, n)
        }
    }

    /**
     * Noise from a 15-bit shift register, each value held for [holdFrom] samples at the start and
     * [holdTo] by the end, so it falls in pitch: a crunch, a whoosh, or rubble. With [swell] it
     * rises and falls, as a swing does, rather than starting at full.
     */
    private fun noise(ms: Int, holdFrom: Int, holdTo: Int, volume: Double, swell: Boolean = false): DoubleArray {
        val n = RATE * ms / 1_000
        var register = 0x4A3B
        var value = 1.0
        var held = 0
        return DoubleArray(n) { i ->
            val t = i.toDouble() / n
            val hold = (holdFrom + (holdTo - holdFrom) * t).roundToInt().coerceAtLeast(1)
            if (held++ >= hold) {
                held = 0
                val bit = (register xor (register shr 1)) and 1
                register = (register shr 1) or (bit shl 14)
                value = if (register and 1 == 0) 1.0 else -1.0
            }
            val shape = if (swell) sin(PI * t) else envelope(i, n)
            value * volume * shape
        }
    }

    /** Square notes one after another, each with its own attack and decay. */
    private fun notes(hz: List<Double>, ms: Int, duty: Double, volume: Double): DoubleArray =
        hz.map { square(ms, it, it, duty, volume) }.reduce(DoubleArray::plus)

    /** [a] and [b] played together from the start, as long as the longer one. */
    private fun mix(a: DoubleArray, b: DoubleArray): DoubleArray =
        DoubleArray(maxOf(a.size, b.size)) { i -> a.getOrElse(i) { 0.0 } + b.getOrElse(i) { 0.0 } }

    /** Up to full in 3 ms, so it doesn't click, then down to silence by the end. */
    private fun envelope(i: Int, n: Int): Double {
        val attack = RATE * 3 / 1_000
        val rise = if (i < attack) i.toDouble() / attack else 1.0
        val fall = 1.0 - i.toDouble() / n
        return rise * fall * fall
    }
}

/** Plays the lair's sounds. */
fun interface GameSounds {
    fun play(sound: GameSound, voice: HitVoice)
}

/**
 * Plays [ChipSynth]'s sounds through an [AudioTrack] each, off the main thread. Quiet whenever
 * the phone is on silent or vibrate, whatever the app's own switch says (ROADMAP P4-3).
 */
class ChipPlayer(context: Context) : GameSounds {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val worker = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "game-sounds").apply { isDaemon = true } }
    private val made = ConcurrentHashMap<Pair<GameSound, HitVoice>, ShortArray>()

    /** Whether the phone's ringer lets the game make a sound: not on silent or vibrate. */
    val phoneAllowsSound: Boolean get() = audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    override fun play(sound: GameSound, voice: HitVoice) {
        if (!phoneAllowsSound || worker.isShutdown) return
        worker.execute {
            try {
                val samples = made.getOrPut(sound to voice) { ChipSynth.render(sound, voice) }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(ChipSynth.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                worker.schedule({ track.release() }, ChipSynth.lengthMillis(samples) + RELEASE_AFTER_MS, TimeUnit.MILLISECONDS)
            } catch (unavailable: IllegalStateException) {
                // No audio output to be had just now: the game carries on without the sound.
            } catch (unavailable: IllegalArgumentException) {
                Unit
            } catch (unavailable: UnsupportedOperationException) {
                Unit
            }
        }
    }

    /** Stops taking sounds. Those already playing finish and are released. */
    fun close() = worker.shutdown()

    private companion object {
        /** Room after a sound ends before its track is let go. */
        const val RELEASE_AFTER_MS = 200L
    }
}

/** The sounds to play, for tests to swap in their own. Null means the phone's own speaker, through [ChipPlayer]. */
val LocalGameSounds = staticCompositionLocalOf<GameSounds?> { null }

/** The game's sounds while this is on screen. */
@Composable
fun rememberGameSounds(): GameSounds {
    LocalGameSounds.current?.let { return it }
    val context = LocalContext.current
    val player = remember(context) { ChipPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.close() } }
    return player
}
