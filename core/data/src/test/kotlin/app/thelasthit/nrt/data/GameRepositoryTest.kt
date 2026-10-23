package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.SpendResult
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.programme.HitBalance
import app.thelasthit.nrt.domain.programme.LedgerKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameRepositoryTest {

    private lateinit var data: TestData

    @Before
    fun setUp() {
        data = TestData()
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun thereIsNoDemonToHitBeforeAPlanStarts() = runTest {
        assertNull(data.game.demon())
        assertEquals(SpendResult.NoDemon, data.game.spendHit())
        assertEquals(SpendResult.NoDemon, data.game.comboAttack())
    }

    @Test
    fun aHitCanOnlyBeSpentOnceItsEarned() = runTest {
        data.startGumPlan()
        assertEquals(HitBalance(maxHp = 120, earned = 0, spent = 0), data.game.demon()?.balance)
        assertEquals(SpendResult.NotEnough(HitBalance(120, 0, 0)), data.game.spendHit())

        // Week 1 is met with nothing logged.
        data.setTime("2026-11-01T09:00")
        data.programmes.settle()
        assertEquals(1, data.game.demon()?.balance?.banked)

        val spent = data.game.spendHit()
        assertEquals(SpendResult.Spent(HitBalance(120, 1, 1)), spent)
        assertEquals(110, data.game.demon.first()?.balance?.hp)
        assertTrue(data.game.spendHit() is SpendResult.NotEnough)
        assertEquals(listOf(LedgerKind.WEEK_MET, LedgerKind.HIT), data.database.game().allLedger().map { it.kind })
    }

    @Test
    fun theFiveTimesAttackSpendsFiveBankedHitsAtOnce() = runTest {
        data.startGumPlan()
        // Weeks 1 to 4 are met: four hits, one short.
        data.setTime("2026-11-22T09:00")
        data.programmes.settle()
        assertTrue(data.game.comboAttack() is SpendResult.NotEnough)

        // Week 5's result comes in at 04:00 on Sunday 29 November.
        data.setTime("2026-11-29T09:00")
        data.programmes.settle()
        assertEquals(SpendResult.Spent(HitBalance(120, 5, 5)), data.game.comboAttack())
        val balance = checkNotNull(data.game.demon()).balance
        assertEquals(0, balance.banked)
        assertEquals(70, balance.hp)
        val combo = data.database.game().allLedger().last()
        assertEquals(LedgerKind.COMBO, combo.kind)
        assertEquals(-5, combo.hits)
    }

    @Test
    fun theDemonIsDefeatedOnlyAtZeroHp() = runTest {
        data.startGumPlan()
        assertFalse(data.game.markDefeated())
        // Every week met, and every hit spent.
        data.setTime("2027-01-17T09:00")
        data.programmes.settle()
        repeat(2) { assertTrue(data.game.comboAttack() is SpendResult.Spent) }
        assertFalse(data.game.markDefeated())
        repeat(2) { assertTrue(data.game.spendHit() is SpendResult.Spent) }
        assertEquals(0, data.game.demon()?.balance?.hp)

        assertTrue(data.game.markDefeated(london("2027-01-17T09:05")))
        assertTrue(data.game.markDefeated(london("2027-01-18T10:00")))
        // The first time stands.
        assertEquals(london("2027-01-17T09:05"), data.game.demon()?.demon?.defeatedAt)
        assertTrue(data.game.spendHit() is SpendResult.NotEnough)
    }

    @Test
    fun restylingChangesSettingsBeforeThePlanAndTheDemonAfter() = runTest {
        data.game.restyleDemon("Gary", Creature.DRAGON, "ember")
        assertEquals("Gary", data.settings.current().demonName)

        data.startGumPlan()
        data.game.restyleDemon(" Mildred ", Creature.DRAGON, "frost")
        val demon = checkNotNull(data.game.demon()).demon
        assertEquals("Mildred", demon.name)
        assertEquals("frost", demon.variant)
        // The onboarding choice in settings stays as it was.
        assertEquals("Gary", data.settings.current().demonName)
    }

    @Test
    fun unlocksKeepTheirFirstTime() = runTest {
        val first = london("2026-10-23T15:00")
        assertTrue(data.game.unlockAchievement("first_entry", first))
        assertFalse(data.game.unlockAchievement("first_entry", london("2026-10-24T09:00")))
        assertTrue(data.game.unlockAchievement("logged_3_days", london("2026-10-25T09:00")))
        assertEquals(listOf("first_entry", "logged_3_days"), data.game.achievements.first().keys.toList())
        assertEquals(first, data.game.achievements.first()["first_entry"])

        assertTrue(data.game.unlockGear("wooden_stick", first))
        assertFalse(data.game.unlockGear("wooden_stick"))
        assertEquals(mapOf("wooden_stick" to first), data.game.gear.first())
    }

    @Test
    fun onlyUnlockedGearCanBeEquipped() = runTest {
        assertFalse(data.game.equip("legendary_blade"))
        assertNull(data.settings.current().equippedGear)
        data.game.unlockGear("wooden_stick")
        assertTrue(data.game.equip("wooden_stick"))
        assertEquals("wooden_stick", data.settings.current().equippedGear)
        assertTrue(data.game.equip(null))
        assertNull(data.settings.current().equippedGear)
    }
}
