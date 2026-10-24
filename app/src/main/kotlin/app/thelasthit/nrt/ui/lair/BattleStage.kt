package app.thelasthit.nrt.ui.lair

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.creature.SpriteFrame
import app.thelasthit.nrt.domain.creature.SpriteSheet
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.ui.game.GameArt
import app.thelasthit.nrt.ui.game.GameIcons
import app.thelasthit.nrt.ui.game.drawBackdrop
import app.thelasthit.nrt.ui.game.drawPixels
import app.thelasthit.nrt.ui.game.drawRim
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * The lair's stage with the demon on its rock, in its colours: the frame for its HP, or the
 * one [pose] asks for while a strike plays, with the gear swinging, the flash, the pixels thrown
 * off, the damage number and the shake. In dark mode the demon has the backdrop's light rim, in
 * whole sprite pixels.
 */
@Composable
fun BattleScene(
    creature: Creature,
    variant: String?,
    frame: SpriteFrame,
    pose: Pose?,
    equipped: Gear,
    modifier: Modifier = Modifier,
) {
    val colors = LastHit.colors
    val dark = colors.isDark
    val backdrop = GameArt.stage
    val image = remember(dark) { GameArt.backdrop(backdrop, dark) }
    val sheet = remember(creature) { GameArt.sheet(creature) }
    val colours = variant ?: sheet.defaultVariant
    val sprite = GameArt.sprite(sheet, frame, colours)
    // Once it's breaking up, the loose pixels go without a rim, as the mockups' empty rock has them.
    val rim = backdrop.rim(dark)?.takeIf { frame !in BREAKING }?.let { GameArt.rim(sheet, frame, Color(it)) }
    val flash = if (pose?.flash == true) GameArt.flash(sheet, frame, colours, FLASH) else null
    val weapon = GameIcons.gear(equipped)
    val sparkColours = GameIcons.gearColours(equipped, colors.stageSky)
    val measurer = rememberTextMeasurer()
    val damage = pose?.damage
    val damageText = damage?.let { stringResource(R.string.lair_damage, it.amount) }
    val damageStyle = LastHit.type.pixel
    val damageFill = colors.marigold
    val damageEdge = colors.onMarigold
    Canvas(modifier) {
        val scale = maxOf(size.width / backdrop.width, size.height / backdrop.height)
        val shake = pose?.shake ?: Px(0, 0)
        translate(shake.x * scale, shake.y * scale) {
            val (_, origin) = drawBackdrop(backdrop, image)
            val base = origin + Offset(backdrop.spriteX * scale, backdrop.spriteY * scale)
            val at = base + Offset((pose?.knockBack ?: 0) * scale, 0f)
            rim?.let { drawRim(it, at, scale) }
            drawPixels(sprite, at, scale)
            flash?.let { drawPixels(it, at, scale) }
            pose?.weapon?.let { drawPixels(weapon, base + Offset(it.x * scale, it.y * scale), scale) }
            pose?.sparks?.forEach { spark ->
                drawRect(
                    sparkColours[spark.colour % sparkColours.size],
                    topLeft = base + Offset(spark.at.x * scale, spark.at.y * scale),
                    size = Size(scale, scale),
                    alpha = spark.alpha,
                )
            }
            if (damage != null && damageText != null) {
                // The pixel font is drawn on a grid of 8 to the em, so 8 sprite pixels to the em
                // puts each of its pixels on one of the art's.
                val em = (if (damage.big) BIG_DAMAGE_EM else DAMAGE_EM) * scale
                val layout = measurer.measure(damageText, damageStyle.copy(fontSize = em.toSp(), lineHeight = em.toSp()))
                val topLeft = base + Offset(
                    SPRITE_MIDDLE * scale - layout.size.width / 2f,
                    (DAMAGE_BASE - damage.rise) * scale - layout.size.height,
                )
                val edge = em / 8
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx != 0 || dy != 0) drawText(layout, damageEdge, topLeft + Offset(dx * edge, dy * edge), alpha = damage.alpha)
                    }
                }
                drawText(layout, damageFill, topLeft, alpha = damage.alpha)
            }
        }
    }
}

/**
 * The frame the demon shows when no strike is playing: its condition for [hp] of [maxHp], with
 * the second idle frame every other beat while it's fresh, and the empty rock once it's defeated.
 */
fun restingFrame(hp: Int, maxHp: Int, idleTwo: Boolean): SpriteFrame = when {
    maxHp > 0 && hp <= 0 -> SpriteFrame.DEFEAT_4
    else -> SpriteFrame.forHealth(hp, maxHp.coerceAtLeast(1)).let { if (it == SpriteFrame.IDLE_1 && idleTwo) SpriteFrame.IDLE_2 else it }
}

/**
 * Where the gear meets the demon: a pixel in from its front edge, at chest height, so the swing
 * lands on the creature whatever its shape.
 */
fun impactPoint(sheet: SpriteSheet): Px {
    val pixels = sheet.argbPixels(SpriteFrame.IDLE_1)
    val size = sheet.size
    val front = (IMPACT_ROW - 2..IMPACT_ROW + 2).minOf { y -> (0 until size).firstOrNull { x -> pixels[y * size + x] != 0 } ?: size / 2 }
    return Px(front + 1, IMPACT_ROW)
}

/**
 * The phone's animation speed, from its developer or accessibility settings: 1 is normal, and 0
 * means animations are off ("remove animations"), so the lair keeps still.
 */
@Composable
fun rememberAnimatorScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
}

/** The flash a hit makes: the sprite lit up in its highlight colour, inside its outline. */
private val FLASH = Color(0xFFFBF8E8)

/** The defeat's frames after the knockout, when the demon is coming apart. */
private val BREAKING = SpriteFrame.DEFEAT - SpriteFrame.DEFEAT_1

private const val DAMAGE_EM = 8f
private const val BIG_DAMAGE_EM = 12f

/** The middle of a 32-pixel sprite, and the row the damage number sits on before it rises. */
private const val SPRITE_MIDDLE = 16f
private const val DAMAGE_BASE = 2f

private const val IMPACT_ROW = 14
