package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.UnlockRepository
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.NewUnlocks
import app.thelasthit.nrt.domain.game.UnlockNews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnlockRepositoryTest {

    private lateinit var data: TestData
    private lateinit var unlocks: UnlockRepository

    @Before
    fun setUp() = runTest {
        data = TestData()
        unlocks = data.unlockRepository()
        data.settings.setCountry("GB")
        data.products.addFromCatalogue(GUM_4)
    }

    @After
    fun tearDown() = data.close()

    private suspend fun storedBadges(): Map<String, Long> = data.database.game().achievements().associate { it.key to it.unlockedAt }

    @Test
    fun nothingLoggedUnlocksNothing() = runTest {
        assertEquals(NewUnlocks.NONE, unlocks.check())
        assertTrue(unlocks.news.value.isEmpty())
        assertTrue(storedBadges().isEmpty())
    }

    @Test
    fun aNewBadgeIsStoredWithItsTimeAndWaitsForItsToastOnce() = runTest {
        data.chew("2026-10-23T15:00")

        assertEquals(NewUnlocks(listOf(Achievement.FIRST_ENTRY), emptyList()), unlocks.check())
        assertEquals(mapOf("first_entry" to london("2026-10-23T15:05").toEpochMilli()), storedBadges())
        assertEquals(listOf(UnlockNews.Badge(Achievement.FIRST_ENTRY)), unlocks.news.value)

        // Checking again finds nothing new, and the badge keeps its first time.
        data.clock.advance(Duration.ofHours(1))
        assertEquals(NewUnlocks.NONE, unlocks.check())
        assertEquals(1, unlocks.news.value.size)
        assertEquals(london("2026-10-23T15:05").toEpochMilli(), storedBadges()["first_entry"])

        unlocks.shown(UnlockNews.Badge(Achievement.FIRST_ENTRY))
        assertTrue(unlocks.news.value.isEmpty())
    }

    @Test
    fun manyAtOnceAsWhenPhaseFourFirstRunsComeInOneSummary() = runTest {
        // A fortnight of tagged doses from before the game looked: written at once, now, so
        // they're backdated too.
        for (day in 9..22) {
            val date = "2026-10-%02d".format(day)
            for (time in listOf("08:00", "13:00")) {
                data.entries.log(LogRequest(GUM_4, at = london("${date}T$time"), tagKeys = setOf("coffee_tea")))
            }
        }

        val found = unlocks.check()
        assertTrue(found.isSummary)
        assertEquals(
            listOf(
                Achievement.FIRST_ENTRY,
                Achievement.LOGGED_3_DAYS,
                Achievement.LOGGED_7_DAYS,
                Achievement.LOGGED_14_DAYS,
                Achievement.STREAK_10_DAYS,
                Achievement.HONEST_LEDGER,
                Achievement.TAGGED_25,
            ),
            found.achievements,
        )
        assertEquals(listOf(UnlockNews.Summary(found.achievements, emptyList())), unlocks.news.value)
        assertEquals(found.achievements.map { it.key }.toSet(), storedBadges().keys)
    }

    @Test
    fun resultsThatAreDueSettleFirstAndBringTheirGear() = runTest {
        data.startGumPlan()
        assertEquals(NewUnlocks.NONE, unlocks.check())

        // Week 1's result is due by now, and nothing has read the programme since.
        data.setTime("2026-11-01T09:00")
        val found = unlocks.check()
        assertEquals(listOf(Achievement.FIRST_WEEK_MET), found.achievements)
        assertEquals(listOf(Gear.IRON_SWORD), found.gear)
        assertEquals(
            listOf(UnlockNews.Badge(Achievement.FIRST_WEEK_MET), UnlockNews.NewGear(Gear.IRON_SWORD)),
            unlocks.news.value,
        )
        assertEquals(london("2026-11-01T09:00").toEpochMilli(), data.database.game().gear().single { it.key == "iron_sword" }.unlockedAt)
    }

    @Test
    fun breathingSessionsCountWhenTheyRanToTheEnd() = runTest {
        data.breathing.record(BreathingPattern.BOX, london("2026-10-23T12:00"), Duration.ofSeconds(60), rounds = 0, ranToEnd = true)
        data.breathing.record(BreathingPattern.BOX, london("2026-10-23T12:10"), Duration.ofSeconds(40), rounds = 2, ranToEnd = false)
        assertEquals(NewUnlocks.NONE, unlocks.check())

        data.breathing.record(BreathingPattern.BOX, london("2026-10-23T12:20"), Duration.ofSeconds(60), rounds = 3, ranToEnd = true)
        assertEquals(listOf(Achievement.BREATHING_1), unlocks.check().achievements)
    }

    @Test
    fun followingChecksOnStartAndAfterEachWrite() = runBlocking {
        // Logged two days late, so it's backdated too.
        data.chew("2026-10-21T12:00")
        val following = launch(Dispatchers.Default) { unlocks.follow() }
        try {
            withTimeout(10_000) { unlocks.news.first { UnlockNews.Badge(Achievement.FIRST_ENTRY) in it } }

            // The next writes are checked as they land.
            data.chew("2026-10-22T12:00")
            data.chew("2026-10-23T12:00")
            withTimeout(10_000) { unlocks.news.first { UnlockNews.Badge(Achievement.LOGGED_3_DAYS) in it } }
            data.breathing.record(BreathingPattern.FOUR_SEVEN_EIGHT, london("2026-10-23T13:00"), Duration.ofSeconds(60), rounds = 3, ranToEnd = true)
            withTimeout(10_000) { unlocks.news.first { UnlockNews.Badge(Achievement.BREATHING_1) in it } }
        } finally {
            following.cancel()
        }
        assertEquals(setOf("first_entry", "honest_ledger", "logged_days_3", "breathing_1"), storedBadges().keys)
    }

    @Test
    fun theStickIsAlwaysThereAndOnlyUnlockedGearCanBeEquipped() = runTest {
        val start = unlocks.loadout.first()
        assertEquals(setOf(Gear.WOODEN_STICK), start.unlocked)
        assertEquals(Gear.WOODEN_STICK, start.equipped)
        assertFalse(unlocks.equip(Gear.IRON_SWORD))
        assertEquals(null, data.settings.current().equippedGear)

        data.startGumPlan()
        data.setTime("2026-11-01T09:00")
        unlocks.check()
        assertTrue(unlocks.equip(Gear.IRON_SWORD))
        assertEquals("iron_sword", data.settings.current().equippedGear)
        val loadout = unlocks.loadout.first()
        assertEquals(setOf(Gear.WOODEN_STICK, Gear.IRON_SWORD), loadout.unlocked)
        assertEquals(Gear.IRON_SWORD, loadout.equipped)

        assertTrue(unlocks.equip(Gear.WOODEN_STICK))
        assertEquals(Gear.WOODEN_STICK, unlocks.loadout.first().equipped)
    }

    @Test
    fun theShelfShowsEveryBadgeWithWhenItWasEarned() = runTest {
        data.chew("2026-10-23T15:00")
        unlocks.check()
        val shelf = unlocks.shelf.first()
        assertEquals(33, shelf.total)
        assertEquals(1, shelf.earned)
        assertEquals(london("2026-10-23T15:05"), shelf.badges.first { it.achievement == Achievement.FIRST_ENTRY }.earnedAt)
    }
}
