package app.thelasthit.nrt.domain.creature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CreatureTest {

    @Test
    fun `the frame follows the creature's health`() {
        assertEquals(SpriteFrame.IDLE_1, SpriteFrame.forHealth(120, 120))
        assertEquals(SpriteFrame.IDLE_1, SpriteFrame.forHealth(81, 120))
        assertEquals(SpriteFrame.ROUGH, SpriteFrame.forHealth(80, 120))
        assertEquals(SpriteFrame.ROUGH, SpriteFrame.forHealth(41, 120))
        assertEquals(SpriteFrame.BARELY, SpriteFrame.forHealth(40, 120))
        assertEquals(SpriteFrame.BARELY, SpriteFrame.forHealth(0, 120))
    }

    @Test
    fun `the defeat plays after the condition frames, never as one`() {
        assertEquals(listOf("defeat1", "defeat2", "defeat3", "defeat4"), SpriteFrame.DEFEAT.map { it.key })
        assertEquals(SpriteFrame.entries.takeLast(4), SpriteFrame.DEFEAT)
        for (hp in 0..120) {
            assertTrue(SpriteFrame.forHealth(hp, 120) !in SpriteFrame.DEFEAT, "$hp HP")
        }
    }

    @Test
    fun `unknown keys fall back to the dragon`() {
        assertEquals(Creature.BEAR, Creature.fromKey("bear"))
        assertEquals(Creature.DRAGON, Creature.fromKey("unicorn"))
        assertEquals(Creature.DRAGON, Creature.fromKey(null))
    }
}
