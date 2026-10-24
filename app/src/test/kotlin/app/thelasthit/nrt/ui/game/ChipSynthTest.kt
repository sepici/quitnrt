package app.thelasthit.nrt.ui.game

import app.thelasthit.nrt.domain.game.Gear
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChipSynthTest {

    @Test
    fun `every sound is short, audible and never clips`() {
        for (sound in GameSound.entries) {
            val samples = ChipSynth.render(sound)
            val millis = ChipSynth.lengthMillis(samples)
            assertTrue("$sound is $millis ms", millis in 30..700)
            val peak = samples.maxOf { abs(it.toInt()) }
            assertTrue("$sound peaks at $peak", peak > Short.MAX_VALUE / 10)
            assertTrue("$sound peaks at $peak, past 80%", peak <= (Short.MAX_VALUE * 0.8).toInt() + 1)
        }
    }

    @Test
    fun `sounds fade out, so nothing ends on a click`() {
        for (sound in GameSound.entries) {
            val samples = ChipSynth.render(sound)
            val tail = samples.takeLast(ChipSynth.RATE / 1_000).maxOf { abs(it.toInt()) }
            assertTrue("$sound ends at $tail", tail < Short.MAX_VALUE / 20)
        }
    }

    @Test
    fun `the same sound comes out the same every time`() {
        for (sound in GameSound.entries) {
            assertArrayEquals("$sound", ChipSynth.render(sound), ChipSynth.render(sound))
        }
    }

    @Test
    fun `gear changes how a hit sounds, and nothing else`() {
        val stick = HitVoice.of(Gear.WOODEN_STICK)
        val bow = HitVoice.of(Gear.LIGHTNING_BOW)
        for (sound in listOf(GameSound.IMPACT, GameSound.STRIKE, GameSound.FINISHER)) {
            assertFalse("$sound", ChipSynth.render(sound, stick).contentEquals(ChipSynth.render(sound, bow)))
        }
        for (sound in listOf(GameSound.KNOCKOUT, GameSound.BREAK, GameSound.VICTORY, GameSound.SWING)) {
            assertArrayEquals("$sound", ChipSynth.render(sound, stick), ChipSynth.render(sound, bow))
        }
        assertEquals(Gear.entries.size, Gear.entries.map { HitVoice.of(it) }.toSet().size)
    }
}
