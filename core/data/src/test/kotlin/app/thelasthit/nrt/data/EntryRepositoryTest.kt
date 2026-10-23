package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.HistoryFilter
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.time.DayBoundary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryRepositoryTest {

    private lateinit var data: TestData
    private val gum = "nicorette-gum-uk-2mg"
    private val quickMist = "nicorette-quickmist-uk-1mg"
    private val today = LocalDate.parse("2026-10-23")

    @Before
    fun setUp() = runTest {
        data = TestData()
        data.products.addFromCatalogue(gum)
        data.products.addFromCatalogue(quickMist)
    }

    @After
    fun tearDown() = data.close()

    private suspend fun saved(request: LogRequest): LogResult.Saved {
        val result = data.entries.log(request)
        assertTrue("expected Saved but got $result", result is LogResult.Saved)
        return result as LogResult.Saved
    }

    @Test
    fun loggingSavesTheEntryOnTheRightDay() = runTest {
        val entry = saved(LogRequest(gum, tagKeys = setOf("after_meal"))).entry
        assertEquals(today, entry.logicalDate)
        assertEquals(Dose.mg(2), entry.total)

        val summary = data.entries.observeDay(today).first()
        assertEquals(Dose.mg(2), summary.total)
        assertEquals(setOf("after_meal"), summary.entries.single().tagKeys)
        assertEquals(entry.id, summary.lastDose?.id)
    }

    @Test
    fun aDoseAt0130CountsTowardTheDayBefore() = runTest {
        val entry = saved(LogRequest(gum, at = london("2026-10-23T01:30"))).entry
        assertEquals(LocalDate.parse("2026-10-22"), entry.logicalDate)
    }

    @Test
    fun futureTimesAndUnknownProductsAreRefused() = runTest {
        assertEquals(LogResult.TimeInFuture, data.entries.log(LogRequest(gum, at = data.clock.now.plus(Duration.ofMinutes(10)))))
        assertEquals(LogResult.UnknownProduct, data.entries.log(LogRequest("not-a-product")))
    }

    @Test
    fun theSameDoseFromAnotherSourceWithinAMinuteAsksFirst() = runTest {
        val first = saved(LogRequest(gum, source = EntrySource.WIDGET)).entry
        data.clock.advance(Duration.ofSeconds(20))

        val again = data.entries.log(LogRequest(gum, source = EntrySource.APP_FUNCTION))
        assertEquals(LogResult.PossibleDuplicate(first), again)

        saved(LogRequest(gum, source = EntrySource.APP_FUNCTION, allowDuplicate = true))
        assertEquals(2, data.entries.observeDay(today).first().entries.size)
    }

    @Test
    fun goingOverTheDailyLabelLimitWarnsButStillSaves() = runTest {
        assertNull(saved(LogRequest(gum, quantity = 15, at = london("2026-10-23T09:00"))).labelWarning)
        val over = saved(LogRequest(gum))
        assertEquals(LabelWarning.OverDaily(16, 15), over.labelWarning)
    }

    @Test
    fun theHourlySprayLimitOnlyCountsTheLastHour() = runTest {
        saved(LogRequest(quickMist, quantity = 2, at = london("2026-10-23T13:30")))
        saved(LogRequest(quickMist, quantity = 2, at = london("2026-10-23T14:30")))
        assertNull(saved(LogRequest(quickMist, quantity = 2)).labelWarning)
        val over = saved(LogRequest(quickMist, quantity = 1, allowDuplicate = true))
        assertTrue(over.labelWarning is LabelWarning.OverWindow)
    }

    @Test
    fun deleteHidesAnEntryAndUndoBringsItBack() = runTest {
        val entry = saved(LogRequest(gum)).entry
        data.entries.delete(entry.id)
        assertTrue(data.entries.observeDay(today).first().entries.isEmpty())
        data.entries.undoDelete(entry.id)
        assertEquals(1, data.entries.observeDay(today).first().entries.size)
    }

    @Test
    fun deletedEntriesArePurgedAfter30Days() = runTest {
        val entry = saved(LogRequest(gum)).entry
        data.entries.delete(entry.id)
        data.clock.advance(Duration.ofDays(29))
        assertEquals(0, data.entries.purgeDeleted())
        data.clock.advance(Duration.ofDays(2))
        assertEquals(1, data.entries.purgeDeleted())
        assertNull(data.entries.get(entry.id))
    }

    @Test
    fun editingTheQuantityRefreshesTheDoseAndEditingTheTimeMovesTheDay() = runTest {
        val entry = saved(LogRequest(gum)).entry
        val doubled = data.entries.edit(entry.id, LogRequest(gum, quantity = 2)) as LogResult.Saved
        assertEquals(Dose.mg(4), doubled.entry.total)

        val moved = data.entries.edit(entry.id, LogRequest(gum, quantity = 2, at = london("2026-10-22T20:00"))) as LogResult.Saved
        assertEquals(LocalDate.parse("2026-10-22"), moved.entry.logicalDate)
        assertTrue(data.entries.observeDay(today).first().entries.isEmpty())
        assertEquals(LogResult.NotFound, data.entries.edit("missing", LogRequest(gum)))
    }

    @Test
    fun changingTheDayStartMovesEntriesToTheirNewDay() = runTest {
        val entry = saved(LogRequest(gum, at = london("2026-10-23T03:30"))).entry
        assertEquals(LocalDate.parse("2026-10-22"), entry.logicalDate)

        val earlier = LocalTime.of(3, 0)
        data.settings.setDayStart(earlier)
        data.entries.recalculateLogicalDays(DayBoundary(earlier))
        assertEquals(today, data.entries.get(entry.id)?.logicalDate)
    }

    @Test
    fun theFirstDaySkipsDeletedEntries() = runTest {
        assertNull(data.entries.observeFirstDay().first())
        val old = saved(LogRequest(gum, at = london("2026-10-20T09:00")))
        saved(LogRequest(gum, at = london("2026-10-22T09:00")))
        assertEquals(LocalDate.parse("2026-10-20"), data.entries.observeFirstDay().first())

        data.entries.delete(old.entry.id)
        assertEquals(LocalDate.parse("2026-10-22"), data.entries.observeFirstDay().first())
    }

    @Test
    fun aProductFilterFindsThatProductAcrossAllDaysNewestFirst() = runTest {
        val old = saved(LogRequest(quickMist, at = london("2025-03-01T09:00"))).entry
        val recent = saved(LogRequest(quickMist, quantity = 2, at = london("2026-10-22T21:05"))).entry
        saved(LogRequest(gum, at = london("2026-10-22T12:50")))
        val deleted = saved(LogRequest(quickMist, at = london("2026-10-21T19:00"))).entry
        data.entries.delete(deleted.id)

        val matching = data.entries.observeMatching(HistoryFilter.Product(quickMist)).first()
        assertEquals(listOf(recent.id, old.id), matching.map { it.id })
    }

    @Test
    fun aTagFilterFindsDosesCarryingItWithAllTheirTags() = runTest {
        saved(LogRequest(gum, at = london("2026-10-23T13:40"), tagKeys = setOf("after_meal", "stressed")))
        saved(LogRequest(gum, at = london("2026-10-23T10:55"), tagKeys = setOf("work_break")))
        saved(LogRequest(quickMist, at = london("2026-10-22T12:50"), tagKeys = setOf("after_meal")))
        saved(LogRequest(gum, at = london("2026-10-21T08:00")))

        val matching = data.entries.observeMatching(HistoryFilter.Tag("after_meal")).first()
        assertEquals(listOf(london("2026-10-23T13:40"), london("2026-10-22T12:50")), matching.map { it.occurredAt })
        // Each entry comes back once, with every tag it carries, not just the one filtered by.
        assertEquals(setOf("after_meal", "stressed"), matching.first().tagKeys)
        assertTrue(data.entries.observeMatching(HistoryFilter.Tag("bored")).first().isEmpty())
    }

    @Test
    fun matchingFindsExactlyTheLiveEntriesTheFilterRuleKeeps() = runTest {
        val patch = "niquitin-clear-patch-uk-14mg"
        data.products.addFromCatalogue(patch)
        saved(LogRequest(patch, at = london("2026-10-23T07:30"), tagKeys = setOf("woke_up")))
        saved(LogRequest(gum, at = london("2026-10-23T13:40"), tagKeys = setOf("after_meal", "stressed")))
        saved(LogRequest(gum, quantity = 2, at = london("2026-10-23T10:55"), tagKeys = setOf("work_break")))
        saved(LogRequest(quickMist, at = london("2026-10-23T01:20"), tagKeys = setOf("after_meal"))) // counts toward the 22nd
        saved(LogRequest(quickMist, at = london("2026-10-22T12:50"), tagKeys = setOf("stressed")))
        saved(LogRequest(gum, at = london("2026-10-21T08:00")))
        saved(LogRequest(patch, at = london("2025-03-01T07:00"), tagKeys = setOf("after_meal")))
        val deleted = saved(LogRequest(gum, at = london("2026-10-20T19:00"), tagKeys = setOf("after_meal"))).entry
        data.entries.delete(deleted.id)

        val live = data.entries.observeRange(LocalDate.parse("2025-01-01"), today).first()
        assertEquals(7, live.size)
        val filters = listOf(gum, quickMist, patch, "never-logged").map { HistoryFilter.Product(it) } +
            listOf("after_meal", "stressed", "work_break", "woke_up", "bored").map { HistoryFilter.Tag(it) }
        // HistoryFilter.matches is the rule. The queries must keep exactly what it keeps, newest first.
        for (filter in filters) {
            val expected = live.filter(filter::matches).sortedByDescending { it.occurredAt }
            assertEquals("$filter", expected, data.entries.observeMatching(filter).first())
        }
    }

    @Test
    fun productUsesCountLiveEntriesOfEveryDay() = runTest {
        saved(LogRequest(gum, at = london("2025-03-01T09:00")))
        saved(LogRequest(gum, quantity = 3, at = london("2026-10-23T08:10")))
        saved(LogRequest(quickMist, at = london("2026-10-22T21:05")))
        val deleted = saved(LogRequest(quickMist, at = london("2026-10-21T19:00"))).entry
        data.entries.delete(deleted.id)

        // A dose of three pieces is still one dose.
        assertEquals(mapOf(gum to 2, quickMist to 1), data.entries.observeProductUses().first())
    }

    @Test
    fun dosesPerDayCountLiveEntriesByLogicalDay() = runTest {
        saved(LogRequest(gum, at = london("2026-10-23T13:40")))
        saved(LogRequest(gum, at = london("2026-10-23T08:10")))
        saved(LogRequest(gum, at = london("2026-10-23T01:20"))) // counts toward the 22nd
        saved(LogRequest(gum, at = london("2026-10-20T09:00"))) // before the range
        val deleted = saved(LogRequest(gum, at = london("2026-10-21T09:00"))).entry
        data.entries.delete(deleted.id)

        val counts = data.entries.observeDosesPerDay(LocalDate.parse("2026-10-21"), today).first()
        assertEquals(mapOf(today to 2, LocalDate.parse("2026-10-22") to 1), counts)
    }
}
