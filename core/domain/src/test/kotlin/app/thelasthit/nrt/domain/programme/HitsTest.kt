package app.thelasthit.nrt.domain.programme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HitsTest {

    private val programme = planExample()

    /** At every step, the hits earned and the hits still to earn add up to the demon's HP blocks. */
    private fun hitsMatchTheBlocks(programme: Programme) {
        assertEquals(programme.maxHp, HP_PER_HIT * programme.hitsOnOffer)
        assertEquals(programme.hitsOnOffer, programme.earnedHits.size + programme.hitsStillOnOffer, "$programme")
    }

    @Test
    fun `a 12-week programme puts 120 HP on the demon, in 12 blocks`() {
        assertEquals(120, programme.maxHp)
        assertEquals(12, programme.hitsOnOffer)
        assertEquals(120, programme.hp(0))
    }

    @Test
    fun `meeting every week earns a hit for each HP block`() {
        val played = play(programme, "m".repeat(12), ::hitsMatchTheBlocks)
        val after = played.programme
        assertEquals(12, played.hits.size)
        assertEquals(List(12) { EarnedHit.Kind.WEEK_MET }, played.hits.map { it.kind })
        assertEquals(after.maxHp, HP_PER_HIT * played.hits.size)
        assertEquals(0, after.hp(played.hits.size))
    }

    @Test
    fun `with misses, catch-up, a repeat and overtime, the hits still match the HP blocks`() {
        // Week 2 is missed and caught up, week 4 is repeated, 6 and 7 are caught up together, and
        // both NRT-free weeks and the first overtime week are missed before the next is met.
        val played = play(programme, "mxmrmxxmmmmxxxm", ::hitsMatchTheBlocks)
        val after = played.programme
        assertEquals(ProgrammeStatus.COMPLETE, after.status)
        assertEquals(15, after.weeks.size)
        assertEquals(130, after.maxHp)
        assertEquals(13, played.hits.size)
        assertEquals(0, after.hp(played.hits.size))
    }

    @Test
    fun `the hits earned can be worked out again from the weeks, one for each week with HP`() {
        val played = play(programme, "mxmrmxxmmmmxxxm")
        assertEquals(played.hits, played.programme.earnedHits)
        val weeksWithHp = played.programme.weeks.filter { it.earnsHit }.map { it.number }
        assertEquals(weeksWithHp, played.hits.map { it.week }.sorted())
    }

    @Test
    fun `each hit spent takes 10 HP, and only earned hits can be spent`() {
        val after = play(programme, "mm").programme
        assertEquals(110, after.hp(1))
        assertEquals(100, after.hp(2))
        assertThrows<IllegalArgumentException> { after.hp(3) }
        assertThrows<IllegalArgumentException> { after.hp(-1) }
    }
}
