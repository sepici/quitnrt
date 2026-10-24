package app.thelasthit.nrt.ui.lair

import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.domain.game.Attack
import app.thelasthit.nrt.ui.game.GameSound
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One attack in the lair, from the tap to the end of its animation. [cutShort] when the lair left
 * the screen before the animation ended: it shows as it ends and doesn't play again.
 */
data class Strike(val id: Long, val attack: Attack, val hpBefore: Int, val maxHp: Int, val cutShort: Boolean = false) {
    val hpAfter: Int get() = (hpBefore - attack.damage).coerceAtLeast(0)

    /** The final hit: the demon falls, and the defeat plays after the impact. */
    val isFinal: Boolean get() = hpAfter == 0
}

/** A point on the stage in whole sprite pixels, so everything moves in the art's own steps. */
data class Px(val x: Int, val y: Int)

/** The damage number floating up from the demon: [rise] pixels up, at [alpha]. */
data class Damage(val amount: Int, val rise: Int, val alpha: Float, val big: Boolean)

/** A pixel thrown off by a hit, in the gear's colour number [colour]. */
data class Spark(val at: Px, val colour: Int, val alpha: Float)

/**
 * Everything on the stage at one moment of a strike, in sprite pixels from the sprite's top left.
 * [frame] null means the frame for the HP shown, as when idle.
 */
data class Pose(
    val hp: Int,
    val frame: SpriteFrame? = null,
    val knockBack: Int = 0,
    val flash: Boolean = false,
    /** The gear's top left, while it's swinging. */
    val weapon: Px? = null,
    val damage: Damage? = null,
    val sparks: List<Spark> = emptyList(),
    val shake: Px = Px(0, 0),
) {
    /** Whether the demon is down: the defeat's frames are showing. */
    val isDefeated: Boolean get() = frame in SpriteFrame.DEFEAT
}

/**
 * A strike's animation (PLAN 7.3), worked out from the time since it started, so it's the same
 * whatever the frame rate and can be tested on its own.
 *
 * - **A hit:** the gear winds up and swings in; on impact the demon flashes white, shows its hurt
 *   frame and is knocked back, pixels fly off, and "-10" floats up.
 * - **The 5×:** five quick strikes, then a heavier finisher with a screen shake and "-50".
 * - **The final hit:** after the impact, the demon is knocked out and breaks apart into pixels,
 *   through its defeat frames, then the victory jingle.
 *
 * With [reduced] (the phone's animations off) nothing moves or flashes: the hurt frame and the
 * number show where they are, for a moment, and a final hit goes straight to the empty rock.
 *
 * [impact] is where the gear meets the demon, on its front edge, in sprite pixels.
 */
class BattleMotion(val strike: Strike, val reduced: Boolean, private val impact: Px = Px(3, 14)) {

    /** When the blow that does the damage lands, in milliseconds from the tap. */
    val impactAt: Long = when {
        reduced -> 0
        strike.attack == Attack.HIT -> HIT_IMPACT
        else -> FINISHER_IMPACT
    }

    /** When the defeat starts, for a final strike. */
    private val knockoutAt: Long = impactAt + KNOCKOUT_AFTER

    /** How long the whole strike takes. The attack buttons are busy until it's over. */
    val length: Long = when {
        reduced && strike.isFinal -> REDUCED_FINAL_LENGTH
        reduced -> REDUCED_LENGTH
        strike.isFinal -> knockoutAt + DEFEAT_LENGTH
        strike.attack == Attack.HIT -> HIT_LENGTH
        else -> COMBO_LENGTH
    }

    /** The sounds, in order, with when each plays. Haptics follow the same moments. */
    val cues: List<Pair<Long, GameSound>> = buildList {
        if (reduced) {
            add(0L to if (strike.attack == Attack.HIT) GameSound.IMPACT else GameSound.FINISHER)
            if (strike.isFinal) add(REDUCED_VICTORY to GameSound.VICTORY)
            return@buildList
        }
        if (strike.attack == Attack.HIT) {
            add(0L to GameSound.SWING)
            add(HIT_IMPACT to GameSound.IMPACT)
        } else {
            repeat(COMBO_STRIKES) { add(it * STRIKE_EVERY to GameSound.STRIKE) }
            add(FINISHER_WIND_UP to GameSound.SWING)
            add(FINISHER_IMPACT to GameSound.FINISHER)
        }
        if (strike.isFinal) {
            add(knockoutAt to GameSound.KNOCKOUT)
            add(knockoutAt + BREAK_AFTER to GameSound.BREAK)
            add(knockoutAt + VICTORY_AFTER to GameSound.VICTORY)
        }
    }

