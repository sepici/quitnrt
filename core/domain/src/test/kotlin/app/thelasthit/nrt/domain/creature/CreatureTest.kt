package app.thelasthit.nrt.domain.creature

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `unknown keys fall back to the dragon`() {
        assertEquals(Creature.BEAR, Creature.fromKey("bear"))
        assertEquals(Creature.DRAGON, Creature.fromKey("unicorn"))
        assertEquals(Creature.DRAGON, Creature.fromKey(null))
    }
}