    /** The cues due after [from] and up to [to], for a frame that moved the clock on from one to the other. */
    fun cuesBetween(from: Long, to: Long): List<GameSound> = cues.filter { (at, _) -> at in (from + 1)..to }.map { it.second }

    fun at(ms: Long): Pose = if (reduced) reducedAt(ms) else when (strike.attack) {
        Attack.HIT -> hitAt(ms)
        Attack.COMBO -> comboAt(ms)
    }

    private fun reducedAt(ms: Long): Pose {
        val frame = when {
            strike.isFinal -> SpriteFrame.DEFEAT_4
            ms < REDUCED_HURT -> SpriteFrame.HURT
            else -> null
        }
        val damage = if (ms < REDUCED_LENGTH) Damage(strike.attack.damage, rise = 0, alpha = 1f, big = strike.attack == Attack.COMBO) else null
        return Pose(hp = strike.hpAfter, frame = frame, damage = damage)
    }

    private fun hitAt(ms: Long): Pose {
        val landed = ms >= HIT_IMPACT
        val since = ms - HIT_IMPACT
        val weapon = when {
            ms < 80 -> Px(impact.x - 20, impact.y - 10)
            ms < 160 -> Px(impact.x - 21, impact.y - 11)
            ms < 220 -> Px(impact.x - 22, impact.y - 12)
            ms < 260 -> Px(impact.x - 18, impact.y - 6)
            ms < 300 -> Px(impact.x - 15, impact.y - 4)
            // The blade's tip, near its icon's top right, follows through into the demon.
            ms < 420 -> Px(impact.x - 12, impact.y - 2)
            else -> null
        }
        return Pose(
            hp = if (landed) strike.hpAfter else strike.hpBefore,
            // Hurt until it's back on its feet, or, for the final hit, until it's knocked out.
            frame = defeatFrame(ms) ?: if (landed && since < if (strike.isFinal) KNOCKOUT_AFTER else HIT_HURT) SpriteFrame.HURT else null,
            knockBack = if (landed && !isDown(ms)) stepDown(since, from = 3, every = 60) else 0,
            flash = landed && since < 90,
            weapon = weapon,
            damage = damage(since, big = false),
            sparks = if (landed) sparks(since, count = 8, speed = 1f) else emptyList(),
        )
    }

    private fun comboAt(ms: Long): Pose {
        val landed = ms >= FINISHER_IMPACT
        val since = ms - FINISHER_IMPACT
        val striking = ms < COMBO_STRIKES * STRIKE_EVERY
        val strikeIndex = (ms / STRIKE_EVERY).toInt()
        val inStrike = ms - strikeIndex * STRIKE_EVERY
        val weapon = when {
            striking -> if (strikeIndex % 2 == 0) {
                if (inStrike < 40) Px(impact.x - 17, impact.y - 7) else Px(impact.x - 12, impact.y - 3)
            } else {
                if (inStrike < 40) Px(impact.x - 17, impact.y + 1) else Px(impact.x - 12, impact.y - 1)
            }
            ms < FINISHER_WIND_UP -> null
            ms < 850 -> Px(impact.x - 23, impact.y - 13)
            ms < FINISHER_IMPACT -> Px(impact.x - 18, impact.y - 6)
            ms < FINISHER_IMPACT + 160 -> Px(impact.x - 12, impact.y - 2)
            else -> null
        }
        val knockBack = when {
            isDown(ms) -> 0
            striking -> if (inStrike < 60) 1 else 0
            landed -> stepDown(since, from = 5, every = 80)
            else -> 0
        }
        val strikeSparks = if (striking && inStrike < 200) sparks(inStrike, count = 4, speed = 0.7f, seed = strikeIndex) else emptyList()
        return Pose(
            hp = if (landed) strike.hpAfter else strike.hpBefore,
            frame = defeatFrame(ms) ?: if (ms < FINISHER_IMPACT + 400 && (striking || landed)) SpriteFrame.HURT else null,
            knockBack = knockBack,
            flash = (striking && inStrike < 50) || (landed && since < 120),
            weapon = weapon,
            damage = damage(since, big = true),
            sparks = strikeSparks + if (landed) sparks(since, count = 12, speed = 1.4f) else emptyList(),
            shake = if (landed && since < SHAKE.size * SHAKE_EVERY) SHAKE[(since / SHAKE_EVERY).toInt()] else Px(0, 0),
        )
    }

    /** The defeat's frames, once a final strike has knocked the demon out, or null before. */
    private fun defeatFrame(ms: Long): SpriteFrame? {
        if (!strike.isFinal || ms < knockoutAt) return null
        val down = ms - knockoutAt
        return when {
            down < BREAK_AFTER -> SpriteFrame.DEFEAT_1
            down < BREAK_AFTER + 250 -> SpriteFrame.DEFEAT_2
            down < BREAK_AFTER + 500 -> SpriteFrame.DEFEAT_3
            else -> SpriteFrame.DEFEAT_4
        }
    }

    private fun isDown(ms: Long): Boolean = strike.isFinal && ms >= knockoutAt

    /** The number rises a pixel every 100 ms to [DAMAGE_RISE] up, holds, then fades. */
    private fun damage(since: Long, big: Boolean): Damage? {
        if (since < 0 || since >= DAMAGE_LENGTH) return null
        val alpha = if (since < DAMAGE_FADE) 1f else 1f - (since - DAMAGE_FADE).toFloat() / (DAMAGE_LENGTH - DAMAGE_FADE)
        return Damage(strike.attack.damage, rise = minOf(DAMAGE_RISE, (since / 100).toInt()), alpha = alpha, big = big)
    }

    /** Pixels thrown back from the impact, falling as they go, fading over their last half. */
    private fun sparks(since: Long, count: Int, speed: Float, seed: Int = 0): List<Spark> {
        if (since < 0 || since >= SPARK_LENGTH) return emptyList()
        val t = since / 1_000f
        val alpha = if (since < SPARK_LENGTH / 2) 1f else 1f - (since - SPARK_LENGTH / 2).toFloat() / (SPARK_LENGTH / 2)
        return List(count) { i ->
            val (vx, vy) = SPARK_PATHS[(i + seed * 3) % SPARK_PATHS.size]
            val x = impact.x + vx * speed * t
            val y = impact.y + vy * speed * t + GRAVITY * t * t / 2
            Spark(Px(x.roundToInt(), y.roundToInt()), colour = (i + seed) % 3, alpha = alpha)
        }
    }

    /** [from] pixels, less one every [every] ms, down to 0. */
    private fun stepDown(since: Long, from: Int, every: Long): Int = (from - (since / every).toInt()).coerceAtLeast(0)

    companion object {
        const val HIT_IMPACT = 300L
        private const val HIT_HURT = 300L

        /** Long enough for the damage number to fade. */
        const val HIT_LENGTH = HIT_IMPACT + 900L

        const val COMBO_STRIKES = 5
        const val STRIKE_EVERY = 130L
        const val FINISHER_WIND_UP = 650L
        const val FINISHER_IMPACT = 900L
        const val COMBO_LENGTH = FINISHER_IMPACT + 900L

        /** From the impact to the knockout, then the defeat's frames to the empty rock and the jingle. */
        const val KNOCKOUT_AFTER = 400L
        const val BREAK_AFTER = 600L
        const val VICTORY_AFTER = 1_450L
        const val DEFEAT_LENGTH = 1_700L

        const val DAMAGE_RISE = 4
        const val DAMAGE_FADE = 600L
        const val DAMAGE_LENGTH = 900L
        const val SPARK_LENGTH = 460L

        const val REDUCED_HURT = 600L
        const val REDUCED_LENGTH = 900L
        const val REDUCED_VICTORY = 300L
        const val REDUCED_FINAL_LENGTH = 900L

        /** Sprite pixels a second, thrown back and up, away from the swing. */
        private val SPARK_PATHS = listOf(
            -40f to -60f, -62f to -22f, -50f to 26f, -18f to -82f,
            -70f to -48f, -30f to 12f, -46f to -36f, -12f to -50f,
        )

        /** Sprite pixels a second, every second. */
        private const val GRAVITY = 140f

        /** The 5× finisher's shake: an offset every 40 ms, dying away. */
        private const val SHAKE_EVERY = 40L
        private val SHAKE = listOf(Px(2, 0), Px(-2, 1), Px(2, -1), Px(-1, 1), Px(1, 0), Px(-1, 0), Px(1, 0))

        /** The furthest the shake moves the stage each way, across and down. */
        val SHAKE_REACH = Px(SHAKE.maxOf { abs(it.x) }, SHAKE.maxOf { abs(it.y) })
    }
}
